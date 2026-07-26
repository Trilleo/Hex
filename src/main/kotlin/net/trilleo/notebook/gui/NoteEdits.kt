package net.trilleo.notebook.gui

import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.components.MultilineTextField
import net.minecraft.client.gui.components.Whence
import net.trilleo.mixin.MultiLineEditBoxAccessor
import net.trilleo.mixin.MultilineTextFieldAccessor

/**
 * What the formatting buttons actually do to the note's text.
 *
 * The rule every operation here follows is the one a word processor's toolbar follows: **act on the selection,
 * and leave it selected**. Press bold with three words highlighted and you get three bold words still
 * highlighted, ready for italic. Press it with nothing highlighted and you get an empty pair of markers with the
 * caret between them, ready to type into. Anything else — a caret that jumps to the end, a selection that
 * evaporates — makes the buttons useless for the thing they exist for, which is applying two styles in a row.
 *
 * Every edit goes through [replace], which drives the text field the way typing does (select the range, insert
 * over it) rather than swapping the whole document with `setValue`. That keeps the widget's own state — caret,
 * scroll position, the value listener that saves the note — exactly as consistent as it is when a player types.
 *
 * The operations are *toggles*, again as in a word processor: applying bold to text that is already bold takes
 * it off rather than nesting a second pair of markers.
 */
object NoteEdits {

    /** The text model behind [box]. See [MultiLineEditBoxAccessor] for why this needs a mixin. */
    fun fieldOf(box: MultiLineEditBox): MultilineTextField =
        (box as MultiLineEditBoxAccessor).hexTextField()

    // ---- inline styles ---------------------------------------------------------------------------------

    /**
     * Wraps the selection in [marker] — `**`, `*`, `~~`, `` ` `` — or unwraps it when it is already wrapped.
     *
     * Markers immediately *outside* the selection count as wrapping it, which is what makes the button work
     * the second time: double-clicking a bolded word selects the word, not the asterisks around it.
     */
    fun toggleWrap(field: MultilineTextField, marker: String) {
        val selection = field.selectedRange()
        val value = field.value()
        val start = selection.first
        val end = selection.second
        val selected = value.substring(start, end)

        val insideWrapped = selected.length >= marker.length * 2 &&
            selected.startsWith(marker) && selected.endsWith(marker)
        if (insideWrapped) {
            val stripped = selected.substring(marker.length, selected.length - marker.length)
            replace(field, start, end, stripped)
            select(field, start, start + stripped.length)
            return
        }

        val outsideWrapped = start >= marker.length && end + marker.length <= value.length &&
            value.regionMatches(start - marker.length, marker, 0, marker.length) &&
            value.regionMatches(end, marker, 0, marker.length)
        if (outsideWrapped) {
            replace(field, start - marker.length, end + marker.length, selected)
            select(field, start - marker.length, start - marker.length + selected.length)
            return
        }

        replace(field, start, end, "$marker$selected$marker")
        if (selected.isEmpty()) {
            // Nothing was selected, so the useful place to be is between the markers, typing.
            select(field, start + marker.length, start + marker.length)
        } else {
            select(field, start + marker.length, start + marker.length + selected.length)
        }
    }

    /**
     * Colours the selection: the code before it and `&r` after, or the bare code at the caret when nothing is
     * selected.
     *
     * [code] is what follows the `&` — one of the sixteen letters, `z` for chroma, `r` for plain, or `#RRGGBB`
     * for a colour Minecraft has no letter for. It is passed as text rather than as a character precisely so
     * that the last of those is not a second kind of operation.
     *
     * `&r` rather than restoring whatever colour was in force before, because a note is read as much in its
     * source form as rendered, and a reset is the one ending a reader can follow without tracking state.
     */
    fun color(field: MultilineTextField, code: String) {
        val (start, end) = field.selectedRange()
        val selected = field.value().substring(start, end)
        val opening = "&$code"
        if (selected.isEmpty()) {
            replace(field, start, end, opening)
            select(field, start + opening.length, start + opening.length)
            return
        }
        replace(field, start, end, "$opening$selected&r")
        select(field, start + opening.length, start + opening.length + selected.length)
    }

    /** Inserts [text] over the selection and leaves the caret after it — rules, and anything else literal. */
    fun insert(field: MultilineTextField, text: String) {
        val (start, end) = field.selectedRange()
        replace(field, start, end, text)
        select(field, start + text.length, start + text.length)
    }

    /**
     * Wraps the selection as a link, or drops in a template when there is nothing selected.
     *
     * The caret lands on the target, because the label is the part you already have — it is the address you
     * are about to paste.
     */
    fun link(field: MultilineTextField, placeholder: String, target: String) {
        val (start, end) = field.selectedRange()
        val selected = field.value().substring(start, end)
        val label = selected.ifEmpty { placeholder }
        val text = "[$label]($target)"
        replace(field, start, end, text)
        val targetStart = start + label.length + 3
        select(field, targetStart, targetStart + target.length)
    }

    // ---- line prefixes ---------------------------------------------------------------------------------

