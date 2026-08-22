package net.trilleo.sound.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.config.Suggestions
import net.trilleo.sound.SoundConfig
import net.trilleo.sound.SoundIds
import net.trilleo.sound.SoundPlayer
import net.trilleo.sound.SoundValue
import net.trilleo.sound.model.SoundStep
import java.util.*

/**
 * The sound picker — the one screen every sound in Hex is chosen on.
 *
 * A sound setting is one of three things ([SoundValue]), and this screen is how a player says which:
 *
 *  - **A sound**, found by browsing a group or by searching. Every registered sound is here, including
 *    anything another mod added, because the list comes from the registry rather than from a written-down
 *    catalogue.
 *  - **A sequence**, when the setting allows one — and the door to building a new one is on this screen, so
 *    "none of these is quite right" has an answer that does not involve backing out of the menu.
 *  - **Nothing**, when the setting allows it: a title that only appears makes no sound.
 *
 * Pitch and volume are here too, rather than in two rows beside the one that opened this. They are how the
 * chosen sound is played, not settings of their own, and they belong where they can be *heard* while they are
 * set — pressing preview after nudging the pitch is the entire workflow, and it was impossible when the
 * sliders lived two rows away from the value.
 *
 * ### It commits on Done, unlike the colour picker
 *
 * [net.trilleo.color.gui.ColorPickerScreen] writes every intermediate value straight through, so a region box
 * behind it recolours as the handle moves, and it restores the original from `removed()` if the player leaves
 * without pressing Done. That works there because it is a leaf — it opens nothing.
 *
 * This screen is not a leaf: it opens the sequence editor. Minecraft calls `removed()` on the outgoing screen
 * for *every* hand-off, a child included, and `Screen.init` does not run again on the way back, so a
 * revert-on-removed picker would throw the player's choice away the moment they went to build the sequence
 * they were choosing — and leave it thrown away. So the working value is held here and written out by [done],
 * which is the idiom [net.trilleo.config.gui.ProfileEditScreen] and `ConfirmActionScreen` already use.
 * Nothing is lost by it: there is nothing behind a sound picker that applying live would animate.
 *
 * Opened by [net.trilleo.config.gui.ConfigEntryList]'s sound row; a feature never constructs one itself.
 */
