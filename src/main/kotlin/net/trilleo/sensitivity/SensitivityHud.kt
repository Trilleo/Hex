package net.trilleo.sensitivity

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import java.util.*

/**
 * The readout shown under the crosshair while the sensitivity key is held.
 *
 * The hold is otherwise invisible. Sensitivity is a *feel*, and a feel is not something you can count notches
 * of: a player who has scrolled down to line up a shot has no way of knowing whether they are at half their
 * normal sensitivity or a tenth of it, and no way back to a value they liked except by letting go and starting
 * again. One number fixes that.
 *
 * Under it, and only while the magnet is holding one, sits the angle the view has settled on — which is what
 * turns [StickyAim] from something that happens into something you can aim with.
 *
 * Drawn from the feature's frame hook, which is attached to the vanilla chat element and therefore inherits
 * its render condition, so F1 hides this along with everything else.
 */
object SensitivityHud {

    private const val PADDING = 4

    /** How far under the middle of the screen the panel hangs, clear of the crosshair. */
    private const val DROP = 14

    private const val BACKGROUND = 0xB0101010.toInt()
    private const val BORDER = 0x60FFFFFF
    private const val TEXT_COLOR = 0xFFE0E0E0.toInt()
    private const val LOCK_COLOR = 0xFF80E080.toInt()

    /** Draws the readout, or nothing when no hold is running or the player has switched it off. */
    fun draw(extractor: GuiGraphicsExtractor) {
        if (!SensitivityState.active || !SensitivityConfig.hud) return
        val font = Minecraft.getInstance().font

        val value = Component.translatable("hex.sensitivity.hud.value", SensitivityState.percentText)
        val width = font.width(value) + PADDING * 2
        val height = font.lineHeight + PADDING * 2

        val left = (extractor.guiWidth() - width) / 2
        val top = extractor.guiHeight() / 2 + DROP

        extractor.fill(left, top, left + width, top + height, BACKGROUND)
        extractor.outline(left, top, width, height, BORDER)
        extractor.text(font, value, left + PADDING, top + PADDING, TEXT_COLOR)

        // Below the panel rather than inside it, so the panel keeps one size: a box that grew a line every
        // time an angle caught would jump under the crosshair on every pass over one.
        val lock = lockText() ?: return
        val lockX = (extractor.guiWidth() - font.width(lock)) / 2
        extractor.text(font, lock, lockX, top + height + 3, LOCK_COLOR)
    }

    /** "Yaw 180° · Pitch 0°" for whichever axes are sitting on an angle, or null when neither is. */
    private fun lockText(): Component? {
        val yaw = StickyAim.lockedYaw?.let { Component.translatable("hex.sensitivity.hud.yaw", degrees(it)) }
        val pitch = StickyAim.lockedPitch?.let { Component.translatable("hex.sensitivity.hud.pitch", degrees(it)) }
        return when {
            yaw != null && pitch != null -> Component.empty().append(yaw).append(SEPARATOR).append(pitch)
            else -> yaw ?: pitch
        }
    }

    /**
     * One angle, as the player would say it.
     *
     * Folded back into (−180, 180] because vanilla's yaw runs on past a full turn as you spin — a lock
     * reported as "1260°" is the same direction as "180°" and reads as a bug. Pitch is already inside that
     * range, so the same call leaves it alone.
     */
    private fun degrees(value: Double): String =
        String.format(Locale.ROOT, "%.0f°", Mth.wrapDegrees(value))

    /** Not language: the gap between two readings. */
    private val SEPARATOR: Component = Component.literal("  ")
}
