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
 * Wrapping is the expensive part, so [relayout] runs when the text or the width changes and the draw pass only
 * walks rows and skips the ones outside the viewport. The exception is chroma: a flowing colour is a different
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
    )

    private var source: String = ""
    private var blocks: List<NoteBlock> = emptyList()
    private var rows: List<Row> = emptyList()
    private var laidOutHeight: Int = 0

    /** The width the current [rows] were wrapped at, so a resize is noticed without anyone announcing it. */
    private var laidOutAt: Int = -1

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
    }

    override fun extractWidgetRenderState(
        extractor: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        if (!visible) return

        val innerWidth = innerWidth()
        if (innerWidth != laidOutAt) relayout()
        if (animated && System.currentTimeMillis() - builtAt >= ANIMATION_MS) relayout()

        extractor.fill(x, y, x + width, y + height, BACKGROUND_COLOR)
        extractor.outline(x, y, width, height, NotebookTheme.DIVIDER_COLOR)

        if (rows.isEmpty()) {
            extractor.centeredText(font, EMPTY_HINT, x + width / 2, y + PADDING, HINT_COLOR)
            return
        }

        extractor.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1)
        val scroll = scrollAmount().toInt()
        rows.forEach { row ->
            val top = y + PADDING + row.top - scroll
            // Rows are in order, so everything below the pane is as well — but the loop is cheap and stopping
            // early would need an index dance that buys nothing at these sizes.
            if (top + row.height >= y && top <= y + height) draw(extractor, row, top)
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

    // ---- layout ----------------------------------------------------------------------------------------

    private fun innerWidth(): Int = (width - PADDING * 2 - scrollbarWidth()).coerceAtLeast(MIN_WIDTH)

    /**
     * Wraps every block into rows.
     *
     * Continuation lines are indented to line up under the first, which is the whole reason a list item is
     * wrapped at a reduced width and drawn at an offset rather than having its bullet baked into the text.
     */
    private fun relayout() {
        val innerWidth = innerWidth()
        val laid = mutableListOf<Row>()
        var top = 0

        blocks.forEach { block ->
            when (block) {
                is NoteBlock.Blank -> top += font.lineHeight / 2

                is NoteBlock.Rule -> {
                    laid += Row(top, RULE_HEIGHT, 0, null, RULE_COLOR, rule = true)
                    top += RULE_HEIGHT
                }

                is NoteBlock.Heading -> {
                    val scale = headingScale(block.level)
                    val lineHeight = (font.lineHeight * scale).toInt() + 2
                    val text = NoteInline.render(block.text, headingStyle(block.level))
                    val wrapWidth = (innerWidth / scale).toInt().coerceAtLeast(MIN_WIDTH)
                    font.split(text, wrapWidth).forEach { line ->
                        laid += Row(top, lineHeight, 0, line, HEADING_COLOR, scale = scale)
                        top += lineHeight
                    }
                    top += HEADING_GAP
                }

                is NoteBlock.Paragraph -> {
                    val text = NoteInline.render(block.text)
                    font.split(text, innerWidth).forEach { line ->
                        laid += Row(top, font.lineHeight, 0, line, TEXT_COLOR)
                        top += font.lineHeight
                    }
                }

                is NoteBlock.Quote -> {
                    val indent = block.depth * QUOTE_INDENT + QUOTE_TEXT_GAP
                    val text = NoteInline.render(block.text, QUOTE_STYLE)
                    font.split(text, (innerWidth - indent).coerceAtLeast(MIN_WIDTH)).forEach { line ->
                        laid += Row(top, font.lineHeight, indent, line, QUOTE_COLOR, quoteDepth = block.depth)
                        top += font.lineHeight
                    }
                }

                is NoteBlock.Code -> {
                    val line = FormattedCharSequence.forward(block.text, CODE_STYLE)
                    laid += Row(top, font.lineHeight, CODE_INDENT, line, CODE_COLOR, slab = true)
                    top += font.lineHeight
                }

                is NoteBlock.Item -> {
                    val indent = block.depth * LIST_INDENT
                    val marker = markerFor(block)
                    val markerWidth = font.width(marker)
                    val textX = indent + markerWidth
                    val text = NoteInline.render(block.text)
                    val lines = font.split(text, (innerWidth - textX).coerceAtLeast(MIN_WIDTH))
                    if (lines.isEmpty()) {
                        // An empty item is a bullet someone has just typed and not yet filled in. It still
                        // takes a row, or the list would appear to swallow the line they are on.
                        laid += Row(
                            top, font.lineHeight, textX, null, TEXT_COLOR,
                            prefix = FormattedCharSequence.forward(marker, Style.EMPTY), prefixX = indent,
                        )
                        top += font.lineHeight
                    }
                    lines.forEachIndexed { index, line ->
                        laid += Row(
                            top, font.lineHeight, textX, line, TEXT_COLOR,
                            prefix = if (index == 0) {
                                FormattedCharSequence.forward(marker, Style.EMPTY)
                            } else {
                                null
                            },
                            prefixX = indent,
                        )
                        top += font.lineHeight
                    }
                }
            }
        }

        rows = laid
        laidOutHeight = top + PADDING * 2
        laidOutAt = innerWidth
        builtAt = System.currentTimeMillis()
        refreshScrollAmount()
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

        val QUOTE_STYLE: Style = Style.EMPTY.withItalic(true)
        val CODE_STYLE: Style = Style.EMPTY.withColor(CODE_COLOR)
    }
}
