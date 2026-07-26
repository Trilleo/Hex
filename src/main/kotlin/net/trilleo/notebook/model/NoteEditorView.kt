package net.trilleo.notebook.model

/**
 * How the note editor is split between the markdown source and the rendered preview.
 *
 * A setting rather than a per-note property: it describes how someone likes to write, not anything about the
 * note, so it follows the player from note to note exactly as [NoteSort] does.
 */
enum class NoteEditorView {
    /** The source pane alone — the whole width for writing. */
    SOURCE,

    /** Source on the left, preview on the right. */
    SPLIT,

    /** The preview alone, for reading a note back rather than working on it. */
    PREVIEW,
    ;

    fun next(): NoteEditorView = entries[(ordinal + 1) % entries.size]

    val showsSource: Boolean get() = this != PREVIEW

    val showsPreview: Boolean get() = this != SOURCE
}
