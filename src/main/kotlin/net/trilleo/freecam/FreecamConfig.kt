package net.trilleo.freecam

import com.google.gson.reflect.TypeToken
import net.trilleo.config.JsonConfig

/**
 * Base fly speed presets, in blocks per tick (20 ticks/second). The scroll wheel scales around the chosen
 * preset at runtime; see [FreecamState.adjustSpeed].
 */
enum class FlySpeed(val displayName: String, val baseSpeed: Double) {
    SLOW("Slow", 0.2),
    NORMAL("Normal", 0.5),
    FAST("Fast", 1.0),
    TURBO("Turbo", 2.0),
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
        normalize = { @Suppress("SENSELESS_COMPARISON") if (it.flySpeed == null) it.flySpeed = FlySpeed.NORMAL },
    )

    var settings: FreecamSettings = FreecamSettings()
        private set

    fun load() {
        settings = config.load()
    }

    fun save() {
        config.save(settings)
    }
}
