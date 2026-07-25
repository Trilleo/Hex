package net.trilleo.skyblock

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.trilleo.util.TextClean
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Resolves the Skyblock *island* — the thing the scoreboard cannot tell you.
 *
 * The scoreboard's location line names the *area* you are standing in (`Village`, `Community Center`), not the
 * island it belongs to (`Hub`), and it changes as you walk — so it is useless as the "which island is this
 * region on" key that regions and profile rules need. SkyHanni and Skyblocker hit the same wall and get around
 * it the same way: they ask Hypixel directly. This does too, through Hypixel's `/locraw`, whose reply is a
 * single line of JSON —
 *
 * ```
 * {"server":"mini123A","gametype":"SKYBLOCK","mode":"hub","map":"Hub"}
 * ```
 *
 * — where `map` is the island, the same across every area on it. (The heavier, non-intrusive alternative is the
 * Hypixel Mod API's location packet, which is what those mods prefer now; `/locraw` needs no extra dependency
 * or plugin-message plumbing, and it degrades to nothing on a server that does not answer.)
 *
 * **When it asks.** Islands on Hypixel are separate servers, so almost every island change arrives as a world
 * rejoin — which is the main trigger. As a safety net for the rare seamless transfer, a change of scoreboard
 * area with no rejoin re-asks, but no more often than [RERESOLVE_COOLDOWN_MS], so ordinary walking between a
 * hub's areas costs nothing. On a normal Skyblock session this sends exactly one `/locraw` per island.
 *
 * **How it keeps quiet.** The one JSON reply to a request it made is swallowed, so the player never sees it; a
 * `/locraw` the player typed by hand is left alone. It only asks once [Sidebar] confirms this is Skyblock, so a
 * non-Hypixel server never receives the command; if no reply comes it gives up after [MAX_ATTEMPTS] rather than
 * retrying forever. Everything is best-effort and wrapped — a surprise here can never throw into a tick or a
 * chat handler.
 *
 * Driven centrally from [net.trilleo.feature.Features]: [tick] each client tick (after [Sidebar], before the
 * readers of [SkyblockLocation.current]), [onJoin] on world join, [reset] on disconnect, and [onChatReceive]
 * ahead of the feature chat fan-out so the reply is swallowed before any reminder pattern sees it.
 */
object IslandResolver {
    private val LOGGER = LoggerFactory.getLogger("hex/skyblock")

    /** Wants to send a request but has not yet — waiting for the delay, the connection, or the Skyblock flag. */
    @Volatile
    private var pending = false

    /** A request is out and the reply has not come back. */
    @Volatile
    private var awaiting = false

    private var ticksUntilSend = 0
    private var ticksUntilTimeout = 0
    private var attemptsLeft = 0

    /** The last area seen, to notice a change that may mean a seamless transfer to another island. */
    private var lastArea: String? = null

    /** When the island was last resolved, to rate-limit the area-change re-ask. */
    private var lastResolveAtMillis = 0L

    /**
     * Whether a request is out or about to go out, so the island may still be coming.
     *
     * [SkyblockLocation.current] reads this to hold its area fallback back while a resolve is in progress —
     * without it, a fresh join would report the scoreboard area for the second `/locraw` takes to answer, and
     * everything watching the location would read that as an island change. A re-ask after the island is
     * already known does not blank it: [SkyblockLocation.island] keeps its last value across the re-ask, so
     * `current` stays on that island rather than flickering.
     */
    val stillResolving: Boolean get() = pending || awaiting

    /** Arms a fresh resolve for a world just joined: the island from the last server must not linger. */
    fun onJoin() {
        SkyblockLocation.acceptIsland(null)
        lastArea = null
        arm(SEND_DELAY_TICKS, MAX_ATTEMPTS)
    }

    /** Clears everything so the next server starts from a clean slate. */
    fun reset() {
        pending = false
        awaiting = false
        ticksUntilSend = 0
        ticksUntilTimeout = 0
        attemptsLeft = 0
        lastArea = null
        lastResolveAtMillis = 0L
    }

