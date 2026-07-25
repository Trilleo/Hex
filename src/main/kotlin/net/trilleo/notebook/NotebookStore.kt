package net.trilleo.notebook

import net.fabricmc.loader.api.FabricLoader
import net.trilleo.config.JsonConfig
import net.trilleo.notebook.NotebookStore.CLEAN
import net.trilleo.notebook.NotebookStore.DEBOUNCE_TICKS
import net.trilleo.notebook.md.NoteFrontMatter
import net.trilleo.notebook.model.NoteDocument
import net.trilleo.notebook.model.NoteMeta
import net.trilleo.util.AtomicWrite
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.name

/** What `index.json` holds: a cache of every note's metadata, so listing does not mean opening every file. */
class NotebookIndex {
    var version: Int = NotebookStore.INDEX_VERSION
    var notes: MutableMap<String, NoteMeta> = linkedMapOf()
}

/**
 * Where notes live, and the only thing that reads or writes them.
 *
 * ```
 * config/hex/notebook/
 *   index.json          a rebuildable cache of every note's metadata
 *   notes/<id>.md       one note, with its metadata in a front-matter header
 * ```
 *
 * **One file per note.** A note body is prose: putting it in JSON turns every newline into `\n` and destroys
 * the hand-editability this codebase repeatedly pays for. One file also means saving one note rewrites one
 * small file, a single unreadable file costs one note rather than the notebook, and a `.md` dropped into
 * `notes/` is imported simply by being there — [load] adopts anything it finds that the index does not know
 * about, which is a whole import path for free.
 *
 * **The file is the truth; the index is a cache.** Every note file carries its own metadata
 * ([NoteFrontMatter]), so when the two disagree the file wins and a missing, unparseable or too-new index
 * costs a directory scan rather than a single note.
 *
 * **Deliberately not registered with [net.trilleo.config.ConfigRegistry].** That registry's own documentation
 * draws the line — only *settings* belong in it, because everything in it is captured into config profiles and
 * pasted into the clipboard blob. Notes are documents, not a loadout: switching from a "Dwarven" profile to a
 * "Slayers" one must not swap the player's notes out from under them.
 *
 * **Writes are debounced, off-thread and atomic**, following [net.trilleo.suggest.model.ModelStore]. Debounced
 * because typing is continuous; off-thread because it is file IO; atomic because the one thing a notebook must
 * never do is lose text the player has nowhere else.
 */
object NotebookStore {
    private val LOGGER = LoggerFactory.getLogger("hex/notebook")

