package net.trilleo.util

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.sounds.SoundEvents

/**
 * Client-side feedback: a prefixed chat line and a short UI sound.
 *
 * Everything is scheduled through [Minecraft.execute] so callers may be on any thread — the update checker
 * reports from a background thread, features report from the client tick.
 *
 * Messages go through [net.minecraft.world.entity.player.Player.sendSystemMessage]; there is no player
 * before the world loads, so a notification sent too early is silently dropped rather than queued.
 */
object Notify {

    private const val PREFIX = "[Hex] "

    /**
     * Build a prefixed, coloured line without sending it — for callers that pass a message around first, or
     * that `append` further components onto it (hence [MutableComponent], not `Component`).
     */
    fun line(text: String, color: ChatFormatting = ChatFormatting.AQUA): MutableComponent =
        Component.literal("$PREFIX$text").withStyle(color)

    /** Send an already-built message (see [line]) to the player's chat. */
    fun send(client: Minecraft, message: Component) {
        client.execute { client.player?.sendSystemMessage(message) }
    }

    /** Build and send a prefixed, coloured line in one step. */
    fun chat(client: Minecraft, text: String, color: ChatFormatting = ChatFormatting.AQUA) {
        send(client, line(text, color))
    }

    /**
     * Play the standard UI click. [pitch] distinguishes one feature's feedback from another's — and from a
     * plain button press.
     */
    fun uiSound(client: Minecraft, pitch: Float = 1.0f) {
        client.execute {
            client.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch))
        }
    }
}
