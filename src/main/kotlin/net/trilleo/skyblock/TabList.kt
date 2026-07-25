package net.trilleo.skyblock

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.world.level.GameType
import net.trilleo.skyblock.TabList.ORDER
import net.trilleo.skyblock.TabList.POLL_INTERVAL_TICKS
import net.trilleo.util.TextClean

/**
 * The player list — the tab list — read once and handed to everything that interprets it.
 *
 * The companion to [Sidebar], and the reason it exists: Hypixel puts far more on the player list than on the
 * scoreboard, in *widgets* built out of fake players whose display names carry the text. The sidebar names
 * whatever the current island wants to show and nothing else, so most Skyblock events never reach it — the
 * player list carries an `Event:` widget on every island, with the event's name and how long is left. Reading
 * it is the only way to know an event is running while standing somewhere that does not mention it.
 *
 * **Why the lines come out of packets rather than the screen.** The widget text arrives in the player-info
 * packet and lives in [net.minecraft.client.multiplayer.ClientPacketListener.getListedOnlinePlayers], whether
 * or not the player is holding Tab. So this never touches rendering, and nothing here depends on the list
 * being on screen.
 *
 * **Order matters, so it is vanilla's order.** A widget is a header line followed by its own lines, which only
 * hangs together in display order — [ORDER] is a faithful copy of the comparator
 * [net.minecraft.client.gui.components.PlayerTabOverlay] sorts by, built from public getters. Copying four
 * comparisons is deliberately preferred to a mixin on a private field: the mod would then fail to load
 * outright if the field were ever renamed, and the whole point of a best-effort reader is that it degrades
 * instead.
 *
 * Held to the same standard as [Sidebar], for the same reason — the layout is Hypixel's to change:
 *
 *  - every read is wrapped, so a surprise in the list cannot throw into a tick handler;
 *  - a line nothing recognises is simply not recognised; nothing here guesses;
 *  - the consumer treats an empty list as "cannot tell" rather than as "no event".
 *
 * **Blank lines are kept**, unlike [Sidebar]'s. Hypixel separates one widget from the next with an empty
 * entry, so a blank is the end of a widget — information, not noise, to whatever is reading the lines that
 * follow a header.
 *
 * Ticked from the `END_CLIENT_TICK` block in [net.trilleo.feature.Features] right after [Sidebar], outside any
 * feature's enabled check, for the reason set out on [net.trilleo.skyblock.item.HeldItem]: a shared cache must
 * not be at the mercy of one feature's master switch. Reading is skipped entirely off Skyblock — the widgets
 * are Skyblock's, and a vanilla server should not pay for eighty strings a second that nothing will read.
 */
object TabList {

    /**
     * The player list's lines, cleaned and in display order, or empty when there is nothing to read.
     *
     * Replaced wholesale rather than mutated, so a reader on another thread always sees one complete poll's
     * worth of lines rather than a half-rebuilt list.
     */
    @Volatile
    var lines: List<String> = emptyList()
        private set

    private var ticksUntilPoll = 0

    /** Forgets everything, so a stale read cannot survive into the next server. */
    fun reset() {
        lines = emptyList()
        ticksUntilPoll = 0
    }

    /**
     * Re-reads the player list every [POLL_INTERVAL_TICKS] ticks and hands the lines to [SkyblockEvents].
     *
     * Throttled for the same reason [Sidebar] is, and to the same one second: the work is eighty component
     * strings and eighty [TextClean.strip] calls, and nothing read off here moves faster than a countdown
     * measured in minutes.
     */
    fun tick(client: Minecraft) {
        if (--ticksUntilPoll > 0) return
        ticksUntilPoll = POLL_INTERVAL_TICKS
        // The widgets only exist on Skyblock, and the sidebar has already answered that question this tick.
        if (!Sidebar.onSkyblock) {
            if (lines.isNotEmpty()) lines = emptyList()
            return
        }
        runCatching { read(client) }.onFailure {
            // Fail soft and keep the previous lines: a malformed list is not evidence that anything changed,
            // and the events resolver ages its own claims out on a timer regardless.
        }
    }

    private fun read(client: Minecraft) {
        val connection = client.connection ?: return
        val entries = connection.listedOnlinePlayers
        if (entries.isEmpty()) return

        lines = entries.asSequence()
            .sortedWith(ORDER)
            // Vanilla's own cap. Hypixel fills exactly four columns of twenty, so anything past this is a
            // list too long to be the widget layout this reads.
            .take(MAX_ENTRIES)
            .map { info -> TextClean.strip(info.tabListDisplayName?.string ?: info.profile.name()) }
            .toList()

        SkyblockEvents.acceptTabList(lines)
    }

    /**
     * Vanilla's player-list ordering, copied from `PlayerTabOverlay.PLAYER_COMPARATOR`: the server's declared
     * order first, then spectators last, then team, then name case-insensitively.
     *
     * Every part of it is public API on [PlayerInfo], which is what makes the copy preferable to reaching for
     * the field itself. If vanilla ever reorders the list, this reads the old order — a widget's lines would
     * still be contiguous, since Hypixel's own grouping is what the order is derived from.
     */
    private val ORDER: Comparator<PlayerInfo> = compareBy<PlayerInfo>(
        { it.tabListOrder },
        { if (it.gameMode == GameType.SPECTATOR) 1 else 0 },
        { it.team?.name.orEmpty() },
    ).thenComparing({ info: PlayerInfo -> info.profile.name() }, String.CASE_INSENSITIVE_ORDER)

    /** Four columns of twenty, the most vanilla will draw and the most Hypixel fills. */
    private const val MAX_ENTRIES = 80

    private const val POLL_INTERVAL_TICKS = 20
}
