package net.trilleo.notebook.md

import net.trilleo.notebook.model.NoteMeta
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * The note file header.
 *
 * This is the one piece of the notebook where a bug loses text the player wrote and has nowhere else, so it is
 * tested rather than trusted. Everything here is pure: [NoteFrontMatter] touches GSON and nothing of Minecraft,
 * which is exactly why it can be tested at all.
 */
class NoteFrontMatterTest {

    private fun meta(title: String = "Mining routes"): NoteMeta =
        NoteMeta().apply { this.title = title; folder = "skyblock/mining"; tags = mutableListOf("mining") }

    @Test
    fun `body survives a round trip byte for byte`() {
        val body = "# Dwarven Mines\n\nMithril spots are marked.\n"
        val (parsed, out) = NoteFrontMatter.read(NoteFrontMatter.write(meta(), body))

        assertEquals(body, out)
        assertEquals("Mining routes", parsed?.title)
        assertEquals("skyblock/mining", parsed?.folder)
        assertEquals(listOf("mining"), parsed?.tags)
    }

    /**
     * The reason the opening fence is `---hex` rather than `---`: a horizontal rule is a real construct in the
     * body, and a plain fence would let the first one in a note truncate it.
     */
    @Test
    fun `a divider in the body is not mistaken for the closing fence`() {
        val body = "Intro\n\n---\n\nAfter the rule\n"
        val (_, out) = NoteFrontMatter.read(NoteFrontMatter.write(meta(), body))
        assertEquals(body, out)
    }

    @Test
    fun `a body that itself starts with the open marker is preserved`() {
        val body = "---hex\nnot a header\n"
        val (parsed, out) = NoteFrontMatter.read(NoteFrontMatter.write(meta(), body))
        assertEquals(body, out)
        assertEquals("Mining routes", parsed?.title)
    }

    @Test
    fun `text with no header is all body`() {
        val text = "# Just markdown\n\nno header here\n"
        val (parsed, out) = NoteFrontMatter.read(text)
        assertNull(parsed)
        assertEquals(text, out)
    }

    /** Failing open, not throwing: a hand edit that breaks the header must cost the colour, not the contents. */
    @Test
    fun `an unterminated header keeps the text`() {
        val text = "---hex\n{ \"title\": \"broken\"\n# note text\n"
        val (parsed, out) = NoteFrontMatter.read(text)
        assertNull(parsed)
        assertTrue(out.contains("# note text"), "body was: $out")
    }

    @Test
    fun `an unparseable header keeps the text`() {
        val text = "---hex\nnot json at all {{{\n---\n# note text\n"
        val (parsed, out) = NoteFrontMatter.read(text)
        assertNull(parsed)
        assertTrue(out.contains("# note text"), "body was: $out")
    }

    @Test
    fun `CRLF is normalised to LF`() {
        val (parsed, out) = NoteFrontMatter.read("---hex\r\n{}\r\n---\r\nline one\r\nline two\r\n")
        assertEquals("line one\nline two\n", out)
        assertTrue(parsed != null)
    }

    @Test
    fun `an empty body round trips`() {
        val (parsed, out) = NoteFrontMatter.read(NoteFrontMatter.write(meta(), ""))
        assertEquals("", out)
        assertEquals("Mining routes", parsed?.title)
    }

    /** Writing what was read must reproduce the input, or editing a note would rewrite its neighbours' bytes. */
    @Test
    fun `read then write is idempotent`() {
        val once = NoteFrontMatter.write(meta(), "# Heading\n\nBody\n")
        val (parsed, body) = NoteFrontMatter.read(once)
        val twice = NoteFrontMatter.write(parsed!!, body)
        assertEquals(once, twice)
    }
}
