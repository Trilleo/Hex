package net.trilleo.notebook.md

/**
 * A note's markdown source, read as a list of blocks — the structural half of the preview.
 *
 * Deliberately close to a *line* parser rather than a real markdown implementation. Two reasons, and they are
 * the same reason twice: the preview sits beside the source and is redrawn as it is typed, so a parse must be
 * cheap and, far more importantly, must never disagree with the line the caret is on. A note taker writing
 *
 * ```
 * check the boss room
 * bring an aspect of the end
 * ```
 *
 * means two lines and would be baffled to see one paragraph, which is what a conforming parser would give them.
 * So a line is a block, blank lines are kept as spacing, and nothing here reflows anything.
 *
 * Tables are the one construct that cannot obey that, because a table *is* several lines — a header, a rule of
 * dashes, and the rows. They are gathered into a single [Table] block, which is what lets the preview line its
 * columns up. Everything else stays one block per line.
 *
 * What is understood: ATX headings, unordered and ordered lists, task list items, block quotes, fenced code
 * blocks, tables and horizontal rules. Everything else is a paragraph, which is the honest answer for a
 * footnote or a definition list this does not model — the text is still readable, just unadorned.
 */
sealed interface NoteBlock {

    /** `# Heading` through `###### Heading`; [level] is 1..6. */
    class Heading(val level: Int, val text: String) : NoteBlock

    /** A line of prose. */
    class Paragraph(val text: String) : NoteBlock

    /**
     * A list item. [number] is null for a bullet, [done] non-null for a task box.
     *
     * [line] is which line of the source this came from — the one thing a block carries that is about the text
     * rather than about the meaning, and it earns its place: it is how the reading screen turns a click on a
     * check box back into the character to change. See [NoteTasks].
     */
    class Item(
        val depth: Int,
        val number: Int?,
        val done: Boolean?,
        val text: String,
        val line: Int = 0,
    ) : NoteBlock

    /** `> quoted`, one line of it. [depth] counts the `>` markers, so a quote inside a quote indents twice. */
    class Quote(val depth: Int, val text: String) : NoteBlock

    /** One line inside a fenced block. Kept per line so the renderer can draw the fence as one slab. */
    class Code(val text: String, val first: Boolean, val last: Boolean) : NoteBlock

    /**
     * A whole table: an optional header row, the body rows, and how each column is aligned.
     *
     * Ragged tables are normal in hand-written markdown — a row with a cell missing, or one cell too many — so
     * [columns] is the width every row is padded or trimmed to rather than something rows have to agree on.
     */
    class Table(
        val header: List<String>?,
        val rows: List<List<String>>,
        val alignments: List<Align>,
    ) : NoteBlock {
        val columns: Int get() = alignments.size
    }

    /** Which way a table column's cells sit in their column, from the `:---:` markers in the rule. */
    enum class Align { LEFT, CENTER, RIGHT }

    /** `---`, `***` or `___` on a line of its own. */
    data object Rule : NoteBlock

    /** An empty line. Kept, because the gaps a writer put in are part of what they wrote. */
    data object Blank : NoteBlock

    companion object {

        /** Reads [source] into blocks. */
        fun parse(source: String): List<NoteBlock> {
            val blocks = mutableListOf<NoteBlock>()
            val lines = source.split('\n').map { it.trimEnd('\r') }
            var fenced = false
            var fenceStart = -1
            var index = 0

            while (index < lines.size) {
                val line = lines[index]

                if (isFence(line)) {
                    if (fenced) {
                        // The closing fence turns the last line already emitted into the block's last line;
                        // neither fence line is itself content.
                        markLastCodeLine(blocks)
                    } else {
                        fenceStart = blocks.size
                    }
                    fenced = !fenced
                    index++
                    continue
                }

                if (fenced) {
                    blocks += Code(line, first = blocks.size == fenceStart, last = false)
                    index++
                    continue
                }

                val table = readTable(lines, index)
                if (table != null) {
                    blocks += table.block
                    index = table.end
                    continue
                }

                blocks += parseLine(line, index)
                index++
            }

            // An unclosed fence is text someone is in the middle of typing, not an error worth complaining
            // about — the run simply ends where the note does.
            if (fenced) markLastCodeLine(blocks)
            return blocks
        }

        private fun parseLine(line: String, index: Int): NoteBlock {
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
                return Item(depth, null, task.groupValues[1].isNotBlank(), task.groupValues[2], index)
            }

            val bullet = BULLET.matchEntire(trimmed)
            if (bullet != null) return Item(depth, null, null, bullet.groupValues[1], index)

            val ordered = ORDERED.matchEntire(trimmed)
            if (ordered != null) {
                return Item(depth, ordered.groupValues[1].toIntOrNull() ?: 1, null, ordered.groupValues[2], index)
            }

            return Paragraph(trimmed)
        }