    /**
     * Applies a line prefix — `# `, `- `, `> `, `1. `, `- [ ] ` — to every line the selection touches, or
     * removes it from all of them when they all already have it.
     *
     * Whole lines, always: a heading is a property of a line, so applying one to the middle of a sentence
     * would be meaningless, and Word behaves the same way with its paragraph styles. Any *other* block prefix
     * is stripped first, so turning a bullet into a heading gives a heading rather than a bulleted heading.
     *
     * [prefixFor] takes the line's index within the selection so an ordered list can number itself.
     */
    fun toggleLinePrefix(field: MultilineTextField, prefixFor: (Int) -> String) {
        val value = field.value()
        val (selStart, selEnd) = field.selectedRange()
        val start = lineStart(value, selStart)
        val end = lineEnd(value, selEnd)
        val lines = value.substring(start, end).split('\n')

        val allHave = lines.withIndex().all { (index, line) ->
            val prefix = prefixFor(index)
            line.isNotBlank() && line.trimStart().startsWith(prefix)
        }

        val rewritten = lines.mapIndexed { index, line ->
            val indent = line.takeWhile { it == ' ' || it == '\t' }
            val body = stripPrefix(line.trimStart())
            if (allHave) indent + body else indent + prefixFor(index) + body
        }.joinToString("\n")

        replace(field, start, end, rewritten)
        select(field, start, start + rewritten.length)
    }

    /** Every block prefix this editor writes, so switching from one to another replaces rather than stacks. */
    private fun stripPrefix(line: String): String {
        TASK.matchEntire(line)?.let { return it.groupValues[2] }
        BULLET.matchEntire(line)?.let { return it.groupValues[1] }
        ORDERED.matchEntire(line)?.let { return it.groupValues[1] }
        HEADING.matchEntire(line)?.let { return it.groupValues[1] }
        QUOTE.matchEntire(line)?.let { return it.groupValues[1] }
        return line
    }

    /**
     * Ticks or unticks the task boxes the selection touches, and turns a plain line into an unticked one.
     *
     * Separate from [toggleLinePrefix] because a task has three states to a heading's two: not a task, an open
     * task, a done one. Pressing the button walks them in the order a list is actually used — a line becomes a
     * task, a task gets ticked, a ticked task goes back to being a line.
     */
    fun cycleTask(field: MultilineTextField) {
        val value = field.value()
        val (selStart, selEnd) = field.selectedRange()
        val start = lineStart(value, selStart)
        val end = lineEnd(value, selEnd)

        val rewritten = value.substring(start, end).split('\n').joinToString("\n") { line ->
            val indent = line.takeWhile { it == ' ' || it == '\t' }
            val body = line.trimStart()
            val task = TASK.matchEntire(body)
            when {
                task == null -> "$indent- [ ] " + stripPrefix(body)
                task.groupValues[1].isBlank() -> "$indent- [x] " + task.groupValues[2]
                else -> indent + task.groupValues[2]
            }
        }

        replace(field, start, end, rewritten)
        select(field, start, start + rewritten.length)
    }

    // ---- driving the field -----------------------------------------------------------------------------

    /**
     * The selection, low index first.
     *
     * Read from the two cursors rather than from `getSelected()`, whose `StringView` is a protected nested
     * type no caller outside the class can name — see [MultilineTextFieldAccessor]. Clamped because the caret
     * is a live value and this is arithmetic on a string that the widget owns.
     */
    private fun MultilineTextField.selectedRange(): Pair<Int, Int> {
        val accessor = this as MultilineTextFieldAccessor
        val length = value().length
        val a = accessor.hexCursor().coerceIn(0, length)
        val b = accessor.hexSelectCursor().coerceIn(0, length)
        return minOf(a, b) to maxOf(a, b)
    }

    /**
     * Replaces `[start, end)` with [text] by selecting it and typing over it.
     *
     * `insertText` replaces whatever is selected, so this is exactly the path a paste takes — including the
     * value listener that marks the note dirty, which is why nothing here has to save anything itself.
     */
    private fun replace(field: MultilineTextField, start: Int, end: Int, text: String) {
        select(field, start, end)
        field.insertText(text)
    }

    /** Selects `[start, end)`, or places the caret when they are equal. */
    private fun select(field: MultilineTextField, start: Int, end: Int) {
        field.setSelecting(false)
        field.seekCursor(Whence.ABSOLUTE, start)
        if (start != end) {
            field.setSelecting(true)
            field.seekCursor(Whence.ABSOLUTE, end)
            field.setSelecting(false)
        }
    }

    private fun lineStart(value: String, index: Int): Int = value.lastIndexOf('\n', (index - 1).coerceAtLeast(0))
        .let { if (it < 0 || index == 0) 0 else it + 1 }

    private fun lineEnd(value: String, index: Int): Int =
        value.indexOf('\n', index).let { if (it < 0) value.length else it }

    private val HEADING = Regex("^#{1,6}\\s+(.*)$")
    private val QUOTE = Regex("^(?:>\\s*)+(.*)$")
    private val BULLET = Regex("^[-*+]\\s+(.*)$")
    private val ORDERED = Regex("^\\d{1,9}[.)]\\s+(.*)$")
    private val TASK = Regex("^[-*+]\\s+\\[(?:(.))]\\s*(.*)$")
}
