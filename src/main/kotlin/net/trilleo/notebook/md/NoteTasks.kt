package net.trilleo.notebook.md

/**
 * Ticking a check box off in a note, as an edit to the note's text.
 *
 * The reading screen shows a rendered note, but what it changes is still the markdown — there is no second
 * representation to keep in step, so a box ticked while reading is the same edit as typing the `x` yourself, and
 * a note left open in an editor elsewhere sees it as an ordinary change.
 *
 * A pure string function on purpose: this is the one piece of the reading screen that alters what the player
 * wrote, so it is the piece that is worth testing without a client to run it in.
 */
object NoteTasks {

    /**
     * Flips the check box on [line] of [source], or returns [source] unchanged when that line is not a task.
     *
     * Only the box changes. The indent, the bullet character, the spacing and the text are all preserved
     * exactly, because a click on a check box is not an invitation to reformat someone's list.
     */
    fun toggle(source: String, line: Int): String {
        val lines = source.split('\n')
        if (line !in lines.indices) return source

        val toggled = toggleLine(lines[line]) ?: return source
        return lines.toMutableList().also { it[line] = toggled }.joinToString("\n")
    }

    /** Whether [line] of [source] is a task line, and so whether a click on it does anything. */
    fun isTask(source: String, line: Int): Boolean {
        val lines = source.split('\n')
        return line in lines.indices && BOX.containsMatchIn(lines[line])
    }

    private fun toggleLine(line: String): String? {
        val match = BOX.find(line) ?: return null
        // The single character between the brackets, and nothing else on the line.
        val box = match.groups[1]?.range ?: return null
        val done = match.groupValues[1].isNotBlank()
        return line.replaceRange(box, if (done) " " else "x")
    }

    /**
     * The box itself, anchored to the start of the line so that `[x]` written mid-sentence is left alone.
     *
     * `[X]`, and anything else someone has put in the box, counts as ticked — the same rule the parser uses,
     * so what the preview draws and what a click does can never disagree.
     */
    private val BOX = Regex("^\\s*[-*+]\\s+\\[(.)]")
}
