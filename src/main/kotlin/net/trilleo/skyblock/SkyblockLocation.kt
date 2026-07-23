package net.trilleo.skyblock

import java.util.*

/**
 * Where on Skyblock the player currently is, read from the scoreboard sidebar.
 *
 * A view over [Sidebar], which owns the polling and the line extraction; this file is only the rule for
 * recognising the location line among them. It keeps the same caution it always had — it never reports a
 * location it is not confident about, because a wrong island is worse than none — and its callers still treat
 * null as "no opinion" rather than as an error.
 *
 * The sidebar is empty for the first seconds after joining, which is why [current] is null at first and
 * callers must poll rather than read once on join.
 */
object SkyblockLocation {

    /** The current island, lowercased and stripped (e.g. `"private island"`), or null when unknown. */
    @Volatile
    var current: String? = null
        private set

    /** Whether the sidebar looks like Skyblock's at all. Delegated, since it is a fact about the sidebar. */
    val onSkyblock: Boolean get() = Sidebar.onSkyblock

    /** Forgets the island, so a stale one cannot survive into the next server. Called by [Sidebar]. */
    internal fun reset() {
        current = null
    }

    /** Re-derives the island from a fresh set of sidebar lines. Called by [Sidebar] after each poll. */
    internal fun accept(lines: List<String>) {
        current = lines.firstNotNullOfOrNull(::locationOf)
    }

    /**
     * The island named on [line], or null if it names none.
     *
     * Hypixel marks the location line with a leading glyph — `⏣` for most areas, `ф` for the Crimson Isle —
     * which is a far more stable signal than the line's position, since the lines above it come and go with
     * events and profile state.
     */
    private fun locationOf(line: String): String? {
        val marker = line.indexOfFirst { it in LOCATION_MARKERS }
        if (marker < 0) return null
        return line.substring(marker + 1).trim().lowercase(Locale.ROOT).takeIf { it.isNotBlank() }
    }

    /** The glyphs Hypixel prefixes the location line with. */
    private val LOCATION_MARKERS = charArrayOf('⏣', 'ф')
}
