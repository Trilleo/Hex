package net.trilleo.color.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.trilleo.color.ColorConfig
import net.trilleo.color.ColorValue
import net.trilleo.util.Chroma
import kotlin.math.max
import kotlin.math.min

/**
 * The colour picker — the one screen every colour in Hex is chosen on.
 *
 * A colour setting is one of three things ([ColorValue]), and this screen is how a player says which:
 *
 *  - **A colour**, reached four ways, because people arrive knowing different things. The saturation/value
 *    field and the hue bar are for finding a colour by eye; the hex field is for one copied from somewhere
 *    else; the R/G/B fields are for one written down as numbers; and the swatch rows are for the colours
 *    already worth having a name for.
 *  - **Chroma**, when the setting can flow. A button rather than a swatch, because it is not a colour — see
 *    [ColorValue].
 *  - **Nothing**, when the setting allows it: an item that is only being renamed keeps whatever colour Hypixel
 *    gave it.
 *
 * ### It applies as you go
 *
 * Every change is written through [apply] immediately, exactly as a settings row is, so a region box or a HUD
 * panel behind this screen recolours as the handle moves. Leaving without pressing Done — Cancel, Escape, or
 * anything else that closes the screen — puts the original value back from [removed], which is the one place
 * every exit passes through.
 *
 * ### Recent colours
 *
 * Only pressing Done records a colour in [ColorConfig], not every intermediate value the drag passed over.
 * The list is shared by every colour setting in the mod, which is what makes matching one feature's colour to
 * another's a click rather than a hunt.
 *
 * Opened by [net.trilleo.config.gui.ConfigEntryList]'s colour row; a feature never constructs one itself.
 */
