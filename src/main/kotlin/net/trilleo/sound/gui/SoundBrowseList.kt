package net.trilleo.sound.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component
import net.trilleo.sound.SoundIds
import net.trilleo.sound.SoundValue

/**
 * The list of choices inside [SoundPickerScreen] — sound ids, or the saved sequences.
 *
 * One list for both rather than two, because they are the same question asked of two vocabularies: a row
 * names a value, previews it, and marks whether it is the one currently chosen. The rows differ only in what
 * they show underneath the name, which is why [Choice] carries a subtitle rather than there being two row
 * classes.
 *
 * Built on [ContainerObjectSelectionList] like every other list in the mod, with the same row width and
 * scrollbar position, so it looks like the rest of the settings menu rather than like a screen of its own.
 */
class SoundBrowseList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    /** Called when a row is chosen. The picker previews it and records it as the working value. */
    private val onChoose: (String) -> Unit,
    /** Called by a row's preview button, so previewing does not also select. */
    private val onPreview: (String) -> Unit,
    /** The working value, re-read every frame so the tick moves as the selection does. */
    private val current: () -> String,
) : ContainerObjectSelectionList<SoundBrowseList.Row>(minecraft, width, height, top, ROW_HEIGHT) {

    /** One offered value: what it stores, what it is called, and what to say underneath. */
    class Choice(val value: String, val name: String, val subtitle: String)

    override fun getRowWidth(): Int = width - 24

    override fun scrollBarX(): Int = x + width - 8

    /** Replaces the visible rows, or shows the empty hint. Scroll returns to the top. */
    fun show(choices: List<Choice>, emptyHint: Component) {
        clearEntries()
        if (choices.isEmpty()) {
            addEntry(HintRow(emptyHint))
        } else {
            choices.forEach { addEntry(ChoiceRow(it, onChoose, onPreview, current)) }
        }
        // Deliberately *not* preserving the scroll position, unlike the editor lists: this list's contents
        // change because the player typed a different search, and holding their old place in a list that is
        // now about something else would hide the results they just asked for.
        setScrollAmount(0.0)
    }

    /** Scrolls the chosen row into view, for a picker opened on a value far down the list. */
    fun scrollToCurrent() {
        val value = current()
        children().forEachIndexed { index, row ->
            if (row is ChoiceRow && row.value == value) {
                setScrollAmount((index * ROW_HEIGHT).toDouble())
                return
            }
        }
    }

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

    private class ChoiceRow(
        choice: Choice,
        onChoose: (String) -> Unit,
        onPreview: (String) -> Unit,
        private val current: () -> String,
    ) : Row() {

        val value: String = choice.value

        private val name: String = choice.name
        private val subtitle: String = choice.subtitle

        private val preview: Button = Button.builder(Component.literal("▶")) { onPreview(choice.value) }
            .bounds(0, 0, PREVIEW_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.sound.preview.tooltip")))
            .build()

        /**
         * The whole row is the select button, with its label drawn separately over it.
         *
         * A full-width [Button] rather than hit-testing the row, so choosing a sound inherits click routing,
         * keyboard focus and the list's own highlight — the same argument [net.trilleo.config.gui] makes for
         * the colour swatch. Its own message is empty because the row draws two lines of text, which a
         * button's centred single line cannot.
         */
        private val select: Button = Button.builder(Component.empty()) { onChoose(choice.value) }
            .bounds(0, 0, 10, WIDGET_HEIGHT)
            .build()

        override val widgets: List<AbstractWidget> = listOf(select, preview)

        override fun extractContent(
            extractor: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            delta: Float,
        ) {
            val font = Minecraft.getInstance().font
            val chosen = current() == value

            val selectWidth = (contentRight - PREVIEW_WIDTH - GAP - contentX).coerceAtLeast(20)
            place(select, contentX, selectWidth)
            draw(select, extractor, mouseX, mouseY, delta)

            val textX = contentX + PADDING
            val available = selectWidth - PADDING * 2 - TICK_WIDTH

            // literal: a sound id and a sequence's name are neither of them language.
            extractor.text(
                font,
                truncate(name, available),
                textX,
                contentYMiddle - font.lineHeight - 1,
                if (chosen) CHOSEN_COLOR else NAME_COLOR,
            )
            if (subtitle.isNotEmpty()) {
                extractor.text(font, truncate(subtitle, available), textX, contentYMiddle + 1, SUB_COLOR)
            }
            if (chosen) {
                extractor.text(
                    font,
                    "✔",
                    contentX + selectWidth - PADDING - font.width("✔"),
                    contentYMiddle - font.lineHeight / 2,
                    CHOSEN_COLOR,
                )
            }

            place(preview, contentRight - PREVIEW_WIDTH, PREVIEW_WIDTH)
            draw(preview, extractor, mouseX, mouseY, delta)
        }

        private fun truncate(text: String, available: Int): String {
            val font = Minecraft.getInstance().font
            if (font.width(text) <= available) return text
            return font.plainSubstrByWidth(text, available - font.width("…")) + "…"
        }
    }

    /** Shown in place of rows when nothing matches, so the screen explains itself rather than sitting bare. */
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
        const val ROW_HEIGHT = 26

        private const val WIDGET_HEIGHT = 22
        private const val PREVIEW_WIDTH = 22
        private const val GAP = 6
        private const val PADDING = 6
        private const val TICK_WIDTH = 12

        private const val NAME_COLOR = 0xFFFFFFFF.toInt()
        private const val CHOSEN_COLOR = 0xFF80E080.toInt()
        private const val SUB_COLOR = 0xFF909090.toInt()
        private const val HINT_COLOR = 0xFFA0A0A0.toInt()

        /** A sound id row: the short name above, the full id underneath. */
        fun ofSound(id: String): Choice = Choice(id, SoundIds.shortName(id), id)

        /** A sequence row: its name above, its `"@id"` reference underneath so it can be typed by hand. */
        fun ofSequence(id: String, name: String, summary: String): Choice =
            Choice(SoundValue.forSequence(id), name.ifBlank { id }, summary)
    }
}
