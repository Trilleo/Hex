package net.trilleo.notebook.md

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.trilleo.itemcustom.ItemCustomizeConfig
import net.trilleo.util.Chroma

/**
 * One line of markdown, as a styled component — the inline half of the preview.
 *
 * Handles what markdown puts *inside* a line: `**bold**`, `*italic*`, `~~strikethrough~~`, `` `code` `` and
 * `[label](target)`. Blocks — headings, lists, quotes, fences — are [NoteBlocks]' job, and it hands each block's
 * text here.
 *
 * ### Colour codes travel through, they are not a second syntax
 *
 * A note is Minecraft text as much as it is markdown, so `&c` and the mod's own `&z` chroma work in a note
 * exactly as they work in an item name — [Chroma] does that part, and the two settings that shape chroma are the
 * ones from [ItemCustomizeConfig], because a note flowing at a different rate from a renamed item would read as
 * a glitch rather than as a choice.
 *
 * The two syntaxes are interleaved rather than layered, which is the whole difficulty: `&c**red bold**` has to
 * come out red *and* bold even though the emphasis markers split the line into runs that Chroma never sees as
 * one string. So the parser carries the colour state across run boundaries itself — [CodeState] is what a run
 * inherits from everything before it — and hands each run to [Chroma.build] with that state as its base.
 * `&r` returns to plain, as it does everywhere else.
 */
object NoteInline {

    /** The colour state in force at a point in the line: what `&r` and an un-coloured run fall back to. */
    private data class CodeState(val color: Int?, val chroma: Boolean) {
        companion object {
            val NONE = CodeState(null, false)
        }
    }

    /**
     * [text] as a component, with [base] underneath anything the text asks for itself.
     *
     * [base] is how a block passes its own look down — the grey of a quote, the colour of a heading — without
     * this having to know which block it is rendering.
     */
    fun render(text: String, base: Style = Style.EMPTY): MutableComponent {
        val result = Component.empty().withStyle(base)
        var literal = StringBuilder()
        var state = CodeState.NONE
        var bold = false
        var italic = false
        var strike = false

        // Ends the run that has been accumulating and appends it under the styles in force. Called on every
        // marker, because a marker is exactly the point where the styles stop describing what comes next.
        fun flush() {
            if (literal.isEmpty()) return
            val run = literal.toString()
            result.append(paint(run, state, style(bold, italic, strike)))
            state = advance(run, state)
            literal = StringBuilder()
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            val next = text.getOrNull(i + 1)

            // A backslash escapes the character after it, so a note can contain a literal asterisk. Only
            // markers are escapable — a stray backslash stays a backslash, as it does in markdown.
            if (c == '\\' && next != null && next in ESCAPABLE) {
                literal.append(next)
                i += 2
                continue
            }

            when {
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end < 0) {
                        literal.append(c)
                        i++
                    } else {
                        flush()
                        // Code is deliberately not parsed further: inside backticks, `**` is two asterisks
                        // someone wants to see. It does not go through Chroma either, for the same reason.
                        result.append(Component.literal(text.substring(i + 1, end)).withStyle(CODE_STYLE))
                        i = end + 1
                    }
                }

                (c == '*' || c == '_') && next == c -> {
                    flush()
                    bold = !bold
                    i += 2
                }

                c == '*' || c == '_' -> {
                    flush()
                    italic = !italic
                    i++
                }

                c == '~' && next == '~' -> {
                    flush()
                    strike = !strike
                    i += 2
                }

                c == '[' -> {
                    val link = readLink(text, i)
                    if (link == null) {
                        literal.append(c)
                        i++
                    } else {
                        flush()
                        // The target is not drawn and not clickable: a note is not a browser, and a line of
                        // https:// in the middle of a sentence is exactly the noise markdown links exist to
                        // avoid. The source pane is where the address lives.
                        result.append(paint(link.label, state, style(bold, italic, strike).withUnderlined(true)))
                        i = link.end
                    }
                }

                else -> {
                    literal.append(c)
                    i++
                }
            }
        }
        flush()
        return result
    }

    // ---- internals -------------------------------------------------------------------------------------

    private class Link(val label: String, val end: Int)

    /** `[label](target)` starting at [start], or null when it is only a square bracket in a sentence. */
    private fun readLink(text: String, start: Int): Link? {
        val close = text.indexOf(']', start + 1)
        if (close < 0 || close + 1 >= text.length || text[close + 1] != '(') return null
        val target = text.indexOf(')', close + 2)
        if (target < 0) return null
        return Link(text.substring(start + 1, close), target + 1)
    }

    private fun style(bold: Boolean, italic: Boolean, strike: Boolean): Style = Style.EMPTY
        .withBold(bold.takeIf { it })
        .withItalic(italic.takeIf { it })
        .withStrikethrough(strike.takeIf { it })

    /** One run of literal text, coloured by the codes it inherits and the codes it contains. */
    private fun paint(run: String, state: CodeState, style: Style): MutableComponent = Chroma.build(
        run,
        all = state.chroma,
        baseColor = state.color,
        seconds = ItemCustomizeConfig.chromaSeconds,
        width = ItemCustomizeConfig.chromaWidth,
    ).withStyle(style)

    /**
     * The colour state after [run] — the codes it contains, applied to the state it started in.
     *
     * A second pass over the run's characters rather than something [Chroma.build] hands back, so that this
     * stays a pure question about a string and Chroma keeps its single job of turning text into a component.
     */
    private fun advance(run: String, state: CodeState): CodeState {
        var current = state
        var i = 0
        while (i < run.length - 1) {
            val hex = Chroma.hexAt(run, i)
            if (hex != null) {
                current = CodeState(hex, chroma = false)
                i += Chroma.HEX_LENGTH
                continue
            }
            if (run[i] == '&' || run[i] == '§') {
                when (val code = run[i + 1].lowercaseChar()) {
                    Chroma.CODE -> current = CodeState(current.color, chroma = true)
                    'r' -> current = CodeState.NONE
                    else -> colorOf(code)?.let { current = CodeState(it, chroma = false) }
                }
                i += 2
                continue
            }
            i++
        }
        return current
    }

    /** The RGB of a legacy colour code, or null for a formatting code such as `&l`, which carries no colour. */
    private fun colorOf(code: Char): Int? {
        val index = COLOR_CODES.indexOf(code)
        return if (index < 0) null else COLOR_RGB[index]
    }

    private const val COLOR_CODES = "0123456789abcdef"

    /** Vanilla's sixteen, in code order. Kept here rather than read from `ChatFormatting.getColor()` at
     * class-init because that is a nullable box per lookup on a path that runs per run of text. */
    private val COLOR_RGB = intArrayOf(
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
    )

    private const val ESCAPABLE = "\\`*_~[]&#-"

    /** Inline code: a colour rather than a different font, because Minecraft ships no monospace face. */
    private val CODE_STYLE: Style = Style.EMPTY.withColor(0x7FE3B0)
}
