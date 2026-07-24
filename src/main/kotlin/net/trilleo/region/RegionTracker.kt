package net.trilleo.region

import net.minecraft.client.Minecraft
import net.trilleo.region.model.Region
import net.trilleo.reminder.model.TriggerKind
import net.trilleo.skyblock.SkyblockLocation

/**
 * Watches which regions the player is standing in, and reports the edges.
 *
 * **Position comes from the player, never from the camera.** Freecam moves the camera while the body stays
 * put, so reading the camera would fire every region the player flew a preview through — which is precisely
 * the activity regions are drawn with. Taking `player.position()` makes that correct by construction rather
 * than by a check somewhere.
 *
 * Cost per tick is one map lookup and a handful of coordinate comparisons: the candidate list is narrowed to
 * the current island once and cached, and containment is a few adds and multiplies. Nothing here is throttled
 * because nothing needs to be.
 */
object RegionTracker {

    /** Ids of the regions the player was inside as of the last tick. */
    private val inside: MutableSet<String> = HashSet()

    /** Region id to the epoch millisecond its cooldown expires. Deliberately in memory only — see [tick]. */
    private val quietUntil: MutableMap<String, Long> = HashMap()

    /** The regions that apply where the player is now, recomputed only when something below changes. */
    private var candidates: List<Region> = emptyList()
    private var candidateIsland: String? = null
    private var candidateRevision: Int = -1

    /**
     * Crossings this tick has produced but nobody has consumed yet, as (kind, region name) pairs.
     *
     * Regions and reminders are separate features and tick in registration order, so the reminder engine
     * cannot be called from inside the region tick without one feature reaching into the other's schedule.
     * Queueing instead means [drainEdges] works whichever order they run in — at worst a crossing arms its
     * reminder one tick late, which no player can perceive and no countdown can notice.
     */
    private val pending: MutableList<Pair<TriggerKind, String>> = mutableListOf()

    /** Whether the player is currently inside [region]. Read by a reminder's `IN_REGION` condition. */
    fun isInside(region: Region): Boolean = region.id in inside

    /** Whether the player is inside the region with this (lowercased) name. */
    fun isInsideNamed(name: String): Boolean =
        RegionConfig.byName(name)?.let { it.id in inside } == true

    /** The regions the player is standing in, for the list screen's "you are here" marker. */
    fun insideIds(): Set<String> = inside

    /**
     * Forgets where the player was, so an edge cannot straddle two servers or two islands.
     *
     * Cooldowns are cleared alongside: they exist to stop a doorway firing repeatedly in one session, and
     * carrying one across a disconnect would swallow the first alert after coming back.
     */
    fun reset() {
        inside.clear()
        quietUntil.clear()
        pending.clear()
        candidateIsland = null
        candidateRevision = -1
    }

    /**
     * Hands every queued crossing to [arm] and clears the queue. Called from
     * [net.trilleo.reminder.ReminderTriggers].
     */
    fun drainEdges(arm: (TriggerKind, String) -> Unit) {
        if (pending.isEmpty()) return
        pending.forEach { (kind, name) -> arm(kind, name) }
        pending.clear()
    }

    /**
     * Recomputes occupancy and reports what changed.
     *
     * Both [onEnter] and [onLeave] are called for every region that changed state, cooldown notwithstanding —
     * the cooldown is applied by [RegionAlerts], not here, because a region-triggered *reminder* should still
     * arm on entry even when the region's own title is being held back.
     *
     * **Arriving already inside a region counts as entering it.** [reset] on world join empties the occupancy
     * set, so warping straight into a region fires it. That is the honest reading — you did just arrive — and
     * the cooldown is what keeps a string of warps from being noisy.
     */
    fun tick(client: Minecraft, onEnter: (Region) -> Unit, onLeave: (Region) -> Unit) {
        val player = client.player
        if (player == null || client.level == null) {
            // Not "you left everything": there is no position to judge by, and treating that as a departure
            // would fire every leave alert during a loading screen. The same caution SkyblockLocation takes.
            inside.clear()
            return
        }
        if (RegionConfig.settings.skyblockOnly && !SkyblockLocation.onSkyblock) {
            inside.clear()
            return
        }

        val pos = player.position()
        val margin = RegionConfig.settings.exitMargin

        candidatesFor(SkyblockLocation.current).forEach { region ->
            val was = region.id in inside
            // Hysteresis: getting in needs the real boundary, getting out needs to clear it by the margin.
            // Testing both against the same edge would make a player standing on it flip every other tick.
            val now = if (was) region.contains(pos, margin) else region.contains(pos)
            if (now == was) return@forEach

            if (now) {
                inside.add(region.id)
                pending.add(TriggerKind.REGION_ENTER to region.name)
                onEnter(region)
            } else {
                inside.remove(region.id)
                pending.add(TriggerKind.REGION_LEAVE to region.name)
                onLeave(region)
            }
        }
    }

    /** Whether [region] may fire now, and if so starts its cooldown. */
    fun claimCooldown(region: Region, now: Long): Boolean {
        if (now < (quietUntil[region.id] ?: 0L)) return false
        quietUntil[region.id] = now + (region.cooldownSeconds * 1000.0).toLong()
        return true
    }

    /**
     * The enabled regions that could apply on [island], cached until the island or the config changes.
     *
     * A region with no island matches anywhere, which is what makes regions work in singleplayer and off
     * Skyblock; every other one is filtered out here so the per-tick walk only sees plausible candidates.
     */
    private fun candidatesFor(island: String?): List<Region> {
        if (island == candidateIsland && RegionConfig.revision == candidateRevision) return candidates

        candidateIsland = island
        candidateRevision = RegionConfig.revision
        candidates = RegionConfig.settings.regions.filter { region ->
            region.enabled && (region.island.isEmpty() || region.island == island)
        }
        // Anything that just stopped being a candidate has to stop counting as occupied, or returning to the
        // island later would never produce an enter edge for it.
        inside.retainAll(candidates.mapTo(HashSet()) { it.id })
        return candidates
    }
}