    private fun arm(delay: Int, attempts: Int) {
        pending = true
        awaiting = false
        ticksUntilSend = delay
        attemptsLeft = attempts
    }

    /** Sends a request when the moment is right, and gives up on one that goes unanswered. */
    fun tick(client: Minecraft) {
        // A request is out: wait for the reply, and retry (or give up) if it never comes.
        if (awaiting) {
            if (--ticksUntilTimeout <= 0) {
                awaiting = false
                if (attemptsLeft > 0) arm(RETRY_DELAY_TICKS, attemptsLeft)
            }
            return
        }

        // No rejoin, but the area changed — possibly a seamless transfer to another island. Confirm it, but
        // only if the island was resolved before (so a server that never answers is not re-asked forever) and
        // not more often than the cooldown (so walking a hub's areas does not send a command each time).
        val area = SkyblockLocation.area
        if (!pending && area != lastArea) {
            lastArea = area
            if (SkyblockLocation.island != null &&
                System.currentTimeMillis() - lastResolveAtMillis >= RERESOLVE_COOLDOWN_MS
            ) {
                arm(0, 1)
            }
        }

        if (!pending) return
        // Singleplayer has no Hypixel to ask; a non-Skyblock server would only answer "unknown command".
        if (client.isLocalServer) {
            pending = false
            return
        }
        if (!Sidebar.onSkyblock) return
        val connection = client.player?.connection ?: return
        if (--ticksUntilSend > 0) return

        pending = false
        attemptsLeft--
        val sent = runCatching { connection.sendCommand(LOCRAW_COMMAND) }
            .onFailure { LOGGER.warn("Failed to send /locraw for island detection", it) }
            .isSuccess
        if (sent) {
            awaiting = true
            ticksUntilTimeout = TIMEOUT_TICKS
        }
    }

    /**
     * Reads an incoming message, catching the `/locraw` reply. Returns false to swallow it — but only the
     * reply to a request this made; a `/locraw` the player ran themselves stays visible.
     */
    fun onChatReceive(message: Component): Boolean {
        val text = TextClean.strip(message.string)
        if (!looksLikeLocraw(text)) return true

        parseIsland(text)?.let {
            SkyblockLocation.acceptIsland(it)
            lastResolveAtMillis = System.currentTimeMillis()
            lastArea = SkyblockLocation.area
        }

        val ours = awaiting
        awaiting = false
        return !ours
    }

    /**
     * A cheap gate so the JSON is only parsed for a line that could plausibly be a `/locraw` reply. Keys on
     * `"server"`, which every reply carries (even a bare one with no game), so the reply to a request this made
     * is recognised — and swallowed — whatever island, or non-island, it turns out to describe.
     */
    private fun looksLikeLocraw(text: String): Boolean =
        text.startsWith("{") && text.endsWith("}") && text.contains("\"server\"")

    /** The island named by a `/locraw` reply, or null when it is not a Skyblock reply or names no island. */
    private fun parseIsland(json: String): String? = runCatching {
        val obj = JsonParser.parseString(json).asJsonObject
        // Only Skyblock has islands worth naming; guarding on gametype also rejects a reply from another game.
        if (!"SKYBLOCK".equals(stringField(obj, "gametype"), ignoreCase = true)) return@runCatching null
        stringField(obj, "map")?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun stringField(obj: JsonObject, key: String): String? =
        obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private const val LOCRAW_COMMAND = "locraw"

    /** A short beat after Skyblock is confirmed, to let the connection settle before the first request. */
    private const val SEND_DELAY_TICKS = 15

    /** Between a timed-out request and the next attempt. */
    private const val RETRY_DELAY_TICKS = 40

    /** How long to wait for a reply before treating the request as lost. Hypixel answers well inside this. */
    private const val TIMEOUT_TICKS = 100

    /** How many unanswered requests to make before giving up on a server that will not answer. */
    private const val MAX_ATTEMPTS = 3

    /** The floor between area-change re-asks, so heavy walking cannot turn into a stream of commands. */
    private const val RERESOLVE_COOLDOWN_MS = 15_000L
}
