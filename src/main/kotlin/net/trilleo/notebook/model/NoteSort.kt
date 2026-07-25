package net.trilleo.notebook.model

/** How the browser orders notes. Pinned notes always float to the top, whichever of these is chosen. */
enum class NoteSort {
    /** Most recently edited first — the default, because the note you want is usually the one you just wrote. */
    MODIFIED,

    /** Newest first. */
    CREATED,

    /** Alphabetical by title. */
    TITLE,

    /** Whatever order the player dragged them into ([NoteMeta.sortIndex]). */
    MANUAL,
}
