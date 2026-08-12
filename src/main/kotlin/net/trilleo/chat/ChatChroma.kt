package net.trilleo.chat

import net.minecraft.network.chat.FontDescription
import net.minecraft.util.FormattedCharSequence
import net.trilleo.Hex
import net.trilleo.util.Chroma

/**
 * Makes chroma text in the chat log actually flow.
 *
 * ### The problem
 *
 * Everywhere else in this mod, chroma works by rebuilding a component every frame — an item name is re-rendered
 * continuously, so [net.trilleo.itemcustom.ItemCustomLookup] can throw its cached value away each
 * [Chroma.frame] and nothing else has to know. Chat cannot work that way. A chat message is styled *once*, when
 * it arrives: `ChatComponent` wraps it into `FormattedCharSequence` lines and keeps those, so whatever colour a
 * character was given at arrival is the colour it keeps until it scrolls away. Painting a chroma span
 * per-character at arrival would produce a rainbow that never moves, which reads as a bug rather than a feature.
 *
 * ### The approach
 *
 * A chroma span is not coloured at arrival at all. It is *marked*, and the colour is computed per character at
 * draw time by [recolor], which `ChatComponentMixin` wraps around every chat line the game is about to draw.
 *
 * The mark is [FONT] — a font of this mod's own, defined in `assets/hex/font/chroma.json` as a copy of vanilla's
 * `minecraft:default`. Three properties make it the right marker, and no other field of `Style` has all three:
 *
 *  - it **survives the bake**. `Style` is what a `FormattedCharSequence` carries per character, so the mark is
 *    still there at draw time, long after the `Component` it came from has been discarded;
 *  - it is **invisible**. The font names the same glyph providers vanilla's default does, so every character is
 *    the same width it would otherwise be and chat wrapping, trimming and click hit-testing are all unchanged.
 *    An `insertion` marker would have cost shift-click on the span; a sentinel colour would have been a colour
 *    the player could legitimately pick;
 *  - it costs **nothing when unused**. [recolor] hands the sequence straight back unless some enabled rule
 *    actually asks for chroma, so a player who never turns it on pays one boolean per drawn line.
 */
object ChatChroma {

    /**
     * The marker font: `hex:chroma`, glyph-for-glyph identical to `minecraft:default`.
     *
     * A `Resource` record, so equality is by id and a style that has been copied, merged through
     * [net.minecraft.network.chat.Style.applyTo] or round-tripped through a wrap still compares equal.
     */
    val FONT: FontDescription = FontDescription.Resource(Hex.id("chroma"))

    /**
     * The [Chroma.frame] the cached [phase] was read at, or [Chroma.STATIC] for "never".
     *
     * Read once a frame rather than once a character: a chat page is hundreds of characters, and sampling the
     * clock for each of them would give the top and bottom of the screen visibly different phases.
     */
    private var frame: Int = Chroma.STATIC
    private var phase: Double = 0.0

    /**
     * [content] with every character marked with [FONT] recoloured for this frame, or [content] itself when
     * nothing on screen could be marked.
     *
     * The wrapper is lazy — it does no work until the game accepts the sequence — and allocates one object per
     * drawn line, which is the entire per-frame cost of animated chroma.
     */
    fun recolor(content: FormattedCharSequence): FormattedCharSequence {
        if (!ChatHighlightConfig.active || !ChatHighlightConfig.anyChroma) return content

        val phase = phaseNow()
        val width = ChatHighlightConfig.chromaWidth
        return FormattedCharSequence { sink ->
            content.accept { index, style, codePoint ->
                // The index is the character's position in this line, so the colours travel the way the text
                // reads and restart on each wrapped line — which is what keeps a long wrapped message looking
                // like one rainbow per row rather than one rainbow stretched thin across all of them.
                val painted =
                    if (style.font == FONT) style.withColor(Chroma.colorAt(phase, index, width)) else style
                sink.accept(index, painted, codePoint)
            }
        }
    }

    private fun phaseNow(): Double {
        val now = Chroma.frame()
        if (now != frame) {
            frame = now
            phase = Chroma.phase(ChatHighlightConfig.chromaSeconds)
        }
        return phase
    }
}
