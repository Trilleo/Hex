package net.trilleo.hand.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component
import net.trilleo.hand.SwingItemsConfig
import net.trilleo.skyblock.item.ItemRule
import net.trilleo.skyblock.item.ItemRuleKind

/**
 * The scrolling list of per-item swing rules in [SwingItemsScreen].
 *
 * Built on [ContainerObjectSelectionList] the same way [net.trilleo.config.gui.ConfigEntryList] is, rather
 * than on the older paged layout in [net.trilleo.keybind.gui.KeybindScreen]: the list widget supplies real
 * scrolling, the scrollbar, keyboard navigation and mouse routing, and its `extractContent` hook is what this
 * Minecraft build offers in place of a `render(GuiGraphics)` override.
 *
 * Rows apply immediately — editing a value or flipping a kind takes effect as you do it — so there is nothing
 * to submit and no cancel to honour.
 */
class SwingItemList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val screen: SwingItemsScreen,
) : ContainerObjectSelectionList<SwingItemList.Row>(minecraft, width, height, top, ROW_HEIGHT) {

    override fun getRowWidth(): Int = width - 24

    override fun scrollBarX(): Int = x + width - 8

    /** Replaces the visible rows from the live config, or shows the empty-list hint. */
    fun show(rules: List<ItemRule>) {
        val scroll = scrollAmount()
        clearEntries()
        if (rules.isEmpty()) {
            addEntry(HintRow(Component.translatable("hex.swing_items.empty")))
        } else {
            rules.forEach { addEntry(RuleRow(it, screen)) }
        }
        // Preserve the scroll position: this is called after every add and delete, and snapping to the top
        // each time would throw away the player's place in a long list.
        setScrollAmount(scroll)
    }

    /** Scrolls to the bottom, so a freshly appended row is visible rather than just added. */
    fun scrollToBottom() {
        setScrollAmount(maxScrollAmount().toDouble())
    }

    // ---- rows ----------------------------------------------------------------------------------------

    abstract class Row : ContainerObjectSelectionList.Entry<Row>() {
        protected abstract val widgets: List<AbstractWidget>

        override fun children(): List<AbstractWidget> = widgets

        override fun narratables(): List<NarratableEntry> = widgets

        protected fun widgetY(): Int = contentYMiddle - WIDGET_HEIGHT / 2

        protected fun place(widget: AbstractWidget, x: Int, width: Int) {
            widget.x = x
            widget.y = widgetY()
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

    /**
     * One rule: its match kind, the value to match, the captured item name, and a delete button.
     *
     * The value stays in an editable box for both kinds, including UUID. Hiding a raw uuid behind its label
     * would look tidier but would mean the screen and the JSON no longer show the same thing — and the file
     * is documented as hand-editable, so what is in it has to be visible here.
     */
    private class RuleRow(
        private val rule: ItemRule,
        private val screen: SwingItemsScreen,
    ) : Row() {

        private val kindButton: Button = Button.builder(kindLabel(rule.kind)) {
            rule.kind = if (rule.kind == ItemRuleKind.SKYBLOCK_ID) ItemRuleKind.UUID else ItemRuleKind.SKYBLOCK_ID
            // The two kinds fold case differently, so the stored value has to follow the kind.
            rule.normalizeValue()
            field.value = rule.value
            SwingItemsConfig.markDirty()
        }.bounds(0, 0, KIND_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.swing_items.kind.tooltip")))
            .build()

        private val field: EditBox = EditBox(
            Minecraft.getInstance().font, 0, 0, 100, WIDGET_HEIGHT,
            Component.translatable("hex.swing_items.value.hint"),
        ).apply {
            value = rule.value
            setMaxLength(VALUE_MAX_LENGTH)
            // Fold as we go rather than only on the next load, so a hand-typed `hyperion` matches straight
            // away — rows apply live, and a row that needs a restart to work would read as broken. The box
            // keeps showing exactly what was typed; only the stored value is folded.
            //
            // markDirty, not save: a responder fires on every keystroke, and ConfigRegistry batches the
            // write once typing settles.
            setResponder { text ->
                rule.value = text
                rule.normalizeValue()
                SwingItemsConfig.markDirty()
            }
        }

        private val deleteButton: Button = Button.builder(Component.literal("✕")) {
            SwingItemsConfig.settings.rules.remove(rule)
            SwingItemsConfig.save()
            screen.refreshRows()
        }.bounds(0, 0, DELETE_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.swing_items.delete.tooltip")))
            .build()

        override val widgets: List<AbstractWidget> = listOf(field, kindButton, deleteButton)

        override fun extractContent(
            extractor: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            delta: Float,
        ) {
            val font = Minecraft.getInstance().font
            kindButton.message = kindLabel(rule.kind)

            var x = contentX
            place(kindButton, x, KIND_WIDTH)
            draw(kindButton, extractor, mouseX, mouseY, delta)
            x += KIND_WIDTH + GAP

            val fieldWidth = (contentRight - DELETE_WIDTH - GAP - LABEL_WIDTH - GAP - x).coerceAtLeast(60)
            place(field, x, fieldWidth)
            draw(field, extractor, mouseX, mouseY, delta)
            x += fieldWidth + GAP

            if (rule.label.isNotEmpty()) {
                val text = truncate(rule.label, LABEL_WIDTH)
                extractor.text(font, text, x, contentYMiddle - font.lineHeight / 2, LABEL_COLOR)
            }

            place(deleteButton, contentRight - DELETE_WIDTH, DELETE_WIDTH)
            draw(deleteButton, extractor, mouseX, mouseY, delta)
        }

        private fun truncate(text: String, available: Int): String {
            val font = Minecraft.getInstance().font
            if (font.width(text) <= available) return text
            return font.plainSubstrByWidth(text, available - font.width("…")) + "…"
        }

        private companion object {
            fun kindLabel(kind: ItemRuleKind): Component = Component.translatable(
                when (kind) {
                    ItemRuleKind.SKYBLOCK_ID -> "hex.swing_items.kind.skyblock_id"
                    ItemRuleKind.UUID -> "hex.swing_items.kind.uuid"
                },
            )
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
        const val ROW_HEIGHT = 24
        private const val WIDGET_HEIGHT = 20
        private const val KIND_WIDTH = 70
        private const val LABEL_WIDTH = 90
        private const val DELETE_WIDTH = 22
        private const val GAP = 6

        /** Long enough for any Skyblock id or uuid, short enough that a pasted essay cannot land in the file. */
        private const val VALUE_MAX_LENGTH = 64

        private const val LABEL_COLOR = 0xFFA0A0A0.toInt()
        private const val HINT_COLOR = 0xFFA0A0A0.toInt()
    }
}
