package net.trilleo.keybind

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.trilleo.Hex
import net.trilleo.command.Commands
import net.trilleo.feature.Feature
import net.trilleo.keybind.gui.KeybindScreen

/**
 * The keybind shortcuts feature: user-defined key combos that run a sequence of commands / chat messages.
 *
 * Owns the rebindable "open menu" [KeyMapping] (shown under Options → Controls) and drives per-tick combo
 * detection through [KeybindManager]. Persistence lives in [KeybindConfig]; the config GUI is
 * [KeybindScreen], reachable via the keymapping or the `/hexa keybinds` command.
 */
object KeybindsFeature : Feature {
    override val id: String = "keybinds"

    /** The rebindable key that opens the Keybinds screen. Unbound by default. */
    private lateinit var openMenuKey: KeyMapping

    override fun onInit() {
        KeybindConfig.load()

        openMenuKey = KeyMapping(
            "key.hex.open_menu",
            InputConstants.UNKNOWN.value,
            Hex.KEY_CATEGORY,
        )
        KeyMappingHelper.registerKeyMapping(openMenuKey)
    }

    override fun onClientTick(client: Minecraft) {
        while (openMenuKey.consumeClick()) {
            client.setScreen(KeybindScreen(client.screen))
        }
        KeybindManager.onEndTick(client)
    }

    override fun registerCommands(hex: LiteralArgumentBuilder<FabricClientCommandSource>) {
        hex.then(
            Commands.literal("keybinds").executes { ctx ->
                // Defer to the next tick: opening a screen mid-command would be overridden when the chat
                // screen that ran the command closes.
                val client = ctx.source.client
                client.execute { client.setScreen(KeybindScreen(null)) }
                1
            },
        )
    }

    override fun onShutdown() {
        KeybindConfig.save()
    }
}
