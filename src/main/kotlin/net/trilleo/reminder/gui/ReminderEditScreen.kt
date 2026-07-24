package net.trilleo.reminder.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.config.ConfigCategory
import net.trilleo.config.gui.ConfigEntryList
import net.trilleo.reminder.ChatMatcher
import net.trilleo.reminder.ReminderActions
import net.trilleo.reminder.ReminderConfig
import net.trilleo.reminder.ReminderEngine
import net.trilleo.reminder.hud.ReminderHudModel
import net.trilleo.reminder.model.ActionKind
import net.trilleo.reminder.model.Reminder
import net.trilleo.reminder.model.ReminderAction
import net.trilleo.reminder.model.TriggerKind
import net.trilleo.region.RegionConfig
import net.trilleo.util.Duration
import net.trilleo.util.Notify
import java.util.*

/**
 * Edits one reminder.
 *
 * Reuses [ConfigEntryList] rather than hand-rolling a form, by building a throwaway [ConfigCategory] whose
 * entries close over this reminder. That inherits scrolling, keyboard navigation, per-row reset, and
 * tooltip-when-the-lang-file-defines-one for nothing, and it keeps the editor looking like the rest of the
 * mod's settings.
 *
 * The strongest reason for that route is [net.trilleo.config.TextEntry]'s `validate` hook, whose contract is
 * already "show an error under the field and refuse to save while it stands". That is exactly where a regular
 * expression's compile check belongs: a bad pattern surfaces as you type it, rather than silently at the
 * moment a chat line fails to match hours later.
 *
 * The entry list is rebuilt whenever a control changes which other controls apply — a timer has no pattern
 * field, a chat trigger has no island field — which is what [ConfigEntryList.show]'s `preserveScroll` exists
 * for.
 */
