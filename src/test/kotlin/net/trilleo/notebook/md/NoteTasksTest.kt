package net.trilleo.notebook.md

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Ticking a box from the reading screen.
 *
 * This is the one thing that screen changes about a note, and it changes it by rewriting the player's own text,
 * so the cases that matter are all about what it must *not* touch.
 */
class NoteTasksTest {

    @Test
    fun `an open box becomes ticked and back again`() {
        val source = "- [ ] find the boss"

        val ticked = NoteTasks.toggle(source, 0)
        assertEquals("- [x] find the boss", ticked)
        assertEquals(source, NoteTasks.toggle(ticked, 0))
    }

    @Test
    fun `anything in the box counts as ticked, so a capital X unticks`() {
        assertEquals("- [ ] paid the toll", NoteTasks.toggle("- [X] paid the toll", 0))
    }

    /** The click changes one character. Indent, bullet character and spacing are the player's, not ours. */
    @Test
    fun `indentation, bullet and spacing survive untouched`() {
        assertEquals("    *   [x]   deep and oddly spaced", NoteTasks.toggle("    *   [ ]   deep and oddly spaced", 0))
    }

    @Test
    fun `only the named line changes`() {
        val source = "- [ ] first\n- [ ] second\n- [ ] third"

        assertEquals("- [ ] first\n- [x] second\n- [ ] third", NoteTasks.toggle(source, 1))
    }

    @Test
    fun `a line that is not a task is left alone`() {
        val source = "# Heading\njust prose\n- a plain bullet"

        (0..2).forEach { line -> assertEquals(source, NoteTasks.toggle(source, line)) }
    }

    /** A checkbox written mid-sentence is prose about checkboxes, not a checkbox. */
    @Test
    fun `a box in the middle of a line is not a task`() {
        val source = "write - [ ] to start a checklist"

        assertEquals(source, NoteTasks.toggle(source, 0))
        assertFalse(NoteTasks.isTask(source, 0))
    }

    @Test
    fun `a line out of range changes nothing`() {
        val source = "- [ ] only line"

        assertEquals(source, NoteTasks.toggle(source, 5))
        assertEquals(source, NoteTasks.toggle(source, -1))
    }

    @Test
    fun `line endings elsewhere in the note are not disturbed`() {
        val source = "intro\n\n- [ ] task\n\nafter"

        assertEquals("intro\n\n- [x] task\n\nafter", NoteTasks.toggle(source, 2))
    }
}
