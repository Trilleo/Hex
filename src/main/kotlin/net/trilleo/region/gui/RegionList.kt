package net.trilleo.region.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component
import net.trilleo.region.RegionConfig
import net.trilleo.region.RegionTracker
import net.trilleo.region.model.Region

/**
 * The scrolling list of regions in [RegionsScreen].
 *
 * Built on [ContainerObjectSelectionList] the same way [net.trilleo.reminder.gui.ReminderList] is, and holding
 * rows by reference identity for the same reason — [Region] is deliberately not a data class, so deleting a row
 * is an identity remove that cannot take an equal-looking sibling with it.
 *
 * Each row shows whether the player is standing in that region right now, which turns the screen into a way of
 * checking a region works without walking out and back in to hear it fire.
 */
class RegionList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val screen: RegionsScreen,
) : ContainerObjectSelectionList<RegionList.Row>(minecraft, width, height, top, ROW_HEIGHT) {

    override fun getRowWidth(): Int = width - 24

    override fun scrollBarX(): Int = x + width - 8

    /** Replaces the visible rows, or shows the empty-list hint. */
    fun show(regions: List<Region>, emptyHint: Component) {
        val scroll = scrollAmount()
        clearEntries()
        if (regions.isEmpty()) {
            addEntry(HintRow(emptyHint))
        } else {
            regions.forEach { addEntry(RegionRow(it, screen)) }
        }
        // Preserve the scroll position: this is called after every add and delete, and snapping to the top
        // each time would throw away the player's place in a long list.
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

    /** One region: an on/off button, its name and where it is, whether you are in it, and edit and delete. */
    private class RegionRow(
        private val region: Region,
        private val screen: RegionsScreen,
    ) : Row() {

        private val toggleButton: Button = Button.builder(toggleLabel(region.enabled)) {
            region.enabled = !region.enabled
            RegionConfig.save()
        }.bounds(0, 0, TOGGLE_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.regions.toggle.tooltip")))
            .build()

        private val editButton: Button = Button.builder(Component.translatable("hex.regions.edit")) {
            Minecraft.getInstance().setScreen(RegionEditScreen(screen, region))
        }.bounds(0, 0, EDIT_WIDTH, WIDGET_HEIGHT).build()

        private val deleteButton: Button = Button.builder(Component.literal("✕")) {
            RegionConfig.settings.regions.remove(region)
            RegionConfig.save()
            screen.refreshRows()
        }.bounds(0, 0, DELETE_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.regions.delete.tooltip")))
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
            toggleButton.message = toggleLabel(region.enabled)

            var x = contentX
            place(toggleButton, x, TOGGLE_WIDTH)
            draw(toggleButton, extractor, mouseX, mouseY, delta)
            x += TOGGLE_WIDTH + GAP

            val textRight = contentRight - DELETE_WIDTH - GAP - EDIT_WIDTH - GAP - STATE_WIDTH - GAP
            val available = (textRight - x).coerceAtLeast(40)

            // Name above, where-and-how-big muted beneath — the summary is what tells apart two regions the
            // player has named similarly on different islands.
            val nameColor = if (region.enabled) NAME_COLOR else DISABLED_COLOR
            extractor.text(font, truncate(region.name, available), x, contentYMiddle - font.lineHeight - 1, nameColor)
            extractor.text(font, truncate(region.summary(), available), x, contentYMiddle + 1, SUB_COLOR)

            if (RegionTracker.isInside(region)) {
                val here = Component.translatable("hex.regions.here").string
                extractor.text(font, here, textRight + GAP, contentYMiddle - font.lineHeight / 2, HERE_COLOR)
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
        private const val STATE_WIDTH = 40
        private const val GAP = 6

        private const val NAME_COLOR = 0xFFFFFFFF.toInt()
        private const val DISABLED_COLOR = 0xFF808080.toInt()
        private const val SUB_COLOR = 0xFF909090.toInt()
        private const val HERE_COLOR = 0xFF80E080.toInt()
        private const val HINT_COLOR = 0xFFA0A0A0.toInt()
    }
}
