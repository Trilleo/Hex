package net.trilleo.sound.model

import kotlin.math.max

/**
 * A named set of sounds with times on them — what the mod plays when a setting names `"@<id>"`.
 *
 * Plain and `var`-only for the same GSON reasons as [SoundStep], and deliberately **not** a data class: the
 * list screens hold sequences by reference identity, so deleting one cannot take an equal-looking sibling
 * with it. [net.trilleo.region.model.Region] is not a data class for exactly this reason.
 *
 * ### The id is permanent; the name is not
 *
 * [id] is what a `"@id"` reference names, and those references live in `reminders.json`, `regions.json`,
 * `highlights.json` and `chathighlights.json` — inside every action's value *and* inside every nested title
 * spec — as well as in this config's own feedback slots. Repointing all of them on a rename would mean
 * sweeping five files and walking two levels of nesting to keep one edit honest, and missing a single one
 * would silently turn an alert into a UI click.
 *
 * So [id] is generated once from the name at creation and never changes, and [name] is free text the player
 * can rewrite whenever they like. The list row shows both, so the string to type into a hand-edited config is
 * always visible.
 */
class SoundSequence {

    /**
     * The stable slug a `"@id"` reference names, e.g. `"boss-alarm"`. Assigned once at creation; see above.
     */
    var id: String = ""

    /** What the player calls this, shown in the list and on the picker's button. Freely editable. */
    var name: String = ""

    /**
     * The grid the editor rules this sequence against, in beats per minute.
     *
     * An editor setting and nothing more — playback reads [SoundStep.atMillis], which is already absolute, so
     * changing the tempo re-rules the grid without moving a single step. That is deliberate: a sequence that
     * silently retimed itself when the ruler changed would be unpredictable to edit, and the alternative
     * (storing times in beats) would make every step depend on a field it has no reason to care about.
     */
    var bpm: Double = DEFAULT_BPM

    /** Whether the whole sequence repeats when it reaches the end. */
    var loop: Boolean = false

    /** How many times a looping sequence plays in total, `1`..[LOOP_MAX]. Ignored when [loop] is false. */
    var loopCount: Int = 1

    /** The sounds, kept sorted by [SoundStep.atMillis] so the editor and the scheduler agree on order. */
    var steps: MutableList<SoundStep> = mutableListOf()

    /** The preset this came from, or `""` when the player built it. See `net.trilleo.sound.preset`. */
    var presetId: String = ""

    /** The preset revision this copy was taken from, used to offer updates. */
    var presetRevision: Int = 0

    /**
     * Whether the player has edited this since it was copied from a preset. While false, a newer shipped
     * revision may overwrite the definition in place — which is safe precisely because [copyDefinition]
     * leaves [id] alone, so every `"@id"` pointing here keeps pointing here.
     */
    var customized: Boolean = false

    /** Repairs this sequence in place, covering GSON's reflection gaps and bounding every number. */
    fun normalize() {
        @Suppress("SENSELESS_COMPARISON")
        if (id == null) id = ""
        @Suppress("SENSELESS_COMPARISON")
        if (name == null) name = ""
        @Suppress("SENSELESS_COMPARISON")
        if (steps == null) steps = mutableListOf()
        @Suppress("SENSELESS_COMPARISON")
        if (presetId == null) presetId = ""

        bpm = (if (bpm.isFinite()) bpm else DEFAULT_BPM).takeIf { it > 0.0 }?.coerceIn(BPM_MIN, BPM_MAX)
            ?: DEFAULT_BPM
        loopCount = loopCount.coerceIn(1, LOOP_MAX)

        // Trimmed before sorting so a hand-edited file that piled a hundred steps at one instant loses the
        // overflow rather than the ordering.
        if (steps.size > MAX_STEPS) steps = steps.take(MAX_STEPS).toMutableList()
        steps.forEach { it.normalize() }
        // Stable, so two steps at the same instant keep the order the file gave them — which is the order the
        // editor drew them in, and the order they were built in.
        steps.sortBy { it.atMillis }
    }

    /** How long one pass through this sequence lasts, in milliseconds. Zero for a sequence with no steps. */
    fun durationMillis(): Double = steps.maxOfOrNull { it.atMillis } ?: 0.0

    /** How long every pass lasts together, honouring [loop] and [loopCount]. */
    fun totalMillis(): Double = durationMillis() * max(1, if (loop) loopCount else 1)

    /** A complete copy, id and provenance included — for snapshotting, not for duplicating. */
    fun copy(): SoundSequence = SoundSequence().also {
        it.id = id
        it.presetId = presetId
        it.presetRevision = presetRevision
        it.customized = customized
        copyDefinition(it)
    }

    /**
     * Copies everything that describes *what this sounds like* into [into], leaving its [id] and its preset
     * provenance alone.
     *
     * The same split [net.trilleo.reminder.model.Reminder.copyDefinition] makes, and load-bearing for the
     * same reason plus one more: this is how a preset update overwrites an untouched sequence, and keeping
     * the id is what stops every `"@id"` in four other configs breaking when it does.
     */
    fun copyDefinition(into: SoundSequence) {
        into.name = name
        into.bpm = bpm
        into.loop = loop
        into.loopCount = loopCount
        into.steps = steps.mapTo(mutableListOf()) { it.copy() }
    }

    companion object {
        const val DEFAULT_BPM: Double = 120.0
        const val BPM_MIN: Double = 40.0
        const val BPM_MAX: Double = 300.0

        /** How many times a looping sequence may play. */
        const val LOOP_MAX: Int = 8

        /**
         * How many steps one sequence may hold.
         *
         * Well past anything an alert needs, and far below what would exhaust the sound engine's channel
         * pool even if every step landed on the same instant.
         */
        const val MAX_STEPS: Int = 128
    }
}
