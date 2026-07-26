package net.trilleo.util

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.util.Mth

/**
 * Flowing rainbow text — the colour mode every Skyblock mod calls *chroma*.
 *
 * Chroma is a hue that walks forward with the clock and is offset a little further for each character, so the
 * colours appear to travel along the word. Two numbers describe it:
 *
 *  - **seconds** — how long one full trip through the rainbow takes. Lower is faster.
 *  - **width** — how many characters one full rainbow spans. Lower packs more colour into fewer letters; set it
 *    wide enough and a short name is a single slowly shifting colour rather than a stripe.
 *
 * Saturation and brightness are fixed at full: a desaturated chroma reads as a bug rather than a setting, and
 * every mod that offers this does the same.
 *
 * ### The `&z` code
 *
 * [build] parses the legacy `&`/`§` codes into a real component tree and adds one of its own, [CODE] — `&z` —
 * which turns chroma on from that point. That is the convention NotEnoughUpdates and SkyHanni use, so a name
 * copied from either works here unchanged. A colour code or `&r` turns it off again, exactly as they end any
 * other colour.
 *
 * ### Why a component tree rather than `§` codes in the text
 *
 * Chroma needs a colour *per character*, and a legacy code can only name one of sixteen. So [build] strips the
 * codes out of the text and expresses them as styles on child components instead. That also fixes a smaller
 * problem: a `§` code left inside a literal takes over everything after it, overriding any colour set on the
 * component — which is why a base colour and inline codes could not previously be combined.
 *
 * ### Animating
 *
 * The colour of a given character changes every frame, so anything caching a chroma component has to know when
 * to throw it away. [frame] is the token for that: it ticks at roughly the display's rate, and a cache that
 * stores it alongside the value re-renders exactly when it needs to. See
 * [net.trilleo.itemcustom.ItemCustomLookup] for a worked example.
 */
object Chroma {

    /** The format code that starts chroma, following NotEnoughUpdates and SkyHanni. */
    const val CODE: Char = 'z'

    /** Vanilla's own format marker, and the one this mod asks players to type. Both are accepted. */
    private const val SECTION = '§'
    private const val AMPERSAND = '&'

    const val SECONDS_MIN: Double = 0.5
    const val SECONDS_MAX: Double = 20.0
    const val SECONDS_STEP: Double = 0.5
    const val SECONDS_DEFAULT: Double = 3.0

    const val WIDTH_MIN: Double = 2.0
    const val WIDTH_MAX: Double = 80.0
    const val WIDTH_STEP: Double = 1.0
    const val WIDTH_DEFAULT: Double = 20.0

    /**
     * The stamp for something that does not animate, and so never needs re-rendering on time alone. Negative
     * so it can never collide with a real [frame].
     */
    const val STATIC: Int = -1

    /** About 60 a second — fast enough to look continuous, coarse enough to be worth caching between. */
    private const val FRAME_MS = 16L

    private const val SATURATION = 1.0f
    private const val BRIGHTNESS = 1.0f

    /**
     * A token that changes as the animation does. Two values built in the same frame share one; a cache
     * holding a chroma component alongside the frame it was built for stays correct for free.
     *
     * Always non-negative, so [STATIC] stays distinguishable.
     */
    fun frame(): Int = ((System.currentTimeMillis() / FRAME_MS) % Int.MAX_VALUE).toInt()

    /**
     * Where the cycle has got to right now, as `0.0..1.0`.
     *
     * Split out from [colorAt] so a caller colouring a whole word reads the clock once rather than once per
     * character — otherwise the first and last letters are sampled at measurably different times.
     */
    fun phase(seconds: Double): Double {
        val cycle = (if (seconds <= 0.0) SECONDS_DEFAULT else seconds) * 1000.0
        return (System.currentTimeMillis() % cycle.toLong()) / cycle
    }

    /** The colour, as packed RGB, for the character [offset] places along a run at this [phase]. */
    fun colorAt(phase: Double, offset: Int, width: Double): Int {
        val span = if (width <= 0.0) WIDTH_DEFAULT else width
        // Subtracted so the colours travel the way the text reads. `mod` rather than `%` because the result
        // has to stay in 0..1 for a negative offset, and `%` would keep the sign.
        val hue = (phase - offset / span).mod(1.0)
        return Mth.hsvToRgb(hue.toFloat(), SATURATION, BRIGHTNESS)
    }

    /** The chroma colour for a single value read on its own — a HUD label, say, with no run around it. */
    fun color(offset: Int, seconds: Double, width: Double): Int = colorAt(phase(seconds), offset, width)

    /**
     * Whether text built from [raw] with [all] would animate, and so must not be cached past a [frame].
     *
     * A pure string test, deliberately: the caller asking this is usually deciding whether to keep a value,
     * and it must not have to build the value to find out.
     */
    fun uses(raw: String, all: Boolean): Boolean {
        if (all) return true
        var i = 0
        while (i < raw.length - 1) {
            if (isMarker(raw[i]) && raw[i + 1].lowercaseChar() == CODE) return true
            i++
        }
        return false
    }

    /**
     * Turns [raw] — plain text with `&` or `§` codes in it — into a styled component.
     *
     * @param all start with chroma already on, for "make this whole thing flow" without typing a code.
     * @param baseColor the colour for text that names none of its own, or null to inherit from whatever the
     *   component ends up inside. `&r` returns to this rather than to nothing.
     */
    fun build(
        raw: String,
        all: Boolean = false,
        baseColor: Int? = null,
        seconds: Double = SECONDS_DEFAULT,
        width: Double = WIDTH_DEFAULT,
    ): MutableComponent = Painter(all, baseColor, phase(seconds), width).paint(raw)

