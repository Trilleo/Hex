package net.trilleo.title.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Telling a title's codes from its words.
 *
 * The whole editor rests on this. [TitleFormat.merge] decides whether a line styles the alert's message or
 * replaces it, and getting that wrong in either direction is silent: a title that swallowed its own message, or
 * one that printed `&c&l` at the player. [TitleFormat.leadingCodes] decides which preset the row claims to be
 * showing, which is wrong just as quietly.
 */
class TitleFormatTest {

    // ---- reading codes -----------------------------------------------------------------------------------

    @Test
    fun `codes are not words`() {
        assertEquals("BOSS", TitleFormat.visibleText("&c&lBOSS"))
        assertEquals("BOSS", TitleFormat.visibleText("&#FF5555BOSS"))
        assertEquals("red then yellow", TitleFormat.visibleText("&cred then &eyellow"))
        assertEquals("", TitleFormat.visibleText("&c&l"))
    }

    @Test
    fun `an unknown code is an ampersand somebody typed`() {
        // The same rule Chroma applies to item names: a region genuinely called "Rock & Roll" keeps its
        // ampersand rather than losing a letter to a code that does not exist.
        assertEquals("Rock & Roll", TitleFormat.visibleText("Rock & Roll"))
        assertEquals("&q is not a code", TitleFormat.visibleText("&q is not a code"))
    }

    @Test
    fun `a half-typed hex code is not a code yet`() {
        // What the box holds on the way to `&#FF5555`. Treated as a code it would eat the characters after it.
        assertEquals("&#FF55", TitleFormat.visibleText("&#FF55"))
    }

    @Test
    fun `both markers are accepted`() {
        // A title pasted from somewhere else arrives with section signs in it.
        assertEquals("BOSS", TitleFormat.visibleText("§c§lBOSS"))
    }

    @Test
    fun `hasText ignores codes and whitespace`() {
        assertFalse(TitleFormat.hasText(""))
        assertFalse(TitleFormat.hasText("&c&l"))
        assertFalse(TitleFormat.hasText("&c   "))
        assertTrue(TitleFormat.hasText("&cx"))
    }

    // ---- leading codes -----------------------------------------------------------------------------------

    @Test
    fun `leading codes stop at the first word`() {
        assertEquals("&c&l", TitleFormat.leadingCodes("&c&lBOSS &einbound"))
        assertEquals("&#FF5555", TitleFormat.leadingCodes("&#FF5555BOSS"))
        assertEquals("", TitleFormat.leadingCodes("BOSS &cinbound"))
        assertEquals("&c&l", TitleFormat.leadingCodes("&c&l"))
    }

    @Test
    fun `a preset replaces only the codes it put there`() {
        // The `&e` mid-line is the player saying "and this bit in yellow". Re-applying a preset to change the
        // sound must not flatten it.
        val relabelled = TitleFormat.withLeadingCodes("&c&lBOSS &einbound", "&6&l")
        assertEquals("&6&lBOSS &einbound", relabelled)
    }

    @Test
    fun `applying a preset twice does not stack its codes`() {
        var line = TitleFormat.withLeadingCodes("BOSS", "&c&l")
        line = TitleFormat.withLeadingCodes(line, "&c&l")
        assertEquals("&c&lBOSS", line)
    }

    // ---- the merge rule ----------------------------------------------------------------------------------

    @Test
    fun `codes alone dress the alert's own message`() {
        assertEquals("&c&lcookie ran out", TitleFormat.merge("&c&l", "cookie ran out"))
    }

    @Test
    fun `words of its own replace the message`() {
        assertEquals("&c&lBOSS", TitleFormat.merge("&c&lBOSS", "cookie ran out"))
    }

    @Test
    fun `a line nobody has touched is the message, plainly`() {
        assertEquals("cookie ran out", TitleFormat.merge("", "cookie ran out"))
    }

    @Test
    fun `a blank message leaves a codes-only line with nothing to say`() {
        // Which is what stops Titles.show drawing a flash of nothing: it drops a line with no visible text.
        assertFalse(TitleFormat.hasText(TitleFormat.merge("&c&l", "")))
    }
}
