package net.trilleo.sound.model

/**
 * One sound in a [SoundSequence]: what to play, when, and how.
 *
 * Plain, `var`-only and no-arg constructible for the same GSON reasons as
 * [net.trilleo.reminder.model.ReminderAction] — the reflective constructor does not run Kotlin's defaults, so
 * every field has to survive arriving zeroed and [normalize] has to be able to put it back.
 *
 * **[atMillis] is absolute, measured from the start of the sequence, not a delay after the step before it.**
 * A timeline is a picture of absolute time: a clip sits where it sits, and dragging one must not shift every
 * clip after it. Storing offsets would make a drag an edit of two steps and a delete an edit of one more,
 * and the editor would spend its life keeping a chain consistent. The inspector still shows the gap from the
 * previous step, because that is how a player describes a rhythm — but it is derived, not stored.
 */
class SoundStep {

    /**
     * The sound event id, e.g. `"minecraft:block.note_block.pling"`.
     *
     * Always a plain id: a step may not name another sequence. [normalize] strips a leading
     * [net.trilleo.sound.SoundValue.SEQUENCE_PREFIX] rather than trusting the file, because a sequence that
     * contained itself would schedule forever, and forbidding the shape outright is a great deal cheaper than
     * detecting the cycle it creates.
     *
     * Deliberately not validated against the registry here — the registry is not populated when configs load
     * at feature init, so an id is resolved when it is played and checked inline by the editor, which can
     * actually report the problem. Same bargain [net.trilleo.reminder.model.ReminderAction.value] documents.
     */
    var sound: String = DEFAULT_SOUND

    /** When this step plays, in milliseconds from the start of the sequence. */
    var atMillis: Double = 0.0

    /** Playback speed, `0.5`..`2.0`. For a note block sound this is a note — see [NoteNames]. */
    var pitch: Double = 1.0

    /** Loudness, `0.0`..`1.0`. Drawn as the clip's fill height in the editor. */
    var volume: Double = 1.0

    /**
     * Which track this step is drawn on, `0`..[LANE_MAX].
     *
     * Purely presentational: playback reads [atMillis] and nothing else, so two steps on different lanes at
     * the same time play together exactly as two on one lane would. Lanes exist because a sequence with a
     * drone under a melody is unreadable when both are on one row, which is the same reason a DAW has them.
     */
    var lane: Int = 0

    /** Repairs this step in place, covering GSON's reflection gaps and bounding every number. */
    fun normalize() {
        @Suppress("SENSELESS_COMPARISON")
        if (sound == null) sound = DEFAULT_SOUND
        // A step naming a sequence would recurse. Stripping the marker leaves a plain id, which is the
        // closest thing to what such a file was trying to say.
        sound = sound.trim().removePrefix(SEQUENCE_MARKER).ifBlank { DEFAULT_SOUND }

        atMillis = atMillis.sane(0.0).coerceIn(0.0, MAX_MILLIS)
        pitch = pitch.sane(1.0).coerceIn(PITCH_MIN, PITCH_MAX)
        volume = volume.sane(1.0).coerceIn(VOLUME_MIN, VOLUME_MAX)
        lane = lane.coerceIn(0, LANE_MAX)
    }

    /** A field-for-field copy, for duplicating a step or snapshotting the list for undo. */
    fun copy(): SoundStep = SoundStep().also {
        it.sound = sound
        it.atMillis = atMillis
        it.pitch = pitch
        it.volume = volume
        it.lane = lane
    }

    /** Replaces a NaN or infinite value — which no slider can produce but a hand-edited file can. */
    private fun Double.sane(fallback: Double): Double = if (isFinite()) this else fallback

    companion object {
        /** The same short, bright note [net.trilleo.reminder.model.ReminderAction] defaults to. */
        const val DEFAULT_SOUND: String = "minecraft:block.note_block.pling"

        /**
         * The pitch range, which is also exactly the note block's twenty-five notes — see [NoteNames]. The
         * two agreeing is what lets the editor show either spelling of the same value without a second scale.
         */
        const val PITCH_MIN: Double = 0.5
        const val PITCH_MAX: Double = 2.0

        const val VOLUME_MIN: Double = 0.0
        const val VOLUME_MAX: Double = 1.0

        /** The highest lane index; eight lanes, `0`..`7`. */
        const val LANE_MAX: Int = 7

        /**
         * The furthest into a sequence a step may sit, one minute.
         *
         * Not a musical limit but a practical one: these are alert sounds, and a step scheduled an hour out
         * would sit in the scheduler across world changes waiting to surprise someone.
         */
        const val MAX_MILLIS: Double = 60_000.0

        /** Kept as a string here so this file does not depend on the value type it is guarding against. */
        private const val SEQUENCE_MARKER: String = "@"
    }
}
