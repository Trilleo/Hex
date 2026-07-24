package net.trilleo.region.model

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.trilleo.reminder.model.ReminderAction
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * How a [Region] reads the box it stores.
 *
 * Persisted by name, so constants may be appended but not renamed or reordered. A name this build does not
 * know deserializes to null and is repaired to [BOX] by the config's normalizer — the mildest repair
 * available, since a box is the shape the stored numbers literally describe.
 */
enum class RegionShape {
    /** The stored box itself. */
    BOX,

    /** A vertical cylinder inscribed in the box: [Region.radius] across, the box's height tall. */
    CYLINDER,

    /** A sphere inscribed in the box, of [Region.radius]. */
    SPHERE,
}

/**
 * One named volume of world space on one island, which the player can be inside or outside of.
 *
 * **Every shape stores the same six numbers — an axis-aligned box — and the shape reinterprets them.** That
 * single decision carries most of this class's weight:
 *
 *  - every way of capturing a region ends at a box and nothing else, so the three capture modes share one
 *    output rather than each needing to produce its own shape's parameters;
 *  - switching a region between box, cylinder and sphere is instant and never asks the player to re-capture,
 *    because there is nothing shape-specific to recompute;
 *  - the JSON stays six readable coordinates that anyone can edit by hand.
 *
 * The price is that a cylinder cannot be elliptical and a sphere cannot be an ellipsoid: both take the
 * *smallest* half-extent as their radius, so they always fit inside the box rather than escaping it. The
 * editor shows the resulting radius, so this reads as a stated rule rather than as a surprise.
 *
 * A deliberately plain class rather than a data class, so equality is identity — the editor screens hold rows
 * by reference to delete them, exactly as [net.trilleo.reminder.model.Reminder] and
 * [net.trilleo.skyblock.item.ItemRule] do. `var`-only with a no-arg constructor because GSON instantiates it
 * reflectively and never runs Kotlin's defaults.
 */
class Region {
    /**
     * Stable identity, a UUID string. Nothing outside the region list refers to a region by id — a reminder
     * trigger names it by [name], which keeps that file readable — but the id is what lets the edit screens
     * and the tracker's occupancy set survive a rename.
     */
    var id: String = ""

    /**
     * The region's name, lowercased.
     *
     * Folded and de-duplicated by the config's normalizer, because this is the payload a
     * [net.trilleo.reminder.model.TriggerKind.REGION_ENTER] trigger carries — matching stays a plain `==`
     * against an already-folded string, the same arrangement island names have.
     */
    var name: String = ""

    /**
     * The Skyblock island this region is on, lowercased, or `""` for "anywhere".
     *
     * **Not optional in practice.** Coordinates repeat across islands — `(0, 70, 0)` exists on every one of
     * them — so a region without an island would fire in places the player has never seen. It is captured
     * automatically from [net.trilleo.skyblock.SkyblockLocation] and only blank when that had no opinion,
     * which is also what makes regions usable off Skyblock entirely.
     */
    var island: String = ""

    var enabled: Boolean = true

    var shape: RegionShape = RegionShape.BOX

    var minX: Double = 0.0
    var minY: Double = 0.0
    var minZ: Double = 0.0
    var maxX: Double = 0.0
    var maxY: Double = 0.0
    var maxZ: Double = 0.0

    /** The message shown when the region is entered — the title line, and the label drawn in the preview. */
    var text: String = ""

    /** Shown instead of [text] on leaving, when [notifyOnLeave] is set. Falls back to [text] when blank. */
    var leaveText: String = ""

    /** Whether leaving fires the actions as well as entering. */
    var notifyOnLeave: Boolean = false

    /**
     * Run when the region is entered (or left). Never empty — the normalizer adds a title action if it is.
     *
     * The same type a reminder holds, and run by the same
     * [net.trilleo.reminder.ReminderActions.run], so there is one implementation of "turn an action into a
     * title or a sound" rather than one per feature.
     * [net.trilleo.reminder.model.ActionKind.HUD] is meaningless here — the panel draws reminder phases, and
     * a region has none — so the editor offers only the title and the sound.
     */
    var actions: MutableList<ReminderAction> = mutableListOf()

    /** How long after firing this region stays quiet, in seconds. Stops a doorway becoming a machine gun. */
    var cooldownSeconds: Double = DEFAULT_COOLDOWN_SECONDS

    /** `"#AARRGGBB"` for the preview outline and fill, or `""` to use the tab's default colour. */
    var color: String = ""

    // ---- geometry ------------------------------------------------------------------------------------

    /** The stored box. */
    fun aabb(): AABB = AABB(minX, minY, minZ, maxX, maxY, maxZ)

