package net.trilleo.notebook.gui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractScrollArea
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import net.trilleo.notebook.NotebookConfig
import net.trilleo.notebook.md.NoteBlock
import net.trilleo.notebook.md.NoteInline
import net.trilleo.util.Chroma

/** How far one notch of the wheel moves the pane. Top-level because it is read by the superclass constructor. */
private const val SCROLL_RATE = 9

/**
 * The rendered half of the editor: a note as it reads, beside the source it is written in.
 *
 * A scrolling pane rather than a second text box — nothing here is editable, which is what lets it draw things a
 * text box cannot: headings at their own size, a bar down a quote, a slab behind a fenced block, a rule that is
 * an actual line. [NoteBlock] decides what each line *is* and [NoteInline] what it looks like inside; this lays
 * the result out and draws it.
 *
 * ### Laid out once, drawn every frame
 *
 * Wrapping is the expensive part, so [relayout] runs when the text, the width or the line spacing changes and
 * the draw pass only walks rows and skips the ones outside the viewport. The exception is chroma: a flowing
 * colour is a different
 * component every frame, so a note that uses `&z` rebuilds on a timer — [ANIMATION_MS] rather than every frame,
 * which is indistinguishable to the eye and bounds the cost on a long note.
 */
class NotePreview(
    private val font: Font,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
) : AbstractScrollArea(
    x,
    y,
    width,
    height,
    Component.translatable("hex.notebook.editor.preview"),
    AbstractScrollArea.defaultSettings(SCROLL_RATE),
) {

    /** One laid-out line: what to draw, where, and any decoration that belongs to the block it came from. */
    private class Row(
        val top: Int,
        val height: Int,
        val textX: Int,
        val text: FormattedCharSequence?,
        val color: Int,
        val scale: Float = 1.0f,
        val prefix: FormattedCharSequence? = null,
        val prefixX: Int = 0,
        val quoteDepth: Int = 0,
        val slab: Boolean = false,
        val rule: Boolean = false,
        val cells: List<Cell>? = null,
        /** Where the cell text sits inside this row's box — the row's own top padding, on its first line. */
        val cellOffsetY: Int = 0,
        val grid: Grid? = null,
        /** The source line of the check box this row can toggle, when [onToggleTask] is set. */
        val taskLine: Int? = null,
    )

    /** One table cell, already trimmed to its column. */
    private class Cell(val text: FormattedCharSequence, val x: Int)

    /**
     * Where a laid-out line sits in its table, so the borders are drawn once each rather than per line.
     *
     * A table row can be several lines tall once its cells wrap, so [topRule] and [bottomRule] are about the
     * *edges of the table*, not about which row this is — only the first line of the first row draws a top,
     * and only the last line of a row that ends something draws a bottom.
     */
    private class Grid(
        val columnEdges: List<Int>,
        val right: Int,
        val header: Boolean,
        val topRule: Boolean,
        val bottomRule: Boolean,
    )

    /**
     * Called with a source line number when a check box is clicked, or null for a pane that only displays.
     *
     * This is the whole difference between the editor's preview and the reading screen: the same widget, with
     * or without the one interaction that changes the note. See [net.trilleo.notebook.md.NoteTasks].
     */
    var onToggleTask: ((Int) -> Unit)? = null

    private var source: String = ""
    private var blocks: List<NoteBlock> = emptyList()
    private var rows: List<Row> = emptyList()
    private var laidOutHeight: Int = 0

    /** The width the current [rows] were wrapped at, so a resize is noticed without anyone announcing it. */
    private var laidOutAt: Int = -1

    /** The line spacing they were laid out with, so a change to the setting is noticed the same way. */
    private var spacingAt: Int = -1

    /** Whether the note uses chroma, and so has to be rebuilt on the clock rather than only on edits. */
    private var animated: Boolean = false
    private var builtAt: Long = 0L

    /** Replaces the text being previewed. Cheap to call on every keystroke — it returns early when nothing changed. */
    fun setSource(text: String) {
        if (text == source) return
        source = text
        blocks = NoteBlock.parse(text)
        animated = Chroma.uses(text, all = false)
        relayout()
    }

    override fun contentHeight(): Int = laidOutHeight

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, message)
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        updateScrolling(event)

        val toggle = onToggleTask ?: return
        if (isOverScrollbar(event.x(), event.y())) return
        rowAt(event.y())?.taskLine?.let(toggle)
    }

    /** The row under [mouseY], in screen coordinates, or null for the padding between them. */
    private fun rowAt(mouseY: Double): Row? {
        val scroll = scrollAmount().toInt()
        return rows.firstOrNull { row ->
            val top = y + PADDING + row.top - scroll
            mouseY >= top && mouseY < top + row.height
        }
    }

    override fun extractWidgetRenderState(
        extractor: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        if (!visible) return

        val innerWidth = innerWidth()
        if (innerWidth != laidOutAt || NotebookConfig.lineSpacing != spacingAt) relayout()
        if (animated && System.currentTimeMillis() - builtAt >= ANIMATION_MS) relayout()

        extractor.fill(x, y, x + width, y + height, BACKGROUND_COLOR)
        extractor.outline(x, y, width, height, NotebookTheme.DIVIDER_COLOR)

        if (rows.isEmpty()) {
            extractor.centeredText(font, EMPTY_HINT, x + width / 2, y + PADDING, HINT_COLOR)
            return
        }

        val hovered = if (onToggleTask != null && isHovered) rowAt(mouseY.toDouble()) else null

        extractor.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1)
        val scroll = scrollAmount().toInt()
        rows.forEach { row ->
            val top = y + PADDING + row.top - scroll
            // Rows are in order, so everything below the pane is as well — but the loop is cheap and stopping
            // early would need an index dance that buys nothing at these sizes.
            if (top + row.height >= y && top <= y + height) {
                // A tickable line says so before it is clicked, since nothing else on a reading screen reacts
                // to the mouse at all.
                if (row === hovered && row.taskLine != null) {
                    extractor.fill(x + 1, top - 1, x + width - 1, top + row.height - 1, HOVER_COLOR)
                }
                draw(extractor, row, top)
            }
        }
        extractor.disableScissor()

        extractScrollbar(extractor, mouseX, mouseY)
    }

    private fun draw(extractor: GuiGraphicsExtractor, row: Row, top: Int) {
        val left = x + PADDING

        if (row.slab) {
            extractor.fill(left - 2, top - 1, x + width - PADDING, top + row.height - 1, SLAB_COLOR)
        }
        repeat(row.quoteDepth) { depth ->
            val barX = left + depth * QUOTE_INDENT
            extractor.fill(barX, top - 1, barX + QUOTE_BAR, top + row.height - 1, QUOTE_BAR_COLOR)
        }
        if (row.rule) {
            val middle = top + row.height / 2
            extractor.horizontalLine(left, x + width - PADDING, middle, RULE_COLOR)
            return
        }

        row.grid?.let { grid -> drawGrid(extractor, grid, top, row.height) }
        row.cells?.forEach { cell ->
            extractor.text(font, cell.text, left + cell.x, top + row.cellOffsetY, row.color)
        }

        row.prefix?.let { extractor.text(font, it, left + row.prefixX, top, PREFIX_COLOR) }

        val text = row.text ?: return
        if (row.scale == 1.0f) {
            extractor.text(font, text, left + row.textX, top, row.color)
            return
        }
        // Headings are drawn through the matrix so they are genuinely larger rather than merely bolder. The
        // position is divided by the scale because the scale applies to it too.
        extractor.pose().pushMatrix()
        extractor.pose().scale(row.scale)
        extractor.text(
            font,
            text,
            ((left + row.textX) / row.scale).toInt(),
            (top / row.scale).toInt(),
            row.color,
        )
        extractor.pose().popMatrix()
    }

    /**
     * A table row's borders: the lines between its columns, and the rules that close the table off.
     *
     * Drawn per row rather than per table because a table is laid out as rows like everything else — which is
     * what lets it scroll and be clipped without knowing it is a table at all.
     */
    private fun drawGrid(extractor: GuiGraphicsExtractor, grid: Grid, top: Int, height: Int) {
        val left = x + PADDING
        if (grid.header) {
            extractor.fill(left, top - 1, left + grid.right, top + height - 1, SLAB_COLOR)
        }
        grid.columnEdges.drop(1).forEach { edge ->
            extractor.fill(left + edge - 1, top - 1, left + edge, top + height - 1, GRID_COLOR)
        }
        if (grid.topRule) extractor.horizontalLine(left, left + grid.right, top - 1, GRID_COLOR)
        if (grid.bottomRule) extractor.horizontalLine(left, left + grid.right, top + height - 1, GRID_COLOR)
    }

    // ---- layout ----------------------------------------------------------------------------------------

    private fun innerWidth(): Int = (width - PADDING * 2 - scrollbarWidth()).coerceAtLeast(MIN_WIDTH)

    /**
     * How far apart lines sit — the font's own height plus whatever the player has asked for on top.
     *
     * A setting rather than a constant because the right answer depends on the note and the reader: prose read
     * at a glance while playing wants air around it, a long checklist wants to fit on one screen. It is read
     * through here rather than captured, and [spacingAt] notices when it changes.
     */
    private fun lineHeight(): Int = font.lineHeight + NotebookConfig.lineSpacing

    /**
     * Wraps every block into rows.
     *
     * Continuation lines are indented to line up under the first, which is the whole reason a list item is
     * wrapped at a reduced width and drawn at an offset rather than having its bullet baked into the text.
     */
    private fun relayout() {
        val innerWidth = innerWidth()
        val lineHeight = lineHeight()
        val laid = mutableListOf<Row>()
        var top = 0

        blocks.forEach { block ->
            when (block) {
                is NoteBlock.Blank -> top += lineHeight / 2

                is NoteBlock.Rule -> {
                    laid += Row(top, RULE_HEIGHT, 0, null, RULE_COLOR, rule = true)
                    top += RULE_HEIGHT
                }

                is NoteBlock.Heading -> {
                    val scale = headingScale(block.level)
                    val headingHeight = (font.lineHeight * scale).toInt() + NotebookConfig.lineSpacing + 2
                    val text = NoteInline.render(block.text, headingStyle(block.level))
                    val wrapWidth = (innerWidth / scale).toInt().coerceAtLeast(MIN_WIDTH)
                    font.split(text, wrapWidth).forEach { line ->
                        laid += Row(top, headingHeight, 0, line, HEADING_COLOR, scale = scale)
                        top += headingHeight
                    }
                    top += HEADING_GAP
                }

                is NoteBlock.Paragraph -> {
                    val text = NoteInline.render(block.text)
                    font.split(text, innerWidth).forEach { line ->
                        laid += Row(top, lineHeight, 0, line, TEXT_COLOR)
                        top += lineHeight
                    }
                }

                is NoteBlock.Quote -> {
                    val indent = block.depth * QUOTE_INDENT + QUOTE_TEXT_GAP
                    val text = NoteInline.render(block.text, QUOTE_STYLE)
                    font.split(text, (innerWidth - indent).coerceAtLeast(MIN_WIDTH)).forEach { line ->
                        laid += Row(top, lineHeight, indent, line, QUOTE_COLOR, quoteDepth = block.depth)
                        top += lineHeight
                    }
                }

                is NoteBlock.Code -> {
                    val line = FormattedCharSequence.forward(block.text, CODE_STYLE)
                    laid += Row(top, lineHeight, CODE_INDENT, line, CODE_COLOR, slab = true)
                    top += lineHeight
                }

                is NoteBlock.Table -> top = layOutTable(block, innerWidth, laid, top)

                is NoteBlock.Item -> {
                    val indent = block.depth * LIST_INDENT
                    val marker = markerFor(block)
                    val markerWidth = font.width(marker)
                    val textX = indent + markerWidth
                    val text = NoteInline.render(block.text)
                    val lines = font.split(text, (innerWidth - textX).coerceAtLeast(MIN_WIDTH))
                    // Only the first row of an item carries the line number: a click anywhere on a wrapped
                    // item would otherwise tick a box several rows above the cursor.
                    val taskLine = block.line.takeIf { block.done != null }
                    if (lines.isEmpty()) {
                        // An empty item is a bullet someone has just typed and not yet filled in. It still
                        // takes a row, or the list would appear to swallow the line they are on.
                        laid += Row(
                            top, lineHeight, textX, null, TEXT_COLOR,
                            prefix = FormattedCharSequence.forward(marker, Style.EMPTY), prefixX = indent,
                            taskLine = taskLine,
                        )
                        top += lineHeight
                    }
                    lines.forEachIndexed { index, line ->
                        laid += Row(
                            top, lineHeight, textX, line, TEXT_COLOR,
                            prefix = if (index == 0) {
                                FormattedCharSequence.forward(marker, Style.EMPTY)
                            } else {
                                null
                            },
                            prefixX = indent,
                            taskLine = taskLine.takeIf { index == 0 },
                        )
                        top += lineHeight
                    }
                }
            }
        }

        rows = laid
        laidOutHeight = top + PADDING * 2
        laidOutAt = innerWidth
        spacingAt = NotebookConfig.lineSpacing
        builtAt = System.currentTimeMillis()
        refreshScrollAmount()
    }

    /**
     * Lays a table out as ordinary rows, and returns where the next block starts.
     *
     * Columns get their natural width when the table fits, and shares of the pane in proportion to it when it
     * does not. **A cell too wide for its column wraps**, and the row takes the height of its tallest cell:
     * a table that hid the end of a sentence would be a table you could not trust, and a note is not worth
     * reading twice — once here and once in the source — to find out what it said.
     *
     * A column's natural width is capped at the pane, so one enormous cell asks for a large share rather than
     * an unbounded one and its neighbours keep enough room to be read at all.
     *
     * ### Measured from the component that is drawn, never from the text
     *
     * Every cell is rendered *once*, up front, and both the column width and the wrapping come from that same
     * component. Measuring the plain string instead would be wrong by exactly the styling: a header cell is
     * bold, bold is a pixel wider per character in Minecraft's font, and a `Floor` heading measured plain but
     * drawn bold overflows its column by one character — which the wrap then honours, and the table reads
     * `Floo` / `r`. Anything that measures one thing and draws another has that bug waiting in it, so this does
     * not have the option.
     */
    private fun layOutTable(table: NoteBlock.Table, innerWidth: Int, laid: MutableList<Row>, start: Int): Int {
        if (table.columns == 0) return start

        val all = listOfNotNull(table.header) + table.rows
        val rendered = all.mapIndexed { rowIndex, cells ->
            val style = if (table.header != null && rowIndex == 0) Style.EMPTY.withBold(true) else Style.EMPTY
            cells.map { NoteInline.render(it, style) }
        }
        val natural = IntArray(table.columns) { column ->
            val widest = rendered.maxOf { font.width(it[column]) } + CELL_PADDING * 2
            widest.coerceAtMost(innerWidth)
        }
        val total = natural.sum().coerceAtLeast(1)
        val widths = if (total <= innerWidth) {
            natural
        } else {
            IntArray(table.columns) {
                (natural[it].toLong() * innerWidth / total).toInt().coerceAtLeast(MIN_CELL)
            }
        }

        val edges = IntArray(table.columns)
        var running = 0
        widths.forEachIndexed { index, width ->
            edges[index] = running
            running += width
        }

        val lineHeight = lineHeight()
        var top = start
        rendered.forEachIndexed { rowIndex, cells ->
            val header = table.header != null && rowIndex == 0

            // Every cell is wrapped to its column and the row is made as tall as the tallest of them, so a
            // long cell costs the row height rather than costing the reader the end of the sentence.
            val wrapped = cells.mapIndexed { column, text ->
                val inner = (widths[column] - CELL_PADDING * 2).coerceAtLeast(1)
                inner to font.split(text, inner)
            }
            val lines = wrapped.maxOf { it.second.size }.coerceAtLeast(1)

            // What the row's text actually covers: full line boxes for all but the last line, and only the
            // glyphs for the last, because a line box carries its leading *below* the glyphs. Padding is then
            // split evenly around that, which is what puts the text in the middle of the cell rather than
            // hard against its top edge.
            val content = (lines - 1) * lineHeight + TEXT_HEIGHT
            val padTop = CELL_PADDING / 2
            val padBottom = CELL_PADDING - padTop

            repeat(lines) { lineIndex ->
                val drawn = wrapped.mapIndexedNotNull { column, (inner, cellLines) ->
                    // A cell shorter than its row sits in the middle of it rather than at the top: a one-line
                    // cell beside a three-line one is the common case in a real table, and hanging it from the
                    // top makes the row read as two rows that have come apart.
                    val lead = (lines - cellLines.size) / 2
                    val line = cellLines.getOrNull(lineIndex - lead) ?: return@mapIndexedNotNull null
                    val slack = (inner - font.width(line)).coerceAtLeast(0)
                    val offset = when (table.alignments[column]) {
                        NoteBlock.Align.RIGHT -> slack
                        NoteBlock.Align.CENTER -> slack / 2
                        NoteBlock.Align.LEFT -> 0
                    }
                    Cell(line, edges[column] + CELL_PADDING + offset)
                }
                val firstOfRow = lineIndex == 0
                val lastOfRow = lineIndex == lines - 1
                // The heights sum to `content + CELL_PADDING` exactly, so the grid's fills stay contiguous
                // however many lines the row turned out to be.
                val height = (if (lastOfRow) TEXT_HEIGHT else lineHeight) +
                        (if (firstOfRow) padTop else 0) +
                        (if (lastOfRow) padBottom else 0)
                laid += Row(
                    top,
                    height,
                    0,
                    null,
                    if (header) HEADING_COLOR else TEXT_COLOR,
                    cells = drawn,
                    cellOffsetY = if (firstOfRow) padTop else 0,
                    grid = Grid(
                        columnEdges = edges.toList(),
                        right = running,
                        header = header,
                        topRule = rowIndex == 0 && lineIndex == 0,
                        bottomRule = lastOfRow && (header || rowIndex == all.lastIndex),
                    ),
                )
                top += height
            }
        }
        return top
    }

    private fun markerFor(item: NoteBlock.Item): String = when {
        item.done != null -> if (item.done) "$TASK_DONE " else "$TASK_TODO "
        item.number != null -> "${item.number}. "
        else -> "$BULLET "
    }

    private fun headingScale(level: Int): Float = when (level) {
        1 -> 1.6f
        2 -> 1.3f
        3 -> 1.15f
        else -> 1.0f
    }

    private fun headingStyle(level: Int): Style =
        if (level <= 3) Style.EMPTY.withBold(true) else Style.EMPTY.withBold(true).withItalic(true)

    private companion object {
        val EMPTY_HINT: Component = Component.translatable("hex.notebook.editor.preview_empty")

        const val PADDING = 5
        const val MIN_WIDTH = 24
        const val RULE_HEIGHT = 7
        const val HEADING_GAP = 2
        const val LIST_INDENT = 10
        const val QUOTE_INDENT = 6
        const val QUOTE_BAR = 2
        const val QUOTE_TEXT_GAP = 6
        const val CODE_INDENT = 4

        /** Padding inside a table cell: this much on each side, and this much split above and below. */
        const val CELL_PADDING = 4

        /**
         * How tall a line of text actually is, as against the 9 of a line *box*.
         *
         * Vanilla's own number — `Button` centres its label with the same 8 — and the distinction matters here
         * because a line box carries its leading below the glyphs, so centring on the box would sit every cell
         * a pixel or two high.
         */
        const val TEXT_HEIGHT = 8
        const val MIN_CELL = 16

        /** Not language: these are the glyphs a list is drawn with, the same in every locale. */
        const val BULLET = "•"
        const val TASK_DONE = "☑"
        const val TASK_TODO = "☐"

        const val ANIMATION_MS = 50L

        const val BACKGROUND_COLOR = 0x30000000
        const val TEXT_COLOR = 0xFFE4E4E4.toInt()
        const val HEADING_COLOR = 0xFFFFFFFF.toInt()
        const val PREFIX_COLOR = 0xFFA8A8A8.toInt()
        const val QUOTE_COLOR = 0xFFA0A0A0.toInt()
        const val QUOTE_BAR_COLOR = 0xFF6A6A6A.toInt()
        const val CODE_COLOR = 0xFF7FE3B0.toInt()
        const val SLAB_COLOR = 0x40000000
        const val RULE_COLOR = 0x80FFFFFF.toInt()
        const val HINT_COLOR = 0xFF808080.toInt()
        const val GRID_COLOR = 0x50FFFFFF
        const val HOVER_COLOR = 0x28FFFFFF

        val QUOTE_STYLE: Style = Style.EMPTY.withItalic(true)
        val CODE_STYLE: Style = Style.EMPTY.withColor(CODE_COLOR)
    }
}
