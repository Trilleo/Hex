package net.trilleo.config

import net.minecraft.client.Minecraft
import net.trilleo.skyblock.SkyblockLocation
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Activates the profile that matches where the player is, so a Skyblock setup and a survival setup do not
 * have to be swapped by hand every time.
 *
 * Evaluation is deferred rather than done on join, for two reasons. The obvious one is that Hypixel's
 * scoreboard — the only place the island is readable — is empty for the first seconds of a connection, so an
 * island rule cannot possibly resolve at join time. The subtler one is that switching profiles fires
 * [ConfigHandle.afterReload] hooks (freecam tears down the camera), and doing that mid-world-load is asking
 * for trouble. So joining opens a polling window, and the switch happens once something actually matches.
 *
 * Three guards keep this from being annoying rather than useful:
 *
 *  - a profile picked by hand wins for the rest of the session, so the mod never overrides a deliberate
 *    choice;
 *  - nothing is switched while the active profile has unsaved changes, because there is no screen open to
 *    prompt from and switching would destroy them silently;
 *  - a matching profile that is already active is not re-applied, so a server transfer within Hypixel (which
 *    fires several joins) does not thrash.
 */
object ProfileAutoSwitch {
    private val LOGGER = LoggerFactory.getLogger("hex/config")

    /** Ticks left in the window during which a rule may still resolve, or 0 when not looking. */
    private var ticksRemaining = 0

    /** Set once the player chooses a profile themselves; suppresses switching until they disconnect. */
    private var manualOverride = false

    /** The last island a decision was made for, so moving between islands re-evaluates but idling does not. */
    private var lastIsland: String? = null

    /** Opens the polling window. Any rule may resolve during it; island rules usually resolve late. */
    fun onJoin() {
        ticksRemaining = WINDOW_TICKS
        lastIsland = null
        SkyblockLocation.reset()
    }

    /** Clears session state so the next server starts from a clean slate. */
    fun onDisconnect() {
        ticksRemaining = 0
        manualOverride = false
        lastIsland = null
        SkyblockLocation.reset()
    }

    /** Records that the player chose a profile themselves, which outranks every rule until they disconnect. */
    fun noteManualSwitch() {
        manualOverride = true
    }

    /**
     * Looks for a matching profile, while there is reason to.
     *
     * Runs every tick but does almost nothing most of them: outside the join window it only reacts to the
     * island changing, which is the one context that can shift without a reconnect.
     */
    fun tick(client: Minecraft) {
        if (manualOverride) return
        if (client.level == null) return

        SkyblockLocation.tick(client)

        val island = SkyblockLocation.current
        val islandChanged = island != null && island != lastIsland
        if (ticksRemaining <= 0 && !islandChanged) return
        if (ticksRemaining > 0) ticksRemaining--
        if (island != null) lastIsland = island

        val target = runCatching { matchingProfile(client, island) }
            .onFailure { LOGGER.error("Failed to evaluate auto-switch rules", it) }
            .getOrNull()
            ?: return

        if (target == ConfigProfiles.settings.active) {
            // Already there. Stop looking, or every later tick would re-check for no reason.
            ticksRemaining = 0
            return
        }

        if (ProfileDirtyTracker.isDirty) {
            // Refuse rather than destroy work. Said once, then dropped, so it cannot spam the window.
            ticksRemaining = 0
            ConfigProfiles.notify(
                "Profile '$target' matches this server, but '${ConfigProfiles.settings.active}' has unsaved changes — switch manually to apply it",
            )
            return
        }

        ticksRemaining = 0
        // Deferred by a tick: switching fires reload hooks that touch the camera and the key state, which
        // must not happen in the middle of the tick that decided to switch.
        client.execute {
            if (ConfigProfiles.switchTo(target)) {
                LOGGER.info("Auto-switched to profile '{}'", target)
                ConfigProfiles.notify("Switched to profile '$target' for this server")
                HexConfigScreens.rebuild()
            }
        }
    }

    /**
     * The first profile whose rule matches the current context, or null.
     *
     * First rather than best: ordering is the tiebreak when two profiles claim the same context, and it is
     * the profile list's own order, so it is at least something the user can see and change.
     */
    private fun matchingProfile(client: Minecraft, island: String?): String? =
        ConfigProfiles.entries().firstOrNull { entry ->
            val rule = entry.autoSwitch ?: return@firstOrNull false
            when (rule.kind) {
                AutoSwitchKind.SINGLEPLAYER -> client.isLocalServer
                AutoSwitchKind.SERVER -> !client.isLocalServer && matchesHost(host(client), rule.pattern)
                AutoSwitchKind.SKYBLOCK_ISLAND -> island != null && island == rule.pattern.lowercase(Locale.ROOT)
            }
        }?.name

    /** The connected server's host, without its port and lowercased, or null in singleplayer. */
    private fun host(client: Minecraft): String? = client.currentServer?.ip
        ?.substringBefore(':')
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotEmpty() }

    /**
     * Whether [host] is [pattern] or a subdomain of it.
     *
     * Matching on the dot boundary rather than a bare `endsWith` is what makes `hypixel.net` match
     * `mc.hypixel.net` without also matching `nothypixel.net`.
     */
    private fun matchesHost(host: String?, pattern: String): Boolean {
        if (host == null || pattern.isBlank()) return false
        val wanted = pattern.trim().lowercase(Locale.ROOT)
        return host == wanted || host.endsWith(".$wanted")
    }

    /** Ten seconds — long enough for Hypixel's scoreboard to populate, short enough not to linger. */
    private const val WINDOW_TICKS = 200
}
