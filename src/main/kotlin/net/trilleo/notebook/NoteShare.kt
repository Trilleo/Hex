package net.trilleo.notebook

import net.trilleo.notebook.md.NoteFrontMatter
import net.trilleo.notebook.model.NoteDocument
import net.trilleo.notebook.model.NoteMeta

/**
 * Sending a note to someone else, and taking one back.
 *
 * There is no export *format* here, because there does not need to be one: a note file already carries its own
 * metadata in a front-matter header, so the exported text **is** the file. That is the whole payoff of the
 * decision in [NoteFrontMatter] — a note pasted into a message keeps its colour, its folder and its tags, and
 * a note saved out of a chat window and dropped into `config/hex/notebook/notes/` is picked up by
 * [NotebookStore.load] with no import step at all.
 *
 * Contrast [net.trilleo.config.ConfigProfiles.exportToString], which has to wrap its payload in an envelope
 * because a settings blob is many files with no shape of their own.
 */
object NoteShare {

    /** The outcome of an import, so the caller can explain what happened rather than only saying "no". */
    sealed interface ImportResult {
        /** The text did not look like a note — most likely the clipboard held something else entirely. */
        data object NotANote : ImportResult

        /** Written by a Hex whose note format this version cannot read. */
        data class TooNew(val version: Int) : ImportResult

        /** Added as a new note. */
        data class Added(val document: NoteDocument) : ImportResult
    }

    /** [document] as the exact text of its file — header and body. */
    fun export(document: NoteDocument): String = NotebookStore.snapshotOf(document)

    /**
     * Adopts [text] as a new note.
     *
     * Deliberately permissive about what counts as a note: text with no header is imported as an untitled
     * note rather than refused, because plain markdown from anywhere else is exactly the thing someone will
     * try to paste in, and refusing it would be a worse answer than filing it. Only genuinely empty text and
     * a *newer* format are turned away.
     *
     * The import always creates a new note and never overwrites one that happens to share a title. Losing
     * someone's own note to a paste would be unrecoverable; a duplicate is a two-second fix.
     *
     * Nothing here is allowed to throw: it runs from a GUI click over whatever happened to be on the
     * clipboard, including something hand-edited into nonsense.
     */
    fun import(text: String): ImportResult = runCatching {
        if (text.isBlank()) return@runCatching ImportResult.NotANote

        val (parsed, body) = NoteFrontMatter.read(text)
        if (parsed != null && parsed.v > NoteMeta.FORMAT_VERSION) {
            return@runCatching ImportResult.TooNew(parsed.v)
        }

        val now = System.currentTimeMillis()
        val meta = (parsed ?: NoteMeta()).apply {
            normalize(titleFrom(body).ifEmpty { Notebook.UNTITLED })
            // The sender's timestamps describe their copy; this one starts its life now. The pin and the HUD
            // flag are theirs too — a pasted note must not silently take over the recipient's HUD panel.
            createdAt = now
            modifiedAt = now
            pinned = false
            hud = null
            v = NoteMeta.FORMAT_VERSION
        }

        val document = NoteDocument(Notebook.freeId(meta.title), meta, body.trim('\n'))
        Notebook.adopt(document)
        ImportResult.Added(document)
    }.getOrElse { ImportResult.NotANote }

    /**
     * A title for a note that arrived without one: its first heading, or its first non-blank line.
     *
     * Reading one line beats leaving it "Untitled" — someone pasting a markdown file almost always has a `#`
     * heading at the top, and that is what they would have called it themselves.
     */
    private fun titleFrom(body: String): String {
        val line = body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return ""
        return line.trimStart('#', ' ', '\t').trim().take(MAX_DERIVED_TITLE)
    }

    private const val MAX_DERIVED_TITLE = 60
}
