package net.trilleo.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.lang.reflect.Type
import java.nio.file.Files
import java.nio.file.Path

/**
 * A single JSON config file named `<name>.json`, round-tripped with GSON. It normally lives in
 * [defaultDir] (`config/hex/`), but [loadFrom] / [saveTo] can point the same schema at any other
 * directory — that is how [ConfigProfiles] snapshots a whole config set without each feature knowing.
 *
 * This is the reusable core of every feature's persistence: construct one with the target type and a
 * [default] factory, then call [load] / [save]. Errors never propagate — a failed load degrades to
 * [default] and a failed save is logged and swallowed, so a corrupt file can never crash the client.
 * A file that could not be read is moved aside rather than left to be overwritten; see [setAside], which is
 * what keeps "degrades to defaults" from meaning "throws your settings away".
 *
 * GSON instantiates objects via reflection and ignores Kotlin constructor defaults, so any field absent
 * from the JSON is left at its JVM default (e.g. a missing list becomes `null`). Pass a [normalizer] hook
 * to repair those cases on the freshly loaded value.
 *
 * @param name file base name (without extension); also the key this config is addressed by in
 *   [ConfigRegistry] and in an exported profile blob, so it must be stable across versions.
 * @param type the fully-reified type, e.g. `object : TypeToken<MutableList<Foo>>() {}.type`.
 * @param default produces a fresh value when the file is missing or unreadable.
 * @param normalizer repairs GSON's reflection gaps on a loaded value (no-op by default).
 */
class JsonConfig<T : Any>(
    val name: String,
    private val type: Type,
    private val default: () -> T,
    private val normalizer: (T) -> Unit = {},
) {
    /** The live config directory, `config/hex/`. Profiles live in subdirectories of this. */
    fun defaultDir(): Path = FabricLoader.getInstance().configDir.resolve("hex")

    /** This config's file inside [dir]. */
    fun fileIn(dir: Path): Path = dir.resolve("$name.json")

    /** Reads the live file, or returns a [default] when it is missing or fails to parse. */
    fun load(): T = loadFrom(defaultDir())

    /** Writes [value] to the live file. */
    fun save(value: T) = saveTo(defaultDir(), value)

    /**
     * Reads `<dir>/<name>.json`, or returns a normalized [default] when it is missing or fails to parse.
     * Used directly by the profile system to read a snapshot outside the live directory.
     */
    fun loadFrom(dir: Path): T {
        val path = fileIn(dir)
        if (!Files.exists(path)) return default()
        return try {
            Files.newBufferedReader(path).use { reader ->
                val loaded: T? = GSON.fromJson(reader, type)
                (loaded ?: default()).also(normalizer)
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to load config from {}; setting it aside and starting from defaults", path, e)
            setAside(path)
            default()
        }
    }

    /**
     * Renames an unreadable file out of the way, so the defaults that replace it cannot destroy it.
     *
     * **A failed load is not what loses data — the next save is.** Falling back to defaults puts an empty
     * value into live state, and the first thing that marks the config dirty then writes that emptiness over
     * the file. By the time anyone reads the error in the log, the thing the error was about is gone. Moving
     * the file first means a config broken by a bad hand-edit, a half-written file from a crash, or a field
     * this build reads differently than the one that wrote it costs a session's settings rather than the data.
     *
     * **An existing backup is never overwritten.** If one is already there, this file is one *we* wrote after
     * the first failure, so it holds no data the backup does not — and the backup is the copy worth keeping.
     *
     * Never throws: this runs inside the handler for a load that already failed, and a config that cannot be
     * set aside must still fall back to defaults rather than take the client down with it.
     */
    private fun setAside(path: Path) {
        runCatching {
            val backup = path.resolveSibling("${path.fileName}$BROKEN_SUFFIX")
            if (Files.exists(backup)) {
                LOGGER.warn("Keeping the existing {} rather than replacing it", backup)
                Files.deleteIfExists(path)
            } else {
                Files.move(path, backup)
                LOGGER.warn("The unreadable file is kept at {}", backup)
            }
        }.onFailure { LOGGER.error("Could not set aside {}", path, it) }
    }

    /** Writes [value] as pretty JSON to `<dir>/<name>.json`, creating [dir] if needed. */
    fun saveTo(dir: Path, value: T) {
        val path = fileIn(dir)
        try {
            Files.createDirectories(path.parent)
            Files.newBufferedWriter(path).use { writer -> GSON.toJson(value, writer) }
        } catch (e: Exception) {
            LOGGER.error("Failed to save config to {}", path, e)
        }
    }

    /**
     * Runs the [normalizer] on a value this config did not itself read — an imported clipboard blob, say,
     * which arrives through [GSON] rather than through [loadFrom] and so has skipped the usual repair.
     */
    fun normalize(value: T) = normalizer(value)

    /** The reified type, for deserializing this config out of a combined blob. */
    fun typeToken(): Type = type

    /** A fresh default value, for a "reset everything" path that must not read the file. */
    fun defaultValue(): T = default().also(normalizer)

    companion object {
        private val LOGGER = LoggerFactory.getLogger("hex/config")

        /** Appended to a file that could not be read, so the copy is obvious next to the live one. */
        const val BROKEN_SUFFIX: String = ".broken"

        /**
         * Shared, pretty-printing GSON instance for all config files.
         *
         * HTML escaping is off because these files are meant to be read and edited by hand, and it is on by
         * default — which turns `&` into `&`. That was survivable while an ampersand was rare; a
         * [net.trilleo.title.model.TitleSpec] line is mostly `&` codes, so `"&c&lBOSS"` would be the
         * ordinary content of the field a player is most likely to open the file for. Nothing here is ever
         * embedded in a web page, which is the only thing the escaping protects. Files already written with
         * the escapes still parse — it is valid JSON either way — and come out plain on the next save.
         */
        val GSON: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    }
}