class ReminderEditScreen(
    private val parent: RemindersScreen?,
    private val reminder: Reminder,
) : Screen(Component.translatable("hex.reminders.edit.title")) {

    private var list: ConfigEntryList? = null

    override fun init() {
        val listHeight = height - TOP - FOOTER_HEIGHT
        list = addRenderableWidget(ConfigEntryList(minecraft, width, listHeight, TOP, this))

        addRenderableWidget(StringWidget(MARGIN, 12, width - MARGIN * 2, 12, title, font))

        val y = height - 28
        var x = width / 2 - (BUTTON_WIDTH * 2 + GAP) / 2

        addRenderableWidget(
            Button.builder(Component.translatable("hex.reminders.edit.test")) { test() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.reminders.edit.done")) { onClose() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )

        rebuild(preserveScroll = false)
    }

    /** Rebuilds the rows, since which fields apply depends on the trigger kind. */
    private fun rebuild(preserveScroll: Boolean = true) {
        val category = buildCategory()
        list?.show(listOf(category.title to category.entries), preserveScroll)
    }

    /**
     * Marks this reminder as hand-edited, so [net.trilleo.reminder.preset.PresetSync] stops overwriting it
     * when a newer revision of its preset ships. Called from every setter here — but deliberately *not* from
     * the list screen's on/off button, because switching a preset off is not customising it.
     */
    private fun touch() {
        reminder.customized = true
        ReminderHudModel.invalidate()
        ReminderConfig.markDirty()
    }

    private fun buildCategory(): ConfigCategory = ConfigCategory.build("reminder_edit") {
        toggle(
            "enabled",
            default = true,
            get = { reminder.enabled },
            set = {
                reminder.enabled = it
                if (!it) ReminderEngine.disarm(reminder)
                ReminderHudModel.invalidate()
                ReminderConfig.save()
            },
        )
        text(
            "name",
            default = "",
            get = { reminder.name },
            set = { reminder.name = it; touch() },
        )
        text(
            "text",
            default = "",
            get = { reminder.text },
            set = { reminder.text = it; touch() },
        )

        enum(
            "trigger_kind",
            default = TriggerKind.TIMER,
            get = { reminder.trigger.kind },
            set = {
                reminder.trigger.kind = it
                touch()
                // Which fields apply just changed, so the list has to be rebuilt under the player's cursor.
                rebuild()
            },
        )

        if (reminder.trigger.usesValue()) {
            text(
                triggerValueKey(),
                default = "",
                get = { reminder.trigger.value },
                set = { reminder.trigger.value = it; touch() },
                validate = { value -> validateTriggerValue(value) },
            )
        }

        if (reminder.trigger.kind == TriggerKind.CHAT_MATCH) {
            toggle(
                "literal",
                default = false,
                get = { reminder.trigger.literal },
                set = { reminder.trigger.literal = it; touch(); rebuild() },
            )
        }

        text(
            "delay",
            default = "1m",
            get = { Duration.format((reminder.trigger.seconds * 1000.0).toLong()) },
            set = { typed ->
                Duration.parse(typed)?.let { ms ->
                    reminder.trigger.seconds = ms / 1000.0
                    touch()
                }
            },
            validate = { typed ->
                val ms = Duration.parse(typed)
                when {
                    ms == null -> Component.translatable("hex.reminders.edit.delay.invalid")
                    ms > Duration.MAX_MS -> Component.translatable("hex.reminders.edit.delay.too_long")
                    else -> null
                }
            },
        )

        toggle(
            "repeat",
            default = false,
            get = { reminder.trigger.repeat },
            set = { reminder.trigger.repeat = it; touch() },
        )

        action("conditions") { screen ->
            Minecraft.getInstance()
                .setScreen(ReminderConditionsScreen(screen, reminder, this@ReminderEditScreen::touch))
        }

        // Actions are expressed as switches rather than as a list, because there are only two of them and a
        // list editor for a two-item set would be more machinery than the thing it edits.
        toggle(
            "action_hud",
            default = true,
            get = { reminder.actions.any { it.kind == ActionKind.HUD } },
            set = { setAction(ActionKind.HUD, it) },
        )
        toggle(
            "action_sound",
            default = false,
            get = { reminder.actions.any { it.kind == ActionKind.SOUND } },
            set = { setAction(ActionKind.SOUND, it); rebuild() },
        )
        toggle(
            "action_title",
            default = false,
            get = { reminder.actions.any { it.kind == ActionKind.TITLE } },
            set = { setAction(ActionKind.TITLE, it); rebuild() },
        )

        titleAction()?.let { title ->
            text(
                "title_subtitle",
                default = "",
                get = { title.subtitle },
                set = { title.subtitle = it; touch() },
            )
            color(
                "title_color",
                default = "#FFFFFF",
                get = { title.titleColor.ifBlank { "#FFFFFF" } },
                set = { title.titleColor = it; touch() },
            )
            slider(
                "title_seconds",
                min = ReminderAction.TITLE_SECONDS_MIN,
                max = ReminderAction.TITLE_SECONDS_MAX,
                step = 0.5,
                default = ReminderAction.DEFAULT_TITLE_SECONDS,
                get = { title.titleSeconds },
                set = { title.titleSeconds = it; touch() },
                format = { String.format(Locale.ROOT, "%.1fs", it) },
            )
        }

        soundAction()?.let { sound ->
            text(
                "sound_id",
                default = ReminderAction.DEFAULT_SOUND,
                get = { sound.value },
                set = { sound.value = it; touch() },
                validate = { id ->
                    if (Notify.soundFor(id) == null) Component.translatable("hex.reminders.edit.sound.unknown") else null
                },
            )
            slider(
                "sound_pitch",
                min = ReminderAction.PITCH_MIN,
                max = ReminderAction.PITCH_MAX,
                step = 0.05,
                default = 1.0,
                get = { sound.pitch },
                set = { sound.pitch = it; touch() },
                format = { String.format(Locale.ROOT, "%.2f", it) },
            )
            slider(
                "sound_volume",
                min = ReminderAction.VOLUME_MIN,
                max = ReminderAction.VOLUME_MAX,
                step = 0.05,
                default = 1.0,
                get = { sound.volume },
                set = { sound.volume = it; touch() },
                format = { String.format(Locale.ROOT, "%.0f%%", it * 100) },
            )
        }

        if (reminder.presetId.isNotEmpty() && reminder.customized) {
            action("reset_preset") { _ -> resetToPreset() }
        }
    }

    /** The label key for the trigger's payload field, so each kind names its own field properly. */
    private fun triggerValueKey(): String = when (reminder.trigger.kind) {
        TriggerKind.CHAT_MATCH -> "trigger_pattern"
        TriggerKind.ISLAND_ENTER, TriggerKind.ISLAND_LEAVE -> "trigger_island"
        TriggerKind.HELD_ITEM -> "trigger_item"
        TriggerKind.REGION_ENTER, TriggerKind.REGION_LEAVE -> "trigger_region"
        TriggerKind.TIMER, TriggerKind.WORLD_JOIN -> "trigger_value"
    }

    private fun validateTriggerValue(value: String): Component? {
        if (value.isBlank()) return Component.translatable("hex.reminders.edit.trigger_value.blank")

        // A region trigger names something that has to exist: unlike an island, which Hypixel supplies, a
        // region only exists because the player drew one, so a typo here silently never fires. Reported as a
        // warning the field still accepts, because the region may be drawn after the reminder.
        if (reminder.trigger.kind == TriggerKind.REGION_ENTER || reminder.trigger.kind == TriggerKind.REGION_LEAVE) {
            val known = RegionConfig.byName(value.trim().lowercase(Locale.ROOT)) != null
            return if (known) null else Component.translatable("hex.reminders.edit.trigger_region.unknown")
        }

        if (reminder.trigger.kind != TriggerKind.CHAT_MATCH || reminder.trigger.literal) return null
        // The one place a bad pattern can be reported to the person who wrote it.
        val error = ChatMatcher.compileError(value) ?: return null
        return Component.literal(error)
    }

    private fun setAction(kind: ActionKind, on: Boolean) {
        if (on) {
            if (reminder.actions.none { it.kind == kind }) {
                reminder.actions.add(ReminderAction().apply { this.kind = kind })
            }
        } else {
            reminder.actions.removeAll { it.kind == kind }
            // Never leave a reminder that fires and does nothing — it reads exactly like a broken one.
            if (reminder.actions.isEmpty()) reminder.actions.add(ReminderAction())
        }
        touch()
    }

    private fun soundAction(): ReminderAction? = reminder.actions.firstOrNull { it.kind == ActionKind.SOUND }

    private fun titleAction(): ReminderAction? = reminder.actions.firstOrNull { it.kind == ActionKind.TITLE }

    /** Fires the reminder's actions now, so a sound choice can be heard before committing to it. */
    private fun test() {
        ReminderActions.run(Minecraft.getInstance(), reminder)
    }

    private fun resetToPreset() {
        net.trilleo.reminder.preset.ReminderPresets.resetToPreset(reminder)
        ReminderHudModel.invalidate()
        ReminderConfig.save()
        rebuild()
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun removed() {
        ReminderConfig.save()
        parent?.refreshRows()
    }

    private companion object {
        const val MARGIN = 24
        const val TOP = 32
        const val FOOTER_HEIGHT = 40
        const val BUTTON_WIDTH = 100
        const val BUTTON_HEIGHT = 20
        const val GAP = 6
    }
}
