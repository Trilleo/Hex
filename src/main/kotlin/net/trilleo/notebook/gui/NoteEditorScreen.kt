package net.trilleo.notebook.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.notebook.NoteShare
import net.trilleo.notebook.Notebook
import net.trilleo.notebook.model.NoteDocument
import net.trilleo.util.Notify

/**
 * One note: its title, its text, and the handful of things you do to a whole note.
 *
 * The body is edited as **markdown source** — a plain text area over the note's own text. The visual,
 * block-based editor renders into this same screen later; the mode switch and the toolbar belong to that
 * work, and until then the source pane is the whole of it. Nothing here will need undoing for that: the note's
 * text is canonical either way, so the visual editor is a second view onto exactly this string.
 *
 * Vanilla's [MultiLineEditBox] does all of the text handling, including selection, word motion, clipboard and
 * IME composition. It is the right tool for *source* editing precisely because it treats the text as one
 * unstyled string, which is what source is.
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

    override fun init() {
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

        val bodyTop = HEADER_HEIGHT
        val bodyHeight = height - HEADER_HEIGHT - FOOTER_HEIGHT
        body = MultiLineEditBox.builder()
            .setX(MARGIN)
            .setY(bodyTop)
            .setPlaceholder(Component.translatable("hex.notebook.editor.placeholder"))
            .setShowBackground(true)
            .setShowDecorations(true)
            .build(font, width - MARGIN * 2, bodyHeight, BODY_LABEL)
            .apply {
                setCharacterLimit(MAX_BODY)
                setValue(document.source)
                // Set after the value, so filling the box on open does not itself count as an edit and stamp
                // the note as modified the moment it is looked at.
                setValueListener { text -> Notebook.setSource(document, text) }
            }
        addRenderableWidget(body)

        buildFooter()
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

    private fun titleWidth(): Int = (width - MARGIN * 2 - META_WIDTH - GAP).coerceAtLeast(MIN_TITLE_WIDTH)

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

    // ---- chrome ----------------------------------------------------------------------------------------

    override fun extractBackground(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(extractor, mouseX, mouseY, delta)

        extractor.fill(0, 0, width, HEADER_HEIGHT, PANEL_COLOR)
        extractor.fill(0, height - FOOTER_HEIGHT, width, height, PANEL_COLOR)
        extractor.horizontalLine(0, width, HEADER_HEIGHT - 1, DIVIDER_COLOR)
        extractor.horizontalLine(0, width, height - FOOTER_HEIGHT, DIVIDER_COLOR)
    }

    override fun extractRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(extractor, mouseX, mouseY, delta)

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

    private companion object {
        const val MARGIN = 6
        const val GAP = 6
        const val HEADER_HEIGHT = 32
        const val FOOTER_HEIGHT = 32
        const val TITLE_HEIGHT = 18
        const val BUTTON_HEIGHT = 20
        const val META_WIDTH = 60
        const val ACTION_WIDTH = 60
        const val DONE_WIDTH = 100
        const val MIN_TITLE_WIDTH = 60

        const val MAX_TITLE = 64

        /**
         * A generous ceiling rather than a target. It exists so a paste of something enormous fails visibly at
         * the box instead of at the file write, and it is far beyond anything anyone types by hand.
         */
        const val MAX_BODY = 64_000

        val TITLE_LABEL: Component = Component.translatable("hex.notebook.editor.title_hint")
        val BODY_LABEL: Component = Component.translatable("hex.notebook.editor.body")

        const val PANEL_COLOR = 0xC0101010.toInt()
        const val DIVIDER_COLOR = 0x60FFFFFF
        const val WARNING_COLOR = 0xFFFFD25F.toInt()
    }
}
