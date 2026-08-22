package net.trilleo.title.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.*
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.trilleo.color.ColorConfig
import net.trilleo.color.ColorValue
import net.trilleo.config.ConfigCategory
import net.trilleo.config.gui.ConfigEntryList
import net.trilleo.title.TitleConfig
import net.trilleo.title.Titles
import net.trilleo.title.model.TitleFormat
import net.trilleo.title.model.TitlePreset
import net.trilleo.title.model.TitleSpec
import net.trilleo.util.Chroma
import java.util.*

/**
 * Edits one [TitleSpec] — the whole of how a title looks and sounds.
 *
 * **This screen is the reason the title helper is worth having.** Four features can pop a title, and before it
 * each of them carried its own rows for a subtitle, a colour and a duration; every knob added to titles had to
 * be added four times, in four language-file blocks, and could be forgotten in one of them. Here the controls
 * exist once and every feature reaches them through a single **Title style…** button.
 *
 * ### You write a title, you do not configure one
 *
 * The two lines are text boxes carrying `&` codes, with a toolbar that writes the codes and a live preview
 * above showing the result — the same shape [net.trilleo.notebook.gui.NoteEditorScreen] uses, and for the same
 * reason. The alternative, which this replaced, was a colour row and five style switches *per line*: thirteen
 * rows to say what `&c&lBOSS &einbound` says in one, and which could not say that at all, because a switch
 * applies to a whole line and a code applies from where you put it.
 *
 * The preview is not a mock-up. It builds its component with [Titles.componentFor], the same call that builds
 * the real thing, and rebuilds every frame — so chroma flows here exactly as it will on the alert, and there is
 * no second renderer that could be wrong about the colours.
 *
 * ### The big line usually has no words in it
 *
 * The alert already has a message, so a title line of nothing but codes styles it rather than replacing it —
 * [TitleFormat.merge]. The Title box therefore shows the alert's own message as its hint, which is what makes
 * that rule visible: type nothing and the hint is what appears; type words and they take over.
 *
 * Timings, the sound and the preset stay as rows in a [ConfigEntryList] below, because none of them is
 * something you *write* — they are numbers and a choice, which is what a settings row is for.
 *
 * @param ownerText the alert's message, read lazily: its field may be edited between opening this screen and
 *   looking at the preview.
 * @param onChange records the edit with whatever owns the spec — marking a config dirty, marking a reminder
 *   hand-edited. Called on every setter, so this screen never has to know which config it is editing.
 */
