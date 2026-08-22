package net.trilleo.sound.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component
import net.trilleo.sound.SoundConfig
import net.trilleo.sound.SoundPlayer
import net.trilleo.sound.SoundValue
import net.trilleo.sound.model.SoundSequence
import java.util.*

/**
 * The scrolling list of sequences in [SoundSequencesScreen].
 *
 * Holds rows by reference identity for the same reason [net.trilleo.region.gui.RegionList] does —
 * [SoundSequence] is deliberately not a data class, so deleting a row is an identity remove that cannot take
 * an equal-looking sibling with it.
 *
 * Each row shows the `"@id"` that names it as well as its name, because that reference is what a player types
 * into a hand-edited config, and because a sequence's name can be changed while its id cannot.
 */
class SoundSequenceList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val screen: SoundSequencesScreen,
) : ContainerObjectSelectionList<SoundSequenceList.Row>(minecraft, width, height, top, ROW_HEIGHT) {

    override fun getRowWidth(): Int = width - 24

    override fun scrollBarX(): Int = x + width - 8

    /** Replaces the visible rows, or shows the empty-list hint. */
    fun show(sequences: List<SoundSequence>, emptyHint: Component) {
        val scroll = scrollAmount()
        clearEntries()
        if (sequences.isEmpty()) {
            addEntry(HintRow(emptyHint))
        } else {
            sequences.forEach { addEntry(SequenceRow(it, screen)) }
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

    /** One sequence: preview, its name and shape, and the buttons to edit or delete it. */
    private class SequenceRow(
        private val sequence: SoundSequence,
        private val screen: SoundSequencesScreen,
    ) : Row() {

        private val previewButton: Button = Button.builder(Component.literal("▶")) {
            SoundPlayer.play(Minecraft.getInstance(), SoundValue.forSequence(sequence.id))
        }.bounds(0, 0, PREVIEW_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.sound.preview.tooltip")))
            .build()

        private val editButton: Button = Button.builder(Component.translatable("hex.sounds.edit")) {
            Minecraft.getInstance().setScreen(SoundSequenceEditScreen(screen, sequence))
        }.bounds(0, 0, EDIT_WIDTH, WIDGET_HEIGHT).build()

        private val deleteButton: Button = Button.builder(Component.literal("✕")) {
            SoundConfig.settings.sequences.remove(sequence)
            SoundConfig.save()
            screen.refreshRows()
        }.bounds(0, 0, DELETE_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.sounds.delete.tooltip")))
            .build()

        override val widgets: List<AbstractWidget> = listOf(previewButton, editButton, deleteButton)

        override fun extractContent(
            extractor: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            delta: Float,
        ) {
            val font = Minecraft.getInstance().font

            var x = contentX
            place(previewButton, x, PREVIEW_WIDTH)
            draw(previewButton, extractor, mouseX, mouseY, delta)
            x += PREVIEW_WIDTH + GAP

            val textRight = contentRight - DELETE_WIDTH - GAP - EDIT_WIDTH - GAP
            val available = (textRight - x).coerceAtLeast(40)

            // literal: the name is the player's own words and the id is an id — neither is language.
            extractor.text(
                font,
                truncate(sequence.name, available),
                x,
                contentYMiddle - font.lineHeight - 1,
                NAME_COLOR,
            )
            extractor.text(font, truncate(subtitle(), available), x, contentYMiddle + 1, SUB_COLOR)

            place(editButton, contentRight - DELETE_WIDTH - GAP - EDIT_WIDTH, EDIT_WIDTH)
            draw(editButton, extractor, mouseX, mouseY, delta)

            place(deleteButton, contentRight - DELETE_WIDTH, DELETE_WIDTH)
            draw(deleteButton, extractor, mouseX, mouseY, delta)
        }

        /** `"@id  ·  4 steps · 1.20s"` — the reference first, because that is what has to be typed. */
        private fun subtitle(): String {
            val summary = Component.translatable(
                "hex.sounds.summary",
                sequence.steps.size,
                String.format(Locale.ROOT, "%.2f", sequence.durationMillis() / 1000.0),
            ).string
            return SoundValue.forSequence(sequence.id) + "  ·  " + summary
        }

        private fun truncate(text: String, available: Int): String {
            val font = Minecraft.getInstance().font
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
            extractor.text(font, text, x, contentYMiddle - font.lineHeight / 2, HINT_COLOR)
        }
    }

    companion object {
        const val ROW_HEIGHT = 26

        private const val WIDGET_HEIGHT = 20
        private const val PREVIEW_WIDTH = 22
        private const val EDIT_WIDTH = 44
        private const val DELETE_WIDTH = 22
        private const val GAP = 6

        private const val NAME_COLOR = 0xFFFFFFFF.toInt()
        private const val SUB_COLOR = 0xFF909090.toInt()
        private const val HINT_COLOR = 0xFFA0A0A0.toInt()
    }
}
