package net.trilleo.sound.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The note block scale, which is arithmetic pretending to be music and therefore easy to get subtly wrong.
 *
 * Two things fail silently here. An off-by-one in the octave boundary would label every note correctly except
 * around F/F#, where it would be out by an octave — and nothing would throw. And a rounding error in
 * [NoteNames.noteOf] would make a note chosen in the editor come back as its neighbour on the next redraw,
 * which reads as the slider drifting on its own.
 */
class NoteNamesTest {

    // ---- the scale itself --------------------------------------------------------------------------------

    @Test
    fun `the middle note plays unscaled`() {
        // The whole scale hangs off this: note 12 is the sound as recorded, and everything else is that
        // sound sped up or slowed down.
        assertEquals(1.0, NoteNames.pitchOf(NoteNames.MIDDLE))
    }

    @Test
    fun `the ends of the range are half and double speed`() {
        // Which is also why SoundStep bounds pitch at 0.5 and 2.0 — the mod's range and the note block's are
        // the same range, and piano mode needs no second scale because of it.
        assertEquals(0.5, NoteNames.pitchOf(0))
        assertEquals(2.0, NoteNames.pitchOf(NoteNames.COUNT - 1))
    }

    @Test
    fun `every note survives a round trip through its pitch`() {
        for (note in 0 until NoteNames.COUNT) {
            assertEquals(note, NoteNames.noteOf(NoteNames.pitchOf(note)), "note $note")
        }
    }

    @Test
    fun `a pitch between two notes rounds to the nearer one`() {
        // A pitch dragged on the plain slider still has to name a note when the editor shows it as one.
        val justAboveMiddle = NoteNames.pitchOf(NoteNames.MIDDLE) * 1.01
        assertEquals(NoteNames.MIDDLE, NoteNames.noteOf(justAboveMiddle))
    }

    @Test
    fun `a pitch outside the range is clamped rather than wrapping`() {
        assertEquals(0, NoteNames.noteOf(0.1))
        assertEquals(NoteNames.COUNT - 1, NoteNames.noteOf(8.0))
    }

    @Test
    fun `a nonsensical pitch falls back to the middle`() {
        // A hand-edited file can hold a zero or a NaN; log2 of either is not a note.
        assertEquals(NoteNames.MIDDLE, NoteNames.noteOf(0.0))
        assertEquals(NoteNames.MIDDLE, NoteNames.noteOf(-1.0))
        assertEquals(NoteNames.MIDDLE, NoteNames.noteOf(Double.NaN))
    }

    // ---- naming ------------------------------------------------------------------------------------------

    @Test
    fun `the octave turns over at F sharp, not at C`() {
        // The scale starts on F#3, so the boundary sits six notes in. Getting this wrong mislabels only the
        // notes around it, which is exactly the kind of error that survives a casual look at the editor.
        assertEquals("F#3", NoteNames.nameOf(0))
        assertEquals("C4", NoteNames.nameOf(6))
        assertEquals("F4", NoteNames.nameOf(11))
        assertEquals("F#4", NoteNames.nameOf(12))
        assertEquals("C5", NoteNames.nameOf(18))
        assertEquals("F#5", NoteNames.nameOf(24))
    }

    @Test
    fun `naming a pitch is naming its note`() {
        assertEquals("F#4", NoteNames.nameOfPitch(1.0))
    }

    // ---- when notes apply at all -------------------------------------------------------------------------

    @Test
    fun `only note block sounds are tuned`() {
        // Everything else in the game is a recording rather than an instrument, and calling 1.4 "A#4" there
        // would be arithmetic dressed up as music.
        assertTrue(NoteNames.isNoteBlock("minecraft:block.note_block.pling"))
        assertTrue(NoteNames.isNoteBlock("minecraft:block.note_block.bell"))
        assertFalse(NoteNames.isNoteBlock("minecraft:ui.button.click"))
        assertFalse(NoteNames.isNoteBlock("minecraft:block.amethyst_block.chime"))
        assertFalse(NoteNames.isNoteBlock(""))
    }
}
