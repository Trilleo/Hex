package net.trilleo.sound.preset

import com.google.gson.reflect.TypeToken
import net.trilleo.config.JsonConfig
import net.trilleo.sound.SoundConfig
import net.trilleo.sound.model.SoundSequence
import org.slf4j.LoggerFactory
import java.io.InputStreamReader

/**
 * One shipped preset: a [SoundSequence] plus the bookkeeping that lets a later mod version update it.
 *
 * A preset *is* a sequence — the same class, the same GSON types, the same normalizer — so the catalogue
 * needs no parallel model and no converter that could drift from it.
 */
class SoundPreset {
    /** Stable identity of the preset itself, e.g. `"alarm"`. Never reused for a different preset. */
    var presetId: String = ""

    /** Bumped whenever the shipped definition changes. Drives the update offer. */
    var revision: Int = 1

    /** Grouping in the browser, e.g. `"alert"`. */
    var category: String = "general"

    /** The sequence to copy when the player adds this. */
    var sequence: SoundSequence = SoundSequence()
}

data class SoundPresetFile(
    var presets: MutableList<SoundPreset> = mutableListOf(),
)

/**
 * The bundled sequence catalogue, read from `assets/hex/sounds/presets.json`.
 *
 * Shipped as a JSON resource rather than as Kotlin objects, for the reasons
 * [net.trilleo.reminder.preset.ReminderPresets] gives and one of its own: a sequence is a list of numbers,
 * and a list of numbers is far more legible as data than as constructor calls. It also documents the file
 * format for anyone hand-editing their own `sounds.json`, which is supported.
 *
 * Read from the **classpath**, not the resource manager: it must be available at feature init, before the
 * first resource reload, and a resource pack must not be able to override or break it.
 */
object SoundPresets {
    private val LOGGER = LoggerFactory.getLogger("hex/sound")

    private const val RESOURCE = "/assets/hex/sounds/presets.json"

    /** The shipped catalogue, loaded once. Empty when the resource is missing or malformed. */
    var all: List<SoundPreset> = emptyList()
        private set

    fun load() {
        all = runCatching {
            val stream = javaClass.getResourceAsStream(RESOURCE) ?: return@runCatching emptyList()
            InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                val file: SoundPresetFile? =
                    JsonConfig.GSON.fromJson(reader, object : TypeToken<SoundPresetFile>() {}.type)
                file?.presets?.filter { it.presetId.isNotBlank() } ?: emptyList()
            }
        }.onFailure {
            // A broken catalogue must not stop the feature loading — the player's own sequences are the part
            // that matters, and they live in a different file entirely.
            LOGGER.error("Failed to read the bundled sound presets", it)
        }.getOrDefault(emptyList())

        LOGGER.info("Loaded {} sound preset(s)", all.size)
    }

    fun byId(presetId: String): SoundPreset? = all.firstOrNull { it.presetId == presetId }

    /** Whether this preset already has a copy in the player's library. */
    fun isInstalled(preset: SoundPreset): Boolean =
        SoundConfig.settings.sequences.any { it.presetId == preset.presetId }

    /**
     * Copies [preset] into the player's sequences and returns the new copy.
     *
     * A copy, not a reference: presets are a template catalogue, not a live overlay. That is what makes the
     * update story tractable — the player owns their copy, and [SoundPresetSync] only touches it while they
     * have left it alone.
     */
    fun install(preset: SoundPreset): SoundSequence {
        val copy = SoundSequence()
        preset.sequence.copyDefinition(copy)
        // An id derived from the preset's own, so `"@alarm"` is what a freshly installed Alarm is called and
        // the wiki can name it. Uniqued because a player may already have a sequence by that name.
        copy.id = SoundConfig.uniqueId(preset.presetId)
        copy.presetId = preset.presetId
        copy.presetRevision = preset.revision
        copy.customized = false

        SoundConfig.settings.sequences.add(copy)
        // The catalogue's own entries have never been through the config normalizer, so the freshly installed
        // copy is repaired here rather than waiting for the next load to bound its numbers and sort its steps.
        SoundConfig.normalizeNow()
        SoundConfig.save()
        return copy
    }

    /** Restores a customised sequence to its shipped definition, and re-arms it for preset updates. */
    fun resetToPreset(sequence: SoundSequence) {
        val preset = byId(sequence.presetId) ?: return
        preset.sequence.copyDefinition(sequence)
        sequence.presetRevision = preset.revision
        sequence.customized = false
        SoundConfig.normalizeNow()
        SoundConfig.save()
    }

    /** Whether a newer revision of this sequence's preset ships with the current build. */
    fun hasUpdate(sequence: SoundSequence): Boolean {
        if (sequence.presetId.isEmpty()) return false
        val preset = byId(sequence.presetId) ?: return false
        return preset.revision > sequence.presetRevision
    }
}
