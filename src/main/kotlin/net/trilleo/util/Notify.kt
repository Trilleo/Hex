package net.trilleo.util

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

/**
 * Client-side feedback: a prefixed chat line.
 *
 * Everything is scheduled through [Minecraft.execute] so callers may be on any thread — the update checker
 * reports from a background thread, features report from the client tick.
 *
 * Messages go through [net.minecraft.world.entity.player.Player.sendSystemMessage]; there is no player
 * before the world loads, so a notification sent too early is silently dropped rather than queued.
 *
 * **Sound used to live here and deliberately does not any more.** It moved to
 * [net.trilleo.sound.SoundPlayer] when a sound stopped being "a short click beside a chat line" and became
 * something that could be a whole sequence, with settings of its own. Keeping a second way to make a noise
 * here would have been exactly the parallel audio path that object exists to prevent — so the overloads were
 * deleted rather than left as shims, which is what made the compiler point at every caller.
 */
object Notify {

    private const val PREFIX = "[Hex] "

    /**
     * Build a prefixed, coloured line without sending it — for callers that pass a message around first, or
     * that `append` further components onto it (hence [MutableComponent], not `Component`).
     */
    fun line(text: String, color: ChatFormatting = ChatFormatting.AQUA): MutableComponent =
        Component.literal("$PREFIX$text").withStyle(color)

    /**
     * The same, for text that has to come out of the language files: the prefix is added around a component
     * the caller already built, rather than around a string written in English at the call site.
     */
    fun line(text: Component, color: ChatFormatting = ChatFormatting.AQUA): MutableComponent =
        Component.literal(PREFIX).append(text).withStyle(color)

    /** Send an already-built message (see [line]) to the player's chat. */
    fun send(client: Minecraft, message: Component) {
        client.execute { client.player?.sendSystemMessage(message) }
    }

    /** Build and send a prefixed, coloured line in one step. */
    fun chat(client: Minecraft, text: String, color: ChatFormatting = ChatFormatting.AQUA) {
        send(client, line(text, color))
    }

    /** The same, for a translated message. */
    fun chat(client: Minecraft, text: Component, color: ChatFormatting = ChatFormatting.AQUA) {
        send(client, line(text, color))
    }
}
