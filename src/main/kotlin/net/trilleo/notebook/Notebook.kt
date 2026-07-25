package net.trilleo.notebook

import net.trilleo.notebook.model.NoteDocument
import net.trilleo.notebook.model.NoteMeta
import net.trilleo.notebook.model.NoteSort
import java.util.Locale

/**
 * The notebook in memory: every note, and everything that can be done to one.
 *
 * Screens and commands talk to this rather than to [NotebookStore], so there is exactly one place that knows
 * how a note is created, renamed, duplicated or deleted, and exactly one place that decides when the disk
 * needs telling. [generation] is the token a screen holds to notice that something else changed the list —
 * the same idiom [net.trilleo.itemcustom.ItemCustomizeConfig] uses for its own caches.
 *
 * Documents are held by **reference identity**, never by title or by index, so deleting the note a list row
 * is pointing at can never take an equal-looking sibling instead. That is why [NoteMeta] is not a data class.
 */
object Notebook {

    private val notes = mutableListOf<NoteDocument>()

    /** Changes whenever a note is added, removed, or reordered — not when one's text is edited. */
    var generation: Int = 0
        private set

    /** Reads the notebook from disk. Called once at feature init. */
    fun load() {
        notes.clear()
        notes += NotebookStore.load()
        generation++
    }

    fun all(): List<NoteDocument> = notes

    fun isEmpty(): Boolean = notes.isEmpty()

    fun byId(id: String): NoteDocument? = notes.firstOrNull { it.id == id }

    /**
     * The note whose title the player typed, matched loosely: exactly first, then case-insensitively, then by
     * prefix. Commands take a title because that is what the player knows the note as; ids are internal.
     */
    fun byTitle(title: String): NoteDocument? {
        val wanted = title.trim()
        if (wanted.isEmpty()) return null
        notes.firstOrNull { it.meta.title == wanted }?.let { return it }
        val folded = wanted.lowercase(Locale.ROOT)
        notes.firstOrNull { it.meta.title.lowercase(Locale.ROOT) == folded }?.let { return it }
        notes.firstOrNull { it.id == folded }?.let { return it }
        return notes.firstOrNull { it.meta.title.lowercase(Locale.ROOT).startsWith(folded) }
    }

    /** The note pinned to the HUD, if any. At most one is ever flagged — [pinToHud] enforces it. */
    fun hudNote(): NoteDocument? = notes.firstOrNull { it.meta.showsInHud }

    /** Every folder in use, sorted. Folders are not stored anywhere: they are the set of values notes carry. */
    fun folders(): List<String> =
        notes.mapNotNull { it.meta.folder.ifEmpty { null } }.distinct().sorted()

    /** Every tag in use, sorted. */
    fun tags(): List<String> = notes.flatMap { it.meta.tags }.distinct().sorted()

    // ---- mutation --------------------------------------------------------------------------------------

    /**
     * Creates an empty note called [title] and returns it.
     *
     * The id is derived from the title once, here, and never again — see [NotebookStore.idFor]. A note created
     * with no title is called "Untitled" rather than left blank, because a blank row in the browser is
     * indistinguishable from a broken one.
     */
    fun create(title: String, body: String = "", folder: String = ""): NoteDocument {
        val now = System.currentTimeMillis()
        val meta = NoteMeta().apply {
            this.title = title.trim().ifEmpty { UNTITLED }
            this.folder = folder
            createdAt = now
            modifiedAt = now
            sortIndex = (notes.maxOfOrNull { it.meta.sortIndex } ?: 0) + 1
        }
        val id = NotebookStore.idFor(meta.title, notes.mapTo(HashSet()) { it.id })
        val document = NoteDocument(id, meta, body)
        notes += document
        generation++
        NotebookStore.markDirty(id)
        return document
    }

    /** A copy of [source], titled "… (copy)" and filed alongside it. */
    fun duplicate(source: NoteDocument): NoteDocument {
        val now = System.currentTimeMillis()
        val meta = source.meta.copy().apply {
            title = "${source.meta.title} $COPY_SUFFIX"
            createdAt = now
            modifiedAt = now
            // A copy is never the pinned note and never inherits the original's manual position.
            hud = null
            sortIndex = (notes.maxOfOrNull { it.meta.sortIndex } ?: 0) + 1
        }
        val id = NotebookStore.idFor(meta.title, notes.mapTo(HashSet()) { it.id })
        val document = NoteDocument(id, meta, source.source)
        notes.add(notes.indexOf(source) + 1, document)
        generation++
        NotebookStore.markDirty(id)
        return document
    }

