package net.trilleo.suggest.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.trilleo.suggest.context.ContextSnapshot
import net.trilleo.suggest.model.Candidate
import net.trilleo.suggest.model.NaiveBayes
import net.trilleo.suggest.model.Weights

/**
 * One drawn row of the popup.
 *
 * **For drawing only.** [text] is the whole command line as it would be inserted *unless* it did not fit the
 * chat box, in which case it is cut short and ends in an ellipsis — so what gets accepted is always read back
 * off the [net.trilleo.suggest.model.Candidate], never off a row.
 *
 * @property text the command line to draw, cut to fit the box.
 * @property typedLength how many leading characters the player has already typed, drawn brighter than the
 *   rest so the completion reads as a completion rather than as an unrelated line that happens to be listed.
 *   Zero when the candidate is not a literal continuation of what was typed — a fuzzy match — in which case
 *   there is no meaningful prefix to highlight.
 * @property hint one word on why this is here, right-aligned. Empty for the ordinary case.
 * @property width exactly what this row puts on screen, text and hint together, measured the same way it is
 *   drawn.
 */
class OverlayRow(
    val text: String,
    val typedLength: Int,
    val hint: String,
    val width: Int,
)

/**
 * Draws Hex's ranked suggestion popup.
 *
 * Follows the same discipline as [net.trilleo.reminder.hud.ReminderHudRenderer]: everything that costs
 * anything — formatting, measuring, deciding what a row's hint should say — happens once when the candidate
 * list changes, and what is left here is arithmetic on integers and a handful of draw calls. This runs on the
 * render thread, several times per tick at a high frame rate, so anything that walks the model here would be
 * doing it a hundred times a second to answer a question whose answer changed when a key was pressed.
 *
 * Positioned to sit exactly where vanilla's own popup would, because vanilla's is hidden while this one is up
 * and two different popup positions for the same job would read as a bug.
 */
object SuggestOverlay {

    private const val LINE_HEIGHT = 12
    private const val PADDING = 1

    /** Vanilla's own suggestion-popup fill, so the two are indistinguishable but for their contents. */
    private const val BACKGROUND = 0xD0000000.toInt()

    private const val TEXT = 0xFFAAAAAA.toInt()
    private const val TEXT_TYPED = 0xFFFFFFFF.toInt()
    private const val TEXT_SELECTED = 0xFFFFFF55.toInt()
    private const val HINT = 0xFF7A7A7A.toInt()
    private const val HINT_SELECTED = 0xFFB0A050.toInt()

    /** Room for the hint column, so a long line and a hint cannot overlap. */
    private const val HINT_GAP = 8

    /** Marks a line that had to be cut to fit the chat box. */
    private const val ELLIPSIS = "…"

    /**
     * No row is cut below this, so a wide hint on a narrow box cannot reduce a line to its cut mark.
     *
     * A row this short would overflow rather than be trimmed to nothing, which is the better failure of the
     * two: a suggestion running past the edge of a box that is already too narrow to hold anything is at
     * least still readable.
     */
    private const val MIN_TEXT_WIDTH = 40

