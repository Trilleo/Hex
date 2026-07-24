package net.trilleo.region

import net.minecraft.client.Minecraft
import net.minecraft.gizmos.GizmoProperties
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.TextGizmo
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.trilleo.region.model.Region
import net.trilleo.region.model.RegionShape
import net.trilleo.skyblock.SkyblockLocation
import net.trilleo.util.HexColor
import kotlin.math.sqrt

/**
 * Draws regions as real shapes in the world.
 *
 * Built on `net.minecraft.gizmos`, the game's own debug-shape system: a thread-local collector is installed by
 * [Minecraft.collectPerTickGizmos], anything emitted while it is open is drained at the end of the tick, and
 * [net.minecraft.client.renderer.LevelRenderer] renders what was drained in its main pass — unconditionally,
 * with no debug flag involved. That means a preview costs this mod no rendering code, no mixin, and no render
 * pipeline of its own.
 *
 * Emitted fresh every tick rather than persisted, so the shapes track an edit as it is typed and vanish the
 * instant the preview is switched off; the frames between two ticks reuse the last drain, so this runs twenty
 * times a second and not once per frame.
 */
object RegionRenderer {

    /**
     * Whether the whole-island preview is switched on. Deliberately not persisted: it is a mode the player is
     * in for a minute while drawing, and having it survive a restart would leave boxes on screen with no
     * memory of having asked for them.
     */
    var previewAll: Boolean = false

    /** The region open in the editor, previewed while that screen is up so edits are visible behind it. */
    var focused: Region? = null

    /** How high above the box's top the name label floats, in blocks. */
    private const val LABEL_LIFT = 0.5

    /** Rings used to suggest a sphere. Odd, so one of them sits on the equator. */
    private const val SPHERE_RINGS = 7

    private const val STROKE_WIDTH = 2.0f

    /** Fallback when a colour will not parse — a visible box beats an invisible one. */
    private const val DEFAULT_COLOR = 0x5555FF55

    /** Called every client tick from [RegionFeature]. Cheap, and a no-op when nothing is being previewed. */
    fun tick(client: Minecraft) {
        if (client.level == null) return

        val draft = RegionCapture.draftBox()
        val regions = previewed()
        if (draft == null && regions.isEmpty()) return

        // Everything emitted inside this block lands in the tick's collector; closing it puts back whatever
        // collector was installed before, so this cannot disturb the game's own gizmo use.
        Minecraft.getInstance().collectPerTickGizmos().use {
            draft?.let { box ->
                val color = HexColor.parseOrDefault(RegionConfig.settings.draftColor, DEFAULT_COLOR)
                emitBox(box, color)
                label(client, box, draftLabel(box))
            }
            regions.forEach { region -> emit(client, region) }
        }
    }

    /**
     * Which regions to draw, in priority order.
     *
     * The editor's focused region is always shown even when it is on another island or switched off — the
     * player is looking at it right now, and hiding what they are editing would be perverse.
     */
    private fun previewed(): List<Region> {
        val focus = focused
        if (focus != null) return listOf(focus)
        if (!previewAll || !RegionConfig.active) return emptyList()

        val island = SkyblockLocation.current
        return RegionConfig.settings.regions.filter { region ->
            region.enabled && (region.island.isEmpty() || region.island == island)
        }
    }

    private fun emit(client: Minecraft, region: Region) {
        val color = HexColor.parseOrDefault(
            region.color.ifBlank { RegionConfig.settings.previewColor },
            DEFAULT_COLOR,
        )

        when (region.shape) {
            RegionShape.BOX -> emitBox(region.aabb(), color)
            RegionShape.CYLINDER -> emitCylinder(region, color)
            RegionShape.SPHERE -> emitSphere(region, color)
        }

        if (RegionConfig.settings.previewNames) label(client, region.aabb(), region.name)
    }

    private fun emitBox(box: AABB, color: Int) {
        onTop(Gizmos.cuboid(box, GizmoStyle.strokeAndFill(color or OPAQUE, STROKE_WIDTH, color)))
    }

    /**
     * A cylinder as its two end caps plus four uprights.
     *
     * `CircleGizmo` draws in the horizontal plane only and there is no cylinder primitive, so the shape is
     * assembled from what exists. Four uprights rather than more: they are there to read as a wall between the
     * caps, not to approximate one.
     */
    private fun emitCylinder(region: Region, color: Int) {
        val center = region.center()
        val radius = region.radius()
        val stroke = color or OPAQUE

        onTop(Gizmos.circle(Vec3(center.x, region.minY, center.z), radius.toFloat(), GizmoStyle.stroke(stroke, STROKE_WIDTH)))
        onTop(Gizmos.circle(Vec3(center.x, region.maxY, center.z), radius.toFloat(), GizmoStyle.stroke(stroke, STROKE_WIDTH)))

        listOf(
            Vec3(center.x + radius, 0.0, center.z),
            Vec3(center.x - radius, 0.0, center.z),
            Vec3(center.x, 0.0, center.z + radius),
            Vec3(center.x, 0.0, center.z - radius),
        ).forEach { edge ->
            onTop(
                Gizmos.line(
                    Vec3(edge.x, region.minY, edge.z),
                    Vec3(edge.x, region.maxY, edge.z),
                    stroke,
                    STROKE_WIDTH,
                ),
            )
        }
    }

    /**
     * A sphere as a stack of horizontal rings, each the width of the sphere at that height.
     *
     * Same constraint as the cylinder — only horizontal circles exist — so the illusion is made from the
     * spacing. The poles are skipped: a ring of radius zero draws as a dot and reads as a rendering fault.
     */
    private fun emitSphere(region: Region, color: Int) {
        val center = region.center()
        val radius = region.radius()
        val style = GizmoStyle.stroke(color or OPAQUE, STROKE_WIDTH)

        for (i in 0 until SPHERE_RINGS) {
            // Spread across the diameter but inset from both poles, so every ring has real width.
            val t = (i + 1).toDouble() / (SPHERE_RINGS + 1)
            val dy = (t * 2.0 - 1.0) * radius
            val ringRadius = sqrt((radius * radius - dy * dy).coerceAtLeast(0.0))
            onTop(Gizmos.circle(Vec3(center.x, center.y + dy, center.z), ringRadius.toFloat(), style))
        }
    }

    private fun label(client: Minecraft, box: AABB, text: String) {
        if (text.isBlank()) return
        val pos = Vec3((box.minX + box.maxX) / 2.0, box.maxY + LABEL_LIFT, (box.minZ + box.maxZ) / 2.0)
        // Labels are always drawn on top: a name behind a wall is unreadable rather than merely dim, and the
        // point of the label is to tell two overlapping boxes apart.
        Gizmos.billboardText(text, pos, TextGizmo.Style.whiteAndCentered()).setAlwaysOnTop()
    }

    private fun draftLabel(box: AABB): String = String.format(
        java.util.Locale.ROOT,
        "%.0f×%.0f×%.0f",
        box.maxX - box.minX,
        box.maxY - box.minY,
        box.maxZ - box.minZ,
    )

    /** Applies see-through when the setting asks for it, so a region behind terrain is still visible. */
    private fun onTop(properties: GizmoProperties) {
        if (RegionConfig.settings.previewSeeThrough) properties.setAlwaysOnTop()
    }

    /** Forces full alpha for an outline, so a translucent fill colour still produces a readable edge. */
    private const val OPAQUE = 0xFF000000.toInt()
}
