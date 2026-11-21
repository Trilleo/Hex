package net.trilleo.skyblock

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.trilleo.util.TextClean

/**
 * Which Skyblock profile the player is on: its Hypixel profile ID, its name, and its game mode.
 *
 * Each comes from a different place, because Hypixel never puts all three in one:
 *
 *  - **ID** — the `Profile ID: …` chat line Hypixel sends when the player joins Skyblock and on every server
 *    change inside it. Nothing else a client can read carries it.
 *  - **Name** — the "You are playing on profile" / "Your profile was changed to" chat messages, and the
 *    player list's `Profile:` widget as a fallback for a session where the message was missed. The widget is
 *    skipped in the Rift, where Hypixel shows the name reversed.
 *  - **Game mode** — the sidebar title's symbol, or the player list widget's when the sidebar cannot tell.
 *
 * A profile switch clears the ID at once rather than waiting for the next one: anything read after the switch
 * belongs to the new profile, and attributing it to the old ID would upload it to the wrong collection.
 * [generation] counts those switches, so a reader that captured "profile unknown" can tell whether the ID
 * that arrives later is still the profile it read.
 *
 * Ticked and fed chat centrally from [net.trilleo.feature.Features], outside any feature's enabled check, for
 * the reason set out on [net.trilleo.skyblock.item.HeldItem].
 */
object SkyblockProfile {

    /** The Hypixel profile ID, 32 lowercase hex characters, or null until Hypixel has sent it. */
    @Volatile
    var id: String? = null
        private set

    /** The profile's name as Hypixel shows it (`"Apple"`), or null when unknown. */
    @Volatile
    var name: String? = null
        private set

    /** The profile's game mode, or null when nothing has said yet. */
    @Volatile
    var gameMode: SkyblockGameMode? = null
        private set

    /** Bumped on every profile switch and disconnect; see the class comment. */
    @Volatile
    var generation: Int = 0
        private set

    private var ticksUntilPoll = 0

    /** Forgets everything; the next server may be a different account or profile. */
    fun reset() {
        id = null
        name = null
        gameMode = null
        generation++
        ticksUntilPoll = 0
    }

    /** Observes a chat line. Never swallows anything. */
    fun onChatReceive(message: Component) {
        val text = TextClean.strip(message.string)
        ProfileText.profileId(text)?.let {
            id = it
            return
        }
        val (profileName, source) = ProfileText.profileNameFromChat(text) ?: return
        if (source == ProfileText.NameSource.CHANGED && profileName != name) {
            // A switch: the old ID and mode no longer describe what the player is on.
            id = null
            gameMode = null
            generation++
        }
        name = profileName
    }

    /** Re-reads the game mode, and the name if no chat message has given it, once a second. */
    fun tick(client: Minecraft) {
        if (--ticksUntilPoll > 0) return
        ticksUntilPoll = POLL_INTERVAL_TICKS
        if (!Sidebar.onSkyblock) return
        runCatching {
            val tab = TabList.lines.firstNotNullOfOrNull { ProfileText.tabProfile(it) }
            val fromSidebar = ProfileText.gameMode(Sidebar.title, Sidebar.lines)
            // A symbol on the player list always describes the player's own profile, so it wins. Its absence is
            // weaker evidence than the sidebar's symbol, so a plain widget only decides when the sidebar cannot
            // (a guest visit).
            gameMode = when {
                tab != null && tab.second != SkyblockGameMode.CLASSIC -> tab.second
                fromSidebar != null -> fromSidebar
                tab != null -> SkyblockGameMode.CLASSIC
                else -> gameMode
            }
            if (name == null && tab != null && !inRift()) name = tab.first
        }
    }

    private fun inRift(): Boolean = SkyblockLocation.current?.contains("rift", ignoreCase = true) == true

    private const val POLL_INTERVAL_TICKS = 20
}
