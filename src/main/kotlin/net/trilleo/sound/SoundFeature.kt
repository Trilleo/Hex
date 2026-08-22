package net.trilleo.sound

import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.trilleo.config.ConfigCategory
import net.trilleo.feature.Feature
import net.trilleo.sound.gui.SoundSequencesScreen
import net.trilleo.sound.model.SoundSequence
import net.trilleo.sound.preset.SoundPresetSync
import net.trilleo.sound.preset.SoundPresets
import java.util.*

/**
 * Sound sequences, the feedback slots, and the settings shared by everything the mod plays.
 *
 * Mostly a home rather than a behaviour: the only thing that ticks here is [SoundScheduler.advance], and it
 * is registered on two hooks rather than one because neither covers every case — see that object.
 *
 * Like [net.trilleo.title.TitleFeature], this leaves [enabled] at `true` and gates behaviour on
 * [SoundConfig.active] instead: [net.trilleo.feature.Features.categories] hides a disabled feature's tab, so
 * wiring the master switch to [enabled] would make it impossible to switch back on from the menu.
 */
object SoundFeature : Feature {
    override val id: String = "sounds"

    override fun onInit() {
        SoundConfig.load()
        SoundPresets.load()
        SoundPresetSync.run()
    }

    /**
     * The clock's floor. Deliberately outside the [SoundConfig.active] check — a sequence already in flight
     * has to drain rather than hang if the feature is switched off halfway through it.
     */
    override fun onClientTick(client: Minecraft) {
        SoundScheduler.advance(client)
    }

    /**
     * The clock's fine driver. Not drawing anything — this feature has no HUD — but it is the only per-frame
     * callback a feature gets, and 50 ms of tick granularity is too coarse for a sixteenth note.
     */
    override fun onHudRender(extractor: GuiGraphicsExtractor, delta: DeltaTracker) {
        SoundScheduler.advance(Minecraft.getInstance())
    }

    /** Nothing scheduled should survive into a different world, least of all a looping sequence. */
    override fun onWorldLeave(client: Minecraft) {
        SoundPlayer.stopAll()
    }

    override fun settingsCategory(): ConfigCategory = ConfigCategory.build("sounds") {
        toggle(
            "enabled",
            default = true,
            get = { SoundConfig.active },
            set = { SoundConfig.settings.enabled = it; SoundConfig.save() },
        )

        slider(
            "master_volume",
            min = 0.0,
            max = 1.0,
            step = 0.05,
            default = 1.0,
            get = { SoundConfig.settings.masterVolume },
            set = { SoundConfig.settings.masterVolume = it; SoundConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.0f%%", it * 100) },
        )

        action("sequences") { screen ->
            Minecraft.getInstance().setScreen(SoundSequencesScreen(screen))
        }

        slider(
            "default_bpm",
            min = SoundSequence.BPM_MIN,
            max = SoundSequence.BPM_MAX,
            step = 1.0,
            default = SoundSequence.DEFAULT_BPM,
            get = { SoundConfig.settings.defaultBpm },
            set = { SoundConfig.settings.defaultBpm = it; SoundConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.0f", it) },
        )

        // One row per feedback slot. Each is a full sound value, so any of these short clicks can be swapped
        // for a different sound, retuned, or replaced with a whole sequence.
        SoundSlot.entries.forEach { slot ->
            sound(
                slot.configKey(),
                default = SoundSlotSettings.DEFAULT_SOUND,
                optional = true,
                sequences = true,
                get = { slot.settings().value },
                set = { slot.settings().value = it; SoundConfig.save() },
                defaultPitch = slot.defaultPitch,
                getPitch = { slot.settings().pitch },
                setPitch = { slot.settings().pitch = it; SoundConfig.markDirty() },
                getVolume = { slot.settings().volume },
                setVolume = { slot.settings().volume = it; SoundConfig.markDirty() },
            )
        }

        resetsTo(SoundConfig.handle)
    }
}
