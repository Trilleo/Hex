package net.trilleo.keybind

import com.google.gson.reflect.TypeToken
import net.trilleo.config.ConfigHandle
import net.trilleo.config.ConfigRegistry
import net.trilleo.config.JsonConfig

/**
 * Loads and saves the list of [Keybind]s at `config/hex/keybinds.json`, built on the shared [JsonConfig]
 * helper. Holds the live, mutable list that the GUI edits in place and the manager reads each tick.
 *
 * The [JsonConfig.normalize] hook repairs GSON's reflection gaps: because GSON ignores Kotlin constructor
 * defaults, a keybind whose `actions` / `commands` / `label` field is absent from the JSON deserializes to
 * `null`. It also migrates pre-per-action configs (a `commands` list plus a single `delayTicks`) into the
 * current per-action [Keybind.actions] shape, preserving the old timing.
 */
object KeybindConfig {
    private val file = JsonConfig(
        name = "keybinds",
        type = object : TypeToken<MutableList<Keybind>>() {}.type,
        default = { mutableListOf<Keybind>() },
        normalizer = { list ->
            list.forEach { kb ->
                @Suppress("SENSELESS_COMPARISON")
                if (kb.commands == null) kb.commands = mutableListOf()
                @Suppress("SENSELESS_COMPARISON")
                if (kb.label == null) kb.label = ""
                @Suppress("SENSELESS_COMPARISON")
                if (kb.actions == null) kb.actions = mutableListOf()

                // Pre-switch configs have no `type` at all, and GSON maps an unknown enum name to null too,
                // so both cases fall back to the original command behaviour.
                @Suppress("SENSELESS_COMPARISON")
                if (kb.type == null) kb.type = KeybindType.COMMAND
                @Suppress("SENSELESS_COMPARISON")
                if (kb.switchTarget == null) kb.switchTarget = ""
                @Suppress("SENSELESS_COMPARISON")
                if (kb.switchKeys == null) kb.switchKeys = mutableListOf()

                // Migrate legacy commands+delay into per-action steps (once). Old timing ran action i at
                // i * delayTicks, so the first step waits 0 and every later step waits the old delay.
                if (kb.actions.isEmpty() && kb.commands.isNotEmpty()) {
                    kb.commands.forEachIndexed { i, line ->
                        kb.actions.add(KeybindAction().apply {
                            command = line
                            delayTicks = if (i == 0) 0 else kb.delayTicks.coerceAtLeast(0)
                        })
                    }
                }
                // Retire legacy fields so the next save writes only the actions shape and migration never re-fires.
                kb.commands = mutableListOf()
                kb.delayTicks = 0
            }
        },
    )

    /** The live, mutable list of bindings. Edited in place by the GUI; read each tick by the manager. */
    val keybinds: MutableList<Keybind> = mutableListOf()

    // Unlike the other configs, this one is refilled in place rather than swapped: the GUI and
    // KeybindManager both hold [keybinds] by reference, so rebinding it would leave them editing a list
    // nothing else reads.
    private val handle = ConfigRegistry.register(
        ConfigHandle(
            file,
            adopt = { loaded ->
                keybinds.clear()
                keybinds.addAll(loaded)
            },
            current = { keybinds },
        ),
    )

    fun load() = handle.loadInitial()

    /** Writes immediately. Prefer [markDirty] from anything that fires repeatedly. */
    fun save() = handle.saveNow()

    /** Records that the bindings changed; the write is batched and lands about a second later. */
    fun markDirty() = handle.markDirty()
}
