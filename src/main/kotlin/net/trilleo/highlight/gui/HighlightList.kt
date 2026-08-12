package net.trilleo.highlight.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component
import net.trilleo.highlight.HighlightConfig
import net.trilleo.highlight.HighlightTracker
import net.trilleo.highlight.model.Highlight
import java.util.*

/**
 * The scrolling list of highlight rules in [HighlightsScreen].
 *
 * Built on [ContainerObjectSelectionList] the same way [net.trilleo.region.gui.RegionList] is, and holding rows
 * by reference identity for the same reason — [Highlight] is deliberately not a data class, so deleting a row is
 * an identity remove that cannot take an equal-looking sibling with it.
 *
 * Each row shows how many entities its rule is matching right now, which turns the screen into a way of
 * checking a rule works without hunting the island for the mob it was written for. That is the same job
 * [net.trilleo.region.gui.RegionList]'s "you are here" marker does.
 */
class HighlightList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val screen: HighlightsScreen,
) : ContainerObjectSelectionList<HighlightList.Row>(minecraft, width, height, top, ROW_HEIGHT) {

    override fun getRowWidth(): Int = width - 24

    override fun scrollBarX(): Int = x + width - 8

    /** Replaces the visible rows, or shows the empty-list hint. */
    fun show(highlights: List<Highlight>, emptyHint: Component) {
        val scroll = scrollAmount()
        clearEntries()
        if (highlights.isEmpty()) {
            addEntry(HintRow(emptyHint))
        } else {
            highlights.forEach { addEntry(HighlightRow(it, screen)) }
        }
        // Preserve the scroll position: this is called after every add and delete, and snapping to the top each
        // time would throw away the player's place in a long list.
        setScrollAmount(scroll)
    }

    fun scrollToBottom() {
        setScrollAmount(maxScrollAmount().toDouble())
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

    /** One rule: an on/off button, its name and what it catches, how many it has now, and edit and delete. */
    private class HighlightRow(
        private val highlight: Highlight,
        private val screen: HighlightsScreen,
    ) : Row() {

        private val toggleButton: Button = Button.builder(toggleLabel(highlight.enabled)) {
            highlight.enabled = !highlight.enabled
            HighlightConfig.save()
        }.bounds(0, 0, TOGGLE_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.highlights.toggle.tooltip")))
            .build()

        private val editButton: Button = Button.builder(Component.translatable("hex.highlights.edit")) {
            Minecraft.getInstance().setScreen(HighlightEditScreen(screen, highlight))
        }.bounds(0, 0, EDIT_WIDTH, WIDGET_HEIGHT).build()

        private val deleteButton: Button = Button.builder(Component.literal("✕")) {
            HighlightConfig.settings.highlights.remove(highlight)
            HighlightConfig.save()
            screen.refreshRows()
        }.bounds(0, 0, DELETE_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.highlights.delete.tooltip")))
            .build()

        override val widgets: List<AbstractWidget> = listOf(toggleButton, editButton, deleteButton)

        override fun extractContent(
            extractor: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            delta: Float,
        ) {
            val font = Minecraft.getInstance().font
            toggleButton.message = toggleLabel(highlight.enabled)

            var x = contentX
            place(toggleButton, x, TOGGLE_WIDTH)
            draw(toggleButton, extractor, mouseX, mouseY, delta)
            x += TOGGLE_WIDTH + GAP

            val textRight = contentRight - DELETE_WIDTH - GAP - EDIT_WIDTH - GAP - COUNT_WIDTH - GAP
            val available = (textRight - x).coerceAtLeast(40)

            // Name above, what-it-catches muted beneath — the summary is what tells apart two rules the player
            // has named similarly for two different islands.
            val nameColor = if (highlight.enabled) NAME_COLOR else DISABLED_COLOR
            extractor.text(font, truncate(highlight.name, available), x, contentYMiddle - font.lineHeight - 1, nameColor)
            extractor.text(font, truncate(highlight.summary(), available), x, contentYMiddle + 1, SUB_COLOR)

            val count = HighlightTracker.countFor(highlight)
            if (count > 0) {
                // Locale.ROOT, as everywhere else this mod formats a number.
                val text = String.format(Locale.ROOT, "×%d", count)
                extractor.text(font, text, textRight + GAP, contentYMiddle - font.lineHeight / 2, COUNT_COLOR)
            }

            place(editButton, contentRight - DELETE_WIDTH - GAP - EDIT_WIDTH, EDIT_WIDTH)
            draw(editButton, extractor, mouseX, mouseY, delta)

            place(deleteButton, contentRight - DELETE_WIDTH, DELETE_WIDTH)
            draw(deleteButton, extractor, mouseX, mouseY, delta)
        }

        private fun truncate(text: String, available: Int): String {
            val font = Minecraft.getInstance().font
            if (font.width(text) <= available) return text
            return font.plainSubstrByWidth(text, available - font.width("…")) + "…"
        }

        private companion object {
            fun toggleLabel(enabled: Boolean): Component =
                Component.literal(if (enabled) "✔" else "✖")
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
        const val ROW_HEIGHT = 26
        private const val WIDGET_HEIGHT = 20
        private const val TOGGLE_WIDTH = 22
        private const val EDIT_WIDTH = 44
        private const val DELETE_WIDTH = 22
        private const val COUNT_WIDTH = 30
        private const val GAP = 6

        private const val NAME_COLOR = 0xFFFFFFFF.toInt()
        private const val DISABLED_COLOR = 0xFF808080.toInt()
        private const val SUB_COLOR = 0xFF909090.toInt()
        private const val COUNT_COLOR = 0xFF80E080.toInt()
        private const val HINT_COLOR = 0xFFA0A0A0.toInt()
    }
}
