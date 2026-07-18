package net.trilleo.command

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.network.chat.Component

/**
 * Thin conveniences for building client commands, used inside [net.trilleo.feature.Feature.registerCommands]
 * so features don't repeat the `ClientCommandManager` / `Component` plumbing.
 *
 * The root `/hexa` command itself is owned by [net.trilleo.feature.Features]; features add subcommands to it.
 * [registerStandalone] is the escape hatch for the rare feature that needs a top-level command instead.
 */
object Commands {
    /** A literal command node, e.g. `literal("keybinds")`. */
    fun literal(name: String): LiteralArgumentBuilder<FabricClientCommandSource> =
        ClientCommands.literal(name)

    /** An argument command node, e.g. `argument("amount", IntegerArgumentType.integer())`. */
    fun <T : Any> argument(name: String, type: ArgumentType<T>): RequiredArgumentBuilder<FabricClientCommandSource, T> =
        ClientCommands.argument(name, type)

    /** Send a normal (white) feedback line to the command source. */
    fun feedback(source: FabricClientCommandSource, text: String) {
        source.sendFeedback(Component.literal(text))
    }

    /** Send an error (red) line to the command source. */
    fun error(source: FabricClientCommandSource, text: String) {
        source.sendError(Component.literal(text))
    }

    /**
     * Register a standalone top-level command (not nested under `/hexa`). The factory returns the complete
     * named node, e.g. `registerStandalone { literal("mycmd").executes { ... } }`. Use sparingly — prefer a
     * `/hexa` subcommand — and be mindful of collisions with server-side commands on Hypixel.
     */
    fun registerStandalone(build: () -> LiteralArgumentBuilder<FabricClientCommandSource>) {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(build())
        }
    }
}
