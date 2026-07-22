package net.trilleo.util

/**
 * Packed ARGB colours from the `"#RRGGBB"` / `"#AARRGGBB"` strings the configs carry.
 *
 * Colours are stored as strings rather than packed ints so the JSON stays readable for anyone editing a config
 * file by hand — see [net.trilleo.config.ColorEntry] — which means every consumer needs this conversion.
 */
object HexColor {

    /** Full alpha, for a value written without one. */
    const val OPAQUE: Int = 0xFF000000.toInt()

    /**
     * Parses `"#RRGGBB"` or `"#AARRGGBB"` (the `#` optional) into a packed ARGB int, or null when it is not a
     * hex colour.
     *
     * Parsed as a `Long` before narrowing because `"FF808080"` overflows a signed `Int` and would otherwise
     * fail to parse at all.
     */
    fun parse(text: String): Int? = text.removePrefix("#").toLongOrNull(16)?.toInt()

    /**
     * [parse], defaulting to [fallback] and forcing full alpha unless the value supplied one.
     *
     * For a render path, where a malformed colour must still draw something: an invisible panel is far harder
     * to diagnose than a wrongly coloured one.
     */
    fun parseOrDefault(text: String, fallback: Int, alpha: Boolean = true): Int {
        val parsed = parse(text) ?: return fallback
        return if (alpha) parsed else parsed or OPAQUE
    }
}