    /**
     * One daemon thread, so a save in flight can never hold the game open at exit, and two saves can never
     * interleave into the same temporary file.
     */
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "hex-notebook-save").apply { isDaemon = true }
    }

    /** Note ids with unsaved changes, and whether the index itself needs rewriting. */
    private val dirtyNotes = linkedSetOf<String>()

    @Volatile
    private var indexDirty = false

    /** Ticks until the pending write lands, or [CLEAN] when there is nothing to write. */
    @Volatile
    private var ticksUntilSave: Int = CLEAN

    /** Set when `index.json` is known to be from a newer Hex, so a save does not overwrite a good file. */
    @Volatile
    private var refusedIndex: Boolean = false

    private fun root(): Path = FabricLoader.getInstance().configDir.resolve("hex").resolve("notebook")
    private fun notesDir(): Path = root().resolve("notes")
    private fun indexFile(): Path = root().resolve("index.json")

    internal fun fileFor(id: String): Path = notesDir().resolve("$id.md")

    // ---- loading ---------------------------------------------------------------------------------------

    /**
     * Reads every note from disk, in the order the index remembers, with anything the index does not know
     * about appended.
     *
     * An index this build cannot read is *left alone* rather than overwritten, exactly as
     * [net.trilleo.suggest.model.ModelStore.load] leaves a too-new model alone: the player may simply have
     * launched an older Hex by accident, and the notes themselves are self-describing, so the cost of refusing
     * it is a scan rather than any lost text.
     */
    fun load(): MutableList<NoteDocument> {
        val index = readIndex()
        val documents = mutableListOf<NoteDocument>()
        val seen = HashSet<String>()

        for ((id, cached) in index.notes) {
            val document = readNote(id, cached) ?: continue
            documents += document
            seen += id
        }

        // Anything on disk the index has not heard of — a note written by another installation, restored from
        // a backup, or simply dropped in by hand. Adopting it is what makes copying a file an import.
        for (id in scanIds()) {
            if (!seen.add(id)) continue
            val document = readNote(id, null) ?: continue
            documents += document
            indexDirty = true
        }

        // An index entry whose file has gone is stale bookkeeping, not a lost note.
        if (index.notes.keys.any { it !in seen }) indexDirty = true

        return documents
    }

    private fun readIndex(): NotebookIndex {
        val path = indexFile()
        if (!Files.exists(path)) return NotebookIndex()
        val loaded = runCatching {
            Files.newBufferedReader(path).use { JsonConfig.GSON.fromJson(it, NotebookIndex::class.java) }
        }.getOrElse { e ->
            LOGGER.error("Failed to read the notebook index at {} — rebuilding it from the note files", path, e)
            null
        } ?: return NotebookIndex().also { indexDirty = true }

        @Suppress("SENSELESS_COMPARISON")
        if (loaded.notes == null) loaded.notes = linkedMapOf()

        if (loaded.version > INDEX_VERSION) {
            refusedIndex = true
            LOGGER.warn(
                "Notebook index is version {} but this Hex reads at most {} — reading the note files directly and leaving the index alone",
                loaded.version, INDEX_VERSION,
            )
            return NotebookIndex()
        }
        return loaded
    }

    /** Every `<id>.md` in the notes directory, sorted so an adopted batch lands in a predictable order. */
    private fun scanIds(): List<String> {
        val dir = notesDir()
        if (!Files.isDirectory(dir)) return emptyList()
        return runCatching {
            Files.list(dir).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.name.endsWith(EXTENSION) }
                    .map { it.name.removeSuffix(EXTENSION) }
                    .sorted()
                    .toList()
            }
        }.getOrElse { e ->
            LOGGER.error("Failed to list the notes directory {}", dir, e)
            emptyList()
        }
    }

    /**
     * Reads one note file. The header on disk wins over [cached]: the file is the note, and the index is only
     * a summary of it that may have been written before a hand edit.
     */
    private fun readNote(id: String, cached: NoteMeta?): NoteDocument? {
        val path = fileFor(id)
        if (!Files.exists(path)) return null
        val text = runCatching { Files.readString(path) }.getOrElse { e ->
            LOGGER.error("Failed to read note {} — it is left on disk untouched", path, e)
            return null
        }
        val (fromFile, body) = NoteFrontMatter.read(text)
        val meta = fromFile ?: cached?.copy() ?: NoteMeta()
        meta.normalize(id)
        if (fromFile == null) indexDirty = true
        return NoteDocument(id, meta, body)
    }

    // ---- saving ----------------------------------------------------------------------------------------

    /** Note that [id] changed; the write lands once [DEBOUNCE_TICKS] ticks pass without another change. */
    fun markDirty(id: String) {
        synchronized(dirtyNotes) { dirtyNotes += id }
        indexDirty = true
        ticksUntilSave = DEBOUNCE_TICKS
    }

    /** Note that only the index changed — a reorder, or a note being deleted. */
    fun markIndexDirty() {
        indexDirty = true
        ticksUntilSave = DEBOUNCE_TICKS
    }

    /** Advances the debounce timer. Called once per client tick from the feature. */
    fun tick() {
        if (ticksUntilSave > 0 && --ticksUntilSave == 0) flush()
    }

    /** Writes anything pending now. Called on screen close, on world leave, and at shutdown. */
    fun flush() {
        if (ticksUntilSave == CLEAN && !indexDirty && dirtyNotes.isEmpty()) return
        ticksUntilSave = CLEAN
        submit()
    }

    /**
     * Writes [document] now, whether or not it was marked dirty. This is the definite save point an editor
     * screen's `removed()` uses, so closing a note is never a matter of hoping the debounce lands.
     */
    fun saveNow(document: NoteDocument) {
        if (document.readOnly) return
        markDirty(document.id)
        flush()
    }

    /** Deletes a note's file. The caller has already dropped it from the in-memory list. */
    fun delete(id: String) {
        synchronized(dirtyNotes) { dirtyNotes -= id }
        markIndexDirty()
        runCatching { Files.deleteIfExists(fileFor(id)) }
            .onFailure { LOGGER.error("Failed to delete note {}", fileFor(id), it) }
    }

    /**
     * Blocks until any in-flight write finishes, then stops the writer. Called from the feature's shutdown
     * hook, after [flush], so the last thing typed in a session survives closing the game.
     */
    fun shutdown() {
        flush()
        writer.shutdown()
        runCatching { writer.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS) }
    }

    private fun submit() {
        // Snapshot on the client thread: the writer must not walk the notebook while the GUI is editing it.
        val pending = synchronized(dirtyNotes) {
            val ids = dirtyNotes.toList()
            dirtyNotes.clear()
            ids
        }
        val snapshots = pending.mapNotNull { id -> Notebook.byId(id)?.let { it.id to snapshotOf(it) } }
        val index = if (indexDirty && !refusedIndex) snapshotIndex() else null
        indexDirty = false

        if (snapshots.isEmpty() && index == null) return

        val job = Runnable {
            snapshots.forEach { (id, text) ->
                runCatching { AtomicWrite.writeString(fileFor(id), text) }
                    .onFailure { LOGGER.error("Failed to save note {}", id, it) }
            }
            index?.let { text ->
                runCatching { AtomicWrite.writeString(indexFile(), text) }
                    .onFailure { LOGGER.error("Failed to save the notebook index", it) }
            }
        }
        runCatching { writer.execute(job) }.onFailure {
            // The executor is shut down (we are exiting). Write on this thread rather than lose the note.
            job.run()
        }
    }

    /** One note as the exact text that will be on disk. Also what "export to clipboard" hands out. */
    fun snapshotOf(document: NoteDocument): String = NoteFrontMatter.write(document.meta, document.source)

    private fun snapshotIndex(): String {
        val index = NotebookIndex()
        Notebook.all().forEach { index.notes[it.id] = it.meta.copy() }
        return JsonConfig.GSON.toJson(index)
    }

    // ---- ids -------------------------------------------------------------------------------------------

    /**
     * A filename-safe id for a note called [title], not colliding with [taken].
     *
     * Folded exactly as [net.trilleo.config.ConfigProfiles.sanitize] folds a profile name, because both end up
     * as a path segment and both have to survive a case-insensitive filesystem. The id is fixed at creation
     * and never revisited, so renaming a note is free and nothing that points at one — the HUD pin, a future
     * cross-reference — can be broken by a rename.
     */
    fun idFor(title: String, taken: Set<String>): String {
        val cleaned = title.trim().lowercase(Locale.ROOT).map { ch ->
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '_'
        }.joinToString("").trim('_').take(MAX_ID_LENGTH).trim('_')
        val base = cleaned.ifEmpty { FALLBACK_ID }
        if (base !in taken) return base
        var n = 2
        while ("$base-$n" in taken) n++
        return "$base-$n"
    }

    private const val CLEAN = -1

    /**
     * Two seconds at 20 tps. Longer than [net.trilleo.config.ConfigHandle.DEBOUNCE_TICKS] because a note is
     * far bigger than a setting and typing is continuous, but far shorter than
     * [net.trilleo.suggest.model.ModelStore]'s twenty seconds because this is text the player just wrote and
     * expects to be safe.
     */
    private const val DEBOUNCE_TICKS = 40

    private const val SHUTDOWN_WAIT_SECONDS = 3L

    private const val EXTENSION = ".md"
    private const val MAX_ID_LENGTH = 48
    private const val FALLBACK_ID = "note"

    /** Bumped only when a change would make an older Hex mis-read the index, not merely ignore a new field. */
    const val INDEX_VERSION: Int = 1
}
