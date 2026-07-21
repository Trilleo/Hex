package net.trilleo.freecam

import com.google.gson.reflect.TypeToken
import net.minecraft.client.Minecraft
import net.trilleo.config.ConfigHandle
import net.trilleo.config.ConfigRegistry
import net.trilleo.config.JsonConfig

/**
 * Base fly speed presets, in blocks per tick (20 ticks/second). The scroll wheel scales around the chosen
 * preset at runtime; see [FreecamState.adjustSpeed].
 */
enum class FlySpeed(val baseSpeed: Double) {
    SLOW(0.2),
    NORMAL(0.5),
    FAST(1.0),
    TURBO(2.0),
}

/**
 * User-facing settings for the freecam, persisted at `config/hex/freecam.json`.
 *
 * @property enabled master switch; when off the feature receives no dispatch and its keybind is inert.
 * @property flySpeed base camera fly speed preset.
 */
data class FreecamSettings(
    var enabled: Boolean = true,
    var flySpeed: FlySpeed = FlySpeed.NORMAL,
)

/** Loads and holds the singleton [FreecamSettings]. Call [load] once at feature init. */
object FreecamConfig {
    private val config = JsonConfig(
        name = "freecam",
        type = object : TypeToken<FreecamSettings>() {}.type,
        default = { FreecamSettings() },
        // GSON leaves an absent enum field null despite the non-null Kotlin type; repair it.
        normalizer = { @Suppress("SENSELESS_COMPARISON") if (it.flySpeed == null) it.flySpeed = FlySpeed.NORMAL },
    )

    var settings: FreecamSettings = FreecamSettings()
        private set

    private val handle = ConfigRegistry.register(
        ConfigHandle(
            config,
            adopt = { settings = it },
            current = { settings },
            // A profile switch or clipboard import can turn freecam off while the camera is detached, which
            // would strand it exactly as toggling the setting by hand would. Same guard as FreecamFeature's.
            afterReload = {
                if (!settings.enabled) FreecamState.deactivate(Minecraft.getInstance())
            },
        ),
    )

    fun load() = handle.loadInitial()

    /** Writes immediately. Prefer [markDirty] from anything that fires repeatedly. */
    fun save() = handle.saveNow()

    /** Records that settings changed; the write is batched and lands about a second later. */
    fun markDirty() = handle.markDirty()
}
