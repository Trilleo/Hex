package net.trilleo.title.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * The repairs and the preset matching a title does on its own, before anything of Minecraft is involved.
 *
 * Worth pinning down because both halves are load-bearing in a way that fails silently. A normalizer that let a
 * zero through would ship a title that flashes past unread, and a `matches` that was too strict would leave the
 * preset row reading **Custom** immediately after a preset was chosen — neither of which throws, and neither of
 * which is obvious from the code.
 */
class TitleSpecTest {

    private fun spec(block: TitleSpec.() -> Unit = {}) = TitleSpec().apply(block)

    // ---- normalizing -------------------------------------------------------------------------------------

    @Test
    fun `an absent stay time falls back rather than flashing past`() {
        // Zero is how GSON leaves a key a file written before this field existed never had. Taken literally it
        // is a title drawn only during its fades, which reads as broken rather than as fast.
        val migrated = spec { staySeconds = 0.0 }
        migrated.normalize()
        assertEquals(TitleSpec.DEFAULT_STAY, migrated.staySeconds)
    }

    @Test
    fun `a fade of zero is kept, because it is a real answer`() {
        // Unlike the stay time: "appear instantly" is something a player asks for, and the slider offers it.
        val instant = spec { fadeInSeconds = 0.0; fadeOutSeconds = 0.0 }
        instant.normalize()
        assertEquals(0.0, instant.fadeInSeconds)
        assertEquals(0.0, instant.fadeOutSeconds)
    }

    @Test
    fun `every number is bounded`() {
        val wild = spec {
            fadeInSeconds = 900.0
            staySeconds = 9000.0
            fadeOutSeconds = -4.0
            pitch = 12.0
            volume = -1.0
        }
        wild.normalize()

        assertEquals(TitleSpec.FADE_MAX, wild.fadeInSeconds)
        assertEquals(TitleSpec.STAY_MAX, wild.staySeconds)
        assertEquals(TitleSpec.FADE_MIN, wild.fadeOutSeconds)
        assertEquals(TitleSpec.PITCH_MAX, wild.pitch)
        assertEquals(TitleSpec.VOLUME_MIN, wild.volume)
    }

    @Test
    fun `a value no slider can produce is replaced rather than propagated`() {
        val broken = spec { staySeconds = Double.NaN; pitch = Double.POSITIVE_INFINITY }
        broken.normalize()

        assertEquals(TitleSpec.DEFAULT_STAY, broken.staySeconds)
        assertEquals(1.0, broken.pitch)
    }

    @Test
    fun `colours are canonicalised so two settings holding one colour hold one string`() {
        val typed = spec { title.color = "ff8800"; subtitle.color = "  CHROMA " }
        typed.normalize()

        assertEquals("#FF8800", typed.title.color)
        assertEquals("chroma", typed.subtitle.color)
    }

    // ---- copying -----------------------------------------------------------------------------------------

    @Test
    fun `a copy shares no line with its original`() {
        // Duplicating a reminder duplicates its title; a shallow copy would have the two editing one another.
        val original = spec { title.color = "#FF5555"; title.bold = true; subtitle.text = "beneath" }
        val duplicate = original.copy()

        duplicate.title.color = "#55FF55"
        duplicate.subtitle.text = "changed"

        assertEquals("#FF5555", original.title.color)
        assertEquals("beneath", original.subtitle.text)
        assertTrue(duplicate.title.bold)
    }

    // ---- presets -----------------------------------------------------------------------------------------

    @Test
    fun `a preset is recognised in the spec it just wrote`() {
        TitlePreset.entries.filter { it != TitlePreset.CUSTOM }.forEach { preset ->
            val applied = spec()
            preset.applyTo(applied)
            assertEquals(preset, TitlePreset.of(applied), "$preset should recognise its own work")
        }
    }

    @Test
    fun `nudging one setting drops the spec back to Custom`() {
        val warned = spec()
        TitlePreset.WARNING.applyTo(warned)
        warned.title.italic = true

        assertEquals(TitlePreset.CUSTOM, TitlePreset.of(warned))
    }

    @Test
    fun `a preset leaves the text and the timings alone`() {
        // The two things already decided by the time a preset is reached: re-applying one to tune a colour must
        // not retype the message or reset a dwell time someone chose deliberately.
        val tuned = spec {
            subtitle.text = "written by hand"
            staySeconds = 9.0
            fadeInSeconds = 0.0
        }
        TitlePreset.ALERT.applyTo(tuned)

        assertEquals("written by hand", tuned.subtitle.text)
        assertEquals(9.0, tuned.staySeconds)
        assertEquals(0.0, tuned.fadeInSeconds)
    }

    @Test
    fun `Custom writes nothing at all`() {
        val untouched = spec { title.color = "#123456"; title.underline = true; sound = "hex:nothing" }
        TitlePreset.CUSTOM.applyTo(untouched)

        assertEquals("#123456", untouched.title.color)
        assertTrue(untouched.title.underline)
        assertEquals("hex:nothing", untouched.sound)
    }

    @Test
    fun `re-applying a preset clears the styles a previous one left behind`() {
        // WARNING is bold and INFO is not; without clearStyles the bolding would survive the switch and the
        // preset row would immediately read Custom for a spec nobody had touched.
        val switched = spec()
        TitlePreset.WARNING.applyTo(switched)
        TitlePreset.INFO.applyTo(switched)

        assertFalse(switched.title.bold)
        assertEquals(TitlePreset.INFO, TitlePreset.of(switched))
    }
}