    fun center(): Vec3 = Vec3((minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0)

    fun sizeX(): Double = maxX - minX

    fun sizeY(): Double = maxY - minY

    fun sizeZ(): Double = maxZ - minZ

    /**
     * The radius [shape] uses: the largest that still fits inside the box.
     *
     * A cylinder ignores the vertical extent (it takes its height from the box instead), which is why the two
     * shapes do not share one expression.
     */
    fun radius(): Double = when (shape) {
        RegionShape.BOX -> max(sizeX(), sizeZ()) / 2.0
        RegionShape.CYLINDER -> min(sizeX(), sizeZ()) / 2.0
        RegionShape.SPHERE -> min(min(sizeX(), sizeY()), sizeZ()) / 2.0
    }

    /**
     * Whether [pos] is inside this region, optionally grown by [margin].
     *
     * [margin] is what gives the tracker its hysteresis: entering is tested at zero and leaving against a
     * slightly larger volume, so a player standing exactly on the boundary cannot flap between the two states
     * and fire on every other tick.
     */
    fun contains(pos: Vec3, margin: Double = 0.0): Boolean = when (shape) {
        RegionShape.BOX ->
            pos.x >= minX - margin && pos.x <= maxX + margin &&
                    pos.y >= minY - margin && pos.y <= maxY + margin &&
                    pos.z >= minZ - margin && pos.z <= maxZ + margin

        RegionShape.CYLINDER -> {
            val center = center()
            val reach = radius() + margin
            pos.y >= minY - margin && pos.y <= maxY + margin &&
                    square(pos.x - center.x) + square(pos.z - center.z) <= square(reach)
        }

        RegionShape.SPHERE -> {
            val center = center()
            val reach = radius() + margin
            square(pos.x - center.x) + square(pos.y - center.y) + square(pos.z - center.z) <= square(reach)
        }
    }

    /** Sets the box to the one bounding [a] and [b], in either order. */
    fun setCorners(a: Vec3, b: Vec3) {
        minX = min(a.x, b.x)
        minY = min(a.y, b.y)
        minZ = min(a.z, b.z)
        maxX = max(a.x, b.x)
        maxY = max(a.y, b.y)
        maxZ = max(a.z, b.z)
    }

    /** Sets the box to [radius] either side of [center] horizontally and [halfHeight] vertically. */
    fun setAround(center: Vec3, radius: Double, halfHeight: Double) {
        setCorners(
            Vec3(center.x - radius, center.y - halfHeight, center.z - radius),
            Vec3(center.x + radius, center.y + halfHeight, center.z + radius),
        )
    }

    /** A short description of where and how big this is, for the editor list and `/hexa region list`. */
    fun summary(): String {
        val where = island.ifBlank { "any island" }
        val shapeName = shape.name.lowercase()
        // Locale.ROOT so the numbers read the same whatever language the client is in, as everywhere else
        // this mod formats one.
        val size = when (shape) {
            RegionShape.BOX -> String.format(Locale.ROOT, "%.0f×%.0f×%.0f", sizeX(), sizeY(), sizeZ())
            RegionShape.CYLINDER -> String.format(Locale.ROOT, "r%.0f, %.0f tall", radius(), sizeY())
            RegionShape.SPHERE -> String.format(Locale.ROOT, "r%.0f", radius())
        }
        return "$where — $shapeName $size"
    }

    /** A copy carrying a fresh [id], used when duplicating a region. */
    fun copyDefinition(into: Region) {
        into.name = name
        into.island = island
        into.enabled = enabled
        into.shape = shape
        into.minX = minX
        into.minY = minY
        into.minZ = minZ
        into.maxX = maxX
        into.maxY = maxY
        into.maxZ = maxZ
        into.text = text
        into.leaveText = leaveText
        into.notifyOnLeave = notifyOnLeave
        into.cooldownSeconds = cooldownSeconds
        into.color = color
        into.actions = actions.mapTo(mutableListOf()) { it.copy() }
    }

    private fun square(value: Double): Double = value * value

    companion object {
        const val DEFAULT_COOLDOWN_SECONDS: Double = 30.0

        const val COOLDOWN_MIN: Double = 0.0
        const val COOLDOWN_MAX: Double = 3600.0

        /**
         * The largest a region may be along one axis.
         *
         * Not a performance limit — containment is a handful of comparisons at any size — but a sanity one:
         * a region spanning a million blocks is a typo or a hand-edit gone wrong, and it would swallow the
         * whole island silently.
         */
        const val MAX_EXTENT: Double = 2048.0

        /** The smallest a region may be along one axis, so a mis-click cannot make an untriggerable sliver. */
        const val MIN_EXTENT: Double = 1.0
    }
}
