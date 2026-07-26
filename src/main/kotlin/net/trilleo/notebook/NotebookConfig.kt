package net.trilleo.notebook

import com.google.gson.reflect.TypeToken
import net.trilleo.config.ConfigHandle
import net.trilleo.config.ConfigRegistry
import net.trilleo.config.JsonConfig
import net.trilleo.notebook.model.NoteEditorView
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
 * @property editorView how the note editor splits its width between the markdown source and the rendered
 *   preview. Not nullable: an unknown or absent value is repaired in [NotebookConfig.normalize], the same way
 *   [sort] is.
 * @property lineSpacing extra pixels between the lines of a rendered note, on top of the font's own height.
 *   Nullable for the reason [enabled] is: an absent key would load as `0`, which is a real value here — the
 *   crammed original — and so could not be told apart from someone having chosen it. Read it through
 *   [NotebookConfig.lineSpacing].
 * @property backgroundOpacity how solid the notebook's own panels are, from 0 (see straight through to the
 *   game) to 1 (flat). Nullable for the same reason [enabled] is, and a worse trap here: an absent key would
 *   load as `0.0` and the whole notebook would come up invisible. Read it through
 *   [NotebookConfig.backgroundOpacity].
 */
data class NotebookSettings(
    var enabled: Boolean? = null,
    var sort: NoteSort = NoteSort.MODIFIED,
    var showSnippets: Boolean = true,
    var backgroundOpacity: Double? = null,
    var editorView: NoteEditorView = NoteEditorView.SPLIT,
    var lineSpacing: Int? = null,
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

    val editorView: NoteEditorView get() = settings.editorView

    /** Extra pixels between rendered lines, treating an absent key as [SPACING_DEFAULT] and clamping the rest. */
    val lineSpacing: Int
        get() = (settings.lineSpacing ?: SPACING_DEFAULT).coerceIn(SPACING_MIN, SPACING_MAX)

    /**
     * How solid the notebook's panels are, treating an absent key as [OPACITY_DEFAULT] and clamping anything
     * a hand-edited file supplies — a value outside the range would otherwise pack into a nonsense alpha.
     */
    val backgroundOpacity: Double
        get() = (settings.backgroundOpacity ?: OPACITY_DEFAULT).coerceIn(OPACITY_MIN, OPACITY_MAX)

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
        if (settings.editorView == null) settings.editorView = NoteEditorView.SPLIT
        settings.backgroundOpacity = settings.backgroundOpacity?.coerceIn(OPACITY_MIN, OPACITY_MAX)
        settings.lineSpacing = settings.lineSpacing?.coerceIn(SPACING_MIN, SPACING_MAX)
    }

    /** Fully see-through: the notebook is chrome and text over whatever is behind it. */
    const val OPACITY_MIN: Double = 0.0

    /** Flat: nothing behind the notebook shows through at all. */
    const val OPACITY_MAX: Double = 1.0

    const val OPACITY_STEP: Double = 0.05

    /** The stock look — solid enough to read against a bright world, not the flat black wall of 1.0. */
    const val OPACITY_DEFAULT: Double = 0.75

    /** Lines touching, which is what the font alone gives and what a dense checklist may want. */
    const val SPACING_MIN: Int = 0

    /** Double-spaced and then some; past this a note scrolls more than it reads. */
    const val SPACING_MAX: Int = 8

    /** Enough air to tell one line from the next without the note growing a page longer. */
    const val SPACING_DEFAULT: Int = 2
}
