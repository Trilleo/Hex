package net.trilleo.reminder

import com.google.gson.reflect.TypeToken
import net.trilleo.config.ConfigHandle
import net.trilleo.config.ConfigRegistry
import net.trilleo.config.JsonConfig
import net.trilleo.reminder.ReminderConfig.normalize
import net.trilleo.reminder.hud.HudCorner
import net.trilleo.reminder.hud.HudSettings
import net.trilleo.reminder.hud.HudSort
import net.trilleo.reminder.model.*
import java.util.*

/**
 * Everything about reminders that is a *setting*, persisted at `config/hex/reminders.json`.
 *
 * @property enabled the feature's master switch. Nullable for the same reason
 *   [net.trilleo.hand.SwingItemSettings.enabled] is: GSON leaves an absent `boolean` at the JVM default of
 *   `false`, so a hand-written file omitting the key would load as *disabled*, the opposite of what omitting a
 *   setting should mean. Read it through [ReminderConfig.active].
 */
data class ReminderSettings(
    var enabled: Boolean? = null,
    var reminders: MutableList<Reminder> = mutableListOf(),
    var hud: HudSettings = HudSettings(),
    var defaultSnoozeMinutes: Double = 5.0,
    var flashSeconds: Double = 5.0,
    var soundEnabled: Boolean = true,
)

/**
 * Loads and holds the singleton [ReminderSettings].
 *
 * Registered with [ConfigRegistry], so reminder definitions and the panel's layout join config profiles and
 * clipboard export at no extra cost. What is deliberately *not* here is any reminder's live countdown — that
 * is [ReminderState], in its own unregistered file, for the reasons set out there.
 */
object ReminderConfig {
    private val config = JsonConfig(
        name = "reminders",
        type = object : TypeToken<ReminderSettings>() {}.type,
        default = { ReminderSettings() },
        normalizer = ::normalize,
    )

    var settings: ReminderSettings = ReminderSettings()
        private set

    val handle = ConfigRegistry.register(
        ConfigHandle(
            config,
            adopt = { settings = it; ChatMatcher.invalidate() },
            current = { settings },
        ),
    )

    /** Whether the feature is switched on, treating an absent key as on. */
    val active: Boolean get() = settings.enabled != false

    val hud: HudSettings get() = settings.hud

    fun load() = handle.loadInitial()

    fun save() = handle.saveNow()

    fun markDirty() = handle.markDirty()

    /**
     * Repairs the live settings in place.
     *
     * Needed because [normalize] normally runs on the way out of a file read, and a reminder added by code —
     * installing a preset, say — has never been through it. Without this, a preset's island name would keep
     * whatever case the catalogue used and silently fail to match until the next restart.
     */
    fun normalizeNow() = handle.json.normalize(settings)

    /** The reminder with this id, or null. Linear, but the list is a few dozen entries at most. */
    fun byId(id: String): Reminder? = settings.reminders.firstOrNull { it.id == id }

    /**
     * Repairs a loaded value.
     *
     * Every step covers a way GSON's reflective construction differs from Kotlin: absent objects arrive null,
     * absent primitives arrive zeroed, and an enum name this build does not know arrives null exactly like an
     * absent one. Beyond that it folds case per kind so matching can stay a plain `==` on a hot path, and
     * bounds every number a hand-edited file could put out of range.
     *
     * Two things it deliberately does **not** do:
     *
     *  - **It never compiles a regular expression.** A `PatternSyntaxException` thrown here would propagate
     *    into [JsonConfig.loadFrom]'s catch and degrade the *whole file* to defaults, destroying every other
     *    reminder because one pattern had a stray bracket. Compilation is lazy in [ChatMatcher] and eager in
     *    the editor, which are the two places that can actually report the problem to the player.
     *  - **It never validates a sound id.** The sound registry is not necessarily populated when configs load
     *    at feature init, so an unknown id is resolved at play time instead, falling back to the UI click.
     */
    private fun normalize(settings: ReminderSettings) {
        @Suppress("SENSELESS_COMPARISON")
        if (settings.reminders == null) settings.reminders = mutableListOf()
        @Suppress("SENSELESS_COMPARISON")
        if (settings.hud == null) settings.hud = HudSettings()

        settings.defaultSnoozeMinutes = settings.defaultSnoozeMinutes.sane(5.0).coerceIn(0.5, 120.0)
        settings.flashSeconds = settings.flashSeconds.sane(5.0).coerceIn(0.0, 60.0)

        normalizeHud(settings.hud)

        val seen = HashSet<String>()
        settings.reminders.forEach { reminder -> normalizeReminder(reminder, seen) }
    }

    private fun normalizeHud(hud: HudSettings) {
        @Suppress("SENSELESS_COMPARISON")
        if (hud.corner == null) hud.corner = HudCorner.TOP_LEFT
        @Suppress("SENSELESS_COMPARISON")
        if (hud.sort == null) hud.sort = HudSort.SOONEST_FIRST
        @Suppress("SENSELESS_COMPARISON")
        if (hud.backgroundColor == null) hud.backgroundColor = "#80101010"
        @Suppress("SENSELESS_COMPARISON")
        if (hud.textColor == null) hud.textColor = "#FFE0E0E0"
        @Suppress("SENSELESS_COMPARISON")
        if (hud.flashColor == null) hud.flashColor = "#FFFF5555"

        hud.anchorX = hud.anchorX.sane(0.01).coerceIn(0.0, 1.0)
        hud.anchorY = hud.anchorY.sane(0.35).coerceIn(0.0, 1.0)
        // Non-positive means "absent" rather than "invisible" — the same reading HandConfig gives a zero scale.
        hud.scale = (if (hud.scale <= 0.0) 1.0 else hud.scale).sane(1.0)
            .coerceIn(HudSettings.SCALE_MIN, HudSettings.SCALE_MAX)
        hud.maxRows = (if (hud.maxRows <= 0) 8 else hud.maxRows)
            .coerceIn(HudSettings.MAX_ROWS_MIN, HudSettings.MAX_ROWS_MAX)
    }

