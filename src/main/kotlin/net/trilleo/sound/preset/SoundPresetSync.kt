package net.trilleo.sound.preset

import net.trilleo.sound.SoundConfig
import org.slf4j.LoggerFactory

/**
 * Brings installed sequence presets up to date with the shipped catalogue, without ever destroying the
 * player's work.
 *
 * The same four rules [net.trilleo.reminder.preset.PresetSync] follows, for the same reasons, plus one that
 * is particular to sequences: **the id is never changed.** A sequence's id is what `"@alarm"` names, and
 * those references live in four other config files. An update that renamed one would silently turn every
 * alert using it into a plain UI click. [net.trilleo.sound.model.SoundSequence.copyDefinition] leaves the id
 * alone precisely so this pass cannot.
 *
 *  - **Untouched and outdated** → the definition is replaced in place, keeping the sequence's id, so every
 *    setting pointing at it keeps pointing at it and simply sounds better.
 *  - **Customised** → nothing at all. The library offers "reset to preset" for anyone who wants the new one.
 *  - **A preset that no longer ships** → left installed, never deleted. Dropping a preset from the mod must
 *    not silence an alert somebody depends on.
 *  - **A newly shipped preset** → not installed. Presets are opt-in.
 */
object SoundPresetSync {
    private val LOGGER = LoggerFactory.getLogger("hex/sound")

    /** Runs one sync pass. Call once at feature init, after both the catalogue and the config have loaded. */
    fun run() {
        var updated = 0
        SoundConfig.settings.sequences.forEach { sequence ->
            if (sequence.presetId.isEmpty() || sequence.customized) return@forEach
            val preset = SoundPresets.byId(sequence.presetId) ?: return@forEach
            if (preset.revision <= sequence.presetRevision) return@forEach

            preset.sequence.copyDefinition(sequence)
            sequence.presetRevision = preset.revision
            updated++
        }

        if (updated > 0) {
            SoundConfig.normalizeNow()
            SoundConfig.save()
            LOGGER.info("Updated {} sound preset(s) to a newer revision", updated)
        }
    }

    /** How many installed sequences have a newer revision waiting but were left alone because they are edited. */
    fun pendingUpdates(): Int =
        SoundConfig.settings.sequences.count { it.customized && SoundPresets.hasUpdate(it) }
}
