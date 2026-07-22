package net.trilleo.update

import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import net.trilleo.config.JsonConfig
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Records a downloaded-but-not-yet-applied update and applies it on shutdown.
 *
 * The running mod jar is locked by the JVM (on Windows it cannot even be deleted while the game runs), so
 * the swap cannot happen from inside this process. Instead, when an update is [markPending], the verified
 * new jar sits in `config/hex/update/` and, at shutdown, [applyOnShutdown] launches a small detached OS
 * script that outlives the JVM: it copies the new jar into `mods/`, then retries deleting the old jar
 * until the JVM releases its lock, then removes itself. Copy-before-delete means a failure can never leave
 * the player with no jar at all.
 */
data class PendingUpdate(
    var version: String = "",
    var stagedJar: String = "",
    var oldJar: String = "",
)

object UpdateStaging {
    private val LOGGER = LoggerFactory.getLogger("hex/update")

    private val store = JsonConfig(
        name = "update/pending",
        type = object : TypeToken<PendingUpdate>() {}.type,
        default = { PendingUpdate() },
    )

    private fun pendingPath(): Path =
        UpdateDownloader.stagingDir().resolve("pending.json")

    /**
     * The jar the running mod was loaded from, or `null` when Hex is not running from a real `mods/` jar
     * (e.g. a dev `runClient` launch from classes). Callers use `null` to fall back to notify-only.
     */
    fun currentJar(): Path? {
        val container = FabricLoader.getInstance().getModContainer("hex").orElse(null) ?: return null
        val jar = try {
            container.origin.paths.firstOrNull { it.fileName.toString().endsWith(".jar") }
        } catch (_: UnsupportedOperationException) {
            null // non-PATH origin (nested/dev) — no swappable jar
        } ?: return null
        return if (Files.exists(jar)) jar else null
    }

    /** The pending update, if one is recorded and its staged jar still exists. */
    fun pending(): PendingUpdate? {
        if (!Files.exists(pendingPath())) return null
        val loaded = store.load()
        if (loaded.stagedJar.isEmpty() || !Files.exists(Path.of(loaded.stagedJar))) return null
        return loaded
    }

    /** True when an update for exactly [version] is already staged and waiting to be applied. */
    fun hasPendingFor(version: String): Boolean = pending()?.version == version

    /** Record [stagedJar] (already downloaded and verified) as the replacement for [oldJar]. */
    fun markPending(version: String, stagedJar: Path, oldJar: Path) {
        store.save(PendingUpdate(version, stagedJar.toString(), oldJar.toString()))
    }

    private fun clearPending() {
        runCatching { Files.deleteIfExists(pendingPath()) }
    }

    /**
     * If an update is pending, launch the detached swap helper. Called from the feature's shutdown hook.
     * Returns `false` (leaving the pending record intact) if the helper could not be launched, so the
     * player can still drop the staged jar in manually.
     */
    fun applyOnShutdown(): Boolean {
        val pending = pending() ?: return false
        val staged = Path.of(pending.stagedJar)
        val old = Path.of(pending.oldJar)
        val modsDir = old.parent ?: return false
        val newTarget = modsDir.resolve(staged.fileName.toString())

        return try {
            val windows = System.getProperty("os.name").lowercase().contains("win")
            if (windows) spawnWindows(staged, old, newTarget) else spawnUnix(staged, old, newTarget)
            clearPending()
            LOGGER.info("Launched updater to apply Hex v{} on exit", pending.version)
            true
        } catch (e: Exception) {
            LOGGER.error("Failed to launch updater; staged jar left at {}", staged, e)
            false
        }
    }

    /** Only delete the old jar when it is genuinely a different file from the new one. */
    private fun shouldDeleteOld(old: Path, newTarget: Path): Boolean =
        old.fileName.toString() != newTarget.fileName.toString()

    private fun spawnWindows(staged: Path, old: Path, newTarget: Path) {
        val script = UpdateDownloader.stagingDir().resolve("hex-apply-update.bat")
        // Built line-by-line (not a trimIndent template) so no stray leading whitespace reaches a `:label`.
        val lines = buildList {
            add("@echo off")
            add("copy /y \"$staged\" \"$newTarget\" >nul")
            add("if not exist \"$newTarget\" exit /b 1")
            if (shouldDeleteOld(old, newTarget)) {
                add(":delloop")
                add("del /f /q \"$old\" >nul 2>&1")
                add("if exist \"$old\" ( ping -n 2 127.0.0.1 >nul & goto delloop )")
            }
            add("del /f /q \"$staged\" >nul 2>&1")
            // cmd re-reads the batch file after every command, so a plain `del "%~f0"` makes the next read
            // fail with "The batch file cannot be found" in a visible console. `(goto) 2>nul` tears down the
            // batch context first, so the delete on the same line is the last thing cmd ever reads.
            add("(goto) 2>nul & del /f /q \"%~f0\"")
        }
        Files.writeString(script, lines.joinToString("\r\n"))
        // `start ""` detaches the script into its own process that survives this JVM exiting; `/b` keeps it
        // from allocating a console window (ProcessBuilder already starts `cmd` itself with CREATE_NO_WINDOW).
        ProcessBuilder("cmd", "/c", "start", "", "/b", script.toString()).start()
    }

    private fun spawnUnix(staged: Path, old: Path, newTarget: Path) {
        val script = UpdateDownloader.stagingDir().resolve("hex-apply-update.sh")
        val lines = buildList {
            add("#!/bin/sh")
            add("cp -f \"$staged\" \"$newTarget\" || exit 1")
            if (shouldDeleteOld(old, newTarget)) {
                add("while [ -e \"$old\" ]; do")
                add("  rm -f \"$old\"")
                add("  [ -e \"$old\" ] && sleep 1")
                add("done")
            }
            add("rm -f \"$staged\"")
            add("rm -f \"\$0\"")
        }
        Files.writeString(script, lines.joinToString("\n"))
        // nohup + background so the swap continues after the JVM (and its process group) exits.
        ProcessBuilder("/bin/sh", "-c", "nohup /bin/sh \"$script\" >/dev/null 2>&1 &").start()
    }
}
