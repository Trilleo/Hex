package net.trilleo.config

import com.google.gson.reflect.TypeToken
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.Options
import org.slf4j.LoggerFactory

/**
 * Minecraft's own key bindings, captured into a profile so a profile is a whole setup rather than only the
 * Hex half of one.
 *
 * Stored as `KeyMapping.name` → `KeyMapping.saveString()`, the same vocabulary Minecraft writes to
 * `options.txt`, so a binding survives a restart and an unrecognised entry can simply be skipped.
 *
 * **Off by default, and deliberately so.** `Options.keyMappings` holds every registered binding, including
 * other mods'. That means a profile saved while another mod was bound to `G` will put it back on `G` when the
 * profile is restored, even if the player has since rebound it in that mod's own screen. There is no way
 * around that with a whole-options snapshot, so the feature is opt-in via
 * [ProfileSettings.captureVanillaKeys] and every binding it actually changes is logged.
 *
 * Reading the live options in `current` rather than holding a copy is what makes this cheap: snapshots,
 * exports and the unsaved-changes check all see the true bindings without anything having to notice that the
 * player edited them in vanilla's Controls screen.
 */
object VanillaKeysConfig {
    private val LOGGER = LoggerFactory.getLogger("hex/config")

    private val file = JsonConfig(
        name = "vanilla_keys",
        type = object : TypeToken<MutableMap<String, String>>() {}.type,
        default = { mutableMapOf<String, String>() },
        normalizer = { map -> map.entries.removeAll { it.key.isBlank() || it.value.isBlank() } },
    )

    /**
     * Bindings read from disk but not yet pushed into the game.
     *
     * Configs load during client init, where `Minecraft.getInstance().options` may still be null, so adopting
     * a value only records it and the actual application waits for the first tick.
     */
    @Volatile
    private var pending: Map<String, String>? = null

    private val handle = ConfigRegistry.register(
        ConfigHandle(
            json = file,
            adopt = { pending = it },
            current = { capture() },
            afterReload = { /* applied from [tick]; options may not exist at this point */ },
        ),
    )

    fun load() = handle.loadInitial()

    /** Pushes any pending bindings into the game. Cheap and a no-op once there is nothing waiting. */
    fun tick() {
        val waiting = pending ?: return
        val options = liveOptions() ?: return
        pending = null
        if (!ConfigProfiles.settings.captureVanillaKeys) return
        runCatching { apply(waiting, options) }
            .onFailure { LOGGER.error("Failed to apply saved key bindings", it) }
    }

    /**
     * The current bindings, or nothing at all when capture is off.
     *
     * Returning an empty map while disabled matters beyond saving: it is also what
     * [ProfileDirtyTracker] compares, so with the feature off, rebinding a key in vanilla's Controls screen
     * correctly does *not* make the Hex profile look modified.
     */
    private fun capture(): MutableMap<String, String> {
        if (!ConfigProfiles.settings.captureVanillaKeys) return mutableMapOf()
        val options = liveOptions() ?: return mutableMapOf()
        return options.keyMappings.associateTo(mutableMapOf()) { it.name to it.saveString() }
    }

    /**
     * The game's options, or null if the client is not far enough along to have them.
     *
     * Both are declared non-null by Minecraft and are genuinely null while the client is still being
     * constructed — which is exactly when configs load. The explicit nullable type is what keeps Kotlin from
     * optimising the check away on the strength of a declaration that does not hold this early.
     */
    private fun liveOptions(): Options? {
        val client: Minecraft? = Minecraft.getInstance()
        val options: Options? = client?.options
        return options
    }

    /**
     * Rebinds everything in [saved] that differs from what is bound now.
     *
     * The refresh at the end runs once for the whole batch rather than per binding: `options.save()` writes
     * `options.txt`, and doing that dozens of times for one profile switch would be needless file churn.
     */
    private fun apply(saved: Map<String, String>, options: Options) {
        var changed = 0
        for ((name, value) in saved) {
            val mapping: KeyMapping = KeyMapping.get(name) ?: continue
            if (mapping.saveString() == value) continue
            // A binding written by a version or mod this client does not have must not abort the rest.
            val key = runCatching { InputConstants.getKey(value) }.getOrNull() ?: continue
            mapping.setKey(key)
            LOGGER.info("Rebound '{}' to '{}' from the active profile", name, value)
            changed++
        }
        if (changed == 0) return

        KeyMapping.releaseAll()
        KeyMapping.resetMapping()
        options.save()
        LOGGER.info("Applied {} key binding(s) from the active profile", changed)
    }
}
