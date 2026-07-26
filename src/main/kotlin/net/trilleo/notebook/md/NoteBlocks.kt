package net.trilleo.notebook.md

/**
 * A note's markdown source, read as a list of blocks — the structural half of the preview.
 *
 * Deliberately a *line* parser rather than a real markdown implementation. Two reasons, and they are the same
 * reason twice: the preview sits beside the source and is redrawn as it is typed, so a parse must be cheap and,
 * far more importantly, must never disagree with the line the caret is on. A note taker writing
 *
 * ```
 * check the boss room
 * bring an aspect of the end
 * ```
 *
 * means two lines and would be baffled to see one paragraph, which is what a conforming parser would give them.
 * So a line is a block, blank lines are kept as spacing, and nothing here reflows anything.
 *
 * What is understood: ATX headings, unordered and ordered lists, task list items, block quotes, fenced code
 * blocks, and horizontal rules. Everything else is a paragraph, which is the honest answer for a table or a
 * footnote this does not model — the text is still readable, just unadorned.
 */
sealed interface NoteBlock {

    /** `# Heading` through `###### Heading`; [level] is 1..6. */
    class Heading(val level: Int, val text: String) : NoteBlock

    /** A line of prose. */
    class Paragraph(val text: String) : NoteBlock

    /** A list item. [number] is null for a bullet, [done] non-null for a task box. */
    class Item(val depth: Int, val number: Int?, val done: Boolean?, val text: String) : NoteBlock

    /** `> quoted`, one line of it. [depth] counts the `>` markers, so a quote inside a quote indents twice. */
    class Quote(val depth: Int, val text: String) : NoteBlock

    /** One line inside a fenced block. Kept per line so the renderer can draw the fence as one slab. */
    class Code(val text: String, val first: Boolean, val last: Boolean) : NoteBlock

    /** `---`, `***` or `___` on a line of its own. */
    data object Rule : NoteBlock

    /** An empty line. Kept, because the gaps a writer put in are part of what they wrote. */
    data object Blank : NoteBlock

    companion object {

        /** Reads [source] into blocks, one per line. */
        fun parse(source: String): List<NoteBlock> {
            val blocks = mutableListOf<NoteBlock>()
            val lines = source.split('\n')
            var fenced = false
            var fenceStart = -1

            lines.forEachIndexed { index, raw ->
                val line = raw.trimEnd('\r')

                if (isFence(line)) {
                    if (fenced) {
                        // The closing fence turns the last line already emitted into the block's last line;
                        // neither fence line is itself content.
                        markLastCodeLine(blocks)
                    } else {
                        fenceStart = blocks.size
                    }
                    fenced = !fenced
                    return@forEachIndexed
                }

                if (fenced) {
                    blocks += Code(line, first = blocks.size == fenceStart, last = false)
                    return@forEachIndexed
                }

                blocks += parseLine(line)
            }

            // An unclosed fence is text someone is in the middle of typing, not an error worth complaining
            // about — the run simply ends where the note does.
            if (fenced) markLastCodeLine(blocks)
            return blocks
        }

        private fun parseLine(line: String): NoteBlock {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return Blank
            if (isRule(trimmed)) return Rule

            val heading = HEADING.matchEntire(trimmed)
            if (heading != null) {
                return Heading(heading.groupValues[1].length, heading.groupValues[2])
            }

            val quote = QUOTE.matchEntire(trimmed)
            if (quote != null) {
                val markers = quote.groupValues[1].count { it == '>' }
                return Quote(markers, quote.groupValues[2])
            }

            val depth = indentOf(line)

            val task = TASK.matchEntire(trimmed)
            if (task != null) {
                return Item(depth, null, task.groupValues[1].lowercase() != " ", task.groupValues[2])
            }

            val bullet = BULLET.matchEntire(trimmed)
            if (bullet != null) return Item(depth, null, null, bullet.groupValues[1])

            val ordered = ORDERED.matchEntire(trimmed)
            if (ordered != null) {
                return Item(depth, ordered.groupValues[1].toIntOrNull() ?: 1, null, ordered.groupValues[2])
            }

            return Paragraph(trimmed)
        }

        /** Indent depth in list levels: two spaces or one tab to a level, capped so a stray indent cannot
         * push a line off the pane. */
        private fun indentOf(line: String): Int {
            var spaces = 0
            for (c in line) {
                when (c) {
                    ' ' -> spaces++
                    '\t' -> spaces += INDENT_SPACES
                    else -> return (spaces / INDENT_SPACES).coerceAtMost(MAX_DEPTH)
                }
            }
            return 0
        }

        private fun markLastCodeLine(blocks: MutableList<NoteBlock>) {
            val last = blocks.lastOrNull() as? Code ?: return
            blocks[blocks.lastIndex] = Code(last.text, last.first, last = true)
        }

        private fun isFence(line: String): Boolean {
            val trimmed = line.trim()
            return trimmed.startsWith("```") || trimmed.startsWith("~~~")
        }

        /** Three or more of one rule character, and nothing else. */
        private fun isRule(trimmed: String): Boolean {
            if (trimmed.length < 3) return false
            val first = trimmed[0]
            if (first != '-' && first != '*' && first != '_') return false
            return trimmed.all { it == first }
        }

        private const val INDENT_SPACES = 2
        private const val MAX_DEPTH = 6

        private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
        private val QUOTE = Regex("^((?:>\\s*)+)(.*)$")
        private val TASK = Regex("^[-*+]\\s+\\[(.)]\\s*(.*)$")
        private val BULLET = Regex("^[-*+]\\s+(.*)$")
        private val ORDERED = Regex("^(\\d{1,9})[.)]\\s+(.*)$")
    }
}