class SoundPickerScreen(
    private val parent: Screen?,
    /** What is being set — the setting's own label, shown under the title so the screen has a subject. */
    private val subject: Component,
    private val initial: String,
    private val initialPitch: Double,
    private val initialVolume: Double,
    private val allowNone: Boolean,
    private val allowSequences: Boolean,
    /** Whether this setting carries a pitch and volume of its own; false hides those sliders. */
    private val tunable: Boolean,
    private val apply: (String) -> Unit,
    private val applyPitch: (Double) -> Unit,
    private val applyVolume: (Double) -> Unit,
) : Screen(Component.translatable("hex.sound.title")) {

    /** The working value. Written back to the setting only by [done]. */
    private var value: String = initial
    private var pitch: Double = initialPitch
    private var volume: Double = initialVolume

    private var list: SoundBrowseList? = null
    private var search: EditBox? = null
    private var filterButton: Button? = null

    /** Which entry of [filters] is showing. */
    private var filterIndex: Int = 0

    /**
     * What the filter button cycles through: everything, the sequences, then one entry per registry group.
     *
     * Built in [init] rather than held as a field initialiser, because it reads the sound registry and the
     * saved sequences — neither of which is safe to touch at construction time.
     */
    private var filters: List<String> = listOf(FILTER_ALL)

    override fun init() {
        filters = buildList {
            add(FILTER_ALL)
            if (allowSequences) add(FILTER_SEQUENCES)
            addAll(SoundIds.groupNames())
        }
        filterIndex = filterIndex.coerceIn(0, filters.size - 1)

        val listTop = TOP + SEARCH_HEIGHT + GAP * 2
        val listBottom = height - footerHeight()
        list = addRenderableWidget(
            SoundBrowseList(
                minecraft,
                width,
                (listBottom - listTop).coerceAtLeast(ROW_MIN),
                listTop,
                onChoose = ::choose,
                onPreview = ::previewValue,
                current = { value },
            ),
        )

        val searchWidth = (width - MARGIN * 2 - FILTER_WIDTH - GAP).coerceAtLeast(60)
        search = addRenderableWidget(
            EditBox(font, MARGIN, TOP, searchWidth, SEARCH_HEIGHT, Component.translatable("hex.sound.search")),
        ).apply {
            setHint(Component.translatable("hex.sound.search"))
            setMaxLength(SoundValue.MAX_LENGTH)
            setResponder { rebuildList() }
        }

        filterButton = addRenderableWidget(
            Button.builder(filterLabel()) { cycleFilter() }
                .bounds(MARGIN + searchWidth + GAP, TOP, FILTER_WIDTH, SEARCH_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.sound.filter.tooltip")))
                .build(),
        )

        if (tunable) {
            addRenderableWidget(
                tuner(
                    y = height - TUNE_PITCH_UP,
                    min = SoundStep.PITCH_MIN,
                    max = SoundStep.PITCH_MAX,
                    read = { pitch },
                    write = { pitch = it },
                    label = { Component.translatable("hex.sound.pitch", String.format(Locale.ROOT, "%.2f", it)) },
                ),
            )
            addRenderableWidget(
                tuner(
                    y = height - TUNE_VOLUME_UP,
                    min = 0.0,
                    max = 1.0,
                    read = { volume },
                    write = { volume = it },
                    label = {
                        Component.translatable("hex.sound.volume", String.format(Locale.ROOT, "%.0f%%", it * 100))
                    },
                ),
            )
        }

        layoutFooter()
        rebuildList()
        list?.scrollToCurrent()
    }

    /** The footer: None and Sequences on the left where they are optional, Cancel and Done on the right. */
    private fun layoutFooter() {
        val y = height - BUTTON_HEIGHT - GAP
        var x = MARGIN

        if (allowNone) {
            addRenderableWidget(
                Button.builder(Component.translatable("hex.sound.none")) { choose(SoundValue.NONE) }
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable("hex.sound.none.tooltip")))
                    .build(),
            )
            x += BUTTON_WIDTH + GAP
        }
        if (allowSequences) {
            addRenderableWidget(
                Button.builder(Component.translatable("hex.sound.edit_sequences")) { openSequences() }
                    .bounds(x, y, BUTTON_WIDE, BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable("hex.sound.edit_sequences.tooltip")))
                    .build(),
            )
        }

        addRenderableWidget(
            Button.builder(Component.translatable("hex.sound.cancel")) { onClose() }
                .bounds(width - MARGIN - BUTTON_WIDTH * 2 - GAP, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )
        addRenderableWidget(
            Button.builder(Component.translatable("hex.sound.done")) { done() }
                .bounds(width - MARGIN - BUTTON_WIDTH, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )
    }

    private fun tuner(
        y: Int,
        min: Double,
        max: Double,
        read: () -> Double,
        write: (Double) -> Unit,
        label: (Double) -> Component,
    ): AbstractSliderButton {
        val span = max - min
        return object : AbstractSliderButton(
            MARGIN,
            y,
            width - MARGIN * 2,
            BUTTON_HEIGHT,
            label(read()),
            ((read() - min) / span).coerceIn(0.0, 1.0),
        ) {
            override fun updateMessage() {
                message = label(current())
            }

            override fun applyValue() {
                write(current())
            }

            /** Snapped to hundredths, so a drag cannot write 0.30000000000000004 into a config file. */
            private fun current(): Double = Math.round((min + value * span) * 100.0) / 100.0
        }
    }

    // ---- choosing ----------------------------------------------------------------------------------------

    /**
     * Takes [spec] as the working value and plays it.
     *
     * Previewing on selection rather than making it a second click is deliberate: the reason to open this
     * screen is that you do not know what these are called, and a list of thirty names you cannot hear is the
     * problem rather than the solution.
     */
    private fun choose(spec: String) {
        value = SoundValue.normalize(spec)
        previewValue(value)
    }

    private fun previewValue(spec: String) {
        SoundPlayer.preview(minecraft, spec, pitch, volume)
    }

    private fun cycleFilter() {
        filterIndex = (filterIndex + 1) % filters.size
        filterButton?.message = filterLabel()
        rebuildList()
    }

    private fun filterLabel(): Component = when (val filter = filters[filterIndex]) {
        FILTER_ALL -> Component.translatable("hex.sound.group.all")
        FILTER_SEQUENCES -> Component.translatable("hex.sound.group.sequences")
        // literal: a group name is a registry path segment — `block`, `ui`, `music`, or a mod's namespace.
        // It is an id, not language, and the same rule that leaves item ids alone applies.
        else -> Component.literal(filter)
    }

    private fun rebuildList() {
        val typed = search?.value.orEmpty()
        val filter = filters[filterIndex]

        val choices = if (filter == FILTER_SEQUENCES) {
            sequenceChoices(typed)
        } else {
            val pool = if (filter == FILTER_ALL) SoundIds.ids() else SoundIds.groups()[filter].orEmpty()
            // Ranked rather than merely filtered, so `note_block.pl` finds the pling: Suggestions already
            // knows that nobody types the namespace, and reusing it keeps one definition of "matches".
            val sounds = Suggestions.rank(pool, typed, limit = LIST_LIMIT).map { SoundBrowseList.ofSound(it) }
            // Sequences first under All, never appended: there are hundreds of sounds and a handful of
            // sequences, so putting the player's own work after them would bury it past a scroll nobody
            // makes. Under a group filter they are not shown at all — the filter is the request.
            if (filter == FILTER_ALL && allowSequences) sequenceChoices(typed) + sounds else sounds
        }

        val hint = if (typed.isBlank()) {
            Component.translatable("hex.sound.empty_group")
        } else {
            Component.translatable("hex.sound.no_results", typed)
        }
        list?.show(choices, hint)
    }

    private fun sequenceChoices(typed: String): List<SoundBrowseList.Choice> {
        if (!allowSequences) return emptyList()
        val needle = typed.trim().lowercase(Locale.ROOT)
        return SoundConfig.settings.sequences
            .filter { needle.isEmpty() || it.name.lowercase(Locale.ROOT).contains(needle) || it.id.contains(needle) }
            .map { SoundBrowseList.ofSequence(it.id, it.name, summaryOf(it.steps.size, it.durationMillis())) }
    }

    private fun summaryOf(steps: Int, millis: Double): String =
        Component.translatable(
            "hex.sounds.summary",
            steps,
            String.format(Locale.ROOT, "%.2f", millis / 1000.0),
        ).string

    /**
     * Hands off to the sequence editor.
     *
     * Nothing is written on the way out and nothing is reverted: the working value lives in this screen's
     * fields, and returning here finds them exactly as they were left. That is the whole payoff of committing
     * on Done rather than applying live — see the class comment.
     */
    private fun openSequences() {
        minecraft.setScreen(SoundSequencesScreen(this))
    }

    private fun done() {
        apply(value)
        if (tunable) {
            applyPitch(pitch)
            applyVolume(volume)
        }
        minecraft.setScreen(parent)
    }

    // ---- rendering ---------------------------------------------------------------------------------------

    override fun extractBackground(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(extractor, mouseX, mouseY, delta)
        extractor.centeredText(font, title, width / 2, TITLE_Y, TITLE_COLOR)
        extractor.centeredText(font, subject, width / 2, TITLE_Y + font.lineHeight + 1, SUBJECT_COLOR)
    }

    override fun onClose() {
        // Cancel and Escape both land here, and both leave the setting exactly as it was found.
        SoundPlayer.stopAll()
        minecraft.setScreen(parent)
    }

    override fun removed() {
        // A preview must not outlive the screen that started it — including when this hands off to the
        // sequence editor, which is about to make sounds of its own.
        SoundPlayer.stopAll()
    }

    private fun footerHeight(): Int = if (tunable) FOOTER_TUNABLE else FOOTER_PLAIN

    private companion object {
        const val FILTER_ALL = " all"
        const val FILTER_SEQUENCES = " sequences"

        const val MARGIN = 24
        const val GAP = 6
        const val TITLE_Y = 10
        const val TOP = 34
        const val SEARCH_HEIGHT = 20
        const val FILTER_WIDTH = 90
        const val BUTTON_HEIGHT = 20
        const val BUTTON_WIDTH = 74
        const val BUTTON_WIDE = 110
        const val ROW_MIN = 40

        /** Height reserved under the list: the two tuning sliders plus the button row, or just the buttons. */
        const val FOOTER_PLAIN = 32
        const val FOOTER_TUNABLE = 32 + (BUTTON_HEIGHT + GAP) * 2

        const val TUNE_VOLUME_UP = FOOTER_PLAIN + BUTTON_HEIGHT
        const val TUNE_PITCH_UP = FOOTER_PLAIN + (BUTTON_HEIGHT + GAP) + BUTTON_HEIGHT

        const val LIST_LIMIT = 512

        const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        const val SUBJECT_COLOR = 0xFFA0A0A0.toInt()
    }
}
