package net.trilleo.reminder.gui

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.reminder.ReminderConfig
import net.trilleo.reminder.hud.ReminderHudModel
import net.trilleo.reminder.hud.ReminderHudScreen
import net.trilleo.reminder.model.Reminder
import net.trilleo.reminder.preset.gui.PresetsScreen
import java.util.*

/**
 * The reminder editor: a scrolling [ReminderList] plus a footer for adding, browsing presets, and moving the
 * panel.
 *
 * Reachable from the **Reminders** tab of `/hexa config` and from `/hexa remind edit`. Rows show each
 * reminder's live countdown, so this doubles as a status screen rather than only an editor.
 */
class RemindersScreen(private val parent: Screen?) :
    Screen(Component.translatable("hex.reminders.title")) {

    private var list: ReminderList? = null

    override fun init() {
        val listHeight = height - TOP - FOOTER_HEIGHT
        list = addRenderableWidget(ReminderList(minecraft, width, listHeight, TOP, this))

        addRenderableWidget(StringWidget(MARGIN, 12, width - MARGIN * 2, 12, title, font))

        val y = height - 28
        var x = width / 2 - (BUTTON_WIDTH * 4 + GAP * 3) / 2

        addRenderableWidget(
            Button.builder(Component.translatable("hex.reminders.add")) { addBlank() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.reminders.add.tooltip")))
                .build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.reminders.presets")) {
                minecraft.setScreen(PresetsScreen(this))
            }.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.reminders.presets.tooltip")))
                .build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.reminders.hud_position")) {
                minecraft.setScreen(ReminderHudScreen(this))
            }.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.reminders.done")) { onClose() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )

        refreshRows()
    }

    /** Re-reads the reminders into the list. Called after every add, delete, and return from the sub-editor. */
    fun refreshRows() {
        list?.show(ReminderConfig.settings.reminders)
    }

    private fun addBlank() {
        val reminder = Reminder().apply {
            id = UUID.randomUUID().toString()
            name = Component.translatable("hex.reminders.new_name").string
            text = Component.translatable("hex.reminders.new_text").string
        }
        ReminderConfig.settings.reminders.add(reminder)
        ReminderConfig.save()
        refreshRows()
        // Scroll to it: a row appended out of sight looks like the button did nothing.
        list?.scrollToBottom()
        // Straight into the editor — a blank reminder is never what anyone actually wanted.
        minecraft.setScreen(ReminderEditScreen(this, reminder))
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun removed() {
        // Edits mark the config dirty as they happen; this makes leaving the screen a definite save point
        // rather than waiting on the debounce.
        ReminderConfig.save()
        ReminderHudModel.invalidate()
    }

    private companion object {
        const val MARGIN = 24
        const val TOP = 32
        const val FOOTER_HEIGHT = 40
        const val BUTTON_WIDTH = 84
        const val BUTTON_HEIGHT = 20
        const val GAP = 6
    }
}
