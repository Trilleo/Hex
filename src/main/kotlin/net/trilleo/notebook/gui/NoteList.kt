package net.trilleo.notebook.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component
import net.trilleo.itemcustom.ItemCustomizeConfig
import net.trilleo.notebook.NoteIcon
import net.trilleo.notebook.Notebook
import net.trilleo.notebook.NotebookConfig
import net.trilleo.notebook.model.NoteDocument
import net.trilleo.util.Chroma
import net.trilleo.util.HexColor

/**
 * The scrolling list of notes in [NotebookScreen].
 *
 * Built on [ContainerObjectSelectionList] exactly as [net.trilleo.region.gui.RegionList] is, and holding rows
 * by reference identity for the same reason — [net.trilleo.notebook.model.NoteMeta] is deliberately not a data
 * class, so deleting a row is an identity remove that cannot take an equal-looking sibling with it.
 *
 * A row shows the note's colour as a bar down its left edge, its icon, its title, and a line of context
 * underneath: normally the note's opening line, or the line a search matched. That second line is what makes a
 * list of a dozen similarly-named notes navigable.
 */
class NoteList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val screen: NotebookScreen,
) : ContainerObjectSelectionList<NoteList.Row>(minecraft, width, height, top, ROW_HEIGHT) {

    override fun getRowWidth(): Int = width - 24

    override fun scrollBarX(): Int = x + width - 8

    /**
     * Replaces the visible rows, or shows [emptyHint] when there are none.
     *
     * The scroll position is kept, because this is called after every rename, pin and delete, and snapping to
     * the top each time would throw away the player's place in a long notebook.
     */
    fun show(entries: List<Entry>, emptyHint: Component) {
        val scroll = scrollAmount()
        clearEntries()
        if (entries.isEmpty()) {
            addEntry(HintRow(emptyHint))
        } else {
            entries.forEach { addEntry(NoteRow(it.document, it.subtitle, screen)) }
        }
        setScrollAmount(scroll)
    }

    /** One note and the line of context to show beneath it, which the caller decides. */
    class Entry(val document: NoteDocument, val subtitle: String)

    // ---- rows ------------------------------------------------------------------------------------------

    abstract class Row : ContainerObjectSelectionList.Entry<Row>() {
        protected abstract val widgets: List<AbstractWidget>

        override fun children(): List<AbstractWidget> = widgets

        override fun narratables(): List<NarratableEntry> = widgets

        protected fun place(widget: AbstractWidget, x: Int, width: Int) {
            widget.x = x
            widget.y = contentYMiddle - WIDGET_HEIGHT / 2
            widget.width = width
        }

        protected fun draw(
            widget: AbstractWidget,
            extractor: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            delta: Float,
        ) = widget.extractRenderState(extractor, mouseX, mouseY, delta)
    }

    /** One note: colour bar, icon, title over context, then pin, duplicate and delete. */
    private class NoteRow(
        private val document: NoteDocument,
        private val subtitle: String,
        private val screen: NotebookScreen,
    ) : Row() {

        private val pinButton: Button = Button.builder(pinLabel(document)) {
            Notebook.togglePinned(document)
            screen.refreshRows()
        }.bounds(0, 0, SMALL_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.notebook.pin.tooltip")))
            .build()

        private val editButton: Button = Button.builder(Component.translatable("hex.notebook.edit")) {
            Minecraft.getInstance().setScreen(NoteEditorScreen(screen, document))
        }.bounds(0, 0, EDIT_WIDTH, WIDGET_HEIGHT).build()

        private val duplicateButton: Button = Button.builder(Component.literal("⧉")) {
            Notebook.duplicate(document)
            screen.refreshRows()
        }.bounds(0, 0, SMALL_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.notebook.duplicate.tooltip")))
            .build()

        private val deleteButton: Button = Button.builder(Component.literal("✕")) {
            screen.confirmDelete(document)
        }.bounds(0, 0, SMALL_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.notebook.delete.tooltip")))
            .build()

        override val widgets: List<AbstractWidget> =
            listOf(pinButton, editButton, duplicateButton, deleteButton)

        override fun extractContent(
            extractor: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            delta: Float,
        ) {
            val font = Minecraft.getInstance().font
            pinButton.message = pinLabel(document)

            // The note's own colour as a bar down the left edge — a whole-row tint would fight the selection
            // highlight, and a coloured title would fight the title's own & codes.
            val bar = document.meta.color.takeIf { it.isNotEmpty() }
                ?.let { HexColor.parseOrDefault(it, DEFAULT_BAR, alpha = false) }
                ?: DEFAULT_BAR
            extractor.fill(contentX, contentY, contentX + BAR_WIDTH, contentBottom, bar)

            var x = contentX + BAR_WIDTH + GAP
            extractor.item(NoteIcon.resolve(document.meta.icon), x, contentYMiddle - ICON / 2)
            x += ICON + GAP

            val buttonsWidth = SMALL_WIDTH * 3 + EDIT_WIDTH + GAP * 3
            val available = (contentRight - buttonsWidth - GAP - x).coerceAtLeast(MIN_TEXT_WIDTH)

            // The title goes through Chroma so a note can be named in colour — and in flowing colour — exactly
            // as an item customization can. Everything else on the row stays plain: a coloured snippet would be
            // the note's text bleeding into the browser's own chrome.
            //
            // A title too long for the row falls back to plain truncated text, because truncating a component
            // tree honestly is a measurement problem the browser has no reason to solve. A note whose name does
            // not fit its row has bigger presentation problems than losing its colour.
            val styled = Chroma.build(
                document.meta.title,
                seconds = ItemCustomizeConfig.chromaSeconds,
                width = ItemCustomizeConfig.chromaWidth,
            )
            val plain = styled.string
            val titleY = contentYMiddle - font.lineHeight - 1
            if (font.width(plain) <= available) {
                extractor.text(font, styled, x, titleY, TITLE_COLOR)
            } else {
                extractor.text(font, truncate(plain, available), x, titleY, TITLE_COLOR)
            }

            if (NotebookConfig.settings.showSnippets && subtitle.isNotEmpty()) {
                extractor.text(font, truncate(subtitle, available), x, contentYMiddle + 1, SUB_COLOR)
            }

            var right = contentRight - SMALL_WIDTH
            place(deleteButton, right, SMALL_WIDTH)
            draw(deleteButton, extractor, mouseX, mouseY, delta)
            right -= SMALL_WIDTH + GAP

            place(duplicateButton, right, SMALL_WIDTH)
            draw(duplicateButton, extractor, mouseX, mouseY, delta)
            right -= EDIT_WIDTH + GAP

            place(editButton, right, EDIT_WIDTH)
            draw(editButton, extractor, mouseX, mouseY, delta)
            right -= SMALL_WIDTH + GAP

            place(pinButton, right, SMALL_WIDTH)
            draw(pinButton, extractor, mouseX, mouseY, delta)
        }

        private fun truncate(text: String, available: Int): String {
            val font = Minecraft.getInstance().font
            if (font.width(text) <= available) return text
            return font.plainSubstrByWidth(text, available - font.width("…")) + "…"
        }

        private companion object {
            fun pinLabel(document: NoteDocument): Component =
                Component.literal(if (document.meta.pinned) "★" else "☆")
        }
    }

    /** Shown in place of rows when the list is empty, so the screen explains itself rather than sitting bare. */
    private class HintRow(private val text: Component) : Row() {
        override val widgets: List<AbstractWidget> = emptyList()

        override fun extractContent(
            extractor: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            delta: Float,
        ) {
            val font = Minecraft.getInstance().font
            val x = contentX + (contentWidth - font.width(text)) / 2
            extractor.text(font, text, x, contentYMiddle - font.lineHeight / 2, HINT_COLOR)
        }
    }

    companion object {
        const val ROW_HEIGHT = 28
        private const val WIDGET_HEIGHT = 20
        private const val EDIT_WIDTH = 44
        private const val SMALL_WIDTH = 22
        private const val BAR_WIDTH = 2
        private const val ICON = 16
        private const val GAP = 6
        private const val MIN_TEXT_WIDTH = 40

        private const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        private const val SUB_COLOR = 0xFF909090.toInt()
        private const val HINT_COLOR = 0xFFA0A0A0.toInt()
        private const val DEFAULT_BAR = 0xFF5A5A5A.toInt()
    }
}
