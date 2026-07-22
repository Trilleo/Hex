package net.trilleo.feature

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.KeyMapping
import net.minecraft.network.chat.Component
import net.trilleo.Hex
import net.trilleo.config.*
import net.trilleo.skyblock.item.HeldItem
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
        // Before the features: their configs register with ConfigRegistry as they initialise, and the
        // profile bookkeeping wants to know which profile is active from the very first frame.
        ConfigProfiles.load()

        features.forEach { it.onInit() }

        // Not a feature — it belongs to the profile system — but it registers a config like one, so it has
        // to load alongside them and before the active profile is seeded.
        VanillaKeysConfig.load()

        // Only now are the feature configs registered and loaded, so only now can the active profile be
        // seeded from them.
        ConfigProfiles.ensureActiveSnapshot()

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
                    HexConfigScreens.open(ctx.source.client, null)
                    1
                },
            )
            features.forEach { if (it.enabled) it.registerCommands(hex) }
            dispatcher.register(hex)
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            // Ahead of feature dispatch, and outside the enabled check: a disabled feature's pending config
            // write still has to land.
            ConfigRegistry.tick()
            // Key bindings read from a profile at startup wait for the options to exist; this is where they
            // finally land.
            VanillaKeysConfig.tick()
            ProfileAutoSwitch.tick(client)
            // Owned here rather than by any one feature: the held-item cache has several consumers now (the
            // hand mixins and the reminder engine), and it has to stay live even when the feature that used
            // to tick it is switched off.
            HeldItem.tick(client)

            while (openConfigKey.consumeClick()) {
                client.setScreen(HexConfigScreens.create(client.screen))
            }
            features.forEach { if (it.enabled) it.onClientTick(client) }
        }

        // Attached to the vanilla chat element rather than added first or last, because attaching relative to
        // a vanilla element inherits its render condition — which for every vanilla element but SLEEP is
        // `hideGui`, so F1 hides mod overlays too without a single feature checking for it. Before CHAT puts
        // mod overlays above the scoreboard and title but below chat and the player list.
        //
        // Registered once here and gated at dispatch, never re-registered: `removeElement` exists but Fabric
        // gives no ordering guarantee on re-adding, so a feature toggling `enabled` at runtime must not be
        // able to move itself in the draw order.
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Hex.id("overlays")) { extractor, delta ->
            features.forEach { if (it.enabled) it.onHudRender(extractor, delta) }
        }

        ClientPlayConnectionEvents.JOIN.register { _, _, client ->
            // Ahead of the features and outside the enabled check: profiles are not a feature, and the
            // profile that is about to be adopted decides what the features see.
            ProfileAutoSwitch.onJoin()
            features.forEach { if (it.enabled) it.onWorldJoin(client) }
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, client ->
            ProfileAutoSwitch.onDisconnect()
            // Alongside the tick, and for the same reason: an item from the last server must not go on
            // matching into the next one, whichever features happen to be enabled.
            HeldItem.reset()
            features.forEach { if (it.enabled) it.onWorldLeave(client) }
        }

        ClientReceiveMessageEvents.ALLOW_GAME.register { message, _ ->
            features.all { !it.enabled || it.onChatReceive(message) }
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            // Features first, then the flush: a feature that mutates settings while shutting down still gets
            // those changes written.
            features.forEach { it.onShutdown() }
            ConfigRegistry.flushAll()
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
