package net.trilleo.feature

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * A self-contained mod feature (module). Implement this — usually as an `object` — and register it with
 * [Features.register] in the client entrypoint; [Features] wires the underlying Fabric events once and
 * dispatches to every registered feature.
 *
 * Every hook is a no-op by default, so a feature overrides only the lifecycle points it needs. Tick,
 * chat, world, and command dispatch are all skipped while [enabled] is `false`, making this the single
 * toggle point for a module.
 */
interface Feature {
	/** Stable identifier, e.g. `"keybinds"`. Used for logging and as the `/hexa <id>` subcommand namespace. */
	val id: String

	/** When `false`, the feature is registered but receives no event/command dispatch. */
	val enabled: Boolean get() = true

	/** Called once at startup, in registration order — register keymappings, load config, etc. */
	fun onInit() {}

	/** Add this feature's subcommands under the shared root `/hexa` command. */
	fun registerCommands(hex: LiteralArgumentBuilder<FabricClientCommandSource>) {}

	/** Called every client tick (`END_CLIENT_TICK`). Guard on `client.player` / `client.screen` as needed. */
	fun onClientTick(client: Minecraft) {}

	/** The local player joined a world / server. */
	fun onWorldJoin(client: Minecraft) {}

	/** The local player left the world. */
	fun onWorldLeave(client: Minecraft) {}

	/**
	 * An incoming game chat message. Return `false` to swallow it (hide it from the chat), `true` to let
	 * it through. Runs for every feature; a single `false` cancels the message.
	 */
	fun onChatReceive(message: Component): Boolean = true

	/** The client is stopping — flush any unsaved config here. */
	fun onShutdown() {}
}
