package net.trilleo.itemcustom.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component
import net.trilleo.itemcustom.ItemCustomization
import net.trilleo.itemcustom.ItemCustomizeConfig

/**
 * The scrolling list of customized items in [ItemCustomizeListScreen].
 *
 * Built on [ContainerObjectSelectionList] the same way [net.trilleo.hand.gui.SwingItemList] is: the list
 * widget supplies scrolling, the scrollbar, keyboard navigation and mouse routing, and its `extractContent`
 * hook is what this Minecraft build offers in place of a `render(GuiGraphics)` override.
 *
 * Each row is a summary and two buttons rather than an editable field — unlike the swing list, an entry here
 * has seven values behind it, which is a screen of its own rather than a row. What the row does show is what
 * you need to pick the right one: the item's captured name, whether the entry is doing anything, and the tail
 * of its uuid to tell two otherwise identical items apart.
 */
class ItemCustomizeList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val screen: ItemCustomizeListScreen,
) : ContainerObjectSelectionList<ItemCustomizeList.Row>(minecraft, width, height, top, ROW_HEIGHT) {

    override fun getRowWidth(): Int = width - 24

    override fun scrollBarX(): Int = x + width - 8

    /** Replaces the visible rows from the live config, or shows the empty-list hint. */
    fun show(items: List<ItemCustomization>) {
        val scroll = scrollAmount()
        clearEntries()
        if (items.isEmpty()) {
            addEntry(HintRow(Component.translatable("hex.item_custom.empty")))
        } else {
            items.forEach { addEntry(ItemRow(it, screen)) }
        }
        // Preserve the scroll position: this is called after every add and delete, and snapping to the top
        // each time would throw away the player's place in a long list.
        setScrollAmount(scroll)
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

    /** One customization: its captured name and state, an edit button and a delete button. */
    private class ItemRow(
        private val customization: ItemCustomization,
        private val screen: ItemCustomizeListScreen,
    ) : Row() {

        private val editButton: Button = Button.builder(Component.translatable("hex.item_custom.edit")) {
            screen.edit(customization)
        }.bounds(0, 0, EDIT_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.item_custom.edit.tooltip")))
            .build()

        private val deleteButton: Button = Button.builder(Component.literal("✕")) {
            ItemCustomizeConfig.remove(customization)
            screen.refreshRows()
        }.bounds(0, 0, DELETE_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.item_custom.delete.tooltip")))
            .build()

        override val widgets: List<AbstractWidget> = listOf(editButton, deleteButton)

        override fun extractContent(
            extractor: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            delta: Float,
        ) {
            val font = Minecraft.getInstance().font
            val buttonsX = contentRight - DELETE_WIDTH - GAP - EDIT_WIDTH
            val textWidth = (buttonsX - GAP - contentX).coerceAtLeast(40)

            val label = customization.label.ifEmpty { customization.uuid }
            extractor.text(
                font,
                truncate(label, textWidth - STATE_WIDTH - GAP),
                contentX,
                contentYMiddle - font.lineHeight,
                if (customization.active) LABEL_COLOR else MUTED_COLOR,
            )
            // The uuid tail, because two of the same item look identical by name and this is what separates
            // them — and because it is the key the JSON is written under.
            extractor.text(
                font,
                truncate(uuidTail(), textWidth),
                contentX,
                contentYMiddle + 1,
                MUTED_COLOR,
            )

            state()?.let {
                extractor.text(font, it, buttonsX - GAP - font.width(it), contentYMiddle - font.lineHeight, MUTED_COLOR)
            }

            place(editButton, buttonsX, EDIT_WIDTH)
            draw(editButton, extractor, mouseX, mouseY, delta)
            place(deleteButton, contentRight - DELETE_WIDTH, DELETE_WIDTH)
            draw(deleteButton, extractor, mouseX, mouseY, delta)
        }

        /**
         * A note for an entry that is not currently doing anything, or null for the ordinary case.
         *
         * Switched-off and never-filled-in look identical from the outside — the item renders normally — so
         * saying which it is here is the only way to tell a deliberate pause from a row you forgot to finish.
         */
        private fun state(): Component? = when {
            !customization.active -> Component.translatable("hex.item_custom.state.off")
            !customization.hasEffect() -> Component.translatable("hex.item_custom.state.empty")
            else -> null
        }

        /** The last few characters of the uuid — enough to distinguish, short enough to fit. */
        private fun uuidTail(): String = customization.uuid.takeLast(UUID_TAIL)

        private fun truncate(text: String, available: Int): String {
            val font = Minecraft.getInstance().font
            if (available <= 0) return ""
            if (font.width(text) <= available) return text
            return font.plainSubstrByWidth(text, available - font.width("…")) + "…"
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
            extractor.text(font, text, x, contentYMiddle - font.lineHeight / 2, MUTED_COLOR)
        }
    }

    companion object {
        /** Two lines of text per row — the item's name over its uuid — so this is taller than a settings row. */
        const val ROW_HEIGHT = 26

        private const val WIDGET_HEIGHT = 20
        private const val EDIT_WIDTH = 50
        private const val DELETE_WIDTH = 22
        private const val STATE_WIDTH = 60
        private const val GAP = 6

        /** Enough of a uuid to tell two items apart without swallowing the row. */
        private const val UUID_TAIL = 12

        private const val LABEL_COLOR = 0xFFFFFFFF.toInt()
        private const val MUTED_COLOR = 0xFFA0A0A0.toInt()
    }
}
