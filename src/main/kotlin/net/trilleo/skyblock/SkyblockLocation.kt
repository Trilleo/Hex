package net.trilleo.skyblock

import java.util.*

/**
 * Where on Skyblock the player currently is, from the two things Hypixel is willing to tell a client.
 *
 * There are two different questions here, and they have two different sources:
 *
 *  - the **island** (`"hub"`, `"private island"`, `"dwarven mines"`) — the whole place, stable while you walk
 *    around on it. Hypixel does *not* put this on the scoreboard; it is resolved from Hypixel's own location
 *    data by [IslandResolver], the same way SkyHanni and Skyblocker get it.
 *  - the **area** (`"village"`, `"community center"`, `"royal mines"`) — the patch of ground you are standing
 *    on, which changes as you move. This is what the scoreboard's location line actually names, and [Sidebar]
 *    reads it here.
 *
 * The island is the honest answer to "which island is this region on", so [current] is the island when it is
 * known and the area only as a fallback — a fallback that matters, because it is what regions had before the
 * island could be resolved and it keeps them working if the resolver ever comes up empty (off Hypixel, or if
 * `/locraw` is unavailable). Callers still treat null as "no opinion" rather than as an error: it never reports
 * a place it is not confident about, because a wrong island is worse than none.
 *
 * Both are empty for the first seconds after joining — the scoreboard is blank and the resolver has not
 * answered yet — which is why callers poll rather than read once on join.
 */
object SkyblockLocation {

    /**
     * The island, lowercased (`"hub"`, `"private island"`), or null when it has not been resolved.
     *
     * Set by [IslandResolver] from Hypixel's location data, not from the scoreboard. This is the value that
     * stays put while the player walks across a hub's many areas.
     */
    @Volatile
    var island: String? = null
        private set

    /**
     * The scoreboard's location line, lowercased (`"village"`, `"dwarven mines"`), or null when absent.
     *
     * This is the *area*, not the island: on the Hub it reads `"village"`, `"community center"` and so on, and
     * changes as you move. Kept because it is the fallback for [current] and the signal [IslandResolver] uses
     * to notice a possible island change without a rejoin.
     */
    @Volatile
    var area: String? = null
        private set

    /**
     * The island the player is on: the resolved [island] when known, otherwise the scoreboard [area].
     *
     * This is what regions, reminders, profile auto-switch and command suggestions all read. Preferring the
     * island makes "hub" mean the whole hub rather than whichever corner of it the scoreboard happens to name;
     * falling back to the area is what keeps those features working anywhere the resolver has nothing to say
     * (off Hypixel, or if `/locraw` goes unanswered).
     *
     * **The area fallback is held back while [IslandResolver] still has a request in flight**, so a fresh join
     * resolves straight to `hub` rather than flashing `village` for the second `/locraw` takes to answer.
     * Reporting the area during that window would read as a real island change to everything watching this —
     * an island enter on the area, then a leave off it the moment the true island lands — which is exactly the
     * false edge the readers of this value take pains to avoid.
     */
    val current: String? get() = island ?: area.takeUnless { IslandResolver.stillResolving }

    /** Whether the sidebar looks like Skyblock's at all. Delegated, since it is a fact about the sidebar. */
    val onSkyblock: Boolean get() = Sidebar.onSkyblock

    /** Forgets both readings, so a stale one cannot survive into the next server. Called by [Sidebar]. */
    internal fun reset() {
        island = null
        area = null
    }

    /** Records the island resolved by [IslandResolver]. Null clears it, e.g. on leaving a server. */
    internal fun acceptIsland(name: String?) {
        island = name
    }

    /** Re-derives the area from a fresh set of sidebar lines. Called by [Sidebar] after each poll. */
    internal fun acceptArea(lines: List<String>) {
        area = lines.firstNotNullOfOrNull(::areaOf)
    }

    /**
     * The area named on [line], or null if it names none.
     *
     * Hypixel marks the location line with a leading glyph — `⏣` for most areas, `ф` for the Crimson Isle —
     * which is a far more stable signal than the line's position, since the lines above it come and go with
     * events and profile state.
     */
    private fun areaOf(line: String): String? {
        val marker = line.indexOfFirst { it in LOCATION_MARKERS }
        if (marker < 0) return null
        return line.substring(marker + 1).trim().lowercase(Locale.ROOT).takeIf { it.isNotBlank() }
    }

    /** The glyphs Hypixel prefixes the location line with. */
    private val LOCATION_MARKERS = charArrayOf('⏣', 'ф')
}
