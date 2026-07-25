package net.trilleo.notebook.model

import java.util.Locale

/**
 * Everything about a note except its text: what it is called, where it is filed, and how it looks in the list.
 *
 * Persisted twice, deliberately. The authoritative copy is the front-matter header of the note's own `.md`
 * file, which is what makes a note file a *complete* note — send one to a friend and it keeps its colour, its
 * folder and its tags. The copy in `index.json` is a cache, so the browser can list a hundred notes without
 * opening a hundred files; when the two disagree, the file wins. See [net.trilleo.notebook.NotebookStore].
 *
 * A plain `var`-only class rather than a `data class`, following [net.trilleo.itemcustom.ItemCustomization]
 * and [net.trilleo.region.model.Region]: GSON instantiates it reflectively and never runs Kotlin's defaults,
 * and equality has to stay identity so a list row can delete *this* note rather than one that happens to look
 * the same.
 *
 * The note's id is not a field — it is the base name of its file. See [net.trilleo.notebook.Notebook.create].
 */
class NoteMeta {
    /** What the player calls it. May carry `&` codes, so a note can have a chroma title. */
    var title: String = ""

    /** A `/`-separated path, or blank for unfiled. Folders exist only as the set of values used here. */
    var folder: String = ""

    var tags: MutableList<String> = mutableListOf()

    /** `"#RRGGBB"` for the list row's colour bar, or blank for the theme default. */
    var color: String = ""

    /** An item id drawn as the note's icon, e.g. `"minecraft:writable_book"`. Blank for the default book. */
    var icon: String = ""

    var pinned: Boolean = false

    var createdAt: Long = 0L
    var modifiedAt: Long = 0L

    /** Manual ordering, used only when the sort mode is [NoteSort.MANUAL]. */
    var sortIndex: Int = 0

    /**
     * Whether this note is the one shown on the HUD panel.
     *
     * Nullable for the reason [net.trilleo.itemcustom.ItemCustomization.enabled] documents: GSON leaves an
     * absent `boolean` at `false`, so a nullable field is the only way to tell "the key was absent" from "the
     * player said no". Read through [showsInHud] so the asymmetry stays in one place.
     */
    var hud: Boolean? = null

    /** On the HUD, show only the unchecked task lines rather than the whole note. */
    var hudTasksOnly: Boolean = false

    /**
     * The note format version this file was written by.
     *
     * A note carrying a higher number than this build understands is loaded read-only rather than rewritten,
     * for the same reason [net.trilleo.suggest.model.ModelStore] refuses a too-new model file: launching an
     * older Hex by accident must not destroy what a newer one wrote.
     */
    var v: Int = FORMAT_VERSION

    val showsInHud: Boolean get() = hud == true

    /** Whether this note was written by a build newer than this one, and so must not be saved over. */
    val readOnly: Boolean get() = v > FORMAT_VERSION

    /** The tags as the player typed them into the meta screen, and back. */
    fun tagsAsText(): String = tags.joinToString(", ")

    fun setTagsFromText(text: String) {
        tags = text.split(',').mapNotNull { it.trim().lowercase(Locale.ROOT).ifEmpty { null } }
            .distinct()
            .toMutableList()
    }

    /** A copy, for duplicating a note and for the undo of a meta edit. */
    fun copy(): NoteMeta = NoteMeta().also { copy ->
        copy.title = title
        copy.folder = folder
        copy.tags = tags.toMutableList()
        copy.color = color
        copy.icon = icon
        copy.pinned = pinned
        copy.createdAt = createdAt
        copy.modifiedAt = modifiedAt
        copy.sortIndex = sortIndex
        copy.hud = hud
        copy.hudTasksOnly = hudTasksOnly
        copy.v = v
    }

    /**
     * Repairs what GSON's reflection leaves behind: a field absent from the JSON arrives as the JVM default,
     * which for a non-nullable Kotlin `String` means `null` despite the declared type.
     *
     * Timestamps of `0L` are left alone rather than filled in with the current time. A fabricated date reads
     * as fact once it is on screen, and [net.trilleo.config.ConfigProfiles] makes exactly this argument.
     */
    // UNNECESSARY_SAFE_CALL alongside it for the same reason: `tags` is declared as a list of non-null
    // strings, but GSON will happily deserialize a hand-written `["mining", null]` into one that has a null
    // in it, and Kotlin cannot see that from the type.
    @Suppress("SENSELESS_COMPARISON", "UNNECESSARY_SAFE_CALL")
    fun normalize(fallbackTitle: String) {
        if (title == null) title = ""
        if (folder == null) folder = ""
        if (color == null) color = ""
        if (icon == null) icon = ""
        if (tags == null) tags = mutableListOf()

        title = title.trim().ifEmpty { fallbackTitle }
        folder = folder.trim().trim('/').lowercase(Locale.ROOT)
        color = color.trim()
        icon = icon.trim().lowercase(Locale.ROOT)
        tags = tags.mapNotNull { it?.trim()?.lowercase(Locale.ROOT)?.ifEmpty { null } }
            .distinct()
            .toMutableList()
        if (v <= 0) v = FORMAT_VERSION
    }

    companion object {
        /**
         * The note file format this build writes. Bump it when a change would make an older Hex mis-read a
         * note, not merely ignore something it does not know about.
         */
        const val FORMAT_VERSION: Int = 1
    }
}