    /**
     * Prepares the rows for [candidates]. Called when the candidate list changes, never per frame.
     *
     * @param typed the command text without its slash, for deciding how much of each row to highlight.
     * @param available how much width the popup has, which is the chat box's inner width. Hex's candidates
     *   are whole command lines rather than the single token vanilla completes, so they are the one popup
     *   that can genuinely be wider than the box it sits on — `/party invite SomebodysLongName` with a hint
     *   beside it is an ordinary suggestion, not a pathological one. Anything that does not fit is cut here,
     *   once, rather than being allowed to draw outside the popup's own background.
     */
    fun rows(
        candidates: List<Candidate>,
        typed: String,
        context: ContextSnapshot,
        now: Long,
        available: Int,
    ): List<OverlayRow> {
        if (candidates.isEmpty()) return emptyList()
        val font = Minecraft.getInstance().font
        // What a row may occupy, once the popup's own padding is taken out of the width it has to sit in.
        val room = (available - PADDING * 2).coerceAtLeast(MIN_TEXT_WIDTH)

        return candidates.map { candidate ->
            val hint = hintFor(candidate, context, now)
            val hintWidth = if (hint.isEmpty()) 0 else HINT_GAP + font.width(hint)
            val budget = (room - hintWidth).coerceAtLeast(MIN_TEXT_WIDTH)

            var text = "/${candidate.key}"
            var prefix = if (candidate.key.startsWith(typed, ignoreCase = true)) typed.length + 1 else 0
            if (font.width(text) > budget) {
                text = font.plainSubstrByWidth(text, (budget - font.width(ELLIPSIS)).coerceAtLeast(0)) + ELLIPSIS
                // The ellipsis is not something the player typed, so the highlight stops before it.
                prefix = prefix.coerceAtMost(text.length - ELLIPSIS.length)
            }

            OverlayRow(text, prefix, hint, measure(font, text, prefix) + hintWidth)
        }
    }

    /**
     * Exactly what [draw] will put on screen for a row's text.
     *
     * Measured the same way it is drawn — as two segments when there is a typed prefix — because
     * [Font.width] rounds up to a whole pixel. Measuring the line once and drawing it in two pieces can
     * therefore differ by a pixel, which is a pixel of text hanging outside the background it is supposed to
     * sit on.
     */
    private fun measure(font: Font, text: String, typedLength: Int): Int =
        if (typedLength in 1 until text.length) {
            font.width(text.substring(0, typedLength)) + font.width(text.substring(typedLength))
        } else {
            font.width(text)
        }

    /**
     * One word on why this candidate is in the list.
     *
     * Deliberately not a score. A number invites the player to argue with the arithmetic; a word tells them
     * what the model thinks it knows, which is the thing that is actually useful to disagree with — "here"
     * on a suggestion that makes no sense where you are standing is a much more actionable thing to read than
     * "2.41". The full arithmetic is a `/hexa suggest why` away for anyone who wants it.
     *
     * The match term is skipped when it leads, because "you typed the start of this" is not news.
     */
    private fun hintFor(candidate: Candidate, context: ContextSnapshot, now: Long): String {
        if (candidate.pinned) return "pinned"
        val contributions = Weights.contributions(candidate.features)

        var best = -1
        var bestValue = HINT_FLOOR
        for (i in contributions.indices) {
            if (i == Weights.MATCH || i == Weights.PINNED) continue
            if (contributions[i] > bestValue) {
                bestValue = contributions[i]
                best = i
            }
        }

        return when (best) {
            Weights.CONTEXT -> contextHint(candidate.key, context, now)
            Weights.MARKOV -> "next"
            Weights.PRIOR, Weights.FAMILY -> "often"
            Weights.RECENCY -> "recent"
            Weights.SESSION -> "again"
            Weights.CATALOGUE -> "new"
            else -> ""
        }
    }

    /** Names the context feature that actually did the work, so "here" can instead say "island" or "holding". */
    private fun contextHint(key: String, context: ContextSnapshot, now: Long): String {
        val top = NaiveBayes.contributions(key, context, now).firstOrNull { it.second > 0.0 } ?: return "here"
        return when (top.first) {
            ContextSnapshot.ISLAND, ContextSnapshot.CELL -> "here"
            ContextSnapshot.HELD, ContextSnapshot.HELD_KIND -> "holding"
            ContextSnapshot.HOTBAR, ContextSnapshot.ARMOR -> "loadout"
            ContextSnapshot.CUE -> "chat"
            // The event and the Skyblock clock name themselves rather than being labelled: "dark auction" and
            // "night" say far more, in the same space, than "event" and "time" would.
            ContextSnapshot.SB_EVENT -> context[ContextSnapshot.SB_EVENT].takeIf { it != ContextSnapshot.UNKNOWN }
                ?: "event"

            ContextSnapshot.SB_TIME -> context[ContextSnapshot.SB_TIME].takeIf { it != ContextSnapshot.UNKNOWN }
                ?: "time"

            ContextSnapshot.SB_SEASON -> context[ContextSnapshot.SB_SEASON].takeIf { it != ContextSnapshot.UNKNOWN }
                ?: "season"

            ContextSnapshot.HOUR, ContextSnapshot.DAY -> "usually"
            ContextSnapshot.SESSION -> "on join"
            ContextSnapshot.PREV1, ContextSnapshot.PREV2 -> "next"
            else -> "here"
        }
    }

