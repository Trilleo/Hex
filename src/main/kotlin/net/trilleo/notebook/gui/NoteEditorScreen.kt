package net.trilleo.notebook.gui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.components.MultilineTextField
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.trilleo.notebook.NoteShare
import net.trilleo.notebook.Notebook
import net.trilleo.notebook.NotebookConfig
import net.trilleo.notebook.model.NoteDocument
import net.trilleo.notebook.model.NoteEditorView
import net.trilleo.util.HexColor
import net.trilleo.util.Notify
import java.util.*

/**
 * One note: its title, its text, and the handful of things you do to a whole note.
 *
 * The body is edited as **markdown source**, with a formatting toolbar over it and a rendered preview beside it.
 * The two panes are the same note: [NotePreview] re-reads the source on every keystroke, so the right-hand side
 * is what the left-hand side means, always, and neither is a separate document that could drift.
 *
 * ### Why source and preview rather than one editing surface
 *
 * Because the note's markdown is canonical. An editor that hid the markers would have to keep a map between
 * what is drawn and what is stored for every caret move and every click — and would have to *refuse* anything
 * its parser could not model, which is exactly the text a note is most likely to contain. Source plus preview
 * gives the same answer to "what will this look like" while leaving the text the player wrote untouched and
 * every markdown construct — a table, a footnote, something this build has never heard of — still typeable.
 *
 * Vanilla's [MultiLineEditBox] does all of the text handling, including selection, word motion, clipboard and
 * IME composition. It is the right tool for *source* editing precisely because it treats the text as one
 * unstyled string, which is what source is. The toolbar reaches its selection through [NoteEdits].
 *
 * There is nothing to submit. Typing marks the note dirty and the debounce writes it; leaving the screen is a
 * definite save point on top of that.
 */