    fun delete(document: NoteDocument) {
        if (!notes.remove(document)) return
        generation++
        NotebookStore.delete(document.id)
    }

    /** Renames [document]. The id and therefore the file name deliberately stay as they were. */
    fun rename(document: NoteDocument, title: String) {
        val wanted = title.trim().ifEmpty { UNTITLED }
        if (wanted == document.meta.title) return
        document.meta.title = wanted
        document.touch()
        NotebookStore.markDirty(document.id)
    }

    /** Records that a note's metadata changed, and schedules the debounced write. */
    fun markDirty(document: NoteDocument) {
        if (document.readOnly) return
        document.touch()
        NotebookStore.markDirty(document.id)
    }

    /**
     * Replaces a note's text, if it actually changed.
     *
     * Separate from [markDirty] because an editor's value listener fires for every keystroke *and* once when
     * the box is first filled on open; comparing here means opening a note to read it does not stamp it as
     * modified and shuffle it to the top of the list.
     */
    fun setSource(document: NoteDocument, text: String) {
        if (document.readOnly || text == document.source) return
        document.setSource(text)
        NotebookStore.markDirty(document.id)
    }

    /** Writes [document] immediately — the definite save point an editor screen closes on. */
    fun saveNow(document: NoteDocument) {
        NotebookStore.saveNow(document)
    }

    /**
     * Makes [document] the HUD note, clearing whichever note held that before.
     *
     * Exactly one note at a time, because the panel shows one note and a second flag would mean the HUD
     * silently picking a winner. Passing null just clears.
     */
    fun pinToHud(document: NoteDocument?) {
        notes.forEach { note ->
            val wanted = note === document
            if (note.meta.showsInHud != wanted) {
                note.meta.hud = if (wanted) true else null
                NotebookStore.markDirty(note.id)
            }
        }
        generation++
    }

    /** Toggles the browser's own pin (which floats a note to the top of the list), not the HUD pin. */
    fun togglePinned(document: NoteDocument) {
        document.meta.pinned = !document.meta.pinned
        document.touch()
        NotebookStore.markDirty(document.id)
        generation++
    }

    /** Adds [document] to the notebook — used by import, which builds the document itself. */
    fun adopt(document: NoteDocument) {
        notes += document
        generation++
        NotebookStore.markDirty(document.id)
    }

    /** A free id, for import to claim before it builds the document. */
    fun freeId(title: String): String = NotebookStore.idFor(title, notes.mapTo(HashSet()) { it.id })

    // ---- ordering --------------------------------------------------------------------------------------

    /**
     * [all] in display order: pinned notes first, then whatever [sort] asks for.
     *
     * Pinned always wins over the sort mode. A player pins a note precisely so it stops moving around, and a
     * sort that could bury it again would defeat the point of the pin.
     */
    fun sorted(sort: NoteSort): List<NoteDocument> {
        val comparator = when (sort) {
            NoteSort.MODIFIED -> compareByDescending<NoteDocument> { it.meta.modifiedAt }
            NoteSort.CREATED -> compareByDescending { it.meta.createdAt }
            NoteSort.TITLE -> compareBy { it.meta.title.lowercase(Locale.ROOT) }
            NoteSort.MANUAL -> compareBy { it.meta.sortIndex }
        }
        return notes.sortedWith(compareByDescending<NoteDocument> { it.meta.pinned }.then(comparator))
    }

    /**
     * The title a note gets when it has none. Not translated: it becomes the note's own name, which the
     * player then edits, and a name that changed language when they changed their game language would be a
     * stranger outcome than an English word they immediately overwrite.
     */
    internal const val UNTITLED: String = "Untitled"

    /** Appended to a duplicated note's title. Untranslated for the same reason as [UNTITLED]. */
    private const val COPY_SUFFIX = "(copy)"
}
