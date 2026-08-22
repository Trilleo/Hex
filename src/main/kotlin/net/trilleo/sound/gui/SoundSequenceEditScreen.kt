package net.trilleo.sound.gui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.trilleo.config.ConfigCategory
import net.trilleo.config.gui.ConfigEntryList
import net.trilleo.sound.SoundConfig
import net.trilleo.sound.SoundIds
import net.trilleo.sound.SoundPlayer
import net.trilleo.sound.SoundScheduler
import net.trilleo.sound.model.NoteNames
import net.trilleo.sound.model.SoundSequence
import net.trilleo.sound.model.SoundStep
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The sequence timeline — a multitrack editor for the sounds in one [SoundSequence].
 *
 * A ruler across the top, eight lanes under it, and one clip per step sitting where its time puts it. Drag a
 * clip to retime it, drag it up or down to move it between lanes, drag on empty space to marquee-select, and
 * drag on the ruler to scrub. The panel on the right is the inspector for whatever is selected.
 *
 * ### Why the times are absolute
 *
 * A clip sits where it sits: dragging one moves that step and nothing else. Storing each step as a delay
 * after the one before it would make a single drag an edit of two steps and a delete an edit of a third, and
 * the editor would spend its life keeping a chain consistent instead of drawing a picture of it. The
 * inspector still offers the gap from the previous step, because that is how a rhythm gets described — but it
 * is derived on the way in and out, not stored.
 *
 * ### The grid is the tempo
 *
 * The ruler is marked in seconds because that is what the values are, and ruled in beats because that is how
 * anything rhythmic is built. Changing the BPM re-rules the grid without moving a single step, which is the
 * other half of storing absolute times. Hold **Alt** while dragging to ignore the grid entirely.
 *
 * ### The inspector is a settings list
 *
 * Rather than hand-rolling fields, the right-hand panel is a [ConfigEntryList] fed a throwaway
 * [ConfigCategory] closing over the selected step — the same trick every "edit one object" screen in this mod
 * already uses. That is what gets the sound picker, the reset buttons and the tooltips for free, and it is
 * why a step's sound is chosen exactly the way a reminder's is.
 */
