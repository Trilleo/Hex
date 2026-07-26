package net.trilleo.notebook.md

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * What the preview understands a line to be.
 *
 * Testable for the same reason [NoteFrontMatterTest] is: [NoteBlock] turns a string into a description of that
 * string and touches nothing of Minecraft. The inline half cannot follow it here — it builds `Component`s — so
 * this covers the half that decides a line's *shape*, which is the half with the interesting edges: a rule and
 * a bullet start with the same character, a task and a bullet with the same two.
 */
class NoteBlocksTest {

    private fun parse(source: String) = NoteBlock.parse(source)

    @Test
    fun `headings carry their level and lose their hashes`() {
        val blocks = parse("# Title\n### Third")

        val first = blocks[0] as NoteBlock.Heading
        val second = blocks[1] as NoteBlock.Heading
        assertEquals(1, first.level)
        assertEquals("Title", first.text)
        assertEquals(3, second.level)
        assertEquals("Third", second.text)
    }

    @Test
    fun `seven hashes is a paragraph, because markdown stops at six`() {
        assertInstanceOf(NoteBlock.Paragraph::class.java, parse("####### too deep")[0])
    }

    @Test
    fun `a task is a task rather than the bullet it also looks like`() {
        val open = parse("- [ ] find the boss")[0] as NoteBlock.Item
        val done = parse("- [x] paid the toll")[0] as NoteBlock.Item

        assertEquals(false, open.done)
        assertEquals("find the boss", open.text)
        assertEquals(true, done.done)
        assertEquals("paid the toll", done.text)
    }

    @Test
    fun `bullets and numbers are told apart, and numbers keep theirs`() {
        val bullet = parse("- mithril")[0] as NoteBlock.Item
        val ordered = parse("3. third stop")[0] as NoteBlock.Item

        assertNull(bullet.number)
        assertNull(bullet.done)
        assertEquals(3, ordered.number)
        assertEquals("third stop", ordered.text)
    }

    @Test
    fun `indentation becomes nesting, two spaces to a level`() {
        val blocks = parse("- top\n  - second\n    - third")

        assertEquals(0, (blocks[0] as NoteBlock.Item).depth)
        assertEquals(1, (blocks[1] as NoteBlock.Item).depth)
        assertEquals(2, (blocks[2] as NoteBlock.Item).depth)
    }

    /** The case the rule exists to get right: `---` is a divider, `- ` is a list, and both start with a dash. */
    @Test
    fun `three dashes is a rule and one dash is a bullet`() {
        assertEquals(NoteBlock.Rule, parse("---")[0])
        assertEquals(NoteBlock.Rule, parse("***")[0])
        assertInstanceOf(NoteBlock.Item::class.java, parse("- not a rule")[0])
    }

    @Test
    fun `quotes count their markers`() {
        val once = parse("> said")[0] as NoteBlock.Quote
        val twice = parse("> > said again")[0] as NoteBlock.Quote

        assertEquals(1, once.depth)
        assertEquals("said", once.text)
        assertEquals(2, twice.depth)
    }

    /** Inside a fence nothing is a heading, a bullet or a rule — that is the whole point of a fence. */
    @Test
    fun `a fenced block is code, markers and all`() {
        val blocks = parse("```\n# not a heading\n- not a bullet\n```")

        assertEquals(2, blocks.size)
        val first = blocks[0] as NoteBlock.Code
        val second = blocks[1] as NoteBlock.Code
        assertEquals("# not a heading", first.text)
        assertTrue(first.first)
        assertEquals("- not a bullet", second.text)
        assertTrue(second.last)
    }

    @Test
    fun `an unclosed fence runs to the end rather than throwing the rest away`() {
        val blocks = parse("```\nstill code\nand this too")

        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it is NoteBlock.Code })
        assertTrue((blocks.last() as NoteBlock.Code).last)
    }

    /** Blank lines are kept: the gaps a writer put in are part of what they wrote. */
    @Test
    fun `blank lines survive as blanks`() {
        val blocks = parse("one\n\ntwo")

        assertEquals(3, blocks.size)
        assertEquals(NoteBlock.Blank, blocks[1])
    }

    @Test
    fun `a line of prose is one paragraph, not merged with the next`() {
        val blocks = parse("check the boss room\nbring an aspect of the end")

        assertEquals(2, blocks.size)
        assertEquals("check the boss room", (blocks[0] as NoteBlock.Paragraph).text)
    }
}
