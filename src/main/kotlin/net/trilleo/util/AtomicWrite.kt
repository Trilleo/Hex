package net.trilleo.util

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Writing a file so that a crash mid-write cannot leave a half-written one behind.
 *
 * The text goes to a sibling `.tmp` and is then moved over the target, so the file on disk is only ever
 * complete — the previous version or the new one, never a truncation. That matters for anything whose loss
 * would be unrecoverable: [net.trilleo.suggest.model.ModelStore] uses it for months of learned commands, and
 * [net.trilleo.notebook.NotebookStore] for text the player typed by hand and has nowhere else.
 *
 * Nothing here is caught. Both callers already wrap their write in a `runCatching` that logs, and swallowing
 * a failure here would tell them a save succeeded when it did not.
 */
object AtomicWrite {

    /** Creates [target]'s directory, writes [text] to a temporary sibling, then moves it over [target]. */
    fun writeString(target: Path, text: String) {
        Files.createDirectories(target.parent)
        val temp = target.resolveSibling(target.fileName.toString() + ".tmp")
        Files.writeString(temp, text)
        moveOver(temp, target)
    }

    /**
     * Replaces [target] with [temp], atomically where the filesystem allows it.
     *
     * `ATOMIC_MOVE` is supported on both NTFS and every mainstream Unix filesystem, but not on all of them —
     * some network shares refuse it — so a failure falls back to a plain replace. That gives up the guarantee
     * rather than giving up the save, which is the right way round: a non-atomic write is a small risk taken
     * rarely, and refusing to write at all is a certainty of losing the file's contents.
     */
    fun moveOver(temp: Path, target: Path) {
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