class SoundSequenceEditScreen(
    private val parent: SoundSequencesScreen?,
    private val sequence: SoundSequence,
) : Screen(Component.translatable("hex.sounds.edit.title")) {

    /** What a drag started on, held for its whole life so it keeps tracking outside the rectangle it began in. */
    private enum class Drag { CLIP, MARQUEE, SCRUB }

    /** How finely the grid is ruled, as divisions of a beat. */
    private enum class Snap(val perBeat: Int, val key: String) {
        OFF(0, "off"),
        QUARTER(1, "quarter"),
        EIGHTH(2, "eighth"),
        SIXTEENTH(4, "sixteenth"),
    }

    // ---- geometry, set in init() and read while drawing ---------------------------------------------------

    private var timelineX = 0
    private var timelineY = 0
    private var timelineW = 0
    private var lanesTop = 0
    private var lanesBottom = 0

    /** Zoom, in pixels per second of sequence. */
    private var pxPerSecond = 120.0

    /** Horizontal pan, in milliseconds at the left edge of the timeline. Never negative. */
    private var scrollMillis = 0.0

    // ---- editing state ------------------------------------------------------------------------------------

    /**
     * The selected steps.
     *
     * [SoundStep] is not a data class, so this is an identity set without having to be asked to be — which is
     * what stops selecting one step from also selecting an identical-looking one elsewhere on the timeline.
     */
    private val selection = LinkedHashSet<SoundStep>()

    private var dragging: Drag? = null

    /**
     * The clip the current drag started on, and where in it the cursor took hold — so it keeps its position
     * under the pointer instead of snapping its left edge there.
     *
     * The grabbed clip is the anchor the whole selection moves relative to, and it has to be *this* step
     * rather than whichever one happens to be first in the selection: Ctrl-clicking three clips and then
     * dragging the third would otherwise measure the movement against the first and jump.
     */
    private var grabStep: SoundStep? = null
    private var grabMillis = 0.0
    private var grabLane = 0

    private var marqueeFromX = 0.0
    private var marqueeFromY = 0.0
    private var marqueeToX = 0.0
    private var marqueeToY = 0.0

    private var snap = Snap.SIXTEENTH

    /**
     * Snapshots of the step list, most recent last.
     *
     * Snapshot-based rather than a command pattern: a step list is a few dozen small objects, so copying it
     * costs less than the machinery for describing an edit would, and every edit path gets undo by calling
     * one method before it mutates anything.
     */
    // Fully qualified: this file imports java.util.* for Locale, and java.util.ArrayDeque would otherwise
    // win the name and bring its throw-on-empty removeLast with it.
    private val undoStack = kotlin.collections.ArrayDeque<List<SoundStep>>()

    // ---- playback -----------------------------------------------------------------------------------------

    private var previewHandle = SoundScheduler.NO_HANDLE
    private var playing = false
    private var previewStartNanos = 0L

    /** Where the playhead sits when nothing is playing — the point a Play would start from. */
    private var scrubMillis = 0.0

    // ---- widgets ------------------------------------------------------------------------------------------

    private var inspector: ConfigEntryList? = null
    private var playButton: Button? = null
    private var snapButton: Button? = null

    override fun init() {
        val inspectorX = width - INSPECTOR_W
        timelineX = MARGIN
        timelineY = TOP
        timelineW = (inspectorX - MARGIN * 2).coerceAtLeast(MIN_TIMELINE_W)
        lanesTop = timelineY + RULER_H
        lanesBottom = (height - FOOTER_H).coerceAtLeast(lanesTop + LANE_H)

        inspector = addRenderableWidget(
            ConfigEntryList(minecraft, INSPECTOR_W, height - TOP - FOOTER_H, TOP, this),
        ).apply { x = inspectorX }

        layoutFooter()
        rebuildInspector()
    }

    private fun layoutFooter() {
        val y = height - BUTTON_H - GAP
        var x = MARGIN

        playButton = addRenderableWidget(
            Button.builder(playLabel()) { togglePlay() }
                .bounds(x, y, BUTTON_W, BUTTON_H)
                .tooltip(Tooltip.create(Component.translatable("hex.sounds.edit.play.tooltip")))
                .build(),
        )
        x += BUTTON_W + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.sounds.edit.add_step")) { addStep() }
                .bounds(x, y, BUTTON_W, BUTTON_H)
                .tooltip(Tooltip.create(Component.translatable("hex.sounds.edit.add_step.tooltip")))
                .build(),
        )
        x += BUTTON_W + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.sounds.edit.duplicate")) { duplicateSelection() }
                .bounds(x, y, BUTTON_W, BUTTON_H)
                .tooltip(Tooltip.create(Component.translatable("hex.sounds.edit.duplicate.tooltip")))
                .build(),
        )
        x += BUTTON_W + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.sounds.edit.delete")) { deleteSelection() }
                .bounds(x, y, BUTTON_W, BUTTON_H)
                .tooltip(Tooltip.create(Component.translatable("hex.sounds.edit.delete.tooltip")))
                .build(),
        )
        x += BUTTON_W + GAP

        snapButton = addRenderableWidget(
            Button.builder(snapLabel()) { cycleSnap() }
                .bounds(x, y, BUTTON_W, BUTTON_H)
                .tooltip(Tooltip.create(Component.translatable("hex.sounds.edit.snap.tooltip")))
                .build(),
        )

        addRenderableWidget(
            Button.builder(Component.translatable("hex.sounds.edit.done")) { onClose() }
                .bounds(width - MARGIN - BUTTON_W, y, BUTTON_W, BUTTON_H).build(),
        )
    }

    // ---- coordinates --------------------------------------------------------------------------------------

    /** The x pixel a step at [millis] is drawn at. */
    private fun xOf(millis: Double): Int =
        timelineX + ((millis - scrollMillis) / MILLIS_PER_SECOND * pxPerSecond).roundToInt()

    /** The time the x pixel [x] falls on. */
    private fun msOf(x: Double): Double =
        scrollMillis + (x - timelineX) / pxPerSecond * MILLIS_PER_SECOND

    /** How many milliseconds one grid division is, or zero when the grid is off. */
    private fun gridMillis(): Double =
        if (snap == Snap.OFF) 0.0 else MILLIS_PER_MINUTE / sequence.bpm / snap.perBeat

    /** [millis] on the grid, or unchanged when the grid is off or [free] is set. */
    private fun snapped(millis: Double, free: Boolean): Double {
        val grid = gridMillis()
        if (free || grid <= 0.0) return millis
        return (millis / grid).roundToInt() * grid
    }

    private fun laneAt(y: Double): Int = ((y - lanesTop) / LANE_H).toInt().coerceIn(0, SoundStep.LANE_MAX)

    private fun inTimeline(x: Double, y: Double): Boolean =
        x >= timelineX && x < timelineX + timelineW && y >= lanesTop && y < lanesBottom

    private fun inRuler(x: Double, y: Double): Boolean =
        x >= timelineX && x < timelineX + timelineW && y >= timelineY && y < timelineY + RULER_H

    /** The step whose clip covers ([x], [y]), searched last-drawn-first so overlaps pick the top one. */
    private fun clipAt(x: Double, y: Double): SoundStep? {
        val lane = laneAt(y)
        return sequence.steps.lastOrNull { step ->
            step.lane == lane && x >= xOf(step.atMillis) && x < xOf(step.atMillis) + CLIP_W
        }
    }

    // ---- input --------------------------------------------------------------------------------------------

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (inRuler(event.x(), event.y())) {
            dragging = Drag.SCRUB
            scrubTo(event.x())
            return true
        }
        if (inTimeline(event.x(), event.y())) {
            val hit = clipAt(event.x(), event.y())
            if (hit == null) {
                dragging = Drag.MARQUEE
                marqueeFromX = event.x()
                marqueeFromY = event.y()
                marqueeToX = event.x()
                marqueeToY = event.y()
                if (!event.hasControlDownWithQuirk()) selection.clear()
                rebuildInspector()
                return true
            }
            if (event.hasControlDownWithQuirk()) {
                if (!selection.remove(hit)) selection.add(hit)
            } else if (hit !in selection) {
                selection.clear()
                selection.add(hit)
            }
            rebuildInspector()

            // Before the drag begins, so one Ctrl+Z puts every dragged step back where it was.
            pushUndo()
            dragging = Drag.CLIP
            grabStep = hit
            grabMillis = msOf(event.x()) - hit.atMillis
            grabLane = hit.lane
            return true
        }
        // The inspector, the footer, and anything else the screen owns.
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        when (dragging) {
            Drag.SCRUB -> {
                scrubTo(event.x())
                return true
            }

            Drag.MARQUEE -> {
                marqueeToX = event.x()
                marqueeToY = event.y()
                return true
            }

            Drag.CLIP -> {
                // Alt is read off the drag event rather than polled, so releasing it mid-drag takes effect on
                // the next movement exactly as holding it did.
                val free = event.hasAltDown()
                val anchor = grabStep ?: return true
                val wantedAt = snapped(msOf(event.x()) - grabMillis, free).coerceAtLeast(0.0)
                val deltaMillis = wantedAt - anchor.atMillis
                val deltaLane = laneAt(event.y()) - grabLane

                // Bounded as a group, so dragging a selection into the left edge slides it up against the
                // edge rather than piling every step onto zero.
                val earliest = selection.minOf { it.atMillis }
                val shift = max(deltaMillis, -earliest)
                    .coerceAtMost(SoundStep.MAX_MILLIS - selection.maxOf { it.atMillis })
                val lowestLane = selection.minOf { it.lane }
                val highestLane = selection.maxOf { it.lane }
                val laneShift = deltaLane.coerceIn(-lowestLane, SoundStep.LANE_MAX - highestLane)

                if (shift != 0.0 || laneShift != 0) {
                    selection.forEach {
                        it.atMillis = (it.atMillis + shift).coerceIn(0.0, SoundStep.MAX_MILLIS)
                        it.lane = (it.lane + laneShift).coerceIn(0, SoundStep.LANE_MAX)
                    }
                    grabLane += laneShift
                    SoundConfig.markDirty()
                }
                return true
            }

            null -> return super.mouseDragged(event, dragX, dragY)
        }
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        when (dragging) {
            Drag.MARQUEE -> {
                selectInMarquee()
                rebuildInspector()
            }

            Drag.CLIP -> {
                // Sorted only now: reordering mid-drag would shuffle the list under the selection, and the
                // scheduler does not care about order until the sequence is played.
                sequence.steps.sortBy { it.atMillis }
                SoundConfig.save()
                rebuildInspector()
            }

            else -> Unit
        }
        if (dragging != null) {
            dragging = null
            grabStep = null
            return true
        }
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!inTimeline(mouseX, mouseY) && !inRuler(mouseX, mouseY)) {
            // Outside our rectangle, so the inspector keeps its own scrolling.
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        }
        if (controlHeld()) {
            // Zoom about the cursor: the instant under the pointer must stay under it, or zooming walks the
            // part you were looking at off the screen.
            val anchorMillis = msOf(mouseX)
            pxPerSecond = (pxPerSecond * ZOOM_STEP.pow(scrollY)).coerceIn(ZOOM_MIN, ZOOM_MAX)
            scrollMillis = (anchorMillis - (mouseX - timelineX) / pxPerSecond * MILLIS_PER_SECOND)
                .coerceAtLeast(0.0)
        } else {
            val perNotch = PAN_PIXELS / pxPerSecond * MILLIS_PER_SECOND
            scrollMillis = (scrollMillis - scrollY * perNotch).coerceAtLeast(0.0)
        }
        return true
    }

    /**
     * Whether Ctrl is held right now.
     *
     * Polled rather than read off an event, because [mouseScrolled] is the one input hook in this build that
     * is not handed an event carrying its modifiers — so zoom-on-Ctrl-scroll has no other way to ask.
     */
    private fun controlHeld(): Boolean {
        val window = minecraft.window
        return InputConstants.isKeyDown(window, InputConstants.KEY_LCONTROL) ||
            InputConstants.isKeyDown(window, InputConstants.KEY_RCONTROL)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        // super first, deliberately. Escape is answered by Screen before any child sees it, and a focused
        // EditBox in the inspector has to get Space, Delete and the arrows before the transport does —
        // otherwise typing a sound id into the inspector would start and stop playback.
        if (super.keyPressed(event)) return true

        if (event.hasControlDownWithQuirk()) {
            return when (event.key()) {
                InputConstants.KEY_Z -> { undo(); true }
                InputConstants.KEY_D -> { duplicateSelection(); true }
                InputConstants.KEY_A -> { selectAll(); true }
                else -> false
            }
        }

        return when {
            event.key() == InputConstants.KEY_SPACE -> { togglePlay(); true }
            event.key() == InputConstants.KEY_DELETE -> { deleteSelection(); true }
            event.isLeft -> { nudge(-nudgeStep()); true }
            event.isRight -> { nudge(nudgeStep()); true }
            event.isUp -> { nudgeLane(-1); true }
            event.isDown -> { nudgeLane(1); true }
            else -> false
        }
    }

    /** One grid division, or a tenth of a second when the grid is off — something has to move. */
    private fun nudgeStep(): Double = gridMillis().takeIf { it > 0.0 } ?: FREE_NUDGE_MILLIS

    // ---- editing ------------------------------------------------------------------------------------------

    private fun pushUndo() {
        undoStack.addLast(sequence.steps.map { it.copy() })
        while (undoStack.size > UNDO_DEPTH) undoStack.removeFirst()
    }

    private fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        sequence.steps = previous.toMutableList()
        // The restored steps are different objects, so anything the selection still pointed at is gone.
        selection.clear()
        SoundConfig.save()
        rebuildInspector()
    }

    private fun addStep() {
        if (sequence.steps.size >= SoundSequence.MAX_STEPS) return
        pushUndo()
        val step = SoundStep().also {
            it.atMillis = snapped(playheadMillis(), free = false).coerceIn(0.0, SoundStep.MAX_MILLIS)
            it.lane = selection.firstOrNull()?.lane ?: 0
        }
        sequence.steps.add(step)
        sequence.steps.sortBy { it.atMillis }
        selection.clear()
        selection.add(step)
        SoundConfig.save()
        rebuildInspector()
    }

    private fun duplicateSelection() {
        if (selection.isEmpty()) return
        if (sequence.steps.size + selection.size > SoundSequence.MAX_STEPS) return
        pushUndo()
        // Offset by one grid division so the copy is visible rather than hidden exactly under the original.
        val offset = gridMillis().takeIf { it > 0.0 } ?: FREE_NUDGE_MILLIS
        val copies = selection.map { source ->
            source.copy().also { it.atMillis = (it.atMillis + offset).coerceAtMost(SoundStep.MAX_MILLIS) }
        }
        sequence.steps.addAll(copies)
        sequence.steps.sortBy { it.atMillis }
        selection.clear()
        selection.addAll(copies)
        SoundConfig.save()
        rebuildInspector()
    }

    private fun deleteSelection() {
        if (selection.isEmpty()) return
        pushUndo()
        sequence.steps.removeAll { it in selection }
        selection.clear()
        SoundConfig.save()
        rebuildInspector()
    }

    private fun selectAll() {
        selection.clear()
        selection.addAll(sequence.steps)
        rebuildInspector()
    }

    private fun nudge(millis: Double) {
        if (selection.isEmpty()) return
        pushUndo()
        val earliest = selection.minOf { it.atMillis }
        val latest = selection.maxOf { it.atMillis }
        val shift = millis.coerceIn(-earliest, SoundStep.MAX_MILLIS - latest)
        selection.forEach { it.atMillis = (it.atMillis + shift).coerceIn(0.0, SoundStep.MAX_MILLIS) }
        sequence.steps.sortBy { it.atMillis }
        SoundConfig.markDirty()
        rebuildInspector()
    }

    private fun nudgeLane(delta: Int) {
        if (selection.isEmpty()) return
        pushUndo()
        val lowest = selection.minOf { it.lane }
        val highest = selection.maxOf { it.lane }
        val shift = delta.coerceIn(-lowest, SoundStep.LANE_MAX - highest)
        selection.forEach { it.lane = (it.lane + shift).coerceIn(0, SoundStep.LANE_MAX) }
        SoundConfig.markDirty()
        rebuildInspector()
    }

    private fun selectInMarquee() {
        val left = min(marqueeFromX, marqueeToX)
        val right = max(marqueeFromX, marqueeToX)
        val top = min(marqueeFromY, marqueeToY)
        val bottom = max(marqueeFromY, marqueeToY)
        // A click that never moved is a click on empty space, and has already cleared the selection.
        if (abs(right - left) < MARQUEE_MIN && abs(bottom - top) < MARQUEE_MIN) return
        sequence.steps.forEach { step ->
            val clipLeft = xOf(step.atMillis).toDouble()
            val clipTop = (lanesTop + step.lane * LANE_H).toDouble()
            val overlaps = clipLeft + CLIP_W >= left && clipLeft <= right &&
                clipTop + LANE_H >= top && clipTop <= bottom
            if (overlaps) selection.add(step)
        }
    }

    private fun cycleSnap() {
        snap = Snap.entries[(snap.ordinal + 1) % Snap.entries.size]
        snapButton?.message = snapLabel()
    }

    // ---- transport ----------------------------------------------------------------------------------------

    private fun togglePlay() {
        if (playing) stopPlaying() else startPlaying()
    }

    private fun startPlaying() {
        SoundPlayer.stop(previewHandle)
        // Rewind when the playhead is already at the end, so pressing Play twice replays rather than doing
        // nothing at all.
        if (scrubMillis >= sequence.durationMillis()) scrubMillis = 0.0
        previewHandle = SoundPlayer.playSequence(minecraft, sequence, fromMillis = scrubMillis)
        previewStartNanos = System.nanoTime() - (scrubMillis * NANOS_PER_MILLI).toLong()
        playing = true
        playButton?.message = playLabel()
    }

    private fun stopPlaying() {
        SoundPlayer.stop(previewHandle)
        previewHandle = SoundScheduler.NO_HANDLE
        // Leave the playhead where it stopped, so Play resumes from there.
        scrubMillis = playheadMillis().coerceIn(0.0, SoundStep.MAX_MILLIS)
        playing = false
        playButton?.message = playLabel()
    }

    private fun scrubTo(x: Double) {
        if (playing) stopPlaying()
        scrubMillis = msOf(x).coerceIn(0.0, SoundStep.MAX_MILLIS)
    }

    /**
     * Where the playhead is right now.
     *
     * Measured against the same monotonic clock the scheduler queues against, so the line and the sounds
     * cannot drift apart however long the sequence runs or however the frame rate moves.
     */
    private fun playheadMillis(): Double = if (playing) {
        (System.nanoTime() - previewStartNanos) / NANOS_PER_MILLI
    } else {
        scrubMillis
    }

    private fun playLabel(): Component =
        Component.translatable(if (playing) "hex.sounds.edit.stop" else "hex.sounds.edit.play")

    private fun snapLabel(): Component = Component.translatable("hex.sounds.edit.snap." + snap.key)

    // ---- inspector ----------------------------------------------------------------------------------------

    private fun rebuildInspector() {
        val category = buildInspector()
        inspector?.show(listOf(category.title to category.entries), preserveScroll = true)
    }

    /**
     * The rows for whatever is selected: one step's own settings, or the sequence's when the selection is
     * empty or covers several.
     *
     * A throwaway category closing over the model, exactly as [net.trilleo.region.gui.RegionEditScreen] does
     * — which is what makes a step's sound a real sound row, picker and all, rather than a text field that
     * happens to hold an id.
     */
    private fun buildInspector(): ConfigCategory = ConfigCategory.build("sound_step") {
        val step = selection.singleOrNull()
        if (step == null) {
            sequenceRows(this)
            return@build
        }

        sound(
            "step_sound",
            default = SoundStep.DEFAULT_SOUND,
            optional = false,
            // A step naming a sequence would recurse; the model strips the marker anyway, and offering it
            // here would be offering something that silently undoes itself.
            sequences = false,
            get = { step.sound },
            set = {
                val wasNote = NoteNames.isNoteBlock(step.sound)
                step.sound = it
                SoundConfig.save()
                // Crossing the note-block boundary swaps which pitch row applies, so the list has to be
                // rebuilt rather than merely redrawn.
                if (wasNote != NoteNames.isNoteBlock(it)) rebuildInspector()
            },
        )

        if (NoteNames.isNoteBlock(step.sound)) {
            slider(
                "step_note",
                min = 0.0,
                max = (NoteNames.COUNT - 1).toDouble(),
                step = 1.0,
                default = NoteNames.MIDDLE.toDouble(),
                get = { NoteNames.noteOf(step.pitch).toDouble() },
                set = { step.pitch = NoteNames.pitchOf(it.roundToInt()); SoundConfig.markDirty() },
                // literal-by-way-of-format: a note name is the same twelve symbols in every language.
                format = { NoteNames.nameOf(it.roundToInt()) },
            )
        } else {
            slider(
                "step_pitch",
                min = SoundStep.PITCH_MIN,
                max = SoundStep.PITCH_MAX,
                step = 0.05,
                default = 1.0,
                get = { step.pitch },
                set = { step.pitch = it; SoundConfig.markDirty() },
                format = { String.format(Locale.ROOT, "%.2f", it) },
            )
        }

        slider(
            "step_volume",
            min = SoundStep.VOLUME_MIN,
            max = SoundStep.VOLUME_MAX,
            step = 0.05,
            default = 1.0,
            get = { step.volume },
            set = { step.volume = it; SoundConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.0f%%", it * 100) },
        )

        text(
            "step_at",
            default = "0.00",
            get = { String.format(Locale.ROOT, "%.2f", step.atMillis / MILLIS_PER_SECOND) },
            set = { typed ->
                typed.trim().toDoubleOrNull()?.let {
                    step.atMillis = (it * MILLIS_PER_SECOND).coerceIn(0.0, SoundStep.MAX_MILLIS)
                    sequence.steps.sortBy { s -> s.atMillis }
                    SoundConfig.markDirty()
                }
            },
            validate = { typed ->
                val seconds = typed.trim().toDoubleOrNull()
                when {
                    seconds == null -> Component.translatable("hex.sounds.edit.at.invalid")
                    seconds < 0.0 || seconds * MILLIS_PER_SECOND > SoundStep.MAX_MILLIS ->
                        Component.translatable("hex.sounds.edit.at.range")
                    else -> null
                }
            },
        )

        slider(
            "step_lane",
            min = 0.0,
            max = SoundStep.LANE_MAX.toDouble(),
            step = 1.0,
            default = 0.0,
            get = { step.lane.toDouble() },
            set = { step.lane = it.roundToInt(); SoundConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.0f", it + 1) },
        )
    }

    /** The sequence's own settings, shown when no single step is selected. */
    private fun sequenceRows(builder: ConfigCategory.Builder) = with(builder) {
        text(
            "seq_name",
            default = "",
            get = { sequence.name },
            set = { sequence.name = it; SoundConfig.markDirty() },
        )
        slider(
            "seq_bpm",
            min = SoundSequence.BPM_MIN,
            max = SoundSequence.BPM_MAX,
            step = 1.0,
            default = SoundSequence.DEFAULT_BPM,
            get = { sequence.bpm },
            set = { sequence.bpm = it; SoundConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.0f", it) },
        )
        toggle(
            "seq_loop",
            default = false,
            get = { sequence.loop },
            set = { sequence.loop = it; SoundConfig.save(); rebuildInspector() },
        )
        if (sequence.loop) {
            slider(
                "seq_loop_count",
                min = 1.0,
                max = SoundSequence.LOOP_MAX.toDouble(),
                step = 1.0,
                default = 1.0,
                get = { sequence.loopCount.toDouble() },
                set = { sequence.loopCount = it.roundToInt(); SoundConfig.markDirty() },
                format = { String.format(Locale.ROOT, "%.0f", it) },
            )
        }
    }

    // ---- rendering ----------------------------------------------------------------------------------------

    override fun extractBackground(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(extractor, mouseX, mouseY, delta)
        extractor.text(font, title, MARGIN, TITLE_Y, TITLE_COLOR)
        // literal: the sequence's name is the player's own words.
        extractor.text(font, Component.literal(sequence.name), MARGIN + font.width(title) + GAP * 2, TITLE_Y, SUB_COLOR)
    }

    override fun extractRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        // The editor is its own frame clock. The HUD callback that normally drives the scheduler does not run
        // while a screen is open with no world behind it, and this screen is reachable from the title screen.
        SoundScheduler.advance(Minecraft.getInstance())
        if (playing && !SoundScheduler.isActive(previewHandle)) {
            // The last step has fired. Park the playhead at the end rather than letting it run off.
            scrubMillis = sequence.totalMillis()
            playing = false
            playButton?.message = playLabel()
        }

        super.extractRenderState(extractor, mouseX, mouseY, delta)

        drawRuler(extractor)
        drawLanes(extractor)
        drawPlayhead(extractor)
        drawMarquee(extractor)
    }

    private fun drawRuler(extractor: GuiGraphicsExtractor) {
        val right = timelineX + timelineW
        extractor.fill(timelineX, timelineY, right, timelineY + RULER_H, RULER_BG)
        extractor.horizontalLine(timelineX, right - 1, timelineY + RULER_H - 1, SEPARATOR)

        // Whole seconds, labelled. Stepped in seconds rather than in pixels so the labels stay on round
        // numbers however far the view is zoomed.
        val firstSecond = (scrollMillis / MILLIS_PER_SECOND).toInt()
        var second = firstSecond
        while (true) {
            val x = xOf(second * MILLIS_PER_SECOND)
            if (x >= right) break
            if (x >= timelineX) {
                extractor.verticalLine(x, timelineY + 2, timelineY + RULER_H - 1, TICK)
                extractor.text(font, "${second}s", x + 2, timelineY + 2, RULER_TEXT)
            }
            second++
        }
    }

    private fun drawLanes(extractor: GuiGraphicsExtractor) {
        val right = timelineX + timelineW
        // Corner form: enableScissor takes two corners while outline takes a size. Spelled out rather than
        // computed so the two cannot be confused for each other.
        extractor.enableScissor(timelineX, lanesTop, right, lanesBottom)

        for (lane in 0..SoundStep.LANE_MAX) {
            val top = lanesTop + lane * LANE_H
            if (top >= lanesBottom) break
            extractor.fill(timelineX, top, right, min(top + LANE_H, lanesBottom), if (lane % 2 == 0) LANE_A else LANE_B)
        }

        drawGrid(extractor, right)

        sequence.steps.forEach { step -> drawClip(extractor, step, right) }

        extractor.disableScissor()
    }

    private fun drawGrid(extractor: GuiGraphicsExtractor, right: Int) {
        val beat = MILLIS_PER_MINUTE / sequence.bpm
        if (beat <= 0.0) return
        val divisions = max(1, snap.perBeat)
        val step = beat / divisions
        // Skip a grid too fine to read; drawing a line every pixel is noise, not information.
        if (step / MILLIS_PER_SECOND * pxPerSecond < MIN_GRID_PIXELS) return

        var index = (scrollMillis / step).toInt()
        while (true) {
            val x = xOf(index * step)
            if (x >= right) break
            if (x >= timelineX) {
                val onBeat = index % divisions == 0
                extractor.verticalLine(x, lanesTop, lanesBottom, if (onBeat) GRID_BEAT else GRID_DIVISION)
            }
            index++
        }
    }

    private fun drawClip(extractor: GuiGraphicsExtractor, step: SoundStep, right: Int) {
        val x = xOf(step.atMillis)
        if (x + CLIP_W < timelineX || x > right) return
        val top = lanesTop + step.lane * LANE_H + CLIP_PAD
        if (top >= lanesBottom) return
        val bottom = top + LANE_H - CLIP_PAD * 2
        val selected = step in selection

        extractor.fill(x, top, x + CLIP_W, bottom, if (selected) CLIP_SELECTED_BG else CLIP_BG)

        // Volume as fill height, drawn up from the bottom so a quiet step reads as a short block — the one
        // thing about a step that is worth seeing without selecting it.
        val fillHeight = ((bottom - top) * step.volume).roundToInt()
        if (fillHeight > 0) {
            extractor.fill(x, bottom - fillHeight, x + CLIP_W, bottom, if (selected) CLIP_SELECTED else CLIP_LEVEL)
        }
        extractor.outline(x, top, CLIP_W, bottom - top, if (selected) BORDER_SELECTED else BORDER)

        // Scaled down: a clip is 44 pixels wide and a sound name is not. The position is divided by the scale
        // because the scale applies to it too.
        val label = clipLabel(step)
        extractor.pose().pushMatrix()
        extractor.pose().scale(CLIP_TEXT_SCALE)
        extractor.text(
            font,
            label,
            ((x + 2) / CLIP_TEXT_SCALE).toInt(),
            ((top + 2) / CLIP_TEXT_SCALE).toInt(),
            CLIP_TEXT,
        )
        extractor.pose().popMatrix()
    }

    /** The last segment of the sound's id, plus its note when it is a note block. */
    private fun clipLabel(step: SoundStep): String {
        val name = SoundIds.tinyName(step.sound)
        val note = if (NoteNames.isNoteBlock(step.sound)) " " + NoteNames.nameOfPitch(step.pitch) else ""
        val available = ((CLIP_W - 4) / CLIP_TEXT_SCALE).toInt()
        val full = name + note
        if (font.width(full) <= available) return full
        return font.plainSubstrByWidth(full, available)
    }

    private fun drawPlayhead(extractor: GuiGraphicsExtractor) {
        val x = xOf(playheadMillis())
        if (x < timelineX || x > timelineX + timelineW) return
        // Outside the scissor, so the line is never clipped mid-pixel against the lane area's edge.
        extractor.fill(x, timelineY, x + 1, lanesBottom, PLAYHEAD)
    }

    private fun drawMarquee(extractor: GuiGraphicsExtractor) {
        if (dragging != Drag.MARQUEE) return
        // Clamped to the lane area: a drag that leaves the timeline keeps selecting, but the rectangle it
        // draws must not paint across the inspector sitting beside it.
        val left = min(marqueeFromX, marqueeToX).toInt().coerceIn(timelineX, timelineX + timelineW)
        val right = max(marqueeFromX, marqueeToX).toInt().coerceIn(timelineX, timelineX + timelineW)
        val top = min(marqueeFromY, marqueeToY).toInt().coerceIn(lanesTop, lanesBottom)
        val bottom = max(marqueeFromY, marqueeToY).toInt().coerceIn(lanesTop, lanesBottom)
        extractor.fill(left, top, right, bottom, MARQUEE_FILL)
        extractor.outline(left, top, right - left, bottom - top, MARQUEE_BORDER)
    }

    // ---- lifecycle ----------------------------------------------------------------------------------------

    override fun onClose() {
        stopPlaying()
        SoundPlayer.stopAll()
        minecraft.setScreen(parent)
    }

    override fun removed() {
        // Edits mark the config dirty as they happen; this makes leaving a definite save point rather than
        // waiting on the debounce. Idempotent, which is what makes it safe here — Minecraft calls this for
        // every hand-off, the sound picker included, not only for a real exit.
        sequence.normalize()
        SoundConfig.save()
        SoundPlayer.stopAll()
        parent?.refreshRows()
    }

    private companion object {
        const val MARGIN = 8
        const val GAP = 6
        const val TITLE_Y = 10
        const val TOP = 26
        const val RULER_H = 14
        const val LANE_H = 22
        const val FOOTER_H = 30
        const val BUTTON_H = 20
        const val BUTTON_W = 66
        const val INSPECTOR_W = 260
        const val MIN_TIMELINE_W = 120

        const val CLIP_W = 44
        const val CLIP_PAD = 2
        const val CLIP_TEXT_SCALE = 0.75f

        const val ZOOM_MIN = 20.0
        const val ZOOM_MAX = 800.0
        const val ZOOM_STEP = 1.1
        const val PAN_PIXELS = 60.0
        const val MIN_GRID_PIXELS = 4.0
        const val MARQUEE_MIN = 3.0

        const val UNDO_DEPTH = 32
        const val FREE_NUDGE_MILLIS = 10.0

        const val MILLIS_PER_SECOND = 1000.0
        const val MILLIS_PER_MINUTE = 60_000.0
        const val NANOS_PER_MILLI = 1_000_000.0

        const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        const val SUB_COLOR = 0xFF909090.toInt()
        const val RULER_BG = 0xFF1E1E1E.toInt()
        const val RULER_TEXT = 0xFFB0B0B0.toInt()
        const val TICK = 0xFF707070.toInt()
        const val SEPARATOR = 0xFF505050.toInt()
        const val LANE_A = 0xFF2A2A2A.toInt()
        const val LANE_B = 0xFF242424.toInt()
        const val GRID_BEAT = 0xFF454545.toInt()
        const val GRID_DIVISION = 0xFF333333.toInt()
        const val CLIP_BG = 0xFF1C3A4A.toInt()
        const val CLIP_LEVEL = 0xFF3A7CA5.toInt()
        const val CLIP_SELECTED_BG = 0xFF4A3A1C.toInt()
        const val CLIP_SELECTED = 0xFFC79A3A.toInt()
        const val BORDER = 0xFF000000.toInt()
        const val BORDER_SELECTED = 0xFFFFFFFF.toInt()
        const val CLIP_TEXT = 0xFFE8E8E8.toInt()
        const val PLAYHEAD = 0xFFFF5555.toInt()
        const val MARQUEE_FILL = 0x40FFFFFF
        const val MARQUEE_BORDER = 0xFFFFFFFF.toInt()
    }
}
