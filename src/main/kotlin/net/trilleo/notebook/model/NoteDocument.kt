package net.trilleo.notebook.model

/**
 * One note in memory: its [meta], its markdown [source], and a [generation] that changes whenever either does.
 *
 * **The source text is canonical.** The block tree the visual editor works on is a projection of this string,
 * rebuilt when a note is opened and written back when it is saved, never the other way round. That is what
 * makes the round trip total: a construct the parser cannot model still survives, because the text it came
 * from is what is on disk. It also keeps a note hand-editable, keeps search a substring scan, and keeps GSON
 * out of the business of serialising a polymorphic tree.
 *
 * [generation] is the invalidation token every cache in the notebook hangs off — the list row's snippet, the
 * HUD's prepared rows, the layout cache. It follows the idiom [net.trilleo.itemcustom.ItemCustomizeConfig]
 * already uses: bump one integer on any mutation and let readers compare stamps.
 */
class NoteDocument(
    /** The file base name. Stable for the life of the note; renaming the title never changes it. */
    val id: String,
    val meta: NoteMeta,
    /** The markdown **body** — the front-matter header has already been stripped by the store. */
    source: String,
) {
    var source: String = source
        private set

    var generation: Int = 0
        private set

    /**
     * Set while the note came from a build newer than this one. Such a note is shown but never written back,
     * so an accidental launch of an older Hex cannot quietly downgrade it.
     */
    val readOnly: Boolean get() = meta.readOnly

    /** Replaces the text, if it actually changed, and stamps [meta].modifiedAt. */
    fun setSource(text: String) {
        if (text == source) return
        source = text
        touch()
    }

    /** Notes that something about this document changed, so anything caching it re-reads. */
    fun touch() {
        generation++
        meta.modifiedAt = System.currentTimeMillis()
    }

    /**
     * The first non-blank line of the body with its markdown stripped, for the list row's second line.
     *
     * Deliberately crude — it runs the block parser over nothing and only ever looks at the head of the file,
     * because it is called for every visible row of a list that may hold hundreds of notes.
     */
    fun snippet(limit: Int): String {
        val line = source.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return ""
        val plain = line.trimStart('#', '>', '-', '*', ' ', '\t')
        return if (plain.length <= limit) plain else plain.take(limit).trimEnd() + "…"
    }
}
