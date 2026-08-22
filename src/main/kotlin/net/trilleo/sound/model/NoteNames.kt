package net.trilleo.sound.model

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The note block scale, for showing a step's pitch as a note rather than as a number.
 *
 * A note block plays twenty-five pitches, F#3 to F#5, and Minecraft reaches them by scaling playback speed:
 * note `n` plays at `2^((n - 12) / 12)`, so note 12 is unscaled and the ends of the range are exactly half and
 * double speed. That is the whole reason [net.trilleo.sound.model.SoundStep.PITCH_MIN] and `PITCH_MAX` are
 * 0.5 and 2.0 — the mod's pitch range and the note block's range are the same range, and this object is only
 * a second way of spelling it. No conversion is lossy in the direction that matters: a pitch chosen as a note
 * round-trips exactly, and a pitch dragged on the plain slider snaps to the nearest note only when it is
 * *shown* as one.
 *
 * Only note block sounds are labelled this way. Every other sound in the game is a recording rather than a
 * tuned instrument, so calling 1.4 "A#4" there would be arithmetic dressed up as music.
 *
 * Note names are not language — they are the same twelve symbols in every locale, exactly like an item id or
 * a key name — so they are built as plain strings and shown with `Component.literal`.
 */
object NoteNames {

    /** How many notes a note block has: F#3 (0) through F#5 (24). */
    const val COUNT: Int = 25

    /** The note that plays unscaled, i.e. at pitch 1.0. */
    const val MIDDLE: Int = 12

    /** The id prefix every note block sound shares. */
    const val NOTE_BLOCK_PREFIX: String = "minecraft:block.note_block."

    /**
     * The twelve names, starting at F# because that is where a note block's range starts.
     *
     * Sharps rather than flats throughout, which is what the community's note block tooling uses and what
     * makes the octave arithmetic in [nameOf] a single division.
     */
    private val NAMES = listOf("F#", "G", "G#", "A", "A#", "B", "C", "C#", "D", "D#", "E", "F")

    /** Whether [soundId] names a note block sound, and therefore has notes rather than a raw pitch. */
    fun isNoteBlock(soundId: String): Boolean = soundId.trim().startsWith(NOTE_BLOCK_PREFIX)

    /** The nearest note to [pitch], clamped into the playable range. */
    fun noteOf(pitch: Double): Int {
        if (!pitch.isFinite() || pitch <= 0.0) return MIDDLE
        // log2 via the natural log: Kotlin has no log2 for Double on every target, and this is exact enough
        // for a value that is about to be rounded to a whole note anyway.
        val note = (MIDDLE + 12.0 * ln(pitch) / ln(2.0)).roundToInt()
        return note.coerceIn(0, COUNT - 1)
    }

    /** The pitch note [note] plays at. Exactly 1.0 at [MIDDLE], 0.5 at 0 and 2.0 at 24. */
    fun pitchOf(note: Int): Double = 2.0.pow((note.coerceIn(0, COUNT - 1) - MIDDLE) / 12.0)

    /**
     * [note] as it is written, e.g. `"F#3"`, `"C4"`, `"F#5"`.
     *
     * The octave steps up between F and F#, not between B and C, because the scale starts on F#: note 0 is
     * F#3, note 6 is C4 and note 12 is F#4, so the boundary sits six notes in. Adding 6 before dividing is
     * what moves it there.
     */
    fun nameOf(note: Int): String {
        val n = note.coerceIn(0, COUNT - 1)
        return NAMES[n % 12] + (3 + (n + 6) / 12)
    }

    /** [pitch] as a note name — the two steps above in one, for a label. */
    fun nameOfPitch(pitch: Double): String = nameOf(noteOf(pitch))
}
