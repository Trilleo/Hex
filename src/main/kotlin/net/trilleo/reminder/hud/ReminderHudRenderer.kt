package net.trilleo.reminder.hud

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.trilleo.reminder.ReminderConfig
import net.trilleo.skyblock.SkyblockLocation
import net.trilleo.util.HexColor

/** The panel's box in panel space (screen pixels divided by the scale), as laid out by [ReminderHudRenderer]. */
class PanelRect(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * Draws the reminder panel.
 *
 * This runs once per frame, so it does no work that scales with content: [ReminderHudModel] has already
 * formatted every row's text and measured its width on the tick. What is left is arithmetic on a handful of
 * integers and about ten draw calls for a full panel.
 *
 * The same function draws the live HUD and the drag-to-move editor's preview, so there is exactly one layout
 * implementation and the preview cannot drift from what the player will actually see.
 */
object ReminderHudRenderer {

    private const val PADDING = 4
    private const val LINE_HEIGHT = 10

    /** How long each half of the flash cycle lasts, in milliseconds. */
    private const val FLASH_PERIOD = 400L

    private const val DEFAULT_BACKGROUND = 0x80101010.toInt()
    private const val DEFAULT_TEXT = 0xFFE0E0E0.toInt()
    private const val DEFAULT_FLASH = 0xFFFF5555.toInt()
    private const val BORDER = 0x40FFFFFF

    /** Whether the panel would draw at all right now, so the editor can offer a preview when it would not. */
    fun visible(): Boolean {
        val hud = ReminderConfig.hud
        if (!ReminderConfig.active) return false
        if (hud.skyblockOnly && !SkyblockLocation.onSkyblock) return false
        return ReminderHudModel.rows.isNotEmpty() || hud.showWhenEmpty
    }

    /** Draws the live panel, or nothing when [visible] is false. */
    fun draw(extractor: GuiGraphicsExtractor) {
        if (!visible()) return
        drawRows(extractor, ReminderHudModel.rows, ReminderHudModel.contentWidth)
    }

    /**
     * Where the panel sits, in panel space, for [rows] of the given width.
     *
     * Anchors are fractions of the screen rather than pixel coordinates, so a saved position survives a
     * resolution change, fullscreen, and a GUI-scale change — all of which move `guiWidth`/`guiHeight` under
     * an absolute coordinate. The corner then decides which way the panel grows, so a right-anchored panel
     * extends leftwards and stays put as rows are added instead of running off the edge. The final clamp
     * guarantees the panel is always fully on screen, however extreme the anchor.
     */
    fun layout(extractor: GuiGraphicsExtractor, rowCount: Int, contentWidth: Int): PanelRect {
        val hud = ReminderConfig.hud
        val scale = hud.scale.toFloat()

        val width = contentWidth + PADDING * 2
        val height = rowCount.coerceAtLeast(1) * LINE_HEIGHT + PADDING * 2

        val screenWidth = (extractor.guiWidth() / scale).toInt()
        val screenHeight = (extractor.guiHeight() / scale).toInt()
        val anchorX = (hud.anchorX * screenWidth).toInt()
        val anchorY = (hud.anchorY * screenHeight).toInt()

        val left = when (hud.corner) {
            HudCorner.TOP_LEFT, HudCorner.BOTTOM_LEFT -> anchorX
            HudCorner.TOP_RIGHT, HudCorner.BOTTOM_RIGHT -> anchorX - width
        }
        val top = when (hud.corner) {
            HudCorner.TOP_LEFT, HudCorner.TOP_RIGHT -> anchorY
            HudCorner.BOTTOM_LEFT, HudCorner.BOTTOM_RIGHT -> anchorY - height
        }

        return PanelRect(
            left.coerceIn(0, (screenWidth - width).coerceAtLeast(0)),
            top.coerceIn(0, (screenHeight - height).coerceAtLeast(0)),
            width,
            height,
        )
    }

    /**
     * Draws [rows] as the panel, whatever the current visibility rules say. The editor calls this directly to
     * show a sample panel when nothing is counting down, so there is always something to grab hold of.
     */
    fun drawRows(extractor: GuiGraphicsExtractor, rows: List<HudRow>, contentWidth: Int) {
        val hud = ReminderConfig.hud
        val font = Minecraft.getInstance().font
        val rect = layout(extractor, rows.size, contentWidth)

        extractor.pose().pushMatrix()
        extractor.pose().scale(hud.scale.toFloat())

        if (hud.background) {
            val background = HexColor.parseOrDefault(hud.backgroundColor, DEFAULT_BACKGROUND)
            extractor.fill(rect.left, rect.top, rect.left + rect.width, rect.top + rect.height, background)
            extractor.outline(rect.left, rect.top, rect.width, rect.height, BORDER)
        }

        // No per-reminder animation state: the phase of the flash is a pure function of the wall clock, which
        // is frame-rate independent, allocates nothing, and stays correct across a pause.
        val bright = (System.currentTimeMillis() / FLASH_PERIOD) % 2L == 0L
        val textColor = HexColor.parseOrDefault(hud.textColor, DEFAULT_TEXT)
        val flashColor = HexColor.parseOrDefault(hud.flashColor, DEFAULT_FLASH)

        rows.forEachIndexed { index, row ->
            val color = if (row.flashing && bright) flashColor else textColor
            extractor.text(font, row.text, rect.left + PADDING, rect.top + PADDING + index * LINE_HEIGHT, color)
        }

        extractor.pose().popMatrix()
    }
}
