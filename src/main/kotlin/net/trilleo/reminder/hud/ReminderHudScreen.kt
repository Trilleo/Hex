package net.trilleo.reminder.hud

import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.trilleo.reminder.ReminderConfig
import java.util.*

/**
 * Drag the reminder panel to where you want it.
 *
 * The panel drawn here is the real one, through the same [ReminderHudRenderer.drawRows] the HUD uses, so what
 * is positioned is exactly what will appear in play. When nothing is counting down there is nothing to grab,
 * so a sample panel stands in — a position editor that only works while a reminder happens to be running
 * would be useless most of the time.
 */
class ReminderHudScreen(private val parent: Screen?) :
    Screen(Component.translatable("hex.reminders.hud.title")) {

    /**
     * Where in the panel the drag started, in screen pixels. Kept so the panel holds its position under the
     * cursor instead of snapping its corner to it on the first mouse move.
     */
    private var grabX = 0
    private var grabY = 0
    private var dragging = false

    /** The panel's box in screen pixels, recomputed each frame and read by the mouse handlers. */
    private var panel: IntArray? = null

    override fun init() {
        val y = height - 28
        var x = width / 2 - (BUTTON_WIDTH * 2 + GAP) / 2

        addRenderableWidget(
            Button.builder(Component.translatable("hex.reminders.hud.reset")) {
                ReminderConfig.hud.anchorX = DEFAULT_ANCHOR_X
                ReminderConfig.hud.anchorY = DEFAULT_ANCHOR_Y
                ReminderConfig.save()
            }.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.reminders.hud.done")) { onClose() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )
    }

    override fun extractBackground(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(extractor, mouseX, mouseY, delta)

        // Centre and third guides, so the panel can be lined up with something rather than eyeballed.
        extractor.verticalLine(width / 2, 0, height, GUIDE_COLOR)
        extractor.horizontalLine(0, width, height / 2, GUIDE_COLOR)

        val rows = previewRows()
        val contentWidth = rows.maxOfOrNull { it.width } ?: 0
        val rect = ReminderHudRenderer.layout(extractor, rows.size, contentWidth)
        val scale = ReminderConfig.hud.scale

        panel = intArrayOf(
            (rect.left * scale).toInt(),
            (rect.top * scale).toInt(),
            (rect.width * scale).toInt(),
            (rect.height * scale).toInt(),
        )

        ReminderHudRenderer.drawRows(extractor, rows, contentWidth)

        panel?.let { extractor.outline(it[0], it[1], it[2], it[3], HANDLE_COLOR) }

        extractor.centeredText(font, title, width / 2, 12, TITLE_COLOR)
        extractor.centeredText(
            font,
            Component.translatable("hex.reminders.hud.hint").withStyle(ChatFormatting.GRAY),
            width / 2,
            24,
            HINT_COLOR,
        )
        extractor.centeredText(
            font,
            Component.literal(
                String.format(Locale.ROOT, "%.3f, %.3f", ReminderConfig.hud.anchorX, ReminderConfig.hud.anchorY),
            ),
            width / 2,
            height - 44,
            HINT_COLOR,
        )
    }

    /** The live rows, or a stand-in when nothing is counting down so there is always something to drag. */
    private fun previewRows(): List<HudRow> {
        val live = ReminderHudModel.rows
        if (live.isNotEmpty()) return live
        return SAMPLE_LABELS.map { key ->
            val text = Component.translatable(key)
            HudRow(text, false, font.width(text))
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val rect = panel
        if (event.button() == LEFT_BUTTON && rect != null && inside(rect, event.x(), event.y())) {
            dragging = true
            grabX = (event.x() - rect[0]).toInt()
            grabY = (event.y() - rect[1]).toInt()
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        val rect = panel
        if (!dragging || rect == null) return super.mouseDragged(event, dragX, dragY)

        // The anchor pins whichever corner the settings name, so the drag has to be expressed against that
        // same corner — otherwise dragging a bottom-right panel would move it the wrong way.
        val left = event.x() - grabX
        val top = event.y() - grabY
        val anchorPixelX = when (ReminderConfig.hud.corner) {
            HudCorner.TOP_LEFT, HudCorner.BOTTOM_LEFT -> left
            HudCorner.TOP_RIGHT, HudCorner.BOTTOM_RIGHT -> left + rect[2]
        }
        val anchorPixelY = when (ReminderConfig.hud.corner) {
            HudCorner.TOP_LEFT, HudCorner.TOP_RIGHT -> top
            HudCorner.BOTTOM_LEFT, HudCorner.BOTTOM_RIGHT -> top + rect[3]
        }

        ReminderConfig.hud.anchorX = (anchorPixelX / width).coerceIn(0.0, 1.0)
        ReminderConfig.hud.anchorY = (anchorPixelY / height).coerceIn(0.0, 1.0)
        ReminderConfig.markDirty()
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (dragging) {
            dragging = false
            ReminderConfig.save()
            return true
        }
        return super.mouseReleased(event)
    }

    /** Arrow keys nudge, Shift speeds it up — the only way to place the panel exactly. */
    override fun keyPressed(event: KeyEvent): Boolean {
        val step = if (event.hasShiftDown()) NUDGE_FAST else NUDGE
        val dx = when {
            event.isLeft -> -step
            event.isRight -> step
            else -> 0
        }
        val dy = when {
            event.isUp -> -step
            event.isDown -> step
            else -> 0
        }
        if (dx == 0 && dy == 0) return super.keyPressed(event)

        ReminderConfig.hud.anchorX = (ReminderConfig.hud.anchorX + dx.toDouble() / width).coerceIn(0.0, 1.0)
        ReminderConfig.hud.anchorY = (ReminderConfig.hud.anchorY + dy.toDouble() / height).coerceIn(0.0, 1.0)
        ReminderConfig.markDirty()
        return true
    }

    private fun inside(rect: IntArray, x: Double, y: Double): Boolean =
        x >= rect[0] && x <= rect[0] + rect[2] && y >= rect[1] && y <= rect[1] + rect[3]

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun removed() {
        // Nudges mark the config dirty as they happen; this makes leaving the screen a definite save point
        // rather than waiting on the debounce.
        ReminderConfig.save()
    }

    private companion object {
        const val BUTTON_WIDTH = 100
        const val BUTTON_HEIGHT = 20
        const val GAP = 6

        const val LEFT_BUTTON = 0

        const val NUDGE = 1
        const val NUDGE_FAST = 10

        const val DEFAULT_ANCHOR_X = 0.01
        const val DEFAULT_ANCHOR_Y = 0.35

        const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        const val HINT_COLOR = 0xFF9A9A9A.toInt()
        const val GUIDE_COLOR = 0x30FFFFFF
        const val HANDLE_COLOR = 0xFFFFFF55.toInt()

        val SAMPLE_LABELS = listOf(
            "hex.reminders.hud.sample.1",
            "hex.reminders.hud.sample.2",
            "hex.reminders.hud.sample.3",
        )
    }
}
