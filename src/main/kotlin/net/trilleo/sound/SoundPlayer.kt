package net.trilleo.sound

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.trilleo.sound.model.SoundSequence
import net.trilleo.sound.model.SoundStep

/**
 * **The one place this mod makes a sound.** Nothing else builds a [SimpleSoundInstance].
 *
 * Every route in — a reminder firing, a title landing, a region chiming, a toggle clicking, a picker
 * previewing — arrives here as a [SoundValue] plus a pitch and a volume, and this decides whether that means
 * silence, one sound now, or a sequence spread over the next few seconds. Keeping a single entry point is
 * what stops a feature added later growing a parallel audio path that could drift from this one, exactly as
 * [net.trilleo.color.gui.ColorPickerScreen] does for colour.
 *
 * ### What the master switches do and do not cover
 *
 * This object owns one gate, [SoundConfig.active], and one scale, [SoundSettings.masterVolume]. The
 * per-feature switches — the Reminders tab's and the Titles tab's — are deliberately left at their own call
 * sites rather than folded in here. They are named for their features, and a player who turns reminder sounds
 * off has not asked for the attack-mode toggle to fall silent too.
 */
object SoundPlayer {

    /**
     * Plays whatever [spec] names, returning a handle [stop] understands, or
     * [SoundScheduler.NO_HANDLE] when nothing was played.
     *
     * @param pitch scales the result. For one sound it *is* the pitch; for a sequence it multiplies every
     *   step's own, so setting a reminder to 1.5 transposes the whole thing rather than flattening it.
     * @param volume the same, against [SoundSettings.masterVolume] as well.
     *
     * Never throws. The callers are alert pipelines mid-dispatch, and one unplayable sound must not strand a
     * reminder's phase transition.
     */
    fun play(client: Minecraft, spec: String, pitch: Double = 1.0, volume: Double = 1.0): Long {
        if (SoundValue.isNone(spec)) return SoundScheduler.NO_HANDLE
        if (!SoundConfig.active) return SoundScheduler.NO_HANDLE

        SoundValue.sequenceId(spec)?.let { id ->
            val sequence = SoundConfig.byId(id)
            if (sequence != null) return playSequence(client, sequence, pitch, volume)
            // A reference to a sequence that is no longer there. One click rather than silence, for the
            // reason a bad sound id has always fallen back rather than staying quiet: a missed alert is a far
            // worse failure than a wrong one, and the picker reports the broken reference where it can be
            // seen and fixed.
            playNow(client, fallbackSound(), pitch.toFloat(), scaled(volume))
            return SoundScheduler.NO_HANDLE
        }

        playNow(client, SoundValue.eventFor(spec) ?: fallbackSound(), clampPitch(pitch), scaled(volume))
        return SoundScheduler.NO_HANDLE
    }

    /**
     * Plays the sound a named piece of mod feedback is set to.
     *
     * The six call sites that used to pass a pitch constant of their own now name a [SoundSlot] instead, so
     * the vocabulary they were already sharing by convention is a real one that the player can retune.
     */
    fun feedback(client: Minecraft, slot: SoundSlot): Long {
        val settings = slot.settings()
        return play(client, settings.value, settings.pitch, settings.volume)
    }

    /**
     * The same as [play], for the picker's preview button and the editor's transport.
     *
     * A separate name rather than a separate path: previewing must sound exactly like the real thing, so
     * anything that made it different would be a bug waiting to be reported as "it sounded fine in the menu".
     */
    fun preview(client: Minecraft, spec: String, pitch: Double = 1.0, volume: Double = 1.0): Long =
        play(client, spec, pitch, volume)

    /**
     * Plays [sequence] starting [fromMillis] in, so the editor's playhead can start where it was left.
     *
     * Steps before [fromMillis] are skipped rather than crammed onto the start, which is what scrubbing into
     * the middle of a sequence has to mean.
     */
    fun playSequence(
        client: Minecraft,
        sequence: SoundSequence,
        pitch: Double = 1.0,
        volume: Double = 1.0,
        fromMillis: Double = 0.0,
    ): Long {
        if (!SoundConfig.active) return SoundScheduler.NO_HANDLE
        if (sequence.steps.isEmpty()) return SoundScheduler.NO_HANDLE

        val handle = SoundScheduler.beginSequence()
        val passes = if (sequence.loop) sequence.loopCount.coerceAtLeast(1) else 1
        // The length of one pass, not of the last step, so a trailing rest is kept when the sequence repeats.
        val passLength = sequence.durationMillis()

        for (pass in 0 until passes) {
            val passOffset = pass * passLength
            for (step in sequence.steps) {
                val at = passOffset + step.atMillis - fromMillis
                if (at < 0.0) continue
                val event = SoundValue.eventFor(step.sound) ?: fallbackSound()
                val queued = SoundScheduler.scheduleIn(
                    at,
                    event,
                    clampPitch(step.pitch * pitch),
                    scaled(step.volume * volume),
                    handle,
                )
                // The queue is full. Stop here rather than trying every remaining step in turn: the rest of
                // this sequence is later than what is already queued, so nothing would fit anyway.
                if (!queued) return handle
            }
        }
        return handle
    }

    /** Drops anything still waiting under [handle]. Sounds already playing finish, as they must. */
    fun stop(handle: Long) = SoundScheduler.stop(handle)

    /** Drops everything waiting. */
    fun stopAll() = SoundScheduler.stopAll()

    /**
     * One sound, right now — the only place a [SimpleSoundInstance] is built.
     *
     * `forUI` rather than a positioned instance so nothing here is attenuated by distance or occluded by a
     * wall: these are interface sounds and alerts, and an alert the player cannot hear because they are
     * standing behind a pillar is not an alert.
     *
     * Scheduled through [Minecraft.execute] so callers may be on any thread, and because that runs inline
     * when it is already on the client thread, a sound requested during a frame is heard in that frame.
     */
    internal fun playNow(client: Minecraft, sound: SoundEvent, pitch: Float, volume: Float) {
        client.execute {
            client.soundManager.play(SimpleSoundInstance.forUI(sound, pitch, volume))
        }
    }

    /**
     * The standard UI click, unwrapped from its registry holder.
     *
     * [SoundEvents] exposes its constants as holders, and the [SimpleSoundInstance.forUI] overload that takes
     * a holder accepts no volume — so it has to be unwrapped here for every caller that wants one.
     */
    private fun fallbackSound(): SoundEvent = SoundEvents.UI_BUTTON_CLICK.value()

    private fun clampPitch(pitch: Double): Float =
        (if (pitch.isFinite()) pitch else 1.0).coerceIn(SoundStep.PITCH_MIN, SoundStep.PITCH_MAX).toFloat()

    /** [volume] against the master scale, bounded — a hand-edited multiplier cannot push past full. */
    private fun scaled(volume: Double): Float {
        val raw = if (volume.isFinite()) volume else 1.0
        return (raw * SoundConfig.settings.masterVolume).coerceIn(0.0, 1.0).toFloat()
    }
}
