package net.trilleo.region

import net.minecraft.client.Minecraft
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.trilleo.freecam.FreecamState
import net.trilleo.region.model.Region
import net.trilleo.skyblock.SkyblockLocation
import java.util.*
import kotlin.math.max
import kotlin.math.min

/** Which way a region currently being drawn is being drawn. */
enum class CaptureMode {
    /** Two marked points become opposite corners. */
    CORNERS,

    /** The player's path is folded into a bounding box until they stop. */
    WALK,
}

/**
 * The region being drawn right now, if any.
 *
 * All three ways of setting a region's bounds converge here on one output — a box — which is what keeps them
 * cheap to have all three of. "Region here" needs no draft at all and produces its box outright; the other two
 * accumulate into this and finish into the same place.
 *
 * Held in memory only. A half-drawn region is a thing the player is doing this minute, not a thing worth
 * surviving a restart, and persisting it would mean deciding what a draft from another island means on load.
 */
object RegionCapture {

    var mode: CaptureMode? = null
        private set

    /** Points marked so far in [CaptureMode.CORNERS], or the running bounds in [CaptureMode.WALK]. */
    private var minPoint: Vec3? = null
    private var maxPoint: Vec3? = null

    /** How many corners have been marked, for the HUD's "point 1 of 2". */
    var marked: Int = 0
        private set

    /** Whether a draft is being drawn. */
    val active: Boolean get() = mode != null

    /** The draft's box so far, or null when nothing has been marked yet. */
    fun draftBox(): AABB? {
        val lo = minPoint ?: return null
        val hi = maxPoint ?: return null
        return AABB(lo.x, lo.y, lo.z, hi.x, hi.y, hi.z)
    }

    /**
     * Where a mark lands: the freecam's camera when it is flying, otherwise the player's feet.
     *
     * The freecam case is the one that matters. A region's top corner is usually in mid-air, and the only
     * ways to stand there are to build up to it or to fly — so binding capture to a camera that already flies
     * removes the single most tedious step in drawing a box. With freecam off the same key still works, which
     * is what stops the binding being dead half the time.
     */
    fun markPoint(client: Minecraft): Vec3? =
        if (FreecamState.active) FreecamState.position else client.player?.position()

    /** Starts (or restarts) a two-corner capture. */
    fun beginCorners() {
        mode = CaptureMode.CORNERS
        minPoint = null
        maxPoint = null
        marked = 0
    }

    /** Starts a walk-the-perimeter capture, seeded from where the player is standing. */
    fun beginWalk(from: Vec3) {
        mode = CaptureMode.WALK
        minPoint = from
        maxPoint = from
        marked = 1
    }

    /** Abandons the draft. */
    fun cancel() {
        mode = null
        minPoint = null
        maxPoint = null
        marked = 0
    }

    /**
     * Records a corner. Returns true once both are in and the draft is ready to finish.
     *
     * A third mark restarts rather than being ignored: a player who marks a corner in the wrong place expects
     * to be able to start over without hunting for a cancel key.
     */
    fun addCorner(point: Vec3): Boolean {
        if (mode != CaptureMode.CORNERS) beginCorners()
        if (marked >= 2) beginCorners()

        if (marked == 0) {
            minPoint = point
            maxPoint = point
            marked = 1
            return false
        }
        expand(point)
        marked = 2
        return true
    }

    /** Folds the player's current position into a walk capture. Called every tick while one is running. */
    fun tickWalk(client: Minecraft) {
        if (mode != CaptureMode.WALK) return
        val pos = client.player?.position() ?: return
        expand(pos)
    }

    /**
     * Turns the draft into a region and clears it, or returns null when there is nothing usable yet.
     *
     * A walk capture is padded vertically because a player walking an outline traces a surface, not a volume:
     * the raw box would be about two blocks tall and would miss them the moment they jumped.
     */
    fun finish(name: String): Region? {
        val lo = minPoint ?: return null
        val hi = maxPoint ?: return null
        val padY = if (mode == CaptureMode.WALK) RegionConfig.settings.defaultHeight else 0.0
        cancel()

        return newRegion(name).apply {
            setCorners(
                Vec3(lo.x, lo.y - padY, lo.z),
                Vec3(hi.x, hi.y + padY, hi.z),
            )
        }
    }

    /** A region of [radius] around [center], the one-keypress path — no draft involved. */
    fun around(name: String, center: Vec3): Region = newRegion(name).apply {
        setAround(center, RegionConfig.settings.defaultRadius, RegionConfig.settings.defaultHeight)
    }

    /**
     * A blank region with an id, a unique name, and the island the player is on.
     *
     * Capturing the island here rather than asking for it is the whole reason regions can be made in one
     * keypress: it is the one field the player would otherwise have to look up and type, and the scoreboard
     * already knows it.
     */
    private fun newRegion(name: String): Region = Region().apply {
        id = UUID.randomUUID().toString()
        this.name = RegionConfig.uniqueName(name)
        island = SkyblockLocation.current.orEmpty()
        text = this.name
    }

    private fun expand(point: Vec3) {
        val lo = minPoint
        val hi = maxPoint
        if (lo == null || hi == null) {
            minPoint = point
            maxPoint = point
            return
        }
        minPoint = Vec3(min(lo.x, point.x), min(lo.y, point.y), min(lo.z, point.z))
        maxPoint = Vec3(max(hi.x, point.x), max(hi.y, point.y), max(hi.z, point.z))
    }
}
