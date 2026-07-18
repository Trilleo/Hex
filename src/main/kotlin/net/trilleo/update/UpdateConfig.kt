package net.trilleo.update

import com.google.gson.reflect.TypeToken
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

	fun load() {
		settings = config.load()
	}

	fun save() {
		config.save(settings)
	}
}
