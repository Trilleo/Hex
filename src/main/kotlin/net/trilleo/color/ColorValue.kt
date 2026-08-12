package net.trilleo.color

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.trilleo.util.Chroma
import net.trilleo.util.HexColor
import java.util.*

/**
 * What a colour setting in this mod may hold, and how that text becomes a colour on screen.
 *
 * Every colour the player can choose — an entity glow, a chat highlight, a region box, an item name, a note —
 * is stored as one of exactly three things:
 *
 *  - **`"#RRGGBB"`** or **`"#AARRGGBB"`** — a fixed colour. Stored as text rather than a packed int so a config
 *    file stays readable and editable by hand, which is the same bargain [HexColor] documents.
 *  - **`"chroma"`** — the flowing rainbow. Not a colour but a *mode*, and it lives in the same field as one on
 *    purpose: chroma answers the question "what colour is this?", so making it a second setting beside the
 *    colour only created two controls that could disagree. See [isChroma].
 *  - **`""`** — no colour at all, for settings where "leave it alone" is a real answer: an item that is only
 *    being renamed, a note with no colour of its own.
 *
 * `"chroma"` can never collide with a hex value — `h`, `r`, `o` and `m` are not hex digits — so the three cases
 * are distinguishable without a marker character or a wrapper type.
 *
 * ### Reading a value
 *
 * Render paths call [resolve], which folds all three cases into one packed ARGB int and never fails. Anything
 * that must know *which* case it has (a cache deciding whether it may keep a value for more than one frame,
 * say) asks [isChroma] first — that is a pure string test and does not build anything.
 *
 * ### Adding a colour setting to a feature
 *
 * Use `ConfigCategory.Builder.color`, which produces the row that opens
 * [net.trilleo.color.gui.ColorPickerScreen]. Do not hand-roll a colour control: every colour in the mod is
 * picked through that one screen, so a new feature inherits the palettes, the recent colours, the eyedropper
 * fields and chroma for free — and stays consistent with the rest of the mod without trying to.
 */
object ColorValue {

    /** The value that means "flow through the rainbow". */
    const val CHROMA: String = "chroma"

    /** The value that means "no colour of its own". */
    const val NONE: String = ""

    /** `#AARRGGBB` — the longest a stored value gets, and the edit field's limit. */
    const val MAX_LENGTH: Int = 9

    /** Strips the alpha byte, for the many APIs that take plain RGB. */
    const val RGB_MASK: Int = 0xFFFFFF

    /** Whether [spec] asks for chroma. Case- and whitespace-insensitive, since it can be typed by hand. */
    fun isChroma(spec: String): Boolean = spec.trim().equals(CHROMA, ignoreCase = true)

    /** Whether [spec] asks for no colour at all. */
    fun isNone(spec: String): Boolean = spec.isBlank()

    /**
     * The packed ARGB of a fixed colour, or null when [spec] is chroma, empty, or not a colour.
     *
     * Stricter than [HexColor.parse], which accepts any run of hex digits: this demands exactly six or eight,
     * so a half-typed `"#FF"` reads as "not a colour yet" rather than as a very dark blue. That distinction is
     * what lets an edit field reject a value instead of applying a nonsense one on the way to a good one.
     */
    fun parse(spec: String): Int? {
        val body = spec.trim().removePrefix("#")
        if (body.length != 6 && body.length != 8) return null
        if (!body.all { Character.digit(it, 16) >= 0 }) return null
        // Through a Long first: "FF808080" overflows a signed Int and would otherwise fail to parse at all.
        val value = body.toLongOrNull(16)?.toInt() ?: return null
        return if (body.length == 6) value or HexColor.OPAQUE else value
    }

    /**
     * [argb] as the text a config file carries — `"#RRGGBB"`, or `"#AARRGGBB"` when [alpha] is set.
     *
     * Uppercase and always prefixed, so two settings holding the same colour hold the same string and a row's
     * "is this still the default?" test is a plain comparison.
     */
    fun format(argb: Int, alpha: Boolean): String = if (alpha) {
        String.format(Locale.ROOT, "#%08X", argb)
    } else {
        String.format(Locale.ROOT, "#%06X", argb and RGB_MASK)
    }

    /**
     * [spec] in its canonical spelling, or [spec] itself when it is chroma or empty.
     *
     * What a picker writes back and what a text field is corrected to, so `ff8800`, `#ff8800` and `#FF8800`
     * all end up stored identically.
     */
    fun normalize(spec: String, alpha: Boolean): String = when {
        isChroma(spec) -> CHROMA
        isNone(spec) -> NONE
        else -> parse(spec)?.let { format(it, alpha) } ?: spec.trim()
    }