        // ---- tables ------------------------------------------------------------------------------------

        private class TableRun(val block: Table, val end: Int)

        /**
         * A table starting at [start], or null.
         *
         * The rule of dashes is what makes a table a table: a line with pipes in it is far more likely to be
         * prose about a command than a one-row table, and markdown itself asks for the rule, so requiring it
         * costs nothing and stops `use /warp | it is faster` from being drawn as a grid.
         */
        private fun readTable(lines: List<String>, start: Int): TableRun? {
            if (start + 1 >= lines.size) return null
            if (!looksLikeRow(lines[start])) return null
            val alignments = readAlignments(lines[start + 1]) ?: return null

            val header = cellsOf(lines[start])
            val rows = mutableListOf<List<String>>()
            var index = start + 2
            while (index < lines.size && looksLikeRow(lines[index])) {
                rows += cellsOf(lines[index])
                index++
            }

            val columns = maxOf(alignments.size, header.size)
            return TableRun(
                Table(
                    header = fit(header, columns),
                    rows = rows.map { fit(it, columns) },
                    alignments = List(columns) { alignments.getOrElse(it) { Align.LEFT } },
                ),
                index,
            )
        }

        private fun looksLikeRow(line: String): Boolean = line.contains('|') && line.isNotBlank()

        /** The alignment of each column from a rule line such as `|:---|---:|`, or null when it is not one. */
        private fun readAlignments(line: String): List<Align>? {
            if (!looksLikeRow(line)) return null
            val cells = cellsOf(line)
            if (cells.isEmpty()) return null
            return cells.map { cell ->
                val match = ALIGNMENT.matchEntire(cell) ?: return null
                val left = match.groupValues[1].isNotEmpty()
                val right = match.groupValues[2].isNotEmpty()
                when {
                    left && right -> Align.CENTER
                    right -> Align.RIGHT
                    else -> Align.LEFT
                }
            }
        }

        /**
         * The cells of one row.
         *
         * The outer pipes are optional in markdown and dropping them is what most people type, so a leading or
         * trailing one is removed rather than producing an empty cell at each end. `\|` is an escaped pipe and
         * stays inside its cell — that is the only way to put one in a table.
         */
        private fun cellsOf(line: String): List<String> {
            val trimmed = line.trim().removePrefix("|").removeSuffix("|")
            val cells = mutableListOf<String>()
            val cell = StringBuilder()
            var i = 0
            while (i < trimmed.length) {
                val c = trimmed[i]
                when {
                    c == '\\' && i + 1 < trimmed.length && trimmed[i + 1] == '|' -> {
                        cell.append('|')
                        i += 2
                    }

                    c == '|' -> {
                        cells += cell.toString().trim()
                        cell.setLength(0)
                        i++
                    }

                    else -> {
                        cell.append(c)
                        i++
                    }
                }
            }
            cells += cell.toString().trim()
            return cells
        }

        /** Pads a ragged row out to [columns], or trims one that runs long. */
        private fun fit(cells: List<String>, columns: Int): List<String> =
            List(columns) { cells.getOrElse(it) { "" } }

        // ---- line shapes -------------------------------------------------------------------------------

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
        private val ALIGNMENT = Regex("^(:?)-{1,}(:?)$")
    }
}
