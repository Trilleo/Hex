package net.trilleo.reminder.preset

import net.trilleo.reminder.ReminderConfig
import org.slf4j.LoggerFactory

/**
 * Brings installed presets up to date with the shipped catalogue, without ever destroying the player's work.
 *
 * The problem this solves is specific: Hypixel rewords its chat messages, so a preset's pattern eventually
 * stops matching, and the fix has to reach players who installed the broken version. But a player may also
 * have deliberately changed that preset, and silently overwriting their edit would be far worse than leaving
 * a stale pattern in place.
 *
 * The `customized` flag settles it. While it is false, the player has never touched the copy, so replacing its
 * definition takes nothing from them and hands them the fix for free. Once it is true they are told about the
 * update and left to take it or not.
 *
 * Four rules, each chosen for its failure mode:
 *
 *  - **Untouched and outdated** → the definition is replaced in place, keeping the reminder's id, its on/off
 *    state, and its running countdown. A corrected pattern must not cost the player a timer.
 *  - **Customised** → nothing at all. The editor offers "reset to preset" for anyone who wants the new version.
 *  - **A preset that no longer ships** → left installed, never deleted. Dropping a preset from the mod must
 *    not delete a reminder somebody depends on.
 *  - **A newly shipped preset** → not installed. Presets are opt-in; auto-installing would fill a player's
 *    panel with things they never asked for the first time they updated.
 */
object PresetSync {
    private val LOGGER = LoggerFactory.getLogger("hex/reminder")

    /** Runs one sync pass. Call once at feature init, after both the catalogue and the config have loaded. */
    fun run() {
        var updated = 0
        ReminderConfig.settings.reminders.forEach { reminder ->
            if (reminder.presetId.isEmpty() || reminder.customized) return@forEach
            val preset = ReminderPresets.byId(reminder.presetId) ?: return@forEach
            if (preset.revision <= reminder.presetRevision) return@forEach

            // Only the definition fields — copyDefinition deliberately leaves id, enabled and preset
            // provenance alone, which is what keeps the running countdown attached.
            preset.reminder.copyDefinition(reminder)
            reminder.presetRevision = preset.revision
            updated++
        }

        if (updated > 0) {
            ReminderConfig.normalizeNow()
            ReminderConfig.save()
            LOGGER.info("Updated {} reminder preset(s) to a newer revision", updated)
        }
    }

    /** How many installed reminders have a newer revision waiting but were left alone because they are edited. */
    fun pendingUpdates(): Int =
        ReminderConfig.settings.reminders.count { it.customized && ReminderPresets.hasUpdate(it) }
}