    /**
     * The colour to draw right now for [spec], falling back to [fallback] for an empty or unparseable value.
     *
     * This is the render path's single entry point, and it cannot fail — an invisible panel is far harder to
     * diagnose than a wrongly coloured one, which is the same reasoning [HexColor.parseOrDefault] gives.
     *
     * @param seconds how long one full trip through the rainbow takes, when [spec] is chroma. Each feature
     *   that can flow keeps its own, exactly as item names and chat highlights already do.
     * @param offset how far along a run this colour sits, in characters. Only text has one; a single-colour
     *   surface — a glow, a box, a panel — leaves it at zero and simply changes colour over time.
     */
    fun resolve(spec: String, fallback: Int, seconds: Double = Chroma.SECONDS_DEFAULT, offset: Int = 0): Int {
        if (isChroma(spec)) return Chroma.color(offset, seconds, Chroma.WIDTH_DEFAULT) or HexColor.OPAQUE
        return parse(spec) ?: fallback
    }

    /** [argb] with its alpha byte replaced by [a] (`0..255`). */
    fun withAlpha(argb: Int, a: Int): Int = (argb and RGB_MASK) or ((a and 0xFF) shl 24)

    /** The alpha byte of [argb], as `0..255`. */
    fun alphaOf(argb: Int): Int = (argb ushr 24) and 0xFF

    /**
     * A colour offered in the picker: the value it writes, and the name shown while it is hovered.
     *
     * The name is a [Component] rather than a string because it is language — the picker is one of the few
     * screens a player opens without knowing what they are looking for, and "Dark aqua" is the whole reason a
     * swatch is more useful than the digits behind it.
     */
    class Swatch(
        val spec: String,
        val rgb: Int,
        val name: Component,
        /**
         * The `&` code that writes this colour into text, for the sixteen that have one.
         *
         * Null everywhere else. The picker never reads it — it deals in values, not codes — but the notebook
         * editor's palette does, which is what lets both read the same table instead of keeping two lists of
         * the same sixteen colours in step by hand.
         */
        val code: Char? = null,
    )

    /**
     * Minecraft's sixteen colour codes, in code order (`&0` first, `&f` last).
     *
     * Derived from [ChatFormatting] rather than written out, so the list is the game's own and cannot drift
     * from it. They are here because a Skyblock player thinks in these colours: Hypixel writes every rarity,
     * every stat and every broadcast in one of them, so "the same gold the legendary items use" is a thing a
     * player wants and would otherwise have to look up the digits for.
     *
     * Names come from `hex.color.vanilla.<name>` rather than from any vanilla key, because the game has no
     * translation for a *format* colour — its `color.minecraft.*` keys are dye colours, which are a different
     * and only partly overlapping set.
     */
    val VANILLA: List<Swatch> by lazy {
        ChatFormatting.values()
            .filter { it.isColor }
            .map { formatting ->
                // Enum.name rather than ChatFormatting.getName(): the two agree for every constant, and
                // Kotlin resolves the bare `name` to the enum's own property regardless.
                val id = formatting.name.lowercase(Locale.ROOT)
                val rgb = formatting.color ?: 0
                Swatch(
                    format(rgb, alpha = false),
                    rgb,
                    Component.translatable("hex.color.vanilla.$id"),
                    formatting.char,
                )
            }
    }

    /**
     * A dozen colours Minecraft has no code for.
     *
     * Deliberately *not* another arrangement of the sixteen: the whole reason to offer presets beside them is
     * to cover what they cannot say. Chosen to stay legible against both a dark HUD and a bright sky, which
     * rules out the very dark and the very pale.
     */
    val PRESETS: List<Swatch> by lazy {
        listOf(
            "orange" to 0xFF8800,
            "amber" to 0xFFC400,
            "lime" to 0xB6FF00,
            "mint" to 0x7FFFD4,
            "teal" to 0x00C8B4,
            "sky" to 0x4FC3F7,
            "violet" to 0x9B6DFF,
            "magenta" to 0xE040FB,
            "pink" to 0xFF7EB6,
            "coral" to 0xFF6B5A,
            "crimson" to 0xC81E3C,
            "slate" to 0x8A97A8,
        ).map { (id, rgb) ->
            Swatch(format(rgb, alpha = false), rgb, Component.translatable("hex.color.preset.$id"))
        }
    }
}
