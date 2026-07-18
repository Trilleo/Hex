package net.trilleo.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.lang.reflect.Type
import java.nio.file.Files
import java.nio.file.Path

/**
 * A single JSON config file at `config/hex/<name>.json`, round-tripped with GSON.
 *
 * This is the reusable core of every feature's persistence: construct one with the target type and a
 * [default] factory, then call [load] / [save]. Errors never propagate — a failed load degrades to
 * [default] and a failed save is logged and swallowed, so a corrupt file can never crash the client.
 *
 * GSON instantiates objects via reflection and ignores Kotlin constructor defaults, so any field absent
 * from the JSON is left at its JVM default (e.g. a missing list becomes `null`). Pass a [normalize] hook
 * to repair those cases on the freshly loaded value.
 *
 * @param name file base name (without extension); the file is `config/hex/<name>.json`.
 * @param type the fully-reified type, e.g. `object : TypeToken<MutableList<Foo>>() {}.type`.
 * @param default produces a fresh value when the file is missing or unreadable.
 * @param normalize repairs GSON's reflection gaps on a loaded value (no-op by default).
 */
class JsonConfig<T : Any>(
    name: String,
    private val type: Type,
    private val default: () -> T,
    private val normalize: (T) -> Unit = {},
) {
    private val path: Path = FabricLoader.getInstance().configDir.resolve("hex").resolve("$name.json")

    /** Reads the file, or returns a normalized [default] when it is missing or fails to parse. */
    fun load(): T {
        if (!Files.exists(path)) return default()
        return try {
            Files.newBufferedReader(path).use { reader ->
                val loaded: T? = GSON.fromJson(reader, type)
                (loaded ?: default()).also(normalize)
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to load config from {}, using defaults", path, e)
            default()
        }
    }

    /** Writes [value] as pretty JSON, creating `config/hex/` if needed. Failures are logged, not thrown. */
    fun save(value: T) {
        try {
            Files.createDirectories(path.parent)
            Files.newBufferedWriter(path).use { writer -> GSON.toJson(value, writer) }
        } catch (e: Exception) {
            LOGGER.error("Failed to save config to {}", path, e)
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("hex/config")

        /** Shared, pretty-printing GSON instance for all config files. */
        val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    }
}
