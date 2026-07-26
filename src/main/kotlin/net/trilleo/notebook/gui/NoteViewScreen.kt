package net.trilleo.notebook.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.itemcustom.ItemCustomizeConfig
import net.trilleo.notebook.Notebook
import net.trilleo.notebook.md.NoteTasks
import net.trilleo.notebook.model.NoteDocument
import net.trilleo.util.Chroma

/**
 * A note as it reads, with nothing to edit — and one thing to do.
 *
 * The editor is for writing; this is for *using* a note while playing. Full width, no toolbar and no source
 * pane, so a dungeon checklist or a set of directions is as large and as legible as the screen allows.
 *
 * ### Reading is not the same as being read-only
 *
 * The one interaction that survives is the check box: a checklist you cannot tick is a picture of a checklist.
 * Clicking a task line flips it in the note's markdown through [NoteTasks] — the same edit as typing the `x`
 * yourself, so there is no second state to keep in step and a note open elsewhere sees an ordinary change.
 *
 * A note from a newer Hex is shown but not tickable, for the reason its editor is read-only: this build cannot
 * know what else is in the file it would be rewriting.
 */
class NoteViewScreen(
    private val parent: Screen?,
    private val document: NoteDocument,
) : Screen(Component.translatable("hex.notebook.view.title")) {

    private lateinit var preview: NotePreview

    override fun init() {
        preview = NotePreview(font, MARGIN, HEADER_HEIGHT, width - MARGIN * 2, bodyHeight()).apply {
            setSource(document.source)
            if (!document.readOnly) onToggleTask = ::toggleTask
        }
        addRenderableWidget(preview)

        val y = height - FOOTER_HEIGHT + (FOOTER_HEIGHT - BUTTON_HEIGHT) / 2

        addRenderableWidget(
            Button.builder(Component.translatable("hex.notebook.edit")) {
                minecraft.setScreen(NoteEditorScreen(parent as? NotebookScreen, document))
            }.bounds(MARGIN, y, ACTION_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.notebook.view.edit.tooltip")))
                .build(),
        )

        addRenderableWidget(
            Button.builder(Component.translatable("gui.done")) { onClose() }
                .bounds(width / 2 - DONE_WIDTH / 2, y, DONE_WIDTH, BUTTON_HEIGHT)
                .build(),
        )
    }

    /**
     * Ticks the box on [line] and puts the result back in front of the reader.
     *
     * Written through [Notebook.setSource], the same door the editor's text box uses, so the note is marked
     * dirty and the debounce saves it. [NotePreview.setSource] then re-reads what the note now says rather
     * than being told what changed — the rendering has one source of truth, which is the text.
     */
    private fun toggleTask(line: Int) {
        val updated = NoteTasks.toggle(document.source, line)
        if (updated == document.source) return
        Notebook.setSource(document, updated)
        preview.setSource(updated)
    }

    override fun extractBackground(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(extractor, mouseX, mouseY, delta)

        val panel = NotebookTheme.panel()
        extractor.fill(0, 0, width, HEADER_HEIGHT, panel)
        extractor.fill(0, height - FOOTER_HEIGHT, width, height, panel)
        extractor.horizontalLine(0, width, HEADER_HEIGHT - 1, NotebookTheme.DIVIDER_COLOR)
        extractor.horizontalLine(0, width, height - FOOTER_HEIGHT, NotebookTheme.DIVIDER_COLOR)

        // The note's own title, through Chroma exactly as the browser's list draws it, so a note named in
        // colour is named in colour here too.
        extractor.text(
            font,
            Chroma.build(
                document.meta.title,
                seconds = ItemCustomizeConfig.chromaSeconds,
                width = ItemCustomizeConfig.chromaWidth,
            ),
            MARGIN,
            TITLE_Y,
            TITLE_COLOR,
        )
    }

    override fun extractRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(extractor, mouseX, mouseY, delta)

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

    private fun bodyHeight(): Int = height - HEADER_HEIGHT - FOOTER_HEIGHT - MARGIN

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun removed() {
        // A ticked box is an edit like any other, so leaving is a save point like any other.
        Notebook.saveNow(document)
        (parent as? NotebookScreen)?.refreshRows()
    }

    private companion object {
        const val MARGIN = 6
        const val HEADER_HEIGHT = 28
        const val FOOTER_HEIGHT = 32
        const val BUTTON_HEIGHT = 20
        const val ACTION_WIDTH = 60
        const val DONE_WIDTH = 100
        const val TITLE_Y = 10

        const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        const val WARNING_COLOR = 0xFFFFD25F.toInt()
    }
}
