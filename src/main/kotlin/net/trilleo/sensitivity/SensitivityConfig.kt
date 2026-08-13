package net.trilleo.sensitivity

import com.google.gson.reflect.TypeToken
import net.minecraft.client.Minecraft
import net.trilleo.config.ConfigHandle
import net.trilleo.config.ConfigRegistry
import net.trilleo.config.JsonConfig

/**
 * User-facing settings for the sensitivity hold, persisted at `config/hex/sensitivity.json`.
 *
 * Both numbers are percentages rather than raw option values, because that is the unit the player already
 * reads sensitivity in — Minecraft's own slider says "100%", not "0.5".
 *
 * @property enabled master switch; when off the keybind is inert and the wheel goes back to the hotbar.
 * @property stepPercent how much one wheel notch changes the sensitivity, as a percentage of the current
 *   value. Multiplicative rather than additive so a notch is worth the same everywhere: at 10% a step down
 *   from 100% lands on 90%, and a step down from 10% lands on 9% — still a usable move, where subtracting a
 *   fixed amount would have hit zero.
 * @property snapPercent the multiplier applied the instant the key goes down, before any scrolling, as a
 *   percentage of your normal sensitivity. 100 means "start where I already was".
 */
data class SensitivitySettings(
    var enabled: Boolean = true,
    var stepPercent: Double = 10.0,
    var snapPercent: Double = 100.0,
)

/** Loads and holds the singleton [SensitivitySettings]. Call [load] once at feature init. */
object SensitivityConfig {
    private val config = JsonConfig(
        name = "sensitivity",
        type = object : TypeToken<SensitivitySettings>() {}.type,
        default = { SensitivitySettings() },
        // A step of 0 would make the wheel do nothing at all, and a negative one would invert it in a way no
        // setting offers; a hand-edited file must not be able to produce either.
        normalizer = {
            it.stepPercent = it.stepPercent.coerceIn(STEP_MIN, STEP_MAX)
            it.snapPercent = it.snapPercent.coerceIn(SNAP_MIN, SNAP_MAX)
        },
    )

    const val STEP_MIN: Double = 1.0
    const val STEP_MAX: Double = 50.0
    const val SNAP_MIN: Double = 10.0
    const val SNAP_MAX: Double = 200.0

    var settings: SensitivitySettings = SensitivitySettings()
        private set

    /** Exposed so the settings menu can offer this tab a reset button. */
    val handle = ConfigRegistry.register(
        ConfigHandle(
            config,
            adopt = { settings = it },
            current = { settings },
            // A profile switch or a clipboard import can switch the feature off while the key is held down,
            // which would otherwise leave the borrowed sensitivity in place with nothing left to restore it.
            afterReload = {
                if (!settings.enabled) SensitivityState.end(Minecraft.getInstance())
            },
        ),
    )

    fun load() = handle.loadInitial()

    /** Writes immediately. Prefer [markDirty] from anything that fires repeatedly. */
    fun save() = handle.saveNow()

    /** Records that settings changed; the write is batched and lands about a second later. */
    fun markDirty() = handle.markDirty()
}
