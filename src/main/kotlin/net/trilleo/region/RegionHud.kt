package net.trilleo.region

import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import java.util.*

/**
 * The hint panel shown while a region is being drawn.
 *
 * Capture is a mode with no screen — the player is flying or walking around the world — so the only place it
 * can explain itself is the HUD. Without this, a two-corner capture is indistinguishable from nothing having
 * happened: the first mark produces no visible box, and there is no way to tell which key finishes it.
 *
 * Drawn from the feature's frame hook, which is attached to the vanilla chat element and therefore inherits
 * its render condition, so F1 hides this along with everything else.
 */
object RegionHud {

    private const val MARGIN = 6
    private const val PADDING = 4
    private const val LINE_HEIGHT = 10

    private const val BACKGROUND = 0xB0101010.toInt()
    private const val BORDER = 0x60FFFFFF
    private const val TITLE_COLOR = 0xFFFFCC55.toInt()
    private const val TEXT_COLOR = 0xFFE0E0E0.toInt()
    private const val HINT_COLOR = 0xFF909090.toInt()

    /**
     * Key bindings named in the hints, supplied by the feature rather than reached for.
     *
     * The panel has to name the keys the player has actually bound, and there is no point drawing a hint for
     * one that is unbound — that is the case where the command is the only way through, so it is what gets
     * shown instead.
     */
    var markKey: KeyMapping? = null
    var walkKey: KeyMapping? = null

    /** Draws the panel, or nothing when no capture is running. */
    fun draw(extractor: GuiGraphicsExtractor) {
        val mode = RegionCapture.mode ?: return
        val font = Minecraft.getInstance().font

        val title = when (mode) {
            CaptureMode.CORNERS -> Component.translatable("hex.regions.capture.corners").string
            CaptureMode.WALK -> Component.translatable("hex.regions.capture.walk").string
        }

        val status = when (mode) {
            CaptureMode.CORNERS ->
                Component.translatable("hex.regions.capture.marked", RegionCapture.marked, 2).string

            CaptureMode.WALK -> Component.translatable("hex.regions.capture.walking").string
        }

        val size = RegionCapture.draftBox()?.let {
            String.format(
                Locale.ROOT,
                "%.0f × %.0f × %.0f",
                it.maxX - it.minX,
                it.maxY - it.minY,
                it.maxZ - it.minZ,
            )
        }

        val hint = when (mode) {
            CaptureMode.CORNERS -> hintFor(
                markKey,
                "hex.regions.capture.hint.mark",
                "hex.regions.capture.hint.mark_cmd"
            )

            CaptureMode.WALK -> hintFor(walkKey, "hex.regions.capture.hint.walk", "hex.regions.capture.hint.walk_cmd")
        }

        val lines = listOfNotNull(status, size, hint)
        val width = (listOf(title) + lines).maxOf { font.width(it) } + PADDING * 2
        val height = (lines.size + 1) * LINE_HEIGHT + PADDING * 2

        // Top centre: a capture happens while the player is looking around the world, so the panel sits where
        // it is readable without competing with the reminder panel's corner or the hotbar.
        val left = (extractor.guiWidth() - width) / 2
        val top = MARGIN

        extractor.fill(left, top, left + width, top + height, BACKGROUND)
        extractor.outline(left, top, width, height, BORDER)

        var y = top + PADDING
        extractor.text(font, title, left + PADDING, y, TITLE_COLOR)
        y += LINE_HEIGHT
        lines.forEachIndexed { index, line ->
            // The last line is always the key hint, which is guidance rather than state.
            val color = if (index == lines.lastIndex) HINT_COLOR else TEXT_COLOR
            extractor.text(font, line, left + PADDING, y, color)
            y += LINE_HEIGHT
        }
    }

    /** The hint naming [key], or the command fallback when nothing is bound to it. */
    private fun hintFor(key: KeyMapping?, boundKey: String, unboundKey: String): String =
        if (key == null || key.isUnbound) {
            Component.translatable(unboundKey).string
        } else {
            Component.translatable(boundKey, key.translatedKeyMessage).string
        }
}
