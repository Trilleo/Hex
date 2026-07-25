package net.trilleo.notebook.md

import net.trilleo.notebook.model.NoteDocument
import java.util.Locale

/**
 * Finding a note by typing part of it.
 *
 * Search is a plain case-insensitive substring scan over the title, folder, tags and body. Nothing is
 * indexed: a notebook is tens of notes of a few kilobytes each, so a scan is microseconds, and an index would
 * be a second copy of the truth to keep in step for no measurable gain.
 *
 * The one piece of structure is the [Field] ranking, so that typing `mining` puts the note *called* "Mining"
 * above the one that merely mentions mining in a sentence.
 */
object NoteSearch {

    /** Where a query matched, best first. The ordinal is the rank. */
    enum class Field { TITLE, TAG, FOLDER, BODY }

    class Hit(val document: NoteDocument, val field: Field, val snippet: String)

    /** Whether [document] matches [query] at all. Used to filter without building snippets. */
    fun matches(document: NoteDocument, query: String): Boolean = fieldOf(document, folded(query)) != null

    /**
     * Every note matching [query], best match first, ties broken by most recently edited.
     *
     * A blank query matches nothing rather than everything: the caller showing an unfiltered list already has
     * [net.trilleo.notebook.Notebook.sorted] for that, and returning everything here would make an empty
     * search box look like a search that found the whole notebook.
     */
    fun search(documents: List<NoteDocument>, query: String): List<Hit> {
        val folded = folded(query)
        if (folded.isEmpty()) return emptyList()
        return documents
            .mapNotNull { document ->
                val field = fieldOf(document, folded) ?: return@mapNotNull null
                Hit(document, field, snippetFor(document, folded, field))
            }
            .sortedWith(compareBy<Hit> { it.field.ordinal }.thenByDescending { it.document.meta.modifiedAt })
    }

    private fun folded(query: String): String = query.trim().lowercase(Locale.ROOT)

    /** The best field [folded] matches in, or null for no match. Ordered so the strongest signal wins. */
    private fun fieldOf(document: NoteDocument, folded: String): Field? {
        if (folded.isEmpty()) return null
        val meta = document.meta
        if (meta.title.lowercase(Locale.ROOT).contains(folded)) return Field.TITLE
        if (meta.tags.any { it.contains(folded) }) return Field.TAG
        if (meta.folder.contains(folded)) return Field.FOLDER
        if (document.source.lowercase(Locale.ROOT).contains(folded)) return Field.BODY
        return null
    }

    /**
     * A line of context to show under the row: for a body hit, the matching line with the match in it; for
     * anything else, the note's ordinary snippet, since the match is already visible in the row itself.
     */
    private fun snippetFor(document: NoteDocument, folded: String, field: Field): String {
        if (field != Field.BODY) return document.snippet(SNIPPET_LENGTH)
        val line = document.source.lineSequence()
            .firstOrNull { it.lowercase(Locale.ROOT).contains(folded) }
            ?.trim()
            ?: return document.snippet(SNIPPET_LENGTH)
        return if (line.length <= SNIPPET_LENGTH) line else line.take(SNIPPET_LENGTH).trimEnd() + "…"
    }

    private const val SNIPPET_LENGTH = 80
}
