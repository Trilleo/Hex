package net.trilleo.reminder

import net.trilleo.reminder.model.TriggerKind
import net.trilleo.skyblock.SkyblockLocation
import net.trilleo.skyblock.item.HeldItem

/**
 * Watches the world for the edges that arm a reminder.
 *
 * Both sources it reads are polled caches maintained elsewhere — [SkyblockLocation] every twenty ticks, and
 * [HeldItem] on stack identity — so this adds two reference comparisons per tick and nothing else. It holds
 * only the previous value of each, in the same shape [net.trilleo.config.ProfileAutoSwitch] tracks the island.
 */
object ReminderTriggers {

    private var lastIsland: String? = null
    private var lastHeldId: String? = null

    /** Forgets what it saw, so an edge cannot straddle two servers. */
    fun reset() {
        lastIsland = null
        lastHeldId = null
    }

    /**
     * Arms whatever the last tick's changes call for.
     *
     * **A location going unknown is not a departure.** [SkyblockLocation] reports null for "I cannot tell"
     * rather than for "you left" — its sidebar is empty for the first seconds after a join and can hiccup at
     * any time — so `island → null` deliberately arms nothing. Treating uncertainty as a leave would fire
     * every ISLAND_LEAVE reminder each time the scoreboard stuttered, which is exactly the kind of false
     * alarm that makes a player switch a feature off.
     */
    fun tick(arm: (TriggerKind, String) -> Unit) {
        val island = SkyblockLocation.current
        if (island != lastIsland) {
            val previous = lastIsland
            // Only a known-to-known or unknown-to-known transition counts; see the note above.
            if (previous != null && island != null) arm(TriggerKind.ISLAND_LEAVE, previous)
            if (island != null) arm(TriggerKind.ISLAND_ENTER, island)
            lastIsland = island
        }

        val held = HeldItem.id
        if (held != lastHeldId) {
            if (held != null) arm(TriggerKind.HELD_ITEM, held)
            lastHeldId = held
        }
    }
}
