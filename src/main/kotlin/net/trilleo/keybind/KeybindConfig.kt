package net.trilleo.keybind

import com.google.gson.reflect.TypeToken
import net.trilleo.config.JsonConfig

/**
 * Loads and saves the list of [Keybind]s at `config/hex/keybinds.json`, built on the shared [JsonConfig]
 * helper. Holds the live, mutable list that the GUI edits in place and the manager reads each tick.
 *
 * The [JsonConfig.normalize] hook repairs GSON's reflection gaps: because GSON ignores Kotlin constructor
 * defaults, a keybind whose `commands` / `label` field is absent from the JSON deserializes to `null`.
 */
object KeybindConfig {
	private val file = JsonConfig(
		name = "keybinds",
		type = object : TypeToken<MutableList<Keybind>>() {}.type,
		default = { mutableListOf<Keybind>() },
		normalize = { list ->
			list.forEach { kb ->
				@Suppress("SENSELESS_COMPARISON")
				if (kb.commands == null) kb.commands = mutableListOf()
				@Suppress("SENSELESS_COMPARISON")
				if (kb.label == null) kb.label = ""
			}
		},
	)

	/** The live, mutable list of bindings. Edited in place by the GUI; read each tick by the manager. */
	val keybinds: MutableList<Keybind> = mutableListOf()

	fun load() {
		keybinds.clear()
		keybinds.addAll(file.load())
	}

	fun save() {
		file.save(keybinds)
	}
}
