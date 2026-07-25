package net.trilleo.notebook

import com.google.gson.reflect.TypeToken
import net.trilleo.config.ConfigHandle
import net.trilleo.config.ConfigRegistry
import net.trilleo.config.JsonConfig
import net.trilleo.notebook.model.NoteSort

/**
 * Everything about the notebook that is a *setting*, persisted at `config/hex/notebook.json`.
 *
 * The notes themselves are emphatically not here — they live under `config/hex/notebook/` and are handled by
 * [NotebookStore], outside [ConfigRegistry]. This file holds how the notebook is *displayed*; that one holds
 * what the player wrote. The split is what lets settings follow a config profile while the notes do not.
 *
 * @property enabled the feature's master switch. Nullable for the same reason
 *   [net.trilleo.reminder.ReminderSettings.enabled] is: GSON leaves an absent `boolean` at the JVM default of
 *   `false`, so a hand-written file omitting the key would load as *disabled*, the opposite of what omitting a
 *   setting should mean. Read it through [NotebookConfig.active].
 */
data class NotebookSettings(
    var enabled: Boolean? = null,
    var sort: NoteSort = NoteSort.MODIFIED,
    var showSnippets: Boolean = true,
)

/**
 * Loads and holds the singleton [NotebookSettings].
 *
 * Registered with [ConfigRegistry], so display preferences join config profiles and clipboard export at no
 * extra cost — and, deliberately, *only* those. See [NotebookStore] for why the notes stay out.
 */
object NotebookConfig {
    private val config = JsonConfig(
        name = "notebook",
        type = object : TypeToken<NotebookSettings>() {}.type,
        default = { NotebookSettings() },
        normalizer = ::normalize,
    )

    var settings: NotebookSettings = NotebookSettings()
        private set

    val handle = ConfigRegistry.register(
        ConfigHandle(config, adopt = { settings = it }, current = { settings }),
    )

    /** Whether the feature is switched on, treating an absent key as on. */
    val active: Boolean get() = settings.enabled != false

    val sort: NoteSort get() = settings.sort

    fun load() = handle.loadInitial()

    fun save() = handle.saveNow()

    fun markDirty() = handle.markDirty()

    /**
     * Repairs a loaded value.
     *
     * An enum name this build does not know arrives `null` exactly as an absent one does — a file written by
     * a newer Hex with a sort mode this version has never heard of would otherwise leave a null in a
     * non-nullable field and throw the first time the browser opened.
     */
    @Suppress("SENSELESS_COMPARISON")
    private fun normalize(settings: NotebookSettings) {
        if (settings.sort == null) settings.sort = NoteSort.MODIFIED
    }
}
