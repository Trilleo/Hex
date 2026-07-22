package net.trilleo.hand

import kotlin.math.roundToInt

/**
 * The bridge between [HandConfig] and the hand mixins. Declared as an `object` so the Java mixins can reach
 * it as `HandState.INSTANCE`, mirroring [net.trilleo.freecam.FreecamState].
 *
 * Every accessor reads the live settings on each call rather than caching — the config setters persist
 * immediately and mutate the same object, so a slider drag shows up on the very next frame.
 */
object HandState {

    /** Whether the feature is on at all; every mixin early-outs on this. */
    private val active: Boolean get() = HandConfig.settings.enabled

    /**
     * Whether the main-hand pose needs adjusting. Returns `false` for the neutral transform so the common
     * case costs no pose push/pop at all.
     */
    fun shouldTransform(): Boolean {
        if (!active) return false
        val settings = HandConfig.settings
        return settings.offsetX != 0.0 ||
                settings.offsetY != 0.0 ||
                settings.offsetZ != 0.0 ||
                settings.scale != 1.0 ||
                settings.rotationX != 0.0 ||
                settings.rotationY != 0.0 ||
                settings.rotationZ != 0.0
    }

    val offsetX: Float get() = HandConfig.settings.offsetX.toFloat()
    val offsetY: Float get() = HandConfig.settings.offsetY.toFloat()
    val offsetZ: Float get() = HandConfig.settings.offsetZ.toFloat()
    val scale: Float get() = HandConfig.settings.scale.toFloat()
    val rotationX: Float get() = HandConfig.settings.rotationX.toFloat()
    val rotationY: Float get() = HandConfig.settings.rotationY.toFloat()
    val rotationZ: Float get() = HandConfig.settings.rotationZ.toFloat()

    /**
     * Whether the first-person swing animation should be held at rest.
     *
     * Two independent reasons can hide it: a per-item rule matching what is in the main hand, or the global
     * `disable_swing` setting. The per-item half deliberately does **not** check [active] — the list has its
     * own switch and its own config file, and someone turning off the hand *display* did not ask to lose a
     * curated item list. See [SwingItems] for the cost of that call (three early-outs, no NBT).
     */
    fun shouldSuppressSwing(): Boolean =
        SwingItems.suppressesHeldItem() || (active && HandConfig.settings.disableSwing)

    /**
     * Rescales the local player's swing animation length. [vanilla] is whatever the game computed (Haste
     * and Mining Fatigue already applied), so the multiplier composes with those rather than replacing
     * them. Never returns less than one tick, which would stall the animation entirely.
     */
    fun swingDuration(vanilla: Int): Int {
        if (!active) return vanilla
        val speed = HandConfig.settings.swingSpeed
        if (speed <= 0.0) return vanilla
        return (vanilla / speed).roundToInt().coerceAtLeast(1)
    }
}
