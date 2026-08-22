package net.trilleo.sound.preset.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.sound.SoundPlayer
import net.trilleo.sound.SoundValue
import net.trilleo.sound.gui.SoundSequencesScreen
import net.trilleo.sound.preset.SoundPreset
import net.trilleo.sound.preset.SoundPresets

/**
 * Browses the bundled sequence catalogue and adds presets to the player's library.
 *
 * Adding copies the preset rather than referencing it, so the player owns what lands in their library and can
 * edit it freely — see [SoundPresets.install]. Every row previews, because a list of names is no way to
 * choose a sound.
 */
class SoundPresetsScreen(private val parent: SoundSequencesScreen?) :
    Screen(Component.translatable("hex.sounds.presets.title")) {

    private var list: PresetList? = null

    override fun init() {
        val listHeight = height - TOP - FOOTER_HEIGHT
        list = addRenderableWidget(PresetList(minecraft, width, listHeight, TOP, this))

        addRenderableWidget(StringWidget(MARGIN, 12, width - MARGIN * 2, 12, title, font))

        addRenderableWidget(
            Button.builder(Component.translatable("hex.sounds.presets.done")) { onClose() }
                .bounds(width / 2 - BUTTON_WIDTH / 2, height - 28, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )

        refreshRows()
    }

    fun refreshRows() {
        list?.show(SoundPresets.all)
    }

    override fun onClose() {
        SoundPlayer.stopAll()
        minecraft.setScreen(parent)
    }

    override fun removed() {
        SoundPlayer.stopAll()
        parent?.refreshRows()
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
        private val screen: SoundPresetsScreen,
    ) : ContainerObjectSelectionList<PresetList.Row>(minecraft, width, height, top, ROW_HEIGHT) {

        override fun getRowWidth(): Int = width - 24

        override fun scrollBarX(): Int = x + width - 8

        fun show(presets: List<SoundPreset>) {
            val scroll = scrollAmount()
            clearEntries()
            if (presets.isEmpty()) {
                addEntry(HintRow(Component.translatable("hex.sounds.presets.empty")))
            } else {
                presets.groupBy { it.category }.toSortedMap().forEach { (category, group) ->
                    addEntry(HeadingRow(Component.translatable("hex.sounds.presets.category.$category")))
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

        /** One preset: its name, a description, a preview, and an add button that disables once installed. */
        private class PresetRow(
            private val preset: SoundPreset,
            private val screen: SoundPresetsScreen,
        ) : Row() {

            /**
             * Previews the shipped definition rather than an installed copy.
             *
             * The catalogue's sequence is not in the config and has no id, so it cannot be named by a
             * `"@ref"` — it is played directly. That is also the honest thing to preview here: this row is
             * offering the preset, not whatever the player may since have done to their copy of it.
             */
            private val previewButton: Button = Button.builder(Component.literal("▶")) {
                SoundPlayer.playSequence(Minecraft.getInstance(), preset.sequence)
            }.bounds(0, 0, PREVIEW_WIDTH, WIDGET_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.sound.preview.tooltip")))
                .build()

            private val addButton: Button = Button.builder(addLabel()) {
                val installed = SoundPresets.install(preset)
                // Straight to the value the player will need: the reference this is now called.
                SoundPlayer.play(Minecraft.getInstance(), SoundValue.forSequence(installed.id))
                screen.refreshRows()
            }.bounds(0, 0, ADD_WIDTH, WIDGET_HEIGHT).build()

            override val widgets: List<AbstractWidget> = listOf(previewButton, addButton)

            override fun extractContent(
                extractor: GuiGraphicsExtractor,
                mouseX: Int,
                mouseY: Int,
                hovered: Boolean,
                delta: Float,
            ) {
                val font = Minecraft.getInstance().font
                val installed = SoundPresets.isInstalled(preset)
                addButton.active = !installed
                addButton.message = addLabel()

                var x = contentX
                place(previewButton, x, PREVIEW_WIDTH)
                draw(previewButton, extractor, mouseX, mouseY, delta)
                x += PREVIEW_WIDTH + GAP

                val textRight = contentRight - ADD_WIDTH - GAP
                val available = (textRight - x).coerceAtLeast(40)

                val name = Component.translatable("hex.sound.preset.${preset.presetId}.name")
                val desc = Component.translatable("hex.sound.preset.${preset.presetId}.desc")
                extractor.text(
                    font,
                    truncate(name.string, available),
                    x,
                    contentYMiddle - font.lineHeight - 1,
                    if (installed) INSTALLED_COLOR else NAME_COLOR,
                )
                extractor.text(font, truncate(desc.string, available), x, contentYMiddle + 1, SUB_COLOR)

                place(addButton, contentRight - ADD_WIDTH, ADD_WIDTH)
                draw(addButton, extractor, mouseX, mouseY, delta)
            }

            private fun addLabel(): Component = Component.translatable(
                if (SoundPresets.isInstalled(preset)) "hex.sounds.presets.added" else "hex.sounds.presets.add",
            )

            private fun truncate(text: String, available: Int): String {
                val font = Minecraft.getInstance().font
                if (font.width(text) <= available) return text
                return font.plainSubstrByWidth(text, available - font.width("…")) + "…"
            }
        }

        /** A category caption. */
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
            private const val PREVIEW_WIDTH = 22
            private const val ADD_WIDTH = 60
            private const val GAP = 6

            private const val NAME_COLOR = 0xFFFFFFFF.toInt()
            private const val INSTALLED_COLOR = 0xFF80E080.toInt()
            private const val SUB_COLOR = 0xFF909090.toInt()
            private const val HEADING_COLOR = 0xFFFFD700.toInt()
            private const val HINT_COLOR = 0xFFA0A0A0.toInt()
        }
    }
}
