package net.trilleo.skyblock

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * The lines Hypixel writes about the current profile. A wrong answer here uploads one profile's shards into
 * another profile's collection on trilleo.net, so the cases that must *not* match matter as much as the ones
 * that must.
 */
class ProfileTextTest {

    @Test
    fun `a profile ID line gives the ID in one canonical form`() {
        assertEquals(
            "691d6a3b23ea454180b5771facc73b16",
            ProfileText.profileId("Profile ID: 691d6a3b-23ea-4541-80b5-771facc73b16"),
        )
        assertEquals(
            "691d6a3b23ea454180b5771facc73b16",
            ProfileText.profileId("Profile ID: 691D6A3B23EA454180B5771FACC73B16"),
        )
    }

    @Test
    fun `anything else is not a profile ID`() {
        assertNull(ProfileText.profileId("Profile ID: not-a-uuid"))
        assertNull(ProfileText.profileId("[MVP+] Someone: Profile ID: 691d6a3b-23ea-4541-80b5-771facc73b16"))
    }

    @Test
    fun `the profile name comes out of both chat messages without the co-op suffix`() {
        assertEquals("Apple" to ProfileText.NameSource.JOINED, ProfileText.profileNameFromChat("You are playing on profile: Apple"))
        assertEquals(
            "Banana" to ProfileText.NameSource.JOINED,
            ProfileText.profileNameFromChat("You are playing on profile: Banana (Co-op)"),
        )
        assertEquals(
            "Mango" to ProfileText.NameSource.CHANGED,
            ProfileText.profileNameFromChat("Your profile was changed to: Mango"),
        )
        assertNull(ProfileText.profileNameFromChat("Player: You are playing on profile: Apple x"))
    }

    @Test
    fun `the player list widget gives the name and the mode symbol`() {
        assertEquals("Apple" to SkyblockGameMode.IRONMAN, ProfileText.tabProfile("Profile: Apple ♲"))
        assertEquals("Kiwi" to SkyblockGameMode.STRANDED, ProfileText.tabProfile("Profile: Kiwi ☀"))
        assertEquals("Lime" to SkyblockGameMode.BINGO, ProfileText.tabProfile("Profile: Lime Ⓑ"))
        assertEquals("Pear" to SkyblockGameMode.CLASSIC, ProfileText.tabProfile("Profile: Pear"))
        assertNull(ProfileText.tabProfile("Profiles: 3"))
    }

    @Test
    fun `the sidebar title and lines give the game mode`() {
        assertEquals(SkyblockGameMode.IRONMAN, ProfileText.gameMode("SKYBLOCK ♲", emptyList()))
        assertEquals(SkyblockGameMode.STRANDED, ProfileText.gameMode("SKYBLOCK", listOf("☀ Stranded")))
        assertEquals(SkyblockGameMode.BINGO, ProfileText.gameMode("SKYBLOCK Ⓑ", emptyList()))
        assertEquals(SkyblockGameMode.CLASSIC, ProfileText.gameMode("SKYBLOCK", listOf("⏣ Village")))
    }

    @Test
    fun `a guest visit or another game cannot tell the mode`() {
        assertNull(ProfileText.gameMode("SKYBLOCK GUEST", emptyList()))
        assertNull(ProfileText.gameMode("BED WARS", emptyList()))
    }
}