class ColorPickerScreen(
    private val parent: Screen?,
    /** What is being coloured — the setting's own label, shown under the title so the screen has a subject. */
    private val subject: Component,
    private val initial: String,
    /** Whether the setting stores an alpha byte. Only then does the hex field accept eight digits. */
    private val alpha: Boolean,
    private val allowChroma: Boolean,
    private val allowNone: Boolean,
    private val apply: (String) -> Unit,
) : Screen(Component.translatable("hex.color.title")) {

    /** Which of [ColorValue]'s three kinds of value the screen is currently holding. */
    private enum class Mode { FIXED, CHROMA, NONE }

    /** What the mouse is currently dragging, or null. Held so a drag that leaves the widget still tracks. */
    private enum class Drag { SATURATION_VALUE, HUE }

    private var mode: Mode = when {
        ColorValue.isChroma(initial) -> Mode.CHROMA
        ColorValue.isNone(initial) -> Mode.NONE
        else -> Mode.FIXED
    }

    /**
     * The fixed colour, kept even while [mode] is chroma or none.
     *
     * That is what makes the three buttons a real choice rather than a one-way door: switching to chroma and
     * back returns the colour that was there, instead of a default the player never picked.
     */
    private var argb: Int = ColorValue.parse(initial) ?: DEFAULT_COLOR

    private var hue: Float = 0f
    private var saturation: Float = 0f
    private var brightness: Float = 1f

    /** True while the screen is writing its own fields, so a responder cannot fight the change that caused it. */
    private var syncing: Boolean = false

    private var dragging: Drag? = null

    /** Set by Done. [removed] reads it to decide whether to keep the new value or restore the old one. */
    private var committed: Boolean = false

    /** A one-line result of the last Copy or Paste, cleared on the next edit. */
    private var notice: Component? = null

    private lateinit var hexBox: EditBox
    private lateinit var redBox: EditBox
    private lateinit var greenBox: EditBox
    private lateinit var blueBox: EditBox

    private var grids: List<Grid> = emptyList()

    // ---- layout, measured in init and read while drawing --------------------------------------------------

    private var svX = 0
    private var svY = 0
    private var svW = 0
    private var svH = 0
    private var hueY = 0
    private var rightX = 0
    private var rightW = 0
    private var previewY = 0

    override fun init() {
        toHsv(argb)

        val inner = width - MARGIN * 2
        val footerY = height - FOOTER_HEIGHT

        // The swatch rows are measured first and given the space they need; the picking field takes whatever
        // is left. Done the other way round, a short window would push the palettes under the footer, and a
        // palette you cannot reach is worse than a small gradient.
        grids = buildGrids(inner)
        val gridsHeight = grids.sumOf { it.height() + SECTION_GAP }
        val gridsTop = footerY - GAP - gridsHeight

        val topHeight = gridsTop - GAP - CONTENT_TOP
        svH = (topHeight - HUE_HEIGHT - GAP).coerceIn(SV_MIN_HEIGHT, SV_MAX_HEIGHT)
        svW = (svH * 4 / 3).coerceIn(SV_MIN_WIDTH, min(SV_MAX_WIDTH, inner / 2))
        svX = MARGIN
        svY = CONTENT_TOP
        hueY = svY + svH + GAP

        rightX = MARGIN + svW + COLUMN_GAP
        rightW = width - rightX - MARGIN
        previewY = CONTENT_TOP

        var y = previewY + PREVIEW_HEIGHT + GAP

        hexBox = EditBox(font, rightX, y, rightW, FIELD_HEIGHT, Component.translatable("hex.color.hex")).apply {
            setHint(Component.translatable("hex.color.hex"))
            setMaxLength(ColorValue.MAX_LENGTH)
            setResponder(::onHexTyped)
        }
        addRenderableWidget(hexBox)
        y += FIELD_HEIGHT + GAP

        val channelWidth = (rightW - GAP * 2) / 3
        redBox = channelBox(rightX, y, channelWidth, "hex.color.r")
        greenBox = channelBox(rightX + channelWidth + GAP, y, channelWidth, "hex.color.g")
        blueBox = channelBox(rightX + (channelWidth + GAP) * 2, y, channelWidth, "hex.color.b")
        y += FIELD_HEIGHT + GAP

        val halfWidth = (rightW - GAP) / 2
        addRenderableWidget(
            Button.builder(Component.translatable("hex.color.copy")) { copy() }
                .bounds(rightX, y, halfWidth, FIELD_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.color.copy.tooltip")))
                .build(),
        )
        addRenderableWidget(
            Button.builder(Component.translatable("hex.color.paste")) { paste() }
                .bounds(rightX + halfWidth + GAP, y, rightW - halfWidth - GAP, FIELD_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.color.paste.tooltip")))
                .build(),
        )

        // The grids stack upwards from the footer, in the order they were built.
        var gy = gridsTop
        grids.forEach { grid ->
            grid.y = gy + LABEL_HEIGHT
            gy += grid.height() + SECTION_GAP
        }

        buildFooter(footerY)
        syncFields()
    }

    private fun channelBox(x: Int, y: Int, boxWidth: Int, key: String): EditBox {
        val label = Component.translatable(key)
        val box = EditBox(font, x, y, boxWidth, FIELD_HEIGHT, label).apply {
            setHint(label)
            setMaxLength(3)
            setResponder { onChannelTyped() }
        }
        addRenderableWidget(box)
        return box
    }

    private fun buildFooter(footerY: Int) {
        val y = footerY + (FOOTER_HEIGHT - FIELD_HEIGHT - GAP) / 2
        var x = MARGIN

        if (allowChroma) {
            addRenderableWidget(
                Button.builder(Component.translatable("hex.color.chroma")) { choose(Mode.CHROMA) }
                    .bounds(x, y, MODE_WIDTH, FIELD_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable("hex.color.chroma.tooltip")))
                    .build(),
            )
            x += MODE_WIDTH + GAP
        }
        if (allowNone) {
            addRenderableWidget(
                Button.builder(Component.translatable("hex.color.none")) { choose(Mode.NONE) }
                    .bounds(x, y, MODE_WIDTH, FIELD_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable("hex.color.none.tooltip")))
                    .build(),
            )
        }

        addRenderableWidget(
            Button.builder(Component.translatable("gui.cancel")) { onClose() }
                .bounds(width - MARGIN - DONE_WIDTH * 2 - GAP, y, DONE_WIDTH, FIELD_HEIGHT)
                .build(),
        )
        addRenderableWidget(
            Button.builder(Component.translatable("gui.done")) { done() }
                .bounds(width - MARGIN - DONE_WIDTH, y, DONE_WIDTH, FIELD_HEIGHT)
                .build(),
        )
    }

    private fun buildGrids(inner: Int): List<Grid> {
        val recent = ColorConfig.recent.mapNotNull { spec ->
            ColorValue.parse(spec)?.let { ColorValue.Swatch(spec, it and ColorValue.RGB_MASK, Component.literal(spec)) }
        }
        return listOf(
            Grid(Component.translatable("hex.color.minecraft"), ColorValue.VANILLA, inner),
            Grid(Component.translatable("hex.color.presets"), ColorValue.PRESETS, inner),
            Grid(
                Component.translatable("hex.color.recent"),
                recent,
                inner,
                empty = Component.translatable("hex.color.recent.empty"),
            ),
        )
    }

    // ---- the value ----------------------------------------------------------------------------------------

    /** The value this screen currently stands for, in the spelling a config file will carry. */
    private fun currentValue(): String = when (mode) {
        Mode.CHROMA -> ColorValue.CHROMA
        Mode.NONE -> ColorValue.NONE
        Mode.FIXED -> ColorValue.format(argb, alpha)
    }

    /** Writes the current value through to the setting. Called on every change, as a settings row is. */
    private fun push() {
        apply(currentValue())
    }

    private fun choose(target: Mode) {
        mode = target
        notice = null
        syncFields()
        push()
    }

    /** Adopts [color] as the fixed colour, switching out of chroma or none if that is where we were. */
    private fun setColor(color: Int, fromHsv: Boolean = false) {
        // Alpha is carried rather than taken from the new colour: nothing but the hex field can express one,
        // so a swatch or a drag would otherwise silently make a translucent setting opaque.
        argb = ColorValue.withAlpha(color, ColorValue.alphaOf(argb))
        mode = Mode.FIXED
        notice = null
        if (!fromHsv) toHsv(argb)
        syncFields()
        push()
    }

    private fun toHsv(color: Int) {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val maximum = max(r, max(g, b))
        val minimum = min(r, min(g, b))
        val span = maximum - minimum

        brightness = maximum
        saturation = if (maximum <= 0f) 0f else span / maximum
        // A grey has no hue to speak of, so the previous one is kept — otherwise dragging the value slider
        // down to black and back up would come back red rather than the colour that was there.
        if (span > 0f) {
            hue = when (maximum) {
                r -> ((g - b) / span) / 6f
                g -> (2f + (b - r) / span) / 6f
                else -> (4f + (r - g) / span) / 6f
            }
            if (hue < 0f) hue += 1f
        }
    }

    private fun fromHsv(): Int = Mth.hsvToRgb(hue, saturation, brightness) and ColorValue.RGB_MASK

    /** Pushes the current value into the text fields without those fields pushing back. */
    private fun syncFields() {
        syncing = true
        hexBox.value = currentValue()
        val rgb = argb and ColorValue.RGB_MASK
        redBox.value = ((rgb shr 16) and 0xFF).toString()
        greenBox.value = ((rgb shr 8) and 0xFF).toString()
        blueBox.value = (rgb and 0xFF).toString()
        syncing = false
    }

    private fun onHexTyped(text: String) {
        if (syncing) return
        notice = null
        when {
            allowChroma && ColorValue.isChroma(text) -> {
                mode = Mode.CHROMA
                push()
            }

            allowNone && text.isBlank() -> {
                mode = Mode.NONE
                push()
            }

            else -> {
                // An unparseable value is simply not adopted — the field keeps showing what was typed, so a
                // colour can be typed through its half-finished states without the setting flickering.
                val parsed = ColorValue.parse(text) ?: return
                argb = if (alpha) parsed else ColorValue.withAlpha(parsed, ColorValue.alphaOf(argb))
                mode = Mode.FIXED
                toHsv(argb)
                syncing = true
                val rgb = argb and ColorValue.RGB_MASK
                redBox.value = ((rgb shr 16) and 0xFF).toString()
                greenBox.value = ((rgb shr 8) and 0xFF).toString()
                blueBox.value = (rgb and 0xFF).toString()
                syncing = false
                push()
            }
        }
    }

    private fun onChannelTyped() {
        if (syncing) return
        val r = channelOf(redBox) ?: return
        val g = channelOf(greenBox) ?: return
        val b = channelOf(blueBox) ?: return
        notice = null
        argb = ColorValue.withAlpha((r shl 16) or (g shl 8) or b, ColorValue.alphaOf(argb))
        mode = Mode.FIXED
        toHsv(argb)
        syncing = true
        hexBox.value = currentValue()
        syncing = false
        push()
    }

    /** One R/G/B field as `0..255`, or null while it does not hold a number in range. */
    private fun channelOf(box: EditBox): Int? = box.value.trim().toIntOrNull()?.takeIf { it in 0..255 }

    // ---- clipboard ----------------------------------------------------------------------------------------

    private fun copy() {
        minecraft?.keyboardHandler?.setClipboard(currentValue())
        notice = Component.translatable("hex.color.copied", currentValue())
    }

    /**
     * Reads a colour off the clipboard.
     *
     * Deliberately forgiving about the spelling — `#FF8800`, `FF8800` and `0xFF8800` all arrive from somewhere
     * real, and a picker that accepted only one of them would send the player back to edit the text by hand.
     */
    private fun paste() {
        val text = minecraft?.keyboardHandler?.clipboard.orEmpty().trim()
        val cleaned = text.removePrefix("0x").removePrefix("0X")
        when {
            allowChroma && ColorValue.isChroma(cleaned) -> choose(Mode.CHROMA)
            else -> {
                val parsed = ColorValue.parse(cleaned)
                if (parsed == null) {
                    notice = Component.translatable("hex.color.paste.invalid")
                    return
                }
                argb = if (alpha) parsed else ColorValue.withAlpha(parsed, ColorValue.alphaOf(argb))
                mode = Mode.FIXED
                toHsv(argb)
                syncFields()
                push()
            }
        }
    }

    // ---- input ---------------------------------------------------------------------------------------------

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val x = event.x
        val y = event.y

        if (inside(x, y, svX, svY, svW, svH)) {
            dragging = Drag.SATURATION_VALUE
            pickSaturationValue(x, y)
            return true
        }
        if (inside(x, y, svX, hueY, svW, HUE_HEIGHT)) {
            dragging = Drag.HUE
            pickHue(x)
            return true
        }
        grids.forEach { grid ->
            grid.hit(x, y)?.let { swatch ->
                ColorValue.parse(swatch.spec)?.let { setColor(it) }
                return true
            }
            if (grid.hitClear(x, y)) {
                ColorConfig.clearRecent()
                rebuildWidgets()
                return true
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        when (dragging) {
            Drag.SATURATION_VALUE -> {
                pickSaturationValue(event.x, event.y)
                return true
            }

            Drag.HUE -> {
                pickHue(event.x)
                return true
            }

            null -> Unit
        }
        return super.mouseDragged(event, dragX, dragY)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        dragging = null
        return super.mouseReleased(event)
    }

    private fun pickSaturationValue(mouseX: Double, mouseY: Double) {
        saturation = ((mouseX - svX) / svW).toFloat().coerceIn(0f, 1f)
        brightness = 1f - ((mouseY - svY) / svH).toFloat().coerceIn(0f, 1f)
        setColor(fromHsv(), fromHsv = true)
    }

    private fun pickHue(mouseX: Double) {
        hue = ((mouseX - svX) / svW).toFloat().coerceIn(0f, 1f)
        setColor(fromHsv(), fromHsv = true)
    }

    private fun inside(x: Double, y: Double, left: Int, top: Int, w: Int, h: Int): Boolean =
        x >= left && x < left + w && y >= top && y < top + h

    // ---- drawing ---------------------------------------------------------------------------------------------

    /**
     * Drawn after the widgets rather than in the background pass, because none of it sits under one: the
     * gradient, the swatches and the preview occupy space no widget was placed in. The chrome that *does* sit
     * under the fields is in [extractBackground].
     */
    override fun extractRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(extractor, mouseX, mouseY, delta)

        drawSaturationValue(extractor)
        drawHue(extractor)
        drawPreview(extractor, mouseX, mouseY)
        grids.forEach { it.draw(extractor, mouseX, mouseY) }
    }

    override fun extractBackground(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(extractor, mouseX, mouseY, delta)
        extractor.text(font, title, MARGIN, 10, TITLE_COLOR)
        extractor.text(font, subject, MARGIN, 20, MUTED_COLOR)
    }

    /**
     * The saturation/value square: one vertical gradient per pixel column.
     *
     * A column at a time rather than a cell at a time because [GuiGraphicsExtractor.fillGradient] interpolates
     * vertically for free, so the whole square costs one quad per column instead of one per pixel — and the
     * result is genuinely smooth in both directions rather than a visible grid of blocks.
     */
    private fun drawSaturationValue(extractor: GuiGraphicsExtractor) {
        for (column in 0 until svW) {
            val s = column.toFloat() / (svW - 1).coerceAtLeast(1)
            val top = Mth.hsvToRgb(hue, s, 1f) or OPAQUE
            extractor.fillGradient(svX + column, svY, svX + column + 1, svY + svH, top, BLACK)
        }
        extractor.outline(svX, svY, svW, svH, BORDER_COLOR)

        if (mode == Mode.FIXED) {
            val markerX = svX + (saturation * (svW - 1)).toInt()
            val markerY = svY + ((1f - brightness) * (svH - 1)).toInt()
            crosshair(extractor, markerX, markerY)
        }
    }

    /** The hue bar: the rainbow, in six flat-shaded runs of columns. */
    private fun drawHue(extractor: GuiGraphicsExtractor) {
        for (column in 0 until svW) {
            val h = column.toFloat() / (svW - 1).coerceAtLeast(1)
            extractor.fill(svX + column, hueY, svX + column + 1, hueY + HUE_HEIGHT, Mth.hsvToRgb(h, 1f, 1f) or OPAQUE)
        }
        extractor.outline(svX, hueY, svW, HUE_HEIGHT, BORDER_COLOR)

        if (mode == Mode.FIXED) {
            val markerX = svX + (hue * (svW - 1)).toInt()
            extractor.fill(markerX - 1, hueY - 1, markerX + 2, hueY + HUE_HEIGHT + 1, MARKER_DARK)
            extractor.fill(markerX, hueY - 1, markerX + 1, hueY + HUE_HEIGHT + 1, MARKER_LIGHT)
        }
    }

    /**
     * The big swatch, with what it is written across it.
     *
     * Hovering a palette swatch shows *that* colour instead, which is how a swatch gets a name without the
     * screen finding room for a caption row it does not have.
     */
    private fun drawPreview(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val hovered = grids.firstNotNullOfOrNull { it.hit(mouseX.toDouble(), mouseY.toDouble()) }
        val shown = hovered?.let { ColorValue.parse(it.spec) ?: DEFAULT_COLOR }
            ?: when (mode) {
                Mode.CHROMA -> ColorValue.resolve(ColorValue.CHROMA, DEFAULT_COLOR, Chroma.SECONDS_DEFAULT)
                Mode.NONE -> 0
                Mode.FIXED -> argb
            }

        checker(extractor, rightX, previewY, rightW, PREVIEW_HEIGHT)
        extractor.fill(rightX, previewY, rightX + rightW, previewY + PREVIEW_HEIGHT, shown)
        extractor.outline(rightX, previewY, rightW, PREVIEW_HEIGHT, BORDER_COLOR)

        // The "forget these" cross is drawn rather than being a widget, so it has no tooltip of its own; it
        // explains itself here, the same way a swatch's name does.
        val clearing = grids.any { it.hitClear(mouseX.toDouble(), mouseY.toDouble()) }
        val label = hovered?.name
            ?: (if (clearing) Component.translatable("hex.color.recent.clear") else null)
            ?: notice
            ?: when (mode) {
                Mode.CHROMA -> Component.translatable("hex.color.chroma")
                Mode.NONE -> Component.translatable("hex.color.none")
                Mode.FIXED -> Component.literal(ColorValue.format(argb, alpha))
            }
        extractor.centeredText(
            font,
            label,
            rightX + rightW / 2,
            previewY + (PREVIEW_HEIGHT - font.lineHeight) / 2,
            readableOn(shown),
        )
    }

    private fun crosshair(extractor: GuiGraphicsExtractor, x: Int, y: Int) {
        extractor.outline(x - MARKER_RADIUS - 1, y - MARKER_RADIUS - 1, MARKER_SIZE + 2, MARKER_SIZE + 2, MARKER_DARK)
        extractor.outline(x - MARKER_RADIUS, y - MARKER_RADIUS, MARKER_SIZE, MARKER_SIZE, MARKER_LIGHT)
    }

    /**
     * The grey chequerboard a translucent colour is drawn over, so "half transparent" and "half as bright"
     * are told apart — which they cannot be against a flat background.
     */
    private fun checker(extractor: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        var row = 0
        while (row * CHECKER < h) {
            var column = 0
            while (column * CHECKER < w) {
                val light = (row + column) % 2 == 0
                extractor.fill(
                    x + column * CHECKER,
                    y + row * CHECKER,
                    min(x + (column + 1) * CHECKER, x + w),
                    min(y + (row + 1) * CHECKER, y + h),
                    if (light) CHECKER_LIGHT else CHECKER_DARK,
                )
                column++
            }
            row++
        }
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    private fun done() {
        committed = true
        ColorConfig.remember(currentValue())
        onClose()
    }

    /**
     * The single exit. Anything that closes this screen without Done — Cancel, Escape, the game closing a
     * screen out from under it — restores the value that was there when it opened.
     */
    override fun removed() {
        if (!committed) apply(initial)
    }

    /**
     * One labelled row of swatches.
     *
     * A plain class rather than widgets, because a swatch is a coloured rectangle with a name: as a [Button]
     * it would come with vanilla's button texture over the top of the colour it exists to show.
     */
    private inner class Grid(
        private val label: Component,
        private val swatches: List<ColorValue.Swatch>,
        private val available: Int,
        /** Shown in place of an empty row, for the recent colours before anything has been picked. */
        private val empty: Component? = null,
    ) {
        /** Set during layout; the top of the swatches themselves, with the caption above it. */
        var y: Int = 0

        private val perRow = ((available + SWATCH_GAP) / (SWATCH + SWATCH_GAP)).coerceAtLeast(1)

        /** The swatches plus, on the recent row, the slot its "forget these" button occupies. */
        private val slots = if (empty != null && swatches.isNotEmpty()) swatches.size + 1 else swatches.size

        private val rows = if (slots == 0) 1 else (slots + perRow - 1) / perRow

        fun height(): Int = LABEL_HEIGHT + rows * (SWATCH + SWATCH_GAP)

        private fun boundsOf(index: Int): Pair<Int, Int> {
            val row = index / perRow
            val column = index % perRow
            return MARGIN + column * (SWATCH + SWATCH_GAP) to y + row * (SWATCH + SWATCH_GAP)
        }

        /** The swatch under the cursor, or null. */
        fun hit(mouseX: Double, mouseY: Double): ColorValue.Swatch? {
            swatches.forEachIndexed { index, swatch ->
                val (x, top) = boundsOf(index)
                if (inside(mouseX, mouseY, x, top, SWATCH, SWATCH)) return swatch
            }
            return null
        }

        /** Whether the cursor is on this row's "forget these" button, which only the recent row has. */
        fun hitClear(mouseX: Double, mouseY: Double): Boolean {
            if (empty == null || swatches.isEmpty()) return false
            val (x, top) = boundsOf(swatches.size)
            return inside(mouseX, mouseY, x, top, SWATCH, SWATCH)
        }

        fun draw(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
            extractor.text(font, label, MARGIN, y - LABEL_HEIGHT + 1, MUTED_COLOR)

            if (swatches.isEmpty()) {
                empty?.let { extractor.text(font, it, MARGIN, y + (SWATCH - font.lineHeight) / 2, FAINT_COLOR) }
                return
            }

            val current = if (mode == Mode.FIXED) ColorValue.format(argb, alpha = false) else null
            swatches.forEachIndexed { index, swatch ->
                val (x, top) = boundsOf(index)
                val hovered = inside(mouseX.toDouble(), mouseY.toDouble(), x, top, SWATCH, SWATCH)
                val selected = current != null && current.equals(swatch.spec, ignoreCase = true)
                extractor.fill(x, top, x + SWATCH, top + SWATCH, swatch.rgb or OPAQUE)
                extractor.outline(
                    x,
                    top,
                    SWATCH,
                    SWATCH,
                    when {
                        selected -> SELECTED_COLOR
                        hovered -> HOVER_COLOR
                        else -> BORDER_COLOR
                    },
                )
            }

            // Only the recent row carries one: it is the only palette the player put together themselves, so
            // it is the only one there is anything to undo.
            if (empty != null) {
                val (x, top) = boundsOf(swatches.size)
                val hovered = inside(mouseX.toDouble(), mouseY.toDouble(), x, top, SWATCH, SWATCH)
                extractor.outline(x, top, SWATCH, SWATCH, if (hovered) HOVER_COLOR else BORDER_COLOR)
                extractor.centeredText(
                    font,
                    CLEAR_GLYPH,
                    x + SWATCH / 2,
                    top + (SWATCH - font.lineHeight) / 2,
                    if (hovered) SELECTED_COLOR else MUTED_COLOR,
                )
            }
        }
    }

    private companion object {
        const val MARGIN = 12
        const val GAP = 4
        const val COLUMN_GAP = 10
        const val SECTION_GAP = 4
        const val CONTENT_TOP = 34
        const val FOOTER_HEIGHT = 28
        const val FIELD_HEIGHT = 18
        const val PREVIEW_HEIGHT = 20
        const val LABEL_HEIGHT = 10
        const val HUE_HEIGHT = 10
        const val MODE_WIDTH = 54
        const val DONE_WIDTH = 54

        const val SV_MIN_WIDTH = 80
        const val SV_MAX_WIDTH = 170
        const val SV_MIN_HEIGHT = 40
        const val SV_MAX_HEIGHT = 140

        const val SWATCH = 13
        const val SWATCH_GAP = 2

        /** Side of one chequerboard cell, behind a translucent colour. */
        const val CHECKER = 5

        const val MARKER_RADIUS = 2
        const val MARKER_SIZE = MARKER_RADIUS * 2 + 1

        const val OPAQUE = 0xFF000000.toInt()
        const val BLACK = 0xFF000000.toInt()

        /** What an unparseable stored value opens on: white, which is nobody's accident. */
        const val DEFAULT_COLOR = 0xFFFFFFFF.toInt()

        const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        const val MUTED_COLOR = 0xFFA0A0A0.toInt()
        const val FAINT_COLOR = 0xFF6E6E6E.toInt()
        const val BORDER_COLOR = 0xFF202020.toInt()
        const val HOVER_COLOR = 0xFFFFFFFF.toInt()
        const val SELECTED_COLOR = 0xFFFFD25F.toInt()
        const val MARKER_DARK = 0xFF000000.toInt()
        const val MARKER_LIGHT = 0xFFFFFFFF.toInt()
        const val CHECKER_LIGHT = 0xFF8A8A8A.toInt()
        const val CHECKER_DARK = 0xFF5E5E5E.toInt()

        /** Not language: a cross is a cross in every locale, and it fits a 13-pixel square. */
        val CLEAR_GLYPH: Component = Component.literal("×")

        /**
         * Black or white, whichever can be read on [background].
         *
         * Rec. 601 luma rather than the plain average: green carries most of the perceived brightness, and an
         * average calls a saturated blue "light" and then writes black on it.
         */
        fun readableOn(background: Int): Int {
            val r = (background shr 16) and 0xFF
            val g = (background shr 8) and 0xFF
            val b = background and 0xFF
            val luma = (r * 299 + g * 587 + b * 114) / 1000
            // A nearly transparent preview shows the chequerboard rather than the colour, so it is read as
            // mid-grey instead of as whatever hue happens to be behind it.
            val alpha = ColorValue.alphaOf(background)
            val effective = if (alpha < 128) 128 else luma
            return if (effective > 140) 0xFF101010.toInt() else 0xFFF0F0F0.toInt()
        }
    }
}
