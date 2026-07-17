package net.trilleo.keybind

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads and saves the list of [Keybind]s as pretty JSON at `config/hex/keybinds.json`.
 *
 * GSON instantiates objects via reflection and ignores Kotlin constructor defaults, so any field
 * absent from the JSON is left at its JVM default (e.g. a missing `commands` becomes `null`). The
 * load path normalizes those cases.
 */
object KeybindConfig {
	private val LOGGER = LoggerFactory.getLogger("hex/keybinds")
	private val GSON = GsonBuilder().setPrettyPrinting().create()
	private val LIST_TYPE = object : TypeToken<MutableList<Keybind>>() {}.type

	private val path: Path = FabricLoader.getInstance().configDir.resolve("hex").resolve("keybinds.json")

	/** The live, mutable list of bindings. Edited in place by the GUI; read each tick by the manager. */
	val keybinds: MutableList<Keybind> = mutableListOf()

	fun load() {
		keybinds.clear()
		if (!Files.exists(path)) return
		try {
			Files.newBufferedReader(path).use { reader ->
				val loaded: MutableList<Keybind>? = GSON.fromJson(reader, LIST_TYPE)
				loaded?.forEach { kb ->
					@Suppress("SENSELESS_COMPARISON")
					if (kb.commands == null) kb.commands = mutableListOf()
					@Suppress("SENSELESS_COMPARISON")
					if (kb.label == null) kb.label = ""
					keybinds.add(kb)
				}
			}
		} catch (e: Exception) {
			LOGGER.error("Failed to load keybinds from {}, starting with none", path, e)
		}
	}

	fun save() {
		try {
			Files.createDirectories(path.parent)
			Files.newBufferedWriter(path).use { writer -> GSON.toJson(keybinds, writer) }
		} catch (e: Exception) {
			LOGGER.error("Failed to save keybinds to {}", path, e)
		}
	}
}
