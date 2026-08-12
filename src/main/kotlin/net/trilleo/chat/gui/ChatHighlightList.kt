package net.trilleo.chat.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component
import net.trilleo.chat.ChatHighlightConfig
import net.trilleo.chat.model.ChatHighlight
import net.trilleo.color.ColorValue
import net.trilleo.util.Chroma
import net.trilleo.util.HexColor

/**
 * The scrolling list of chat highlight rules in [ChatHighlightsScreen].
 *
 * Built on [ContainerObjectSelectionList] the same way [net.trilleo.highlight.gui.HighlightList] is, and holding
 * rows by reference identity for the same reason — [ChatHighlight] is deliberately not a data class, so deleting
 * a row is an identity remove that cannot take an equal-looking sibling with it.
 *
 * Each row carries a swatch of the colour its rule paints in, animated when the rule uses chroma. That is this
 * feature's answer to the match count the entity list shows: chat rules cannot be counted the way live mobs can,
 * but "which of these four rules is the orange one" is the question actually being asked in front of this screen,
 * and the swatch answers it without opening anything.
 */
class ChatHighlightList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val screen: ChatHighlightsScreen,
) : ContainerObjectSelectionList<ChatHighlightList.Row>(minecraft, width, height, top, ROW_HEIGHT) {

    override fun getRowWidth(): Int = width - 24

    override fun scrollBarX(): Int = x + width - 8

    /** Replaces the visible rows, or shows the empty-list hint. */
    fun show(rules: List<ChatHighlight>, emptyHint: Component) {
        val scroll = scrollAmount()
        clearEntries()
        if (rules.isEmpty()) {
            addEntry(HintRow(emptyHint))
        } else {
            rules.forEach { addEntry(ChatHighlightRow(it, screen)) }
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

    /** One rule: an on/off button, its name and what it catches, its colour, and edit and delete. */
    private class ChatHighlightRow(
        private val rule: ChatHighlight,
        private val screen: ChatHighlightsScreen,
    ) : Row() {

        private val toggleButton: Button = Button.builder(toggleLabel(rule.enabled)) {
            rule.enabled = !rule.enabled
            ChatHighlightConfig.save()
        }.bounds(0, 0, TOGGLE_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.chat_highlights.toggle.tooltip")))
            .build()

        private val editButton: Button = Button.builder(Component.translatable("hex.chat_highlights.edit")) {
            Minecraft.getInstance().setScreen(ChatHighlightEditScreen(screen, rule))
        }.bounds(0, 0, EDIT_WIDTH, WIDGET_HEIGHT).build()

        private val deleteButton: Button = Button.builder(Component.literal("✕")) {
            ChatHighlightConfig.settings.highlights.remove(rule)
            ChatHighlightConfig.save()
            screen.refreshRows()
        }.bounds(0, 0, DELETE_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.chat_highlights.delete.tooltip")))
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
            toggleButton.message = toggleLabel(rule.enabled)

            var x = contentX
            place(toggleButton, x, TOGGLE_WIDTH)
            draw(toggleButton, extractor, mouseX, mouseY, delta)
            x += TOGGLE_WIDTH + GAP

            val textRight = contentRight - DELETE_WIDTH - GAP - EDIT_WIDTH - GAP - SWATCH_WIDTH - GAP
            val available = (textRight - x).coerceAtLeast(40)

            // Name above, what-it-catches muted beneath — the summary is what tells apart two rules the player
            // has named similarly for two different channels.
            val nameColor = if (rule.enabled) NAME_COLOR else DISABLED_COLOR
            extractor.text(
                font,
                truncate(rule.name, available),
                x,
                contentYMiddle - font.lineHeight - 1,
                nameColor,
            )
            extractor.text(
                font,
                truncate(rule.summary().string, available),
                x,
                contentYMiddle + 1,
                SUB_COLOR,
            )

            drawSwatch(extractor, textRight + GAP)

            place(editButton, contentRight - DELETE_WIDTH - GAP - EDIT_WIDTH, EDIT_WIDTH)
            draw(editButton, extractor, mouseX, mouseY, delta)

            place(deleteButton, contentRight - DELETE_WIDTH, DELETE_WIDTH)
            draw(deleteButton, extractor, mouseX, mouseY, delta)
        }

        /**
         * The rule's colour, as a small filled square.
         *
         * A chroma rule samples the clock rather than showing a frozen colour, so the swatch flows exactly as the
         * text will — which is the only honest way to preview a setting whose whole point is that it moves. A
         * disabled rule is drawn flat grey instead, so the list reads at a glance as what is live.
         */
        private fun drawSwatch(extractor: GuiGraphicsExtractor, x: Int) {
            val top = contentYMiddle - SWATCH_HEIGHT / 2
            val rgb = when {
                !rule.enabled -> DISABLED_COLOR
                ChatHighlightConfig.isChroma(rule) -> Chroma.color(
                    0,
                    ChatHighlightConfig.chromaSeconds,
                    ChatHighlightConfig.chromaWidth,
                ) or HexColor.OPAQUE

                else -> ColorValue.resolve(ChatHighlightConfig.colorOf(rule), NAME_COLOR)
            }
            extractor.fill(x, top, x + SWATCH_WIDTH, top + SWATCH_HEIGHT, rgb)
            extractor.outline(x, top, SWATCH_WIDTH, SWATCH_HEIGHT, SWATCH_BORDER)
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
        private const val SWATCH_WIDTH = 14
        private const val SWATCH_HEIGHT = 14
        private const val GAP = 6

        private const val NAME_COLOR = 0xFFFFFFFF.toInt()
        private const val DISABLED_COLOR = 0xFF808080.toInt()
        private const val SUB_COLOR = 0xFF909090.toInt()
        private const val HINT_COLOR = 0xFFA0A0A0.toInt()
        private const val SWATCH_BORDER = 0xFF000000.toInt()
    }
}
