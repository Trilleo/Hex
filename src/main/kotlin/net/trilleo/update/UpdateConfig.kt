package net.trilleo.update

import com.google.gson.reflect.TypeToken
import net.trilleo.config.ConfigHandle
import net.trilleo.config.ConfigRegistry
import net.trilleo.config.JsonConfig

/**
 * User-facing settings for the auto-updater, persisted at `config/hex/update.json`.
 *
 * @property enabled run the background update check on startup.
 * @property includePrereleases treat GitHub prereleases as available updates, not only stable releases.
 */
data class UpdateSettings(
    var enabled: Boolean = true,
    var includePrereleases: Boolean = false,
)

/** Loads and holds the singleton [UpdateSettings]. Call [load] once at feature init. */
object UpdateConfig {
    private val config = JsonConfig(
        name = "update",
        type = object : TypeToken<UpdateSettings>() {}.type,
        default = { UpdateSettings() },
    )

    var settings: UpdateSettings = UpdateSettings()
        private set

    /** Exposed so the settings menu can offer this tab a reset button. */
    val handle = ConfigRegistry.register(
        ConfigHandle(config, adopt = { settings = it }, current = { settings }),
    )

    fun load() = handle.loadInitial()

    /** Writes immediately. Prefer [markDirty] from anything that fires repeatedly. */
    fun save() = handle.saveNow()

    /** Records that settings changed; the write is batched and lands about a second later. */
    fun markDirty() = handle.markDirty()
}
