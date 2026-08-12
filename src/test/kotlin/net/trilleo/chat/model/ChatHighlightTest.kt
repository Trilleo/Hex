package net.trilleo.chat.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * The matching decisions a rule makes on its own, before anything of Minecraft is involved.
 *
 * [ChatHighlight] holds one component-building method, and nothing here calls it — the rest is string and enum
 * comparison, which is exactly the part worth pinning down: these three predicates are consulted on every chat
 * line, and each of them has an edge that is easy to get quietly wrong.
 */
class ChatHighlightTest {

    private fun rule(block: ChatHighlight.() -> Unit) = ChatHighlight().apply(block)

    // ---- text --------------------------------------------------------------------------------------------

    @Test
    fun `matching ignores capitals unless asked not to`() {
        val loose = rule { text = "Drop" }
        assertEquals(10, loose.indexIn("you got a drop!", 0))

        val strict = rule { text = "Drop"; caseSensitive = true }
        assertEquals(-1, strict.indexIn("you got a drop!", 0))
        assertEquals(10, strict.indexIn("you got a Drop!", 0))
    }

    @Test
    fun `an empty text never matches`() {
        // A half-typed rule in the editor must not claim every line in chat for the keystroke between d and drop.
        assertEquals(-1, rule { text = "" }.indexIn("anything at all", 0))
    }

    @Test
    fun `searching resumes past the previous hit`() {
        val r = rule { text = "ha" }
        val first = r.indexIn("ha ha ha", 0)
        val second = r.indexIn("ha ha ha", first + 2)

        assertEquals(0, first)
        assertEquals(3, second)
        assertEquals(6, r.indexIn("ha ha ha", second + 2))
        assertEquals(-1, r.indexIn("ha ha ha", 8))
    }

    // ---- islands -----------------------------------------------------------------------------------------

    @Test
    fun `no island means anywhere, including an island that is not known yet`() {
        val r = rule { islands = "" }

        assertTrue(r.matchesIsland("hub"))
        assertTrue(r.matchesIsland(null))
    }

    @Test
    fun `a restricted rule takes any island in its list`() {
        val r = rule { islands = ChatHighlight.normalizeIslands("Hub, Dwarven Mines") }

        assertTrue(r.matchesIsland("hub"))
        assertTrue(r.matchesIsland("dwarven mines"))
        assertFalse(r.matchesIsland("the end"))
    }

    @Test
    fun `a restricted rule declines an island it cannot see`() {
        // Guessing wrong here means painting chat somewhere the player deliberately excluded, and "not yet"
        // resolves within a second or two anyway.
        assertFalse(rule { islands = "hub" }.matchesIsland(null))
    }

    @Test
    fun `normalizing islands folds case, trims, drops blanks and repeats`() {
        assertEquals("hub, dwarven mines", ChatHighlight.normalizeIslands("  HUB , ,Dwarven Mines,hub  "))
        assertEquals("", ChatHighlight.normalizeIslands("  ,  ,"))
        assertEquals("", ChatHighlight.normalizeIslands(""))
    }

    @Test
    fun `a normalized value survives being normalized again`() {
        // The editor writes through this on every keystroke and the config normalizer runs it again on load, so
        // it has to be idempotent or a stored value would drift from a typed one.
        val once = ChatHighlight.normalizeIslands("Hub , The End")

        assertEquals(once, ChatHighlight.normalizeIslands(once))
    }

    // ---- channels ----------------------------------------------------------------------------------------

    @Test
    fun `ANY takes every channel, and takes a line that has none`() {
        val r = rule { channel = ChatChannel.ANY }

        assertTrue(r.matchesChannel(ChatChannel.PARTY))
        assertTrue(r.matchesChannel(ChatChannel.ALL))
        // A server broadcast belongs to no channel; ANY still wants it, which is what makes it the useful default.
        assertTrue(r.matchesChannel(null))
    }

    @Test
    fun `a scoped rule takes only its own channel`() {
        val r = rule { channel = ChatChannel.PARTY }

        assertTrue(r.matchesChannel(ChatChannel.PARTY))
        assertFalse(r.matchesChannel(ChatChannel.GUILD))
        assertFalse(r.matchesChannel(null))
    }
}
