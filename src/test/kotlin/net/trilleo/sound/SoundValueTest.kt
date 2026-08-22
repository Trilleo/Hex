package net.trilleo.sound

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The three-case value every sound setting in the mod holds.
 *
 * This is the piece that made sequences a value type rather than a schema change, so the property worth
 * pinning down is the one the whole design rests on: **a sequence reference and a sound id can never be
 * mistaken for each other.** If `@` ever became a legal character in a sound id, or the tests below started
 * disagreeing about which case a string falls into, every reminder in every existing config would be reading
 * a field whose meaning had quietly changed.
 *
 * Only the pure half is covered here. [SoundValue.problem] and [SoundValue.describe] resolve against
 * [SoundConfig] and the sound registry, neither of which exists outside a running game.
 */
class SoundValueTest {

    // ---- telling the three cases apart -------------------------------------------------------------------

    @Test
    fun `an empty value is silence and nothing else`() {
        assertTrue(SoundValue.isNone(""))
        assertTrue(SoundValue.isNone("   "))
        assertFalse(SoundValue.isSequence(""))
    }

    @Test
    fun `a sound id is never read as a sequence`() {
        // The case every existing config file is in. If this ever flipped, every reminder written before
        // sequences existed would start looking for a sequence that was never there.
        assertFalse(SoundValue.isSequence("minecraft:block.note_block.pling"))
        assertFalse(SoundValue.isSequence("somemod:weird.sound"))
        assertNull(SoundValue.sequenceId("minecraft:ui.button.click"))
    }

    @Test
    fun `a sequence reference is read as one, leading space and all`() {
        assertTrue(SoundValue.isSequence("@boss-alarm"))
        assertTrue(SoundValue.isSequence("  @boss-alarm"))
        assertEquals("boss-alarm", SoundValue.sequenceId("@boss-alarm"))
    }

    @Test
    fun `a bare marker names no sequence`() {
        // "@" on its own is a file someone started typing. It must not resolve to a sequence with a blank id.
        assertNull(SoundValue.sequenceId("@"))
    }

    @Test
    fun `forSequence and sequenceId are inverses`() {
        val id = "boss-alarm"
        assertEquals(id, SoundValue.sequenceId(SoundValue.forSequence(id)))
    }

    // ---- canonical spelling ------------------------------------------------------------------------------

    @Test
    fun `a sound id is trimmed and lowercased rather than rejected`() {
        // Every registered id is lowercase, and correcting the case is friendlier than refusing a value that
        // is one shifted keystroke away from right.
        assertEquals("minecraft:ui.button.click", SoundValue.normalize("  Minecraft:UI.Button.Click "))
    }

    @Test
    fun `a sequence reference has its id slugged`() {
        assertEquals("@boss-alarm", SoundValue.normalize("@Boss Alarm"))
    }

    @Test
    fun `normalizing is idempotent`() {
        // Rows compare the stored value against a default to decide whether to enable the reset button, so
        // normalizing twice has to produce the same string as normalizing once.
        listOf("", "@Boss Alarm", "minecraft:block.note_block.pling").forEach { raw ->
            val once = SoundValue.normalize(raw)
            assertEquals(once, SoundValue.normalize(once), raw)
        }
    }

    // ---- slugs -------------------------------------------------------------------------------------------

    @Test
    fun `a slug keeps only what can be typed back`() {
        assertEquals("boss-alarm", SoundValue.slug("Boss Alarm"))
        assertEquals("my_sound-2", SoundValue.slug("my_sound-2"))
    }

    @Test
    fun `runs of punctuation collapse instead of being embalmed`() {
        assertEquals("boss-alarm", SoundValue.slug("Boss  Alarm!!"))
        assertEquals("boss-alarm", SoundValue.slug("--Boss---Alarm--"))
    }

    @Test
    fun `a slug is never blank`() {
        // A blank id could not be referenced, and every blank one would collide with every other.
        assertTrue(SoundValue.slug("").isNotEmpty())
        assertTrue(SoundValue.slug("!!!").isNotEmpty())
        assertTrue(SoundValue.slug("   ").isNotEmpty())
    }

    @Test
    fun `a slug never ends up starting or ending with a dash`() {
        // Including after the length cap, which is applied last and could otherwise cut mid-word.
        val long = SoundValue.slug("a very long sequence name that goes on well past the limit it is given")
        assertFalse(long.startsWith("-"))
        assertFalse(long.endsWith("-"))
    }

    @Test
    fun `a slugged name can never look like a sound id`() {
        // The colon is what would make one, so it must not survive slugging — otherwise "@a:b" could be
        // built from a name and would read as a namespaced id to anything that stripped the marker.
        assertFalse(SoundValue.slug("minecraft:pling").contains(':'))
    }
}
