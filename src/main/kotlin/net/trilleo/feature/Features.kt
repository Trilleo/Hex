package net.trilleo.feature

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.KeyMapping
import net.minecraft.network.chat.Component
import net.trilleo.Hex
import net.trilleo.config.ConfigCategory
import net.trilleo.config.gui.ConfigScreen
import org.slf4j.LoggerFactory

/**
 * Central registry and event hub for [Feature]s.
 *
 * Features register themselves via [register] in the client entrypoint, then [bootstrap] wires each
 * underlying Fabric callback exactly once and fans it out to every registered feature. This is the only
 * place event wiring lives — new features never touch the entrypoint or register their own callbacks.
 *
 * All commands are nested under a single root `/hexa` (the bare `hex` name is a Skyblock command); each
 * feature contributes subcommands through
 * [Feature.registerCommands]. Dispatch (tick, chat, world, commands) is skipped for a feature whenever
 * its [Feature.enabled] flag is `false`.
 */
object Features {
    private val LOGGER = LoggerFactory.getLogger("hex/features")

    private val features = mutableListOf<Feature>()

    /** Rebindable key that opens the universal `/hexa config` menu. Owned centrally; unbound by default. */
    private lateinit var openConfigKey: KeyMapping

    /** Register one or more features. Call before [bootstrap]. */
    fun register(vararg fs: Feature) {
        features += fs
    }

    /**
     * Every enabled feature's settings tab, in registration order — the sidebar of the `/hexa config`
     * menu. Rebuilt each call so it always reflects the current feature set.
     */
    fun categories(): List<ConfigCategory> =
        features.filter { it.enabled }.mapNotNull { it.settingsCategory() }

    /** Initialize every feature and wire all Fabric events. Call once from `onInitializeClient`. */
    fun bootstrap() {
        features.forEach { it.onInit() }

        // The universal config menu is owned centrally (like the /hexa config command), so its keybind is
        // registered here rather than by any single feature. Shown under the shared "Hex" category.
        openConfigKey = KeyMapping("key.hex.open_config", InputConstants.UNKNOWN.value, Hex.KEY_CATEGORY)
        KeyMappingHelper.registerKeyMapping(openConfigKey)

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            val hex = ClientCommands.literal("hexa")
            hex.executes { ctx ->
                ctx.source.sendFeedback(rootHelp())
                1
            }
            // The universal config menu is owned centrally, not by any single feature. Defer to the next
            // tick: opening a screen mid-command would be overridden when the chat screen closes.
            hex.then(
                ClientCommands.literal("config").executes { ctx ->
                    val client = ctx.source.client
                    client.execute { client.setScreen(ConfigScreen(null)) }
                    1
                },
            )
            features.forEach { if (it.enabled) it.registerCommands(hex) }
            dispatcher.register(hex)
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (openConfigKey.consumeClick()) {
                client.setScreen(ConfigScreen(client.screen))
            }
            features.forEach { if (it.enabled) it.onClientTick(client) }
        }

        ClientPlayConnectionEvents.JOIN.register { _, _, client ->
            features.forEach { if (it.enabled) it.onWorldJoin(client) }
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, client ->
            features.forEach { if (it.enabled) it.onWorldLeave(client) }
        }

        ClientReceiveMessageEvents.ALLOW_GAME.register { message, _ ->
            features.all { !it.enabled || it.onChatReceive(message) }
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            features.forEach { it.onShutdown() }
        }

        LOGGER.info("Bootstrapped {} feature(s)", features.size)
    }

    /** Help line for bare `/hexa`: the mod version plus the available feature subcommands. */
    private fun rootHelp(): Component {
        val version = FabricLoader.getInstance().getModContainer("hex")
            .map { it.metadata.version.friendlyString }
            .orElse("?")
        // "config" is a central subcommand, not a feature id, so list it ahead of the feature ids.
        val names = listOf("config") + features.filter { it.enabled }.map { it.id }
        val suffix = " — /hexa ${names.joinToString(", ")}"
        return Component.literal("Hex v$version$suffix")
    }
}
