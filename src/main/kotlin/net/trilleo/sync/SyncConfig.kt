package net.trilleo.sync

import com.google.gson.reflect.TypeToken
import net.trilleo.config.ConfigHandle
import net.trilleo.config.ConfigRegistry
import net.trilleo.config.JsonConfig

/**
 * Settings for trilleo.net sync, persisted at `config/hex/trilleo-sync.json`.
 *
 * The token is deliberately **not** here. It lives in its own file that nothing else reads — see
 * [TrilleoAccount] — so that no profile snapshot, clipboard export or reset can ever carry it.
 *
 * @property enabled read the Attribute Menu and Hunting Box at all.
 * @property autoSync send what was read shortly after the menu closes; off means only `/hexa sync now` sends.
 * @property chatSummary say in chat what each sync changed.
 * @property apiUrl the API's address; changed only for testing against a local copy of the site.
 */
data class SyncSettings(
    var enabled: Boolean = true,
    var autoSync: Boolean = true,
    var chatSummary: Boolean = true,
    var apiUrl: String = TrilleoApi.DEFAULT_URL,
)

/** Loads and holds the singleton [SyncSettings]. Call [load] once at feature init. */
object SyncConfig {
    private val config = JsonConfig(
        name = "trilleo-sync",
        type = object : TypeToken<SyncSettings>() {}.type,
        default = { SyncSettings() },
        normalizer = { s ->
            // GSON leaves a field missing from the file null whatever Kotlin says.
            @Suppress("SENSELESS_COMPARISON")
            if (s.apiUrl == null || s.apiUrl.isBlank()) s.apiUrl = TrilleoApi.DEFAULT_URL
        },
    )

    @Volatile
    var settings: SyncSettings = SyncSettings()
        private set

    /**
     * Registered as `global`: which account this install uploads to, and where, belongs to the install and not
     * to a loadout. A profile switch silently turning sync off, or pointing it at a test server, would be the
     * kind of surprise nobody would think to look for.
     */
    val handle = ConfigRegistry.register(
        ConfigHandle(config, adopt = { settings = it }, current = { settings }, global = true),
    )

    fun load() = handle.loadInitial()

    fun save() = handle.saveNow()
}
