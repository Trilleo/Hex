package net.trilleo.reminder.preset

import com.google.gson.reflect.TypeToken
import net.trilleo.config.JsonConfig
import net.trilleo.reminder.ReminderConfig
import net.trilleo.reminder.model.Reminder
import org.slf4j.LoggerFactory
import java.io.InputStreamReader
import java.util.*

/**
 * One shipped preset: a [Reminder] plus the bookkeeping that lets a later mod version update it.
 *
 * A preset *is* a reminder — the same class, the same GSON types, the same normalizer — so the catalogue
 * needs no parallel model and no converter that could drift from it.
 */
class ReminderPreset {
    /** Stable identity of the preset itself, e.g. `"booster_cookie"`. Never reused for a different preset. */
    var presetId: String = ""

    /** Bumped whenever the shipped definition changes. Drives the update offer. */
    var revision: Int = 1

    /** Grouping in the browser, e.g. `"combat"`. */
    var category: String = "general"

    /** The reminder to copy when the player adds this. */
    var reminder: Reminder = Reminder()
}

data class PresetFile(
    var presets: MutableList<ReminderPreset> = mutableListOf(),
)

/**
 * The bundled preset catalogue, read from `assets/hex/reminders/presets.json`.
 *
 * Shipped as a JSON resource rather than as Kotlin objects for three reasons that all point the same way:
 * a preset is literally a [Reminder], so this reuses the model and its normalizer wholesale; Hypixel rewords
 * its chat messages, and fixing a pattern should be a resource edit rather than a code change; and it
 * documents the file format for anyone hand-editing their own `reminders.json`, which is supported.
 *
 * Read from the **classpath**, not the resource manager: it must be available at feature init, before the
 * first resource reload, and a resource pack must not be able to override or break it.
 */
object ReminderPresets {
    private val LOGGER = LoggerFactory.getLogger("hex/reminder")

    private const val RESOURCE = "/assets/hex/reminders/presets.json"

    /** The shipped catalogue, loaded once. Empty when the resource is missing or malformed. */
    var all: List<ReminderPreset> = emptyList()
        private set

    fun load() {
        all = runCatching {
            val stream = javaClass.getResourceAsStream(RESOURCE) ?: return@runCatching emptyList()
            InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                val file: PresetFile? = JsonConfig.GSON.fromJson(reader, object : TypeToken<PresetFile>() {}.type)
                file?.presets?.filter { it.presetId.isNotBlank() } ?: emptyList()
            }
        }.onFailure {
            // A broken catalogue must not stop the feature loading — the player's own reminders are the part
            // that matters, and they live in a different file entirely.
            LOGGER.error("Failed to read the bundled reminder presets", it)
        }.getOrDefault(emptyList())

        LOGGER.info("Loaded {} reminder preset(s)", all.size)
    }

    fun byId(presetId: String): ReminderPreset? = all.firstOrNull { it.presetId == presetId }

    /** Whether this preset already has a copy in the player's list. */
    fun isInstalled(preset: ReminderPreset): Boolean =
        ReminderConfig.settings.reminders.any { it.presetId == preset.presetId }

    /**
     * Copies [preset] into the player's reminders and returns the new copy.
     *
     * A copy, not a reference: presets are a template catalogue, not a live overlay. That is what makes the
     * update story tractable — the player owns their copy, and [PresetSync] only touches it while they have
     * left it alone.
     */
    fun install(preset: ReminderPreset): Reminder {
        val copy = Reminder()
        preset.reminder.copyDefinition(copy)
        copy.id = UUID.randomUUID().toString()
        copy.presetId = preset.presetId
        copy.presetRevision = preset.revision
        copy.customized = false
        copy.enabled = true

        ReminderConfig.settings.reminders.add(copy)
        // The catalogue's own entries have never been through the config normalizer, so the freshly installed
        // copy is repaired here rather than waiting for the next load to fold its case and fill its defaults.
        ReminderConfig.normalizeNow()
        ReminderConfig.save()
        return copy
    }

    /** Restores a customised reminder to its shipped definition, and re-arms it for preset updates. */
    fun resetToPreset(reminder: Reminder) {
        val preset = byId(reminder.presetId) ?: return
        preset.reminder.copyDefinition(reminder)
        reminder.presetRevision = preset.revision
        reminder.customized = false
        ReminderConfig.normalizeNow()
        ReminderConfig.save()
    }

    /** Whether a newer revision of this reminder's preset ships with the current build. */
    fun hasUpdate(reminder: Reminder): Boolean {
        if (reminder.presetId.isEmpty()) return false
        val preset = byId(reminder.presetId) ?: return false
        return preset.revision > reminder.presetRevision
    }
}