    /**
     * Draws the popup above [input].
     *
     * Anchored to the input box rather than to the screen so it follows the chat line wherever the GUI scale
     * puts it, and aligned on `getScreenX(0)` — the screen position of the first character — which is the
     * same anchor vanilla uses, so the two popups occupy the identical column.
     */
    fun draw(extractor: GuiGraphicsExtractor, input: EditBox, rows: List<OverlayRow>, selected: Int) {
        if (rows.isEmpty()) return
        val font = Minecraft.getInstance().font

        val contentWidth = rows.maxOf { it.width }
        val left = leftOf(input, contentWidth)
        val height = rows.size * LINE_HEIGHT
        val top = input.y - height - PADDING

        extractor.fill(left, top, left + contentWidth + PADDING * 2, top + height, BACKGROUND)

        rows.forEachIndexed { index, row ->
            val y = top + index * LINE_HEIGHT + 2
            val chosen = index == selected
            val x = left + PADDING

            if (row.typedLength in 1 until row.text.length) {
                val already = row.text.substring(0, row.typedLength)
                extractor.text(font, already, x, y, TEXT_TYPED)
                extractor.text(
                    font,
                    row.text.substring(row.typedLength),
                    x + font.width(already),
                    y,
                    if (chosen) TEXT_SELECTED else TEXT,
                )
            } else {
                extractor.text(font, row.text, x, y, if (chosen) TEXT_SELECTED else TEXT)
            }

            if (row.hint.isNotEmpty()) {
                extractor.text(
                    font,
                    row.hint,
                    left + PADDING + contentWidth - font.width(row.hint),
                    y,
                    if (chosen) HINT_SELECTED else HINT,
                )
            }
        }
    }

    /**
     * Where the popup's left edge goes.
     *
     * The start of the line, pulled back when the widest row would otherwise run off the right of the chat
     * box, and never off the left of the screen — the same clamp vanilla applies to its own popup, which is
     * the other half of "sit exactly where vanilla's would". Rows are already cut to the box in [rows], so
     * this has nothing to do in the ordinary case; it is what keeps the list on screen when the window has
     * been resized since they were measured.
     */
    private fun leftOf(input: EditBox, contentWidth: Int): Int {
        val start = input.getScreenX(0)
        val rightmost = start + input.innerWidth - (contentWidth + PADDING * 2)
        return (start - PADDING).coerceAtMost(rightmost).coerceAtLeast(0)
    }

    /** Which row [mouseY] is over, or -1. Used for click-to-accept. */
    fun rowAt(input: EditBox, rows: List<OverlayRow>, mouseX: Double, mouseY: Double): Int {
        if (rows.isEmpty()) return -1
        val contentWidth = rows.maxOf { it.width }
        val left = leftOf(input, contentWidth)
        val height = rows.size * LINE_HEIGHT
        val top = input.y - height - PADDING

        if (mouseX < left || mouseX > left + contentWidth + PADDING * 2) return -1
        if (mouseY < top || mouseY >= top + height) return -1
        return ((mouseY - top) / LINE_HEIGHT).toInt().coerceIn(0, rows.size - 1)
    }

    /** Below this a contribution is not what put the candidate in the list, and naming it would mislead. */
    private const val HINT_FLOOR = 0.15
}
