package net.trilleo.hand

import com.google.gson.reflect.TypeToken
import net.trilleo.config.ConfigHandle
import net.trilleo.config.ConfigRegistry
import net.trilleo.config.JsonConfig

/**
 * User-facing settings for the first-person hand display, persisted at `config/hex/hand.json`.
 *
 * Offsets are in hand-space blocks and rotations in degrees; both apply to the main hand only. The swing
 * options apply to both hands — see [HandState] for how each value reaches the render path.
 *
 * @property enabled master switch; when off every hand mixin falls through to vanilla behaviour.
 * @property offsetX main-hand position offset, positive to the right.
 * @property offsetY main-hand position offset, positive upwards.
 * @property offsetZ main-hand position offset, positive away from the camera.
 * @property scale uniform scale of the main-hand arm and item.
 * @property rotationX main-hand pitch in degrees.
 * @property rotationY main-hand yaw in degrees.
 * @property rotationZ main-hand roll in degrees.
 * @property disableSwing hide the first-person swing animation entirely.
 * @property swingSpeed multiplier on swing animation speed; higher is faster.
 */
data class HandSettings(
    var enabled: Boolean = true,
    var offsetX: Double = 0.0,
    var offsetY: Double = 0.0,
    var offsetZ: Double = 0.0,
    var scale: Double = 1.0,
    var rotationX: Double = 0.0,
    var rotationY: Double = 0.0,
    var rotationZ: Double = 0.0,
    var disableSwing: Boolean = false,
    var swingSpeed: Double = 1.0,
) {
    /** Restores every display value to its default, leaving [enabled] alone. */
    fun resetToDefaults() {
        val defaults = HandSettings()
        offsetX = defaults.offsetX
        offsetY = defaults.offsetY
        offsetZ = defaults.offsetZ
        scale = defaults.scale
        rotationX = defaults.rotationX
        rotationY = defaults.rotationY
        rotationZ = defaults.rotationZ
        disableSwing = defaults.disableSwing
        swingSpeed = defaults.swingSpeed
    }

    companion object {
        const val OFFSET_MIN: Double = -1.0
        const val OFFSET_MAX: Double = 1.0
        const val OFFSET_STEP: Double = 0.01

        const val SCALE_MIN: Double = 0.5
        const val SCALE_MAX: Double = 2.0
        const val SCALE_STEP: Double = 0.05

        const val ROTATION_MIN: Double = -180.0
        const val ROTATION_MAX: Double = 180.0
        const val ROTATION_STEP: Double = 1.0

        const val SWING_SPEED_MIN: Double = 0.25
        const val SWING_SPEED_MAX: Double = 4.0
        const val SWING_SPEED_STEP: Double = 0.05
    }
}

/** Loads and holds the singleton [HandSettings]. Call [load] once at feature init. */
object HandConfig {
    private val config = JsonConfig(
        name = "hand",
        type = object : TypeToken<HandSettings>() {}.type,
        default = { HandSettings() },
        normalizer = ::normalize,
    )

    var settings: HandSettings = HandSettings()
        private set

    private val handle = ConfigRegistry.register(
        ConfigHandle(config, adopt = { settings = it }, current = { settings }),
    )

    /**
     * Repairs a loaded value. GSON ignores Kotlin constructor defaults, so a field absent from the JSON is
     * left at its JVM default of `0.0` — harmless for the offsets and rotations (whose default *is* zero)
     * but not for [HandSettings.scale] (an invisible hand) or [HandSettings.swingSpeed] (a division by
     * zero). Those two treat a non-positive value as "absent" and fall back to `1.0`; everything else is
     * clamped into the range its slider offers, so a hand-edited file can never push the renderer somewhere
     * the GUI cannot walk back.
     */
    private fun normalize(settings: HandSettings) {
        with(settings) {
            offsetX = offsetX.coerceIn(HandSettings.OFFSET_MIN, HandSettings.OFFSET_MAX)
            offsetY = offsetY.coerceIn(HandSettings.OFFSET_MIN, HandSettings.OFFSET_MAX)
            offsetZ = offsetZ.coerceIn(HandSettings.OFFSET_MIN, HandSettings.OFFSET_MAX)
            rotationX = rotationX.coerceIn(HandSettings.ROTATION_MIN, HandSettings.ROTATION_MAX)
            rotationY = rotationY.coerceIn(HandSettings.ROTATION_MIN, HandSettings.ROTATION_MAX)
            rotationZ = rotationZ.coerceIn(HandSettings.ROTATION_MIN, HandSettings.ROTATION_MAX)

            scale = if (scale <= 0.0) 1.0 else scale.coerceIn(HandSettings.SCALE_MIN, HandSettings.SCALE_MAX)
            swingSpeed = if (swingSpeed <= 0.0) {
                1.0
            } else {
                swingSpeed.coerceIn(HandSettings.SWING_SPEED_MIN, HandSettings.SWING_SPEED_MAX)
            }
        }
    }

    fun load() = handle.loadInitial()

    /** Writes immediately. Prefer [markDirty] from anything that fires repeatedly, such as a slider drag. */
    fun save() = handle.saveNow()

    /** Records that settings changed; the write is batched and lands about a second later. */
    fun markDirty() = handle.markDirty()
}