class NoteEditorScreen(
    private val parent: NotebookScreen?,
    private val document: NoteDocument,
) : Screen(Component.translatable("hex.notebook.editor.title")) {

    private lateinit var titleBox: EditBox
    private lateinit var body: MultiLineEditBox
    private lateinit var preview: NotePreview

    /** The toolbar's buttons, so the whole row can be switched off for a read-only note in one place. */
    private val tools = mutableListOf<Button>()

    /** Everything the palette panel holds — the swatches, the hex field and its apply button. */
    private val paletteWidgets = mutableListOf<AbstractWidget>()

    /** The hex field and the button that writes what it holds, kept for the value checks between them. */
    private lateinit var hexBox: EditBox
    private lateinit var hexApply: Button
    private var paletteOpen = false
    private var paletteRows = 0

    private var view: NoteEditorView = NotebookConfig.editorView

    override fun init() {
        tools.clear()
        paletteWidgets.clear()

        titleBox = EditBox(font, MARGIN, MARGIN + 4, titleWidth(), TITLE_HEIGHT, TITLE_LABEL).apply {
            setHint(TITLE_LABEL)
            setMaxLength(MAX_TITLE)
            value = document.meta.title
            setEditable(!document.readOnly)
            setResponder { text -> Notebook.rename(document, text) }
        }
        addRenderableWidget(titleBox)

        addRenderableWidget(
            Button.builder(Component.translatable("hex.notebook.meta")) {
                minecraft.setScreen(NoteMetaScreen(this, document))
            }.bounds(width - MARGIN - META_WIDTH, MARGIN + 4, META_WIDTH, TITLE_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.notebook.meta.tooltip")))
                .build(),
        )

        buildToolbar()

        body = MultiLineEditBox.builder()
            .setX(sourceX())
            .setY(bodyTop())
            .setPlaceholder(Component.translatable("hex.notebook.editor.placeholder"))
            // The box's own background is a flat black sprite with no say in how solid it is, so the screen
            // draws the surface behind it instead — see NotebookTheme.body. Decorations stay: they are the
            // character counter, not a background.
            .setShowBackground(false)
            .setShowDecorations(true)
            .build(font, sourceWidth(), bodyHeight(), BODY_LABEL)
            .apply {
                setCharacterLimit(MAX_BODY)
                setValue(document.source)
                // Set after the value, so filling the box on open does not itself count as an edit and stamp
                // the note as modified the moment it is looked at.
                setValueListener { text ->
                    Notebook.setSource(document, text)
                    preview.setSource(text)
                }
                visible = view.showsSource
            }
        addRenderableWidget(body)

        preview = NotePreview(font, previewX(), bodyTop(), previewWidth(), bodyHeight()).apply {
            setSource(document.source)
            visible = view.showsPreview
        }
        addRenderableWidget(preview)

        buildPalette()
        buildFooter()
    }

    // ---- the toolbar -----------------------------------------------------------------------------------

    /**
     * The formatting row.
     *
     * Labels are symbols and stay [Component.literal] — `B`, `•`, `☑` are not language, and a translated `B`
     * on a bold button would be a worse button. The tooltips carry the words.
     */
    private fun buildToolbar() {
        var x = MARGIN
        val y = TOOLBAR_Y
        val toolWidth = toolWidth()

        fun tool(label: Component, key: String, width: Int = toolWidth, action: (MultilineTextField) -> Unit) {
            val button = Button.builder(label) { onField(action) }
                .bounds(x, y, width, TOOL_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.notebook.editor.$key")))
                .build()
            button.active = !document.readOnly
            tools += button
            addRenderableWidget(button)
            x += width + TOOL_GAP
        }

        tool(Component.literal("B").withStyle { it.withBold(true) }, "bold") {
            NoteEdits.toggleWrap(it, "**")
        }
        tool(Component.literal("I").withStyle { it.withItalic(true) }, "italic") {
            NoteEdits.toggleWrap(it, "*")
        }
        tool(Component.literal("S").withStyle { it.withStrikethrough(true) }, "strikethrough") {
            NoteEdits.toggleWrap(it, "~~")
        }
        tool(Component.literal("{}"), "code") { NoteEdits.toggleWrap(it, "`") }

        x += TOOL_GROUP_GAP
        tool(Component.literal("H1"), "heading1") { field ->
            NoteEdits.toggleLinePrefix(field) { "# " }
        }
        tool(Component.literal("H2"), "heading2") { field ->
            NoteEdits.toggleLinePrefix(field) { "## " }
        }
        tool(Component.literal("•"), "bullet") { field ->
            NoteEdits.toggleLinePrefix(field) { "- " }
        }
        tool(Component.literal("1."), "numbered") { field ->
            NoteEdits.toggleLinePrefix(field) { index -> "${index + 1}. " }
        }
        tool(Component.literal("☑"), "task") { NoteEdits.cycleTask(it) }
        tool(Component.literal("❝"), "quote") { field ->
            NoteEdits.toggleLinePrefix(field) { "> " }
        }

        x += TOOL_GROUP_GAP
        tool(Component.literal("—"), "rule") { NoteEdits.insert(it, "\n---\n") }
        tool(Component.literal("⊞"), "table") { NoteEdits.insert(it, TABLE_TEMPLATE) }
        tool(Component.literal("🔗"), "link") { field ->
            NoteEdits.link(
                field,
                Component.translatable("hex.notebook.editor.link.label").string,
                Component.translatable("hex.notebook.editor.link.target").string,
            )
        }

        // The palette is a panel rather than a row of its own, so the toolbar stays one line on any width.
        val palette = Button.builder(Component.literal("&")) { togglePalette() }
            .bounds(x, y, toolWidth, TOOL_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.notebook.editor.color")))
            .build()
        palette.active = !document.readOnly
        tools += palette
        addRenderableWidget(palette)

        // Right-aligned, because it changes the shape of the screen rather than the text — it belongs with the
        // window, not with the formatting.
        addRenderableWidget(
            Button.builder(viewLabel()) { cycleView() }
                .bounds(width - MARGIN - VIEW_WIDTH, y, VIEW_WIDTH, TOOL_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.notebook.editor.view.tooltip")))
                .build(),
        )
    }

    /**
     * The sixteen colours, chroma and reset, laid out under the toolbar and hidden until asked for.
     *
     * Built once and toggled with `visible` rather than rebuilt on each open: rebuilding the screen would take
     * the caret and the selection with it, which are the two things a colour button exists to act on.
     */
    private fun buildPalette() {
        val perRow = ((width - MARGIN * 2 + SWATCH_GAP) / (SWATCH_SIZE + SWATCH_GAP)).coerceAtLeast(1)
        val swatchRows = (PALETTE.size + perRow - 1) / perRow
        // One row more than the swatches need: the last one is the hex field, for the colours Minecraft has
        // no letter for.
        paletteRows = swatchRows + 1

        PALETTE.forEachIndexed { index, entry ->
            val row = index / perRow
            val column = index % perRow
            addPaletteWidget(
                Button.builder(entry.label()) { onField { NoteEdits.color(it, entry.code) } }
                    .bounds(
                        MARGIN + column * (SWATCH_SIZE + SWATCH_GAP),
                        paletteTop() + row * (SWATCH_SIZE + SWATCH_GAP),
                        SWATCH_SIZE,
                        SWATCH_SIZE,
                    )
                    .tooltip(Tooltip.create(Component.translatable(entry.tooltipKey)))
                    .build(),
            )
        }

        val hexY = paletteTop() + swatchRows * (SWATCH_SIZE + SWATCH_GAP)
        hexBox = EditBox(font, MARGIN, hexY, HEX_WIDTH, SWATCH_SIZE, HEX_LABEL).apply {
            setHint(HEX_LABEL)
            setMaxLength(HEX_MAX)
            setResponder { refreshHexApply() }
        }
        addPaletteWidget(hexBox)

        hexApply = Button.builder(Component.literal(SWATCH_GLYPH)) { applyHex() }
            .bounds(MARGIN + HEX_WIDTH + SWATCH_GAP, hexY, SWATCH_SIZE, SWATCH_SIZE)
            .tooltip(Tooltip.create(Component.translatable("hex.notebook.editor.color.custom")))
            .build()
        addPaletteWidget(hexApply)
        refreshHexApply()
    }

    private fun addPaletteWidget(widget: AbstractWidget) {
        widget.visible = false
        widget.active = !document.readOnly
        paletteWidgets += widget
        // addWidget, not addRenderableWidget: the palette floats over the panes, so it is drawn by hand
        // after them — a registered renderable would be painted under the note's own text.
        addWidget(widget)
    }

    /**
     * The colour in the hex field, or null while it is not a colour yet.
     *
     * The `#` is optional and case does not matter, because a colour arrives pasted from a palette site as
     * often as it is typed. What goes into the note is the canonical spelling either way.
     */
    private fun typedHex(): Int? {
        val text = hexBox.value.trim().removePrefix("#")
        if (text.length != HEX_DIGITS) return null
        return HexColor.parse(text)
    }

    /** Keeps the apply button showing the colour it would write, and dead until that colour can be read. */
    private fun refreshHexApply() {
        if (!::hexApply.isInitialized) return
        val rgb = typedHex()
        hexApply.active = rgb != null && !document.readOnly
        hexApply.message = Component.literal(SWATCH_GLYPH)
            .withStyle { style: Style -> style.withColor(rgb ?: UNSET_SWATCH_COLOR) }
    }

    private fun applyHex() {
        val rgb = typedHex() ?: return
        onField { NoteEdits.color(it, String.format(Locale.ROOT, "#%06X", rgb)) }
    }

    private fun togglePalette() {
        paletteOpen = !paletteOpen
        paletteWidgets.forEach { it.visible = paletteOpen }
        if (!paletteOpen && view.showsSource) setFocused(body)
    }

    private fun cycleView() {
        view = view.next()
        NotebookConfig.settings.editorView = view
        NotebookConfig.save()
        // The panes change size as well as visibility, so this is a rebuild rather than a flag flip. The note's
        // text survives it because the document, not the widget, is what holds it.
        rebuildWidgets()
    }

    private fun viewLabel(): Component =
        Component.translatable("hex.notebook.editor.view.${view.name.lowercase()}")

    /** Runs a toolbar action against the source pane, then puts the caret back where the player left it. */
    private fun onField(action: (MultilineTextField) -> Unit) {
        if (document.readOnly) return
        if (!view.showsSource) return
        action(NoteEdits.fieldOf(body))
        setFocused(body)
    }

    private fun buildFooter() {
        val y = height - FOOTER_HEIGHT + (FOOTER_HEIGHT - BUTTON_HEIGHT) / 2
        var x = MARGIN

        addRenderableWidget(
            Button.builder(Component.translatable("hex.notebook.export")) { export() }
                .bounds(x, y, ACTION_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.notebook.export.tooltip")))
                .build(),
        )
        x += ACTION_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.notebook.duplicate")) { duplicate() }
                .bounds(x, y, ACTION_WIDTH, BUTTON_HEIGHT)
                .build(),
        )

        addRenderableWidget(
            Button.builder(Component.translatable("gui.done")) { onClose() }
                .bounds(width / 2 - DONE_WIDTH / 2, y, DONE_WIDTH, BUTTON_HEIGHT)
                .build(),
        )

        addRenderableWidget(
            Button.builder(Component.translatable("hex.notebook.delete")) { delete() }
                .bounds(width - MARGIN - ACTION_WIDTH, y, ACTION_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.notebook.delete.tooltip")))
                .build(),
        )
    }

    // ---- layout ----------------------------------------------------------------------------------------

    private fun titleWidth(): Int = (width - MARGIN * 2 - META_WIDTH - GAP).coerceAtLeast(MIN_TITLE_WIDTH)

    /**
     * How wide each toolbar button is, so the row still fits beside the layout button on a small window.
     *
     * The buttons shrink rather than wrapping to a second line: the toolbar is above the writing surface, and a
     * row that grows taller as the window narrows would take that space away from the note. Below
     * [MIN_TOOL_WIDTH] they stop shrinking and the row is simply clipped — at that width the screen has bigger
     * problems than a hidden colour button.
     */
    private fun toolWidth(): Int {
        val available = width - MARGIN * 2 - VIEW_WIDTH - GAP - TOOL_GROUP_GAP * 2 - TOOL_GAP * (TOOL_COUNT - 1)
        return (available / TOOL_COUNT).coerceIn(MIN_TOOL_WIDTH, TOOL_WIDTH)
    }

    // The panes, as functions rather than fields: the background pass draws them and init places the widgets
    // at them, and the two must not be able to drift apart.
    private fun bodyTop(): Int = HEADER_HEIGHT + TOOLBAR_HEIGHT

    /**
     * The panes stop short of the footer by [COUNTER_HEIGHT].
     *
     * `MultiLineEditBox` draws its character counter just *below* itself and offers no say in the matter, so a
     * pane that ran all the way to the footer would print `1234 / 64000` underneath the Done button. The gap is
     * the counter's own room.
     */
    private fun bodyHeight(): Int = height - bodyTop() - FOOTER_HEIGHT - COUNTER_HEIGHT

    private fun paneWidth(): Int = width - MARGIN * 2

    private fun sourceX(): Int = MARGIN

    private fun sourceWidth(): Int = when (view) {
        NoteEditorView.SPLIT -> (paneWidth() - GAP) / 2
        else -> paneWidth()
    }

    private fun previewX(): Int = when (view) {
        NoteEditorView.SPLIT -> MARGIN + sourceWidth() + GAP
        else -> MARGIN
    }

    private fun previewWidth(): Int = when (view) {
        NoteEditorView.SPLIT -> paneWidth() - sourceWidth() - GAP
        else -> paneWidth()
    }

    private fun paletteTop(): Int = bodyTop() + SWATCH_GAP

    private fun paletteHeight(): Int = paletteRows * (SWATCH_SIZE + SWATCH_GAP) + SWATCH_GAP

    // ---- actions ---------------------------------------------------------------------------------------

    /** Copies the note — header and all — so pasting it anywhere else reconstructs it whole. */
    private fun export() {
        minecraft.keyboardHandler.setClipboard(NoteShare.export(document))
        Notify.chat(minecraft, Component.translatable("hex.notebook.export.done", document.meta.title))
    }

    /**
     * Duplicates and opens the copy.
     *
     * Straight into the copy rather than back to the list, because the reason to duplicate a note is almost
     * always to change the copy — a template filled in differently, last week's checklist reused.
     */
    private fun duplicate() {
        // Flushed first: the copy is made from this note's current text, which may only be in the box.
        Notebook.saveNow(document)
        val copy = Notebook.duplicate(document)
        minecraft.setScreen(NoteEditorScreen(parent, copy))
    }

    /** Hands the confirmation to the browser, which owns the notebook-level actions and the refresh after. */
    private fun delete() {
        val browser = parent
        if (browser == null) {
            // Reached from a command with nothing behind it. Deleting into an empty screen would leave the
            // player looking at the world with no confirmation that anything happened, so send them to the
            // browser and let it ask there.
            val screen = NotebookScreen(null)
            minecraft.setScreen(screen)
            screen.confirmDelete(document)
            return
        }
        minecraft.setScreen(browser)
        browser.confirmDelete(document)
    }

    /**
     * The shortcuts every word processor has, on the pane they apply to.
     *
     * Checked before `super`, which would hand the key to the focused text box and have it typed as a
     * character. Escape and the rest are left alone.
     */
    /**
     * Gives the palette first refusal on a click while it is open.
     *
     * Screens hand a click to the first child that is under the cursor, in the order they were added, and the
     * source pane was added before the swatches that float over it — so without this a swatch would be a hole
     * you click straight through into the text.
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

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.hasControlDown() && !document.readOnly && body.isFocused) {
            val marker = when (event.key) {
                InputConstants.KEY_B -> "**"
                InputConstants.KEY_I -> "*"
                InputConstants.KEY_E -> "`"
                else -> null
            }
            if (marker != null) {
                onField { NoteEdits.toggleWrap(it, marker) }
                return true
            }
        }
        return super.keyPressed(event)
    }

    // ---- chrome ----------------------------------------------------------------------------------------

    override fun extractBackground(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(extractor, mouseX, mouseY, delta)

        val panel = NotebookTheme.panel()
        extractor.fill(0, 0, width, HEADER_HEIGHT + TOOLBAR_HEIGHT, panel)
        extractor.fill(0, height - FOOTER_HEIGHT, width, height, panel)
        extractor.horizontalLine(0, width, HEADER_HEIGHT - 1, NotebookTheme.DIVIDER_COLOR)
        extractor.horizontalLine(0, width, HEADER_HEIGHT + TOOLBAR_HEIGHT - 1, NotebookTheme.DIVIDER_COLOR)
        extractor.horizontalLine(0, width, height - FOOTER_HEIGHT, NotebookTheme.DIVIDER_COLOR)

        // The writing surface, in place of the text area's own sprite. The outline is drawn whatever the
        // opacity is, so that at nothing at all the box still says where it ends and the world begins.
        if (view.showsSource) {
            val right = sourceX() + sourceWidth()
            extractor.fill(sourceX(), bodyTop(), right, bodyTop() + bodyHeight(), NotebookTheme.body())
            extractor.outline(sourceX(), bodyTop(), sourceWidth(), bodyHeight(), NotebookTheme.DIVIDER_COLOR)
        }
    }

    override fun extractRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(extractor, mouseX, mouseY, delta)

        // The palette floats over the panes, so it goes on last — panel first, then the swatches themselves,
        // which are registered for events only and so are not drawn by super.
        if (paletteOpen) {
            extractor.fill(0, bodyTop(), width, bodyTop() + paletteHeight(), NotebookTheme.panel())
            extractor.horizontalLine(0, width, bodyTop() + paletteHeight(), NotebookTheme.DIVIDER_COLOR)
            paletteWidgets.forEach { it.extractRenderState(extractor, mouseX, mouseY, delta) }
        }

        // Said plainly rather than left to be discovered when an edit silently fails to save. A note from a
        // newer Hex is shown, not hidden — the player can still read it and copy out of it.
        if (document.readOnly) {
            extractor.centeredText(
                font,
                Component.translatable("hex.notebook.editor.read_only"),
                width / 2,
                height - FOOTER_HEIGHT - font.lineHeight - 2,
                WARNING_COLOR,
            )
        }
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun removed() {
        // Typing marks the note dirty as it happens; this makes leaving the editor a definite save point
        // rather than waiting on the debounce.
        Notebook.saveNow(document)
        parent?.refreshRows()
    }

    /** One colour the palette offers: the code it writes, and the swatch that stands for it. */
    private class Swatch(val code: String, val rgb: Int, val tooltipKey: String) {
        fun label(): Component = Component.literal(SWATCH_GLYPH).withStyle { style: Style ->
            style.withColor(rgb)
        }
    }

    private companion object {
        const val MARGIN = 6
        const val GAP = 6
        const val HEADER_HEIGHT = 32
        const val TOOLBAR_HEIGHT = 26
        const val TOOLBAR_Y = HEADER_HEIGHT + 3
        const val FOOTER_HEIGHT = 32
        const val TITLE_HEIGHT = 18
        const val BUTTON_HEIGHT = 20
        const val META_WIDTH = 60
        const val ACTION_WIDTH = 60
        const val DONE_WIDTH = 100
        const val MIN_TITLE_WIDTH = 60

        const val TOOL_WIDTH = 20
        const val MIN_TOOL_WIDTH = 14
        const val TOOL_HEIGHT = 20
        const val TOOL_GAP = 2

        /** Every button on the row, palette included — what [toolWidth] divides the space between. */
        const val TOOL_COUNT = 14

        /** A wider gap between the inline, block and insert groups, so the row reads as three sets. */
        const val TOOL_GROUP_GAP = 6
        const val VIEW_WIDTH = 60

        const val SWATCH_SIZE = 16
        const val SWATCH_GAP = 3
        const val SWATCH_GLYPH = "■"
        const val HEX_WIDTH = 66
        const val HEX_MAX = 7
        const val HEX_DIGITS = 6
        const val UNSET_SWATCH_COLOR = 0x505050

        /** The counter `MultiLineEditBox` draws under itself, plus the gap it leaves above it. */
        const val COUNTER_HEIGHT = 13

        /** Not language: it is the shape of the value, in the notation the value is written in. */
        val HEX_LABEL: Component = Component.literal("#RRGGBB")

        /** An empty two-column table, which is the one everybody starts from. */
        const val TABLE_TEMPLATE: String = "\n|  |  |\n|---|---|\n|  |  |\n"

        const val MAX_TITLE = 64

        /**
         * A generous ceiling rather than a target. It exists so a paste of something enormous fails visibly at
         * the box instead of at the file write, and it is far beyond anything anyone types by hand.
         */
        const val MAX_BODY = 64_000

        val TITLE_LABEL: Component = Component.translatable("hex.notebook.editor.title_hint")
        val BODY_LABEL: Component = Component.translatable("hex.notebook.editor.body")

        const val WARNING_COLOR = 0xFFFFD25F.toInt()

        /**
         * Vanilla's sixteen, then chroma and reset — the same set and the same order as the codes themselves,
         * so the palette reads as the thing it writes rather than as a designer's selection.
         */
        val PALETTE = listOf(
            Swatch("0", 0x000000, "hex.notebook.editor.color.black"),
            Swatch("1", 0x0000AA, "hex.notebook.editor.color.dark_blue"),
            Swatch("2", 0x00AA00, "hex.notebook.editor.color.dark_green"),
            Swatch("3", 0x00AAAA, "hex.notebook.editor.color.dark_aqua"),
            Swatch("4", 0xAA0000, "hex.notebook.editor.color.dark_red"),
            Swatch("5", 0xAA00AA, "hex.notebook.editor.color.dark_purple"),
            Swatch("6", 0xFFAA00, "hex.notebook.editor.color.gold"),
            Swatch("7", 0xAAAAAA, "hex.notebook.editor.color.gray"),
            Swatch("8", 0x555555, "hex.notebook.editor.color.dark_gray"),
            Swatch("9", 0x5555FF, "hex.notebook.editor.color.blue"),
            Swatch("a", 0x55FF55, "hex.notebook.editor.color.green"),
            Swatch("b", 0x55FFFF, "hex.notebook.editor.color.aqua"),
            Swatch("c", 0xFF5555, "hex.notebook.editor.color.red"),
            Swatch("d", 0xFF55FF, "hex.notebook.editor.color.light_purple"),
            Swatch("e", 0xFFFF55, "hex.notebook.editor.color.yellow"),
            Swatch("f", 0xFFFFFF, "hex.notebook.editor.color.white"),
            Swatch("z", 0xFF88CC, "hex.notebook.editor.color.chroma"),
            Swatch("r", 0xC0C0C0, "hex.notebook.editor.color.reset"),
        )
    }
}
