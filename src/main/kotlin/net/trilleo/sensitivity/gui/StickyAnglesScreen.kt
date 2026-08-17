package net.trilleo.sensitivity.gui

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.trilleo.sensitivity.SensitivityConfig
import net.trilleo.sensitivity.model.StickyAngle
import net.trilleo.sensitivity.model.StickyAxis

/**
 * The editor for the angles that catch on top of the regular intervals.
 *
 * Reachable from **Custom angles…** in the Sensitivity tab of `/hexa config`.
 *
 * The interval settings cover everything the world is built square to, which is most of it. This is for the
 * rest: the one wall in a dungeon that runs off true, the direction a Bazaar NPC stands in, the pitch a
 * particular jump wants. Those are angles you can only find by *looking* at the thing — so the two capture
 * buttons take the direction the player is already facing, and typing one in by hand is the fallback rather
 * than the way in.
 */
class StickyAnglesScreen(private val parent: Screen?) :
    Screen(Component.translatable("hex.sticky.title")) {

    private var list: StickyAngleList? = null

    override fun init() {
        val listHeight = height - TOP - FOOTER_HEIGHT
        list = addRenderableWidget(StickyAngleList(minecraft, width, listHeight, TOP, this))

        addRenderableWidget(StringWidget(MARGIN, 12, width - MARGIN * 2, 12, title, font))

        val y = height - 28
        var x = width / 2 - (BUTTON_WIDTH * 4 + GAP * 3) / 2

        addRenderableWidget(
            Button.builder(Component.translatable("hex.sticky.add")) { add(StickyAngle()) }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.sticky.add.tooltip")))
                .build(),
        )
        x += BUTTON_WIDTH + GAP

        // Greyed out rather than hidden with no player: the screen is reachable from the config menu on the
        // title screen, where there is no direction to capture but the list is still worth editing.
        addRenderableWidget(
            Button.builder(Component.translatable("hex.sticky.capture_yaw")) { capture(StickyAxis.YAW) }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.sticky.capture_yaw.tooltip")))
                .build(),
        ).active = minecraft.player != null
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.sticky.capture_pitch")) { capture(StickyAxis.PITCH) }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.sticky.capture_pitch.tooltip")))
                .build(),
        ).active = minecraft.player != null
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.sticky.done")) { onClose() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )

        refreshRows()
    }

    /** Re-reads the angles into the list. Called after every capture and delete. */
    fun refreshRows() {
        list?.show(SensitivityConfig.settings.stickyAngles, Component.translatable("hex.sticky.empty"))
    }

    /**
     * Adds the direction the player is facing right now, on [axis].
     *
     * Deliberately usable from inside this screen: the world is still behind it and the player has not turned,
     * so the angle they came here to record is the one they are still holding.
     */
    private fun capture(axis: StickyAxis) {
        val player = minecraft.player ?: return
        val degrees = when (axis) {
            StickyAxis.YAW -> Mth.wrapDegrees(player.yRot.toDouble())
            StickyAxis.PITCH -> player.xRot.toDouble().coerceIn(-90.0, 90.0)
        }
        add(StickyAngle(axis, degrees))
    }

    /** Appends an angle and scrolls to it, unless the list is already at the ceiling a file may hold. */
    private fun add(angle: StickyAngle) {
        if (SensitivityConfig.settings.stickyAngles.size >= SensitivityConfig.ANGLES_MAX) return
        SensitivityConfig.settings.stickyAngles.add(angle)
        SensitivityConfig.normalizeNow()
        SensitivityConfig.save()
        refreshRows()
        list?.scrollToBottom()
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun removed() {
        // Rows apply as they are typed and mark the config dirty; this makes leaving the screen a definite
        // save point rather than waiting on the debounce. Normalised first, so a yaw typed as 400 is folded
        // and clamped before it is written rather than on the next load.
        SensitivityConfig.normalizeNow()
        SensitivityConfig.save()
    }

    private companion object {
        const val MARGIN = 24
        const val TOP = 32
        const val FOOTER_HEIGHT = 40
        const val BUTTON_WIDTH = 84
        const val BUTTON_HEIGHT = 20
        const val GAP = 6
    }
}