    private fun isMarker(c: Char): Boolean = c == SECTION || c == AMPERSAND

    /**
     * The colour of a `&#RRGGBB` code starting at [i], or null when that is not what is there.
     *
     * Minecraft's sixteen codes are sixteen colours, which is not many when the thing being coloured is a
     * player's own text. Components carry a full RGB colour, so the only thing missing was a way to *write*
     * one; `&#RRGGBB` is the spelling plugins and the other Skyblock mods already use, so text pasted from
     * either side keeps its colours.
     *
     * Always [HEX_LENGTH] characters when it matches, so a caller advances by that.
     */
    fun hexAt(raw: String, i: Int): Int? {
        if (i + HEX_LENGTH > raw.length) return null
        if (!isMarker(raw[i]) || raw[i + 1] != HEX_MARKER) return null
        var rgb = 0
        for (offset in 2 until HEX_LENGTH) {
            val digit = Character.digit(raw[i + offset], 16)
            if (digit < 0) return null
            rgb = (rgb shl 4) or digit
        }
        return rgb
    }

    /** `&`, `#`, and six digits. */
    const val HEX_LENGTH: Int = 8

    /** The character that turns a marker into a hex colour rather than a one-letter code. */
    const val HEX_MARKER: Char = '#'

    /**
     * Walks the text once, holding the style the codes have built up so far.
     *
     * A class rather than a nest of local functions because there are seven pieces of state to carry and every
     * one of them is read by two different steps.
     */
    private class Painter(
        private val chromaAll: Boolean,
        private val baseColor: Int?,
        private val phase: Double,
        private val width: Double,
    ) {
        private val root: MutableComponent = Component.empty()

        /** Characters waiting to be emitted as one component; always empty while chroma is on. */
        private val run = StringBuilder()

        private var color: Int? = baseColor
        private var chroma = chromaAll
        private var bold = false
        private var italic = false
        private var underlined = false
        private var strikethrough = false
        private var obfuscated = false

        /**
         * How far along the text we are, counting only characters that are drawn.
         *
         * Codes do not advance it, so inserting a `&l` mid-word does not shunt the colours along; and it keeps
         * counting through text that is *not* chroma, so a run that starts flowing again picks up where it
         * would have been rather than restarting the rainbow.
         */
        private var offset = 0

        fun paint(raw: String): MutableComponent {
            var i = 0
            while (i < raw.length) {
                val c = raw[i]

                // Checked before the one-letter codes, because `&#` is not one of them and would otherwise be
                // emitted as two literal characters.
                val hex = Chroma.hexAt(raw, i)
                if (hex != null) {
                    flush()
                    applyColor(hex)
                    i += Chroma.HEX_LENGTH
                    continue
                }

                val code = if (Chroma.isMarker(c) && i + 1 < raw.length) raw[i + 1].lowercaseChar() else null
                // An unknown code is not a code: an item genuinely called "Rock & Roll" keeps its ampersand.
                if (code != null && applies(code)) {
                    flush()
                    apply(code)
                    i += 2
                    continue
                }
                emit(c)
                i++
            }
            flush()
            return root
        }

        private fun applies(code: Char): Boolean =
            code == CODE || code == 'r' || ChatFormatting.getByCode(code) != null

        private fun apply(code: Char) {
            if (code == CODE) {
                chroma = true
                return
            }
            if (code == 'r') {
                color = baseColor
                chroma = chromaAll
                clearFormats()
                return
            }
            val formatting = ChatFormatting.getByCode(code) ?: return
            if (formatting.isColor) {
                applyColor(formatting.color)
                return
            }
            when (formatting) {
                ChatFormatting.BOLD -> bold = true
                ChatFormatting.ITALIC -> italic = true
                ChatFormatting.UNDERLINE -> underlined = true
                ChatFormatting.STRIKETHROUGH -> strikethrough = true
                ChatFormatting.OBFUSCATED -> obfuscated = true
                // RESET is handled above; nothing else reaches here.
                else -> Unit
            }
        }

        /** Legacy behaviour, and the same for a hex colour: setting a colour also clears bold, italic and the rest. */
        private fun applyColor(rgb: Int?) {
            color = rgb
            chroma = false
            clearFormats()
        }

        private fun clearFormats() {
            bold = false
            italic = false
            underlined = false
            strikethrough = false
            obfuscated = false
        }

        private fun emit(c: Char) {
            if (chroma) {
                // One component per character, which is the whole cost of chroma and the reason it is opt-in.
                val flowing = Chroma.colorAt(phase, offset, width)
                root.append(Component.literal(c.toString()).setStyle(styleWith(flowing)))
            } else {
                run.append(c)
            }
            offset++
        }

        private fun flush() {
            if (run.isEmpty()) return
            root.append(Component.literal(run.toString()).setStyle(styleWith(color)))
            run.setLength(0)
        }

        /**
         * The current formats, with [colorOverride] as the colour.
         *
         * Only the flags that are *on* are set, so anything untouched stays null and inherits from whatever
         * the finished component is appended into — which is how a name ends up italic-free and rarity-tinted
         * without this having to know either.
         */
        private fun styleWith(colorOverride: Int?): Style {
            var style = Style.EMPTY
            if (colorOverride != null) style = style.withColor(colorOverride)
            if (bold) style = style.withBold(true)
            if (italic) style = style.withItalic(true)
            if (underlined) style = style.withUnderlined(true)
            if (strikethrough) style = style.withStrikethrough(true)
            if (obfuscated) style = style.withObfuscated(true)
            return style
        }
    }
}
