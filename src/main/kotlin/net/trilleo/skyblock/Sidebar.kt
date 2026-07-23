package net.trilleo.skyblock

import net.minecraft.client.Minecraft
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.Scoreboard
import net.trilleo.skyblock.Sidebar.POLL_INTERVAL_TICKS
import net.trilleo.util.TextClean
import java.util.*

/**
 * The Skyblock scoreboard sidebar, read once and handed to everything that interprets it.
 *
 * Hypixel puts a surprising amount of state on the sidebar and nowhere else a client can see: the island, the
 * Skyblock date, the Skyblock time of day, and whatever event is running. Parsing it is the only way to know
 * any of that, which makes this inherently fragile — the layout is Hypixel's to change, and it has changed
 * before — so it is deliberately kept as an isolated, best-effort reader:
 *
 *  - every read is wrapped, so a surprise in the sidebar cannot throw into a tick handler;
 *  - a line nothing recognises is simply not recognised; nothing here guesses;
 *  - the consumers below treat null as "cannot tell" rather than as an answer.
 *
 * **Why the walk lives here rather than in [SkyblockLocation].** It used to be the location reader's own, back
 * when the island was the only thing anyone wanted off the sidebar. There are now two interpretations of the
 * same lines, and rebuilding every entry's string twice a second to answer two questions instead of one would
 * be pure waste — the extraction is the expensive part and the interpretation is a regex. So this owns the
 * poll and the strings, and [SkyblockLocation] and [SkyblockCalendar] are views over them.
 *
 * Fanning out to those two directly, rather than exposing a listener list, is a deliberate simplification:
 * there are two of them, they live in this package, and they are *defined* as interpretations of this — a
 * registry would be ceremony around a pair of calls.
 *
 * Ticked from the `END_CLIENT_TICK` block in [net.trilleo.feature.Features], outside any feature's enabled
 * check, for the reason set out on [net.trilleo.skyblock.item.HeldItem]: several features read this now, and a
 * shared cache must not be at the mercy of one feature's master switch. It was previously ticked from
 * [net.trilleo.config.ProfileAutoSwitch], which stopped ticking it the moment the player picked a profile by
 * hand — freezing the island for the rest of the session for every other reader too.
 */
object Sidebar {

    /** Whether the sidebar looks like Skyblock's at all. */
    @Volatile
    var onSkyblock: Boolean = false
        private set

    /**
     * The sidebar's lines, cleaned and in display order (top to bottom), or empty when there is no sidebar.
     *
     * Replaced wholesale rather than mutated, so a reader on another thread always sees one complete poll's
     * worth of lines rather than a half-rebuilt list.
     */
    @Volatile
    var lines: List<String> = emptyList()
        private set

    private var ticksUntilPoll = 0

    /** Forgets everything, here and in both views, so a stale read cannot survive into the next server. */
    fun reset() {
        onSkyblock = false
        lines = emptyList()
        ticksUntilPoll = 0
        SkyblockLocation.reset()
        SkyblockCalendar.reset()
    }

    /**
     * Re-reads the sidebar every [POLL_INTERVAL_TICKS] ticks and refreshes both views.
     *
     * Throttled because parsing walks every sidebar line and rebuilds strings. One second is comfortably fast
     * enough for everything read off it: the island changes on the order of minutes, and the fastest-moving
     * field — the Skyblock clock — advances ten of its minutes every seven real seconds.
     */
    fun tick(client: Minecraft) {
        if (--ticksUntilPoll > 0) return
        ticksUntilPoll = POLL_INTERVAL_TICKS
        runCatching { read(client) }.onFailure {
            // Fail soft and keep the previous answers: a malformed sidebar is not evidence that anything
            // actually changed.
            onSkyblock = false
        }
    }

    private fun read(client: Minecraft) {
        val scoreboard: Scoreboard = client.level?.scoreboard ?: return clear()
        val objective: Objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return clear()

        val title = TextClean.strip(objective.displayName.string)
        onSkyblock = title.uppercase(Locale.ROOT).contains("SKYBLOCK")
        if (!onSkyblock) return clear(keepFlag = true)

        // Sidebar lines carry their text on the owner's team prefix/suffix rather than on the score itself,
        // and are ordered by score descending — the same way vanilla draws them.
        lines = scoreboard.listPlayerScores(objective)
            .asSequence()
            .filter { !it.isHidden }
            .sortedByDescending { it.value }
            .map { entry ->
                val team = scoreboard.getPlayersTeam(entry.owner)
                val prefix = team?.playerPrefix?.string.orEmpty()
                val suffix = team?.playerSuffix?.string.orEmpty()
                TextClean.strip(prefix + suffix)
            }
            .filter { it.isNotBlank() }
            .toList()

        SkyblockLocation.accept(lines)
        SkyblockCalendar.accept(lines)
    }

    private fun clear(keepFlag: Boolean = false) {
        lines = emptyList()
        if (!keepFlag) onSkyblock = false
        SkyblockLocation.reset()
        SkyblockCalendar.reset()
    }

    private const val POLL_INTERVAL_TICKS = 20
}
