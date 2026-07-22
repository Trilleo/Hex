package net.trilleo.reminder.preset.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.reminder.hud.ReminderHudModel
import net.trilleo.reminder.preset.ReminderPreset
import net.trilleo.reminder.preset.ReminderPresets

/**
 * Browses the bundled preset catalogue and adds presets to the player's reminders.
 *
 * Adding copies the preset rather than referencing it, so the player owns what lands in their list and can
 * edit it freely — see [ReminderPresets.install].
 */
class PresetsScreen(private val parent: Screen?) :
    Screen(Component.translatable("hex.reminders.presets.title")) {

    private var list: PresetList? = null

    override fun init() {
        val listHeight = height - TOP - FOOTER_HEIGHT
        list = addRenderableWidget(PresetList(minecraft, width, listHeight, TOP, this))

        addRenderableWidget(StringWidget(MARGIN, 12, width - MARGIN * 2, 12, title, font))

        addRenderableWidget(
            Button.builder(Component.translatable("hex.reminders.presets.done")) { onClose() }
                .bounds(width / 2 - BUTTON_WIDTH / 2, height - 28, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )

        refreshRows()
    }

    fun refreshRows() {
        list?.show(ReminderPresets.all)
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun removed() {
        ReminderHudModel.invalidate()
    }

    private companion object {
        const val MARGIN = 24
        const val TOP = 32
        const val FOOTER_HEIGHT = 40
        const val BUTTON_WIDTH = 100
        const val BUTTON_HEIGHT = 20
    }

    /** The scrolling catalogue, grouped by category. */
    class PresetList(
        minecraft: Minecraft,
        width: Int,
        height: Int,
        top: Int,
        private val screen: PresetsScreen,
    ) : ContainerObjectSelectionList<PresetList.Row>(minecraft, width, height, top, ROW_HEIGHT) {

        override fun getRowWidth(): Int = width - 24

        override fun scrollBarX(): Int = x + width - 8

        fun show(presets: List<ReminderPreset>) {
            val scroll = scrollAmount()
            clearEntries()
            if (presets.isEmpty()) {
                addEntry(HintRow(Component.translatable("hex.reminders.presets.empty")))
            } else {
                presets.groupBy { it.category }.toSortedMap().forEach { (category, group) ->
                    addEntry(HeadingRow(Component.translatable("hex.reminders.presets.category.$category")))
                    group.forEach { addEntry(PresetRow(it, screen)) }
                }
            }
            setScrollAmount(scroll)
        }

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

        /** One preset: its name, a description, and an add button that disables once it is installed. */
        private class PresetRow(
            private val preset: ReminderPreset,
            private val screen: PresetsScreen,
        ) : Row() {

            private val addButton: Button = Button.builder(Component.translatable("hex.reminders.presets.add")) {
                ReminderPresets.install(preset)
                screen.refreshRows()
            }.bounds(0, 0, ADD_WIDTH, WIDGET_HEIGHT).build()

            override val widgets: List<AbstractWidget> = listOf(addButton)

            override fun extractContent(
                extractor: GuiGraphicsExtractor,
                mouseX: Int,
                mouseY: Int,
                hovered: Boolean,
                delta: Float,
            ) {
                val font = Minecraft.getInstance().font
                val installed = ReminderPresets.isInstalled(preset)

                // Adding a second copy of the same preset would give two identical reminders that a later
                // sync could not tell apart, so the button reports the state instead of allowing it.
                addButton.active = !installed
                addButton.message = Component.translatable(
                    if (installed) "hex.reminders.presets.added" else "hex.reminders.presets.add",
                )

                val available = contentRight - ADD_WIDTH - GAP - contentX
                val name = Component.translatable(nameKey())
                val description = Component.translatable(descriptionKey())

                extractor.text(font, name, contentX, contentYMiddle - font.lineHeight - 1, NAME_COLOR)
                extractor.text(
                    font,
                    truncate(description.string, available),
                    contentX,
                    contentYMiddle + 1,
                    SUB_COLOR,
                )

                place(addButton, contentRight - ADD_WIDTH, ADD_WIDTH)
                draw(addButton, extractor, mouseX, mouseY, delta)
            }

            private fun nameKey() = "hex.reminders.preset.${preset.presetId}.name"

            private fun descriptionKey() = "hex.reminders.preset.${preset.presetId}.desc"

            private fun truncate(text: String, available: Int): String {
                val font = Minecraft.getInstance().font
                if (font.width(text) <= available) return text
                return font.plainSubstrByWidth(text, available - font.width("…")) + "…"
            }
        }

        /** A category caption between groups. */
        private class HeadingRow(private val title: Component) : Row() {
            override val widgets: List<AbstractWidget> = emptyList()

            override fun extractContent(
                extractor: GuiGraphicsExtractor,
                mouseX: Int,
                mouseY: Int,
                hovered: Boolean,
                delta: Float,
            ) {
                val font = Minecraft.getInstance().font
                extractor.text(font, title, contentX, contentYMiddle - font.lineHeight / 2, HEADING_COLOR)
            }
        }

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
            private const val ADD_WIDTH = 56
            private const val GAP = 6

            private const val NAME_COLOR = 0xFFFFFFFF.toInt()
            private const val SUB_COLOR = 0xFF909090.toInt()
            private const val HEADING_COLOR = 0xFFFFD080.toInt()
            private const val HINT_COLOR = 0xFFA0A0A0.toInt()
        }
    }
}