    private fun normalizeReminder(reminder: Reminder, seen: MutableSet<String>) {
        @Suppress("SENSELESS_COMPARISON")
        if (reminder.id == null) reminder.id = ""
        @Suppress("SENSELESS_COMPARISON")
        if (reminder.name == null) reminder.name = ""
        @Suppress("SENSELESS_COMPARISON")
        if (reminder.text == null) reminder.text = ""
        @Suppress("SENSELESS_COMPARISON")
        if (reminder.presetId == null) reminder.presetId = ""
        @Suppress("SENSELESS_COMPARISON")
        if (reminder.trigger == null) reminder.trigger = Trigger()
        @Suppress("SENSELESS_COMPARISON")
        if (reminder.conditions == null) reminder.conditions = mutableListOf()
        @Suppress("SENSELESS_COMPARISON")
        if (reminder.actions == null) reminder.actions = mutableListOf()

        // A blank or repeated id would alias another reminder's countdown, so both get a fresh one. Repeats
        // are the realistic case: someone duplicates an entry by hand to make a similar reminder.
        if (reminder.id.isBlank() || !seen.add(reminder.id)) {
            reminder.id = UUID.randomUUID().toString()
            seen.add(reminder.id)
        }

        normalizeTrigger(reminder.trigger)

        // An unnamed reminder renders as a blank row in the editor and a blank label on the panel.
        if (reminder.name.isBlank()) reminder.name = reminder.triggerSummary().replaceFirstChar { it.uppercase() }

        reminder.conditions.forEach { condition ->
            @Suppress("SENSELESS_COMPARISON")
            if (condition.kind == null) condition.kind = ConditionKind.ON_SKYBLOCK
            @Suppress("SENSELESS_COMPARISON")
            if (condition.value == null) condition.value = ""
            condition.value = when (condition.kind) {
                ConditionKind.ON_ISLAND, ConditionKind.NOT_ON_ISLAND,
                // Region names are folded by RegionConfig's own normalizer for exactly this comparison.
                ConditionKind.IN_REGION, ConditionKind.NOT_IN_REGION,
                    -> condition.value.trim().lowercase(Locale.ROOT)

                ConditionKind.HOLDING_ITEM -> condition.value.trim().uppercase(Locale.ROOT)
                ConditionKind.ON_SKYBLOCK -> condition.value
            }
        }
        // A condition needing a value it does not have can never hold, and would wedge the reminder silently.
        reminder.conditions.removeAll { it.usesValue() && it.value.isEmpty() }

        reminder.actions.forEach { it.normalize() }
        // A reminder that fires and does nothing is indistinguishable from one that never fired.
        if (reminder.actions.isEmpty()) reminder.actions.add(ReminderAction())

        // A blank pattern matches every line, so it cannot be left live. Note this switches the reminder off
        // rather than deleting it, unlike SwingItemsConfig which drops a valueless rule: an item rule with no
        // value carries nothing a human typed, whereas a reminder carries a name and a message, and silently
        // destroying those would lose real work.
        if (reminder.trigger.kind == TriggerKind.CHAT_MATCH && reminder.trigger.value.isEmpty()) {
            reminder.enabled = false
        }
    }

    private fun normalizeTrigger(trigger: Trigger) {
        @Suppress("SENSELESS_COMPARISON")
        if (trigger.kind == null) trigger.kind = TriggerKind.TIMER
        @Suppress("SENSELESS_COMPARISON")
        if (trigger.value == null) trigger.value = ""

        trigger.seconds = trigger.seconds.sane(0.0).coerceIn(0.0, Trigger.MAX_SECONDS)
        trigger.value = when (trigger.kind) {
            // Folded so matching is a plain `==` against the already-folded SkyblockLocation / HeldItem /
            // Region values.
            TriggerKind.ISLAND_ENTER, TriggerKind.ISLAND_LEAVE,
            TriggerKind.REGION_ENTER, TriggerKind.REGION_LEAVE,
                -> trigger.value.trim().lowercase(Locale.ROOT)

            TriggerKind.HELD_ITEM -> trigger.value.trim().uppercase(Locale.ROOT)
            // Left exactly as typed: a regular expression is case-sensitive by design, and folding one would
            // silently change what it matches. The editor suggests `(?i)` for callers who want otherwise.
            TriggerKind.CHAT_MATCH -> trigger.value
            TriggerKind.TIMER, TriggerKind.WORLD_JOIN -> trigger.value
        }
    }

    /** Replaces a NaN or infinite value — which no slider can produce but a hand-edited file can — with [fallback]. */
    private fun Double.sane(fallback: Double): Double = if (isFinite()) this else fallback
}