class TitleEditScreen(
    private val parent: Screen?,
    private val spec: TitleSpec,
    private val ownerText: () -> String,
    private val onChange: () -> Unit,
) : Screen(Component.translatable("hex.titles.edit.title")) {

    private lateinit var titleBox: EditBox
    private lateinit var subtitleBox: EditBox
    private var list: ConfigEntryList? = null

    /**
     * The box a toolbar button writes into.
     *
     * Remembered rather than read from [getFocused] at the moment of the click, because clicking a button is
     * itself a focus change — by the time the action runs, the focused widget is the button.
     */
    private var target: EditBox? = null

    /** Everything the palette panel holds, so the whole thing can be shown and hidden in one place. */
    private val paletteWidgets = mutableListOf<AbstractWidget>()

    /** The recent-colour slots, kept separately so [refreshRecent] can repoint them without a rebuild. */
    private val recentButtons = mutableListOf<Button>()

    private lateinit var hexBox: EditBox
    private lateinit var hexApply: Button
    private var paletteOpen = false

    /** How tall the palette panel came out, so the panel behind it can be drawn to fit. */
    private var paletteRows = 0

    override fun init() {
        paletteWidgets.clear()
        recentButtons.clear()

        addRenderableWidget(StringWidget(MARGIN, 8, width - MARGIN * 2, 10, title, font))

        buildToolbar()
        buildBoxes()
        buildPalette()

        val listHeight = (height - listTop() - FOOTER_HEIGHT).coerceAtLeast(BOX_HEIGHT)
        list = addRenderableWidget(ConfigEntryList(minecraft, width, listHeight, listTop(), this))

        addRenderableWidget(
            Button.builder(Component.translatable("hex.titles.edit.done")) { onClose() }
                .bounds(width / 2 - DONE_WIDTH / 2, height - 26, DONE_WIDTH, BUTTON_HEIGHT).build(),
        )

        rebuild(preserveScroll = false)
    }

    // ---- the writing surface ---------------------------------------------------------------------------

    private fun buildBoxes() {
        val boxWidth = width - MARGIN * 2 - LABEL_WIDTH
        var y = boxesTop()

        // The hint is the alert's own message, because that is what an empty box actually shows — which is the
        // one thing about the merge rule that has to be visible without reading anything.
        titleBox = box(boxWidth, y, spec.title, Component.literal(previewWords())) { typed ->
            spec.title = typed
            onChange()
            refreshPresetRow()
        }
        y += BOX_HEIGHT + GAP
        subtitleBox = box(boxWidth, y, spec.subtitle, SUBTITLE_HINT) { typed ->
            spec.subtitle = typed
            onChange()
            refreshPresetRow()
        }

        // Focused on open so the toolbar has something to write into before anything is clicked.
        target = titleBox
        setInitialFocus(titleBox)
    }

    private fun box(boxWidth: Int, y: Int, value: String, hint: Component, onEdit: (String) -> Unit): EditBox =
        EditBox(font, MARGIN + LABEL_WIDTH, y, boxWidth, BOX_HEIGHT, hint).apply {
            setHint(hint)
            setMaxLength(TitleSpec.MAX_LENGTH)
            setValue(value)
            // Set after the value, so filling the box on open is not itself an edit.
            setResponder(onEdit)
            addRenderableWidget(this)
        }

    /**
     * The words the big line will show: the alert's message, or a sample when it has none yet.
     *
     * The sample is why an alert with an empty message still previews as something — a preview that correctly
     * showed nothing would read as a broken screen rather than as an empty message field.
     */
    private fun previewWords(): String =
        ownerText().ifBlank { Component.translatable("hex.titles.preview.text").string }

    // ---- the toolbar -----------------------------------------------------------------------------------

    /**
     * The formatting row.
     *
     * Labels are the codes drawn in the style they write — a bold `B`, an italic `I` — and stay
     * [Component.literal]: they are not language, and a translated `B` on a bold button would be a worse
     * button. The tooltips carry the words.
     *
     * Each writes its code **at the caret** rather than wrapping a selection, because that is what a `&` code
     * means: it applies from where it sits to the end of the line, or until another code changes it. There is
     * nothing to wrap.
     */
    private fun buildToolbar() {
        var x = MARGIN
        val y = TOOLBAR_Y

        fun tool(label: Component, key: String, code: String) {
            addRenderableWidget(
                Button.builder(label) { insert(code) }
                    .bounds(x, y, TOOL_WIDTH, TOOL_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable("hex.titles.edit.$key")))
                    .build(),
            )
            x += TOOL_WIDTH + TOOL_GAP
        }

        tool(Component.literal("B").withStyle { it.withBold(true) }, "bold", "&l")
        tool(Component.literal("I").withStyle { it.withItalic(true) }, "italic", "&o")
        tool(Component.literal("U").withStyle { it.withUnderlined(true) }, "underline", "&n")
        tool(Component.literal("S").withStyle { it.withStrikethrough(true) }, "strikethrough", "&m")
        tool(Component.literal("K").withStyle { it.withObfuscated(true) }, "obfuscated", "&k")

        x += TOOL_GROUP_GAP
        // The palette is a panel rather than a row of its own, so the toolbar stays one line on any width.
        addRenderableWidget(
            Button.builder(Component.literal("&")) { togglePalette() }
                .bounds(x, y, TOOL_WIDTH, TOOL_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.titles.edit.color")))
                .build(),
        )

        addRenderableWidget(
            Button.builder(Component.translatable("hex.titles.edit.preview")) { preview() }
                .bounds(width - MARGIN - PREVIEW_WIDTH, y, PREVIEW_WIDTH, TOOL_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.titles.edit.preview.tooltip")))
                .build(),
        )
    }

    /** Writes [code] into whichever box was last being typed in, and puts the caret back there. */
    private fun insert(code: String) {
        val box = target ?: titleBox
        box.insertText(code)
        setFocused(box)
        target = box
    }

    // ---- the colour palette ----------------------------------------------------------------------------

    /**
     * The sixteen colours, chroma and reset, then the colours picked recently anywhere in the mod.
     *
     * Built once and toggled with `visible` rather than rebuilt on each open, and a panel here rather than a
     * button that opens [net.trilleo.color.gui.ColorPickerScreen] — for the reason the notebook's palette gives:
     * leaving the screen and coming back would lose the caret, which is the very thing a colour is applied at.
     * It shares the picker's colour table and its recent list instead, which is the part worth sharing.
     */
    private fun buildPalette() {
        val perRow = ((width - MARGIN * 2 + SWATCH_GAP) / (SWATCH_SIZE + SWATCH_GAP)).coerceAtLeast(1)
        val top = paletteTop()

        PALETTE.forEachIndexed { index, swatch ->
            val row = index / perRow
            val column = index % perRow
            addPaletteWidget(
                Button.builder(swatchLabel(swatch.rgb)) { insert("&" + swatch.code) }
                    .bounds(
                        MARGIN + column * (SWATCH_SIZE + SWATCH_GAP),
                        top + row * (SWATCH_SIZE + SWATCH_GAP),
                        SWATCH_SIZE,
                        SWATCH_SIZE,
                    )
                    .tooltip(Tooltip.create(swatch.name))
                    .build(),
            )
        }

        val swatchRows = (PALETTE.size + perRow - 1) / perRow
        buildRecent(top + swatchRows * (SWATCH_SIZE + SWATCH_GAP), perRow)

        val hexY = top + (swatchRows + 1) * (SWATCH_SIZE + SWATCH_GAP)
        hexBox = EditBox(font, MARGIN, hexY, HEX_WIDTH, SWATCH_SIZE, HEX_LABEL).apply {
            setHint(HEX_LABEL)
            setMaxLength(HEX_MAX)
            setResponder { refreshHexApply() }
        }
        addPaletteWidget(hexBox)

        hexApply = Button.builder(swatchLabel(UNSET_SWATCH_COLOR)) { applyHex() }
            .bounds(MARGIN + HEX_WIDTH + SWATCH_GAP, hexY, SWATCH_SIZE, SWATCH_SIZE)
            .tooltip(Tooltip.create(Component.translatable("hex.titles.edit.color.custom")))
            .build()
        addPaletteWidget(hexApply)
        refreshHexApply()

        paletteRows = swatchRows + 2
    }

    private fun buildRecent(y: Int, perRow: Int) {
        repeat(minOf(ColorConfig.MAX_RECENT, perRow)) { index ->
            val button = Button.builder(swatchLabel(UNSET_SWATCH_COLOR)) {
                ColorConfig.recent.getOrNull(index)
                    ?.let { ColorValue.parse(it) }
                    ?.let { insert(hexCode(it)) }
            }
                .bounds(MARGIN + index * (SWATCH_SIZE + SWATCH_GAP), y, SWATCH_SIZE, SWATCH_SIZE)
                .tooltip(Tooltip.create(Component.translatable("hex.color.recent")))
                .build()
            recentButtons += button
            addPaletteWidget(button)
        }
        refreshRecent()
    }

    /** Points each recent slot at whatever colour is in it now, hiding the slots with nothing behind them. */
    private fun refreshRecent() {
        val recent = ColorConfig.recent
        recentButtons.forEachIndexed { index, button ->
            val rgb = recent.getOrNull(index)?.let { ColorValue.parse(it) }
            button.visible = paletteOpen && rgb != null
            button.message = swatchLabel(rgb ?: UNSET_SWATCH_COLOR)
        }
    }

    private fun addPaletteWidget(widget: AbstractWidget) {
        widget.visible = false
        paletteWidgets += widget
        // addWidget, not addRenderableWidget: the palette floats over the boxes below it, so it is drawn by
        // hand afterwards — a registered renderable would be painted under them.
        addWidget(widget)
    }

    private fun swatchLabel(rgb: Int): Component = Component.literal(SWATCH_GLYPH)
        .withStyle { style: Style -> style.withColor(rgb and ColorValue.RGB_MASK) }

    private fun hexCode(rgb: Int): String =
        "&" + Chroma.HEX_MARKER + ColorValue.format(rgb, alpha = false).removePrefix("#")

    private fun typedHex(): Int? = ColorValue.parse(hexBox.value)?.and(ColorValue.RGB_MASK)

    /** Keeps the apply button showing the colour it would write, and dead until that colour can be read. */
    private fun refreshHexApply() {
        if (!::hexApply.isInitialized) return
        val rgb = typedHex()
        hexApply.active = rgb != null
        hexApply.message = swatchLabel(rgb ?: UNSET_SWATCH_COLOR)
    }

    private fun applyHex() {
        val rgb = typedHex() ?: return
        // Remembered in the same shared list every colour row in the mod writes to, so a colour invented here
        // is one click away the next time a region or a highlight needs it.
        ColorConfig.remember(ColorValue.format(rgb, alpha = false))
        insert(hexCode(rgb))
        refreshRecent()
    }

    private fun togglePalette() {
        paletteOpen = !paletteOpen
        paletteWidgets.forEach { it.visible = paletteOpen }
        // After the blanket assignment above, which would otherwise show every recent slot including empty ones.
        refreshRecent()
        if (!paletteOpen) target?.let { setFocused(it) }
    }

    // ---- the rows below ---------------------------------------------------------------------------------

    private fun rebuild(preserveScroll: Boolean = true) {
        val category = buildCategory()
        list?.show(listOf(category.title to category.entries), preserveScroll)
        shownPreset = TitlePreset.of(spec)
    }

    /**
     * Rebuilds the rows only when typing has changed which preset the spec matches.
     *
     * The boxes fire their responder on every keystroke, and the preset row is the only row that reads them —
     * so rebuilding the list unconditionally would throw away and re-create every row per character typed, for
     * a label that almost never changes.
     */
    private fun refreshPresetRow() {
        if (list != null && TitlePreset.of(spec) != shownPreset) rebuild()
    }

    /** Which preset the rows were last built for. See [refreshPresetRow]. */
    private var shownPreset: TitlePreset = TitlePreset.CUSTOM

    private fun buildCategory(): ConfigCategory = ConfigCategory.build("title_edit") {
        // A starting point rather than a mode: it writes leading codes into the two boxes above, which are then
        // ordinary text you can edit like anything else you typed.
        enum(
            "preset",
            default = TitlePreset.CUSTOM,
            get = { TitlePreset.of(spec) },
            set = { preset ->
                preset.applyTo(spec)
                onChange()
                // The boxes are showing text this just rewrote.
                titleBox.value = spec.title
                subtitleBox.value = spec.subtitle
                rebuild()
            },
        )

        seconds(
            "fade_in", TitleSpec.FADE_MIN, TitleSpec.FADE_MAX, TitleSpec.DEFAULT_FADE_IN,
            { spec.fadeInSeconds }, { spec.fadeInSeconds = it })
        seconds(
            "stay", TitleSpec.STAY_MIN, TitleSpec.STAY_MAX, TitleSpec.DEFAULT_STAY,
            { spec.staySeconds }, { spec.staySeconds = it })
        seconds(
            "fade_out", TitleSpec.FADE_MIN, TitleSpec.FADE_MAX, TitleSpec.DEFAULT_FADE_OUT,
            { spec.fadeOutSeconds }, { spec.fadeOutSeconds = it })

        // A blank id is what "silent" is stored as, but a row showing "None" reads as a setting nobody has
        // got to yet rather than as a decision — so the decision gets a switch of its own, and the row below
        // only appears once it has been made.
        toggle(
            "sound",
            default = false,
            get = { spec.sound.isNotBlank() },
            set = { on ->
                spec.sound = if (on) DEFAULT_SOUND else ""
                onChange()
                rebuild()
            },
        )
        if (spec.sound.isNotBlank()) {
            sound(
                "sound_choice",
                default = DEFAULT_SOUND,
                // Unlike the four alert editors, silence genuinely is a value here — a blank sound is
                // exactly how "a title that only appears" is stored, and nothing normalizes it away. The
                // switch above and this row say the same thing two ways, which is deliberate: the switch is
                // the decision, and this is the choice.
                optional = true,
                get = { spec.sound },
                set = { spec.sound = it; onChange(); rebuild() },
                defaultPitch = 1.0,
                getPitch = { spec.pitch },
                setPitch = { spec.pitch = it; onChange() },
                defaultVolume = 1.0,
                getVolume = { spec.volume },
                setVolume = { spec.volume = it; onChange() },
            )
        }
    }

    /** One of the three phase-length sliders, all of which read the same and differ only in their bounds. */
    private fun ConfigCategory.Builder.seconds(
        key: String,
        min: Double,
        max: Double,
        default: Double,
        get: () -> Double,
        set: (Double) -> Unit,
    ) {
        slider(
            key,
            min = min,
            max = max,
            step = 0.1,
            default = default,
            get = get,
            set = { set(it); onChange() },
            format = { String.format(Locale.ROOT, "%.1fs", it) },
        )
    }

    // ---- drawing ----------------------------------------------------------------------------------------

    override fun extractRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        // Remembered before anything else, so a click that moves focus onto a toolbar button still leaves the
        // last box the player typed in as the one a code lands in.
        (focused as? EditBox)?.let { if (it !== hexBox) target = it }

        extractPreview(extractor)
        super.extractRenderState(extractor, mouseX, mouseY, delta)

        extractor.text(font, TITLE_LABEL, MARGIN, boxesTop() + LABEL_OFFSET, LABEL_COLOR)
        extractor.text(font, SUBTITLE_LABEL, MARGIN, boxesTop() + BOX_HEIGHT + GAP + LABEL_OFFSET, LABEL_COLOR)

        // The palette floats over the boxes, so it goes on last — panel first, then the swatches themselves,
        // which are registered for events only and so are not drawn by super.
        if (paletteOpen) {
            extractor.fill(0, paletteTop() - SWATCH_GAP, width, paletteBottom(), PANEL_COLOR)
            extractor.outline(
                0,
                paletteTop() - SWATCH_GAP,
                width,
                paletteBottom() - paletteTop() + SWATCH_GAP,
                DIVIDER_COLOR
            )
            paletteWidgets.forEach { it.extractRenderState(extractor, mouseX, mouseY, delta) }
        }
    }

    /**
     * The title as it will actually look, rebuilt every frame.
     *
     * Every frame rather than on every edit because chroma is a colour that changes with the clock: a preview
     * cached even for a tick would step rather than flow, and this is the one place a player judges whether a
     * flowing title looks right.
     *
     * Drawn at the ratio vanilla uses — the big line twice the subtitle — but scaled down to fit a strip rather
     * than the middle of the screen. The words are the alert's message when the line carries only codes, which
     * is what makes the merge rule visible before the title ever fires.
     */
    private fun extractPreview(extractor: GuiGraphicsExtractor) {
        val top = PREVIEW_TOP
        extractor.fill(0, top, width, top + PREVIEW_HEIGHT, PREVIEW_BACKGROUND)
        extractor.horizontalLine(0, width, top, DIVIDER_COLOR)
        extractor.horizontalLine(0, width, top + PREVIEW_HEIGHT, DIVIDER_COLOR)

        val settings = TitleConfig.settings
        val titleLine = TitleFormat.merge(spec.title, previewWords())

        val pose = extractor.pose()
        pose.pushMatrix()
        pose.translate((width / 2).toFloat(), (top + TITLE_BASELINE).toFloat())
        pose.scale(TITLE_SCALE, TITLE_SCALE)
        extractor.centeredText(font, Titles.componentFor(titleLine, settings.defaultTitleColor), 0, 0, WHITE)
        pose.popMatrix()

        if (TitleFormat.hasText(spec.subtitle)) {
            pose.pushMatrix()
            pose.translate((width / 2).toFloat(), (top + SUBTITLE_BASELINE).toFloat())
            pose.scale(SUBTITLE_SCALE, SUBTITLE_SCALE)
            extractor.centeredText(
                font,
                Titles.componentFor(spec.subtitle, settings.defaultSubtitleColor),
                0,
                0,
                WHITE,
            )
            pose.popMatrix()
        }
    }

    /**
     * Gives the palette first refusal on a click while it is open.
     *
     * Screens hand a click to the first child under the cursor in the order they were added, and the boxes were
     * added before the swatches that float over them — so without this a swatch would be a hole you click
     * straight through into the text.
     */
    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (paletteOpen) {
            val hit = paletteWidgets.firstOrNull { it.isMouseOver(event.x(), event.y()) }
            if (hit != null) {
                // Focused by hand as well: the screen normally does that as part of routing a click, and this
                // click never reaches it — without which the hex field would not take a keystroke.
                setFocused(hit)
                return hit.mouseClicked(event, doubleClick)
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    // ---- layout -----------------------------------------------------------------------------------------

    private fun boxesTop(): Int = PREVIEW_TOP + PREVIEW_HEIGHT + GAP

    private fun listTop(): Int = boxesTop() + BOX_HEIGHT * 2 + GAP * 2

    /**
     * The palette opens over the rows *below* the boxes rather than over the boxes themselves.
     *
     * It has to cover something — it is a panel, not a row — and the boxes are the one thing it must not:
     * picking a colour is the moment you most want to see the line you are colouring, and the preview above it
     * changing as you click.
     */
    private fun paletteTop(): Int = listTop() + SWATCH_GAP

    private fun paletteBottom(): Int = paletteTop() + paletteRows * (SWATCH_SIZE + SWATCH_GAP)

    // ---- actions ----------------------------------------------------------------------------------------

    /** Fires the title for real, behind this screen, so the fades and the sound can be judged as well. */
    private fun preview() {
        Titles.show(
            minecraft,
            spec,
            title = TitleFormat.merge(spec.title, previewWords()),
            subtitle = spec.subtitle,
        )
    }

    override fun onClose() {
        // The title from a preview belongs to this screen, not to the world behind it.
        Titles.clear(minecraft)
        minecraft.setScreen(parent)
    }

    /** One colour the palette offers: the code it writes, and the swatch that stands for it. */
    private class Swatch(val code: String, val rgb: Int, val name: Component)

    private companion object {
        /** A short, bright, unmistakably deliberate note — the same one a sound action starts from. */
        const val DEFAULT_SOUND = "minecraft:block.note_block.pling"

        const val MARGIN = 8
        const val GAP = 4
        const val TOOLBAR_Y = 22
        const val TOOL_WIDTH = 20
        const val TOOL_HEIGHT = 20
        const val TOOL_GAP = 2
        const val TOOL_GROUP_GAP = 6
        const val PREVIEW_WIDTH = 60
        const val BUTTON_HEIGHT = 20
        const val DONE_WIDTH = 100
        const val FOOTER_HEIGHT = 32

        const val PREVIEW_TOP = TOOLBAR_Y + TOOL_HEIGHT + 4
        const val PREVIEW_HEIGHT = 54
        const val TITLE_BASELINE = 12
        const val SUBTITLE_BASELINE = 34
        const val TITLE_SCALE = 2.0f
        const val SUBTITLE_SCALE = 1.0f

        const val BOX_HEIGHT = 18
        const val LABEL_WIDTH = 52
        const val LABEL_OFFSET = 5

        const val SWATCH_SIZE = 16
        const val SWATCH_GAP = 3
        const val SWATCH_GLYPH = "■"
        const val HEX_WIDTH = 66
        const val HEX_MAX = 7
        const val UNSET_SWATCH_COLOR = 0x505050

        const val WHITE = 0xFFFFFFFF.toInt()
        const val LABEL_COLOR = 0xFFA0A0A0.toInt()
        const val PANEL_COLOR = 0xF0202024.toInt()
        const val DIVIDER_COLOR = 0xFF404046.toInt()

        /** Dark, so a light title reads against it, and opaque so the world behind cannot confuse the colours. */
        const val PREVIEW_BACKGROUND = 0xFF101014.toInt()

        val HEX_LABEL: Component = Component.literal("#RRGGBB")
        val TITLE_LABEL: Component = Component.translatable("hex.titles.edit.line_title")
        val SUBTITLE_LABEL: Component = Component.translatable("hex.titles.edit.line_subtitle")
        val SUBTITLE_HINT: Component = Component.translatable("hex.titles.edit.subtitle_hint")

        /**
         * Vanilla's sixteen, then chroma and reset.
         *
         * The sixteen come from [ColorValue.VANILLA] rather than being written out again, so this palette, the
         * notebook's and the colour picker's are one table and one set of names. Chroma and reset are appended
         * because they are codes without colours: neither is a value a colour setting could hold, so neither
         * belongs in [ColorValue].
         */
        val PALETTE: List<Swatch> by lazy {
            ColorValue.VANILLA.map { Swatch(it.code?.toString().orEmpty(), it.rgb, it.name) } + listOf(
                Swatch(
                    Chroma.CODE.toString(),
                    CHROMA_SWATCH_COLOR,
                    Component.translatable("hex.titles.edit.color.chroma"),
                ),
                Swatch(
                    Chroma.RESET.toString(),
                    RESET_SWATCH_COLOR,
                    Component.translatable("hex.titles.edit.color.reset"),
                ),
            )
        }

        /** Chroma has no one colour, so its swatch is a pink that none of the sixteen already uses. */
        const val CHROMA_SWATCH_COLOR = 0xFF88CC

        const val RESET_SWATCH_COLOR = 0xC0C0C0
    }
}
