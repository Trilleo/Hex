package net.trilleo.reminder.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component
import net.trilleo.reminder.Phase
import net.trilleo.reminder.ReminderConfig
import net.trilleo.reminder.ReminderEngine
import net.trilleo.reminder.ReminderState
import net.trilleo.reminder.hud.ReminderHudModel
import net.trilleo.reminder.model.Reminder
import net.trilleo.util.Duration

/**
 * The scrolling list of reminders in [RemindersScreen].
 *
 * Built on [ContainerObjectSelectionList] the same way [net.trilleo.hand.gui.SwingItemList] is, and holding
 * rows by reference identity for the same reason — [Reminder] is deliberately not a data class, so deleting a
 * row is an identity remove that cannot take an equal-looking sibling with it.
 */
class ReminderList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val screen: RemindersScreen,
) : ContainerObjectSelectionList<ReminderList.Row>(minecraft, width, height, top, ROW_HEIGHT) {

    override fun getRowWidth(): Int = width - 24

    override fun scrollBarX(): Int = x + width - 8

    /** Replaces the visible rows from the live config, or shows the empty-list hint. */
    fun show(reminders: List<Reminder>) {
        val scroll = scrollAmount()
        clearEntries()
        if (reminders.isEmpty()) {
            addEntry(HintRow(Component.translatable("hex.reminders.empty")))
        } else {
            reminders.forEach { addEntry(ReminderRow(it, screen)) }
        }
        // Preserve the scroll position: this is called after every add and delete, and snapping to the top
        // each time would throw away the player's place in a long list.
        setScrollAmount(scroll)
    }

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

    /** One reminder: an on/off button, its name and trigger, its live state, and edit and delete buttons. */
    private class ReminderRow(
        private val reminder: Reminder,
        private val screen: RemindersScreen,
    ) : Row() {

        private val toggleButton: Button = Button.builder(toggleLabel(reminder.enabled)) {
            reminder.enabled = !reminder.enabled
            // Switching a preset off is not customising it, so `customized` is deliberately untouched here —
            // this reminder should still receive a corrected regex in a later mod version.
            if (!reminder.enabled) ReminderEngine.disarm(reminder)
            ReminderHudModel.invalidate()
            ReminderConfig.save()
        }.bounds(0, 0, TOGGLE_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.reminders.toggle.tooltip")))
            .build()

        private val editButton: Button = Button.builder(Component.translatable("hex.reminders.edit")) {
            Minecraft.getInstance().setScreen(ReminderEditScreen(screen, reminder))
        }.bounds(0, 0, EDIT_WIDTH, WIDGET_HEIGHT).build()

        private val deleteButton: Button = Button.builder(Component.literal("✕")) {
            ReminderConfig.settings.reminders.remove(reminder)
            ReminderState.forget(reminder.id)
            ReminderHudModel.invalidate()
            ReminderConfig.save()
            screen.refreshRows()
        }.bounds(0, 0, DELETE_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.reminders.delete.tooltip")))
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
            toggleButton.message = toggleLabel(reminder.enabled)

            var x = contentX
            place(toggleButton, x, TOGGLE_WIDTH)
            draw(toggleButton, extractor, mouseX, mouseY, delta)
            x += TOGGLE_WIDTH + GAP

            val textRight = contentRight - DELETE_WIDTH - GAP - EDIT_WIDTH - GAP - STATE_WIDTH - GAP
            val available = (textRight - x).coerceAtLeast(40)

            // Name on the upper line, trigger summary muted beneath — two lines because the trigger is what
            // tells apart two reminders a player has named similarly.
            val nameColor = if (reminder.enabled) NAME_COLOR else DISABLED_COLOR
            extractor.text(font, truncate(reminder.name, available), x, contentYMiddle - font.lineHeight - 1, nameColor)
            extractor.text(font, truncate(reminder.triggerSummary(), available), x, contentYMiddle + 1, SUB_COLOR)

            val state = stateLabel()
            extractor.text(font, state, textRight + GAP, contentYMiddle - font.lineHeight / 2, STATE_COLOR)

            place(editButton, contentRight - DELETE_WIDTH - GAP - EDIT_WIDTH, EDIT_WIDTH)
            draw(editButton, extractor, mouseX, mouseY, delta)

            place(deleteButton, contentRight - DELETE_WIDTH, DELETE_WIDTH)
            draw(deleteButton, extractor, mouseX, mouseY, delta)
        }

        /** The live countdown, so the editor doubles as a status screen. */
        private fun stateLabel(): String {
            if (!reminder.enabled) return "off"
            val state = ReminderState.of(reminder.id)
            val now = System.currentTimeMillis()
            return when (state.phase) {
                Phase.ARMED, Phase.SNOOZED -> Duration.format((state.firesAtEpochMs - now).coerceAtLeast(0L))
                Phase.PAUSED -> "paused"
                Phase.FIRING -> "firing"
                Phase.IDLE -> "idle"
            }
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
        private const val STATE_WIDTH = 52
        private const val GAP = 6

        private const val NAME_COLOR = 0xFFFFFFFF.toInt()
        private const val DISABLED_COLOR = 0xFF808080.toInt()
        private const val SUB_COLOR = 0xFF909090.toInt()
        private const val STATE_COLOR = 0xFFA0C0E0.toInt()
        private const val HINT_COLOR = 0xFFA0A0A0.toInt()
    }
}
