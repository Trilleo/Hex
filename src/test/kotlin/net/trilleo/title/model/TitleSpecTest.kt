package net.trilleo.title.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The repairs and the preset matching a title does on its own, before anything of Minecraft is involved.
 *
 * Worth pinning down because both halves fail silently. A normalizer that let a zero through would ship a title
 * that flashes past unread, and a `matches` that was too strict would leave the preset row reading **Custom**
 * immediately after a preset was chosen — neither of which throws, and neither of which is obvious from the
 * code.
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
    fun `the lines are left exactly as written`() {
        // Deliberately not canonicalised: the text is the player's, codes and all, and a normalizer that tidied
        // it would fight the box they typed it into.
        val written = spec { title = "&c&lBOSS &einbound"; subtitle = "  spaced  " }
        written.normalize()

        assertEquals("&c&lBOSS &einbound", written.title)
        assertEquals("  spaced  ", written.subtitle)
    }

    // ---- copying -----------------------------------------------------------------------------------------

    @Test
    fun `a copy is independent of its original`() {
        val original = spec { title = "&c&l"; subtitle = "beneath"; staySeconds = 8.0 }
        val duplicate = original.copy()

        duplicate.title = "&a&l"
        duplicate.subtitle = "changed"
        duplicate.staySeconds = 1.0

        assertEquals("&c&l", original.title)
        assertEquals("beneath", original.subtitle)
        assertEquals(8.0, original.staySeconds)
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
    fun `a preset is still recognised over words the player typed`() {
        // The words are not part of the preset, so typing them must not make the row forget which one is on.
        val warned = spec { title = "BOSS"; subtitle = "get to the platform" }
        TitlePreset.WARNING.applyTo(warned)

        assertEquals("&6&lBOSS", warned.title)
        assertEquals("&eget to the platform", warned.subtitle)
        assertEquals(TitlePreset.WARNING, TitlePreset.of(warned))
    }

    @Test
    fun `recolouring a line drops the spec back to Custom`() {
        val warned = spec()
        TitlePreset.WARNING.applyTo(warned)
        warned.title = "&d" + warned.title

        assertEquals(TitlePreset.CUSTOM, TitlePreset.of(warned))
    }

    @Test
    fun `a preset leaves the words and the timings alone`() {
        val tuned = spec {
            subtitle = "written by hand"
            staySeconds = 9.0
            fadeInSeconds = 0.0
        }
        TitlePreset.ALERT.applyTo(tuned)

        assertEquals("written by hand", TitleFormat.stripLeadingCodes(tuned.subtitle))
        assertEquals(9.0, tuned.staySeconds)
        assertEquals(0.0, tuned.fadeInSeconds)
    }

    @Test
    fun `Custom writes nothing at all`() {
        val untouched = spec { title = "&9&nkeep me"; sound = "hex:nothing" }
        TitlePreset.CUSTOM.applyTo(untouched)

        assertEquals("&9&nkeep me", untouched.title)
        assertEquals("hex:nothing", untouched.sound)
    }

    @Test
    fun `switching preset replaces the codes rather than stacking them`() {
        val switched = spec { title = "BOSS" }
        TitlePreset.WARNING.applyTo(switched)
        TitlePreset.INFO.applyTo(switched)

        assertEquals("&bBOSS", switched.title)
        assertEquals(TitlePreset.INFO, TitlePreset.of(switched))
    }
}
