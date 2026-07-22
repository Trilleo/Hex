package net.trilleo.skyblock

import net.minecraft.client.Minecraft
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.Scoreboard
import java.util.Locale

/**
 * Where on Skyblock the player currently is, read from the scoreboard sidebar.
 *
 * Hypixel puts the island name on the sidebar and nowhere else a client can see, so parsing it is the only
 * way to know. That makes this inherently fragile — the layout is Hypixel's to change, and it has changed
 * before — so it is deliberately kept as an isolated, best-effort reader:
 *
 *  - nothing else depends on it beyond [net.trilleo.config.ProfileAutoSwitch], which treats a null location
 *    as "no opinion" rather than as an error;
 *  - every read is wrapped so a surprise in the sidebar cannot throw into a tick handler;
 *  - it never reports a location it is not confident about, since a wrong island is worse than none.
 *
 * The sidebar is empty for the first seconds after joining, which is why [current] is null at first and
 * callers must poll rather than read once on join.
 */
object SkyblockLocation {

    /** The current island, lowercased and stripped (e.g. `"private island"`), or null when unknown. */
    @Volatile
    var current: String? = null
        private set

    /** Whether the sidebar looks like Skyblock's at all. */
    @Volatile
    var onSkyblock: Boolean = false
        private set

    private var ticksUntilPoll = 0

    /** Forgets everything, so a stale island cannot survive into the next server. */
    fun reset() {
        current = null
        onSkyblock = false
        ticksUntilPoll = 0
    }

    /**
     * Re-reads the sidebar every [POLL_INTERVAL_TICKS] ticks.
     *
     * Throttled because parsing walks every sidebar line and rebuilds strings, and the location changes on
     * the order of minutes — reading it twenty times a second would be pure waste.
     */
    fun tick(client: Minecraft) {
        if (--ticksUntilPoll > 0) return
        ticksUntilPoll = POLL_INTERVAL_TICKS
        runCatching { read(client) }.onFailure {
            // Fail soft and keep the previous answer: a malformed sidebar is not evidence the player moved.
            onSkyblock = false
        }
    }

    private fun read(client: Minecraft) {
        val scoreboard: Scoreboard = client.level?.scoreboard ?: return clearState()
        val objective: Objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return clearState()

        val title = clean(objective.displayName.string)
        onSkyblock = title.uppercase(Locale.ROOT).contains("SKYBLOCK")
        if (!onSkyblock) return clearState(keepFlag = true)

        // Sidebar lines carry their text on the owner's team prefix/suffix rather than on the score itself,
        // and are ordered by score descending — the same way vanilla draws them.
        val lines = scoreboard.listPlayerScores(objective)
            .asSequence()
            .filter { !it.isHidden }
            .sortedByDescending { it.value }
            .map { entry ->
                val team = scoreboard.getPlayersTeam(entry.owner)
                val prefix = team?.playerPrefix?.string.orEmpty()
                val suffix = team?.playerSuffix?.string.orEmpty()
                clean(prefix + suffix)
            }
            .filter { it.isNotBlank() }
            .toList()

        current = lines.firstNotNullOfOrNull { locationOf(it) }
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

    private fun clearState(keepFlag: Boolean = false) {
        current = null
        if (!keepFlag) onSkyblock = false
    }

    /**
     * Strips formatting codes and the invisible characters Hypixel appends.
     *
     * Those trailing zero-width and non-breaking characters differ per line and per tick — they exist to
     * make the scoreboard entries unique — so leaving them in would mean no two reads ever compared equal.
     */
    private fun clean(raw: String): String {
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            when {
                ch == '§' -> i++ // also skip the code character that follows
                ch == ' ' || ch == '​' || ch == '﻿' -> Unit
                ch.isISOControl() -> Unit
                else -> out.append(ch)
            }
            i++
        }
        return out.toString().trim()
    }

    private const val POLL_INTERVAL_TICKS = 20

    /** The glyphs Hypixel prefixes the location line with. */
    private val LOCATION_MARKERS = charArrayOf('⏣', 'ф')
}
