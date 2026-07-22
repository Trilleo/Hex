package net.trilleo.util

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
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
        uiSound(client, defaultSound(), pitch)
    }

    /** Play any registered sound as a UI sound — no position, so it is never attenuated by distance. */
    fun uiSound(client: Minecraft, sound: SoundEvent, pitch: Float = 1.0f, volume: Float = 1.0f) {
        client.execute {
            client.soundManager.play(SimpleSoundInstance.forUI(sound, pitch, volume))
        }
    }

    /**
     * Play the sound named by [id] (e.g. `"minecraft:block.note_block.pling"`), falling back to the standard
     * UI click when it names nothing this client knows.
     *
     * Falling back rather than staying silent is deliberate: the caller is a user-configured reminder, and a
     * reminder that makes no sound is indistinguishable from one that never fired. A wrong sound is a far
     * smaller failure than a missed alert, and the config screen validates the id at the point it is typed.
     */
    fun uiSound(client: Minecraft, id: String, pitch: Float = 1.0f, volume: Float = 1.0f) {
        uiSound(client, soundFor(id) ?: defaultSound(), pitch, volume)
    }

    /**
     * The standard UI click, unwrapped from its registry holder.
     *
     * [SoundEvents] exposes its constants as `Holder<SoundEvent>`, and the [SimpleSoundInstance.forUI]
     * overload that takes a holder accepts no volume — so the holder has to be unwrapped here for every
     * caller that wants one.
     */
    private fun defaultSound(): SoundEvent = SoundEvents.UI_BUTTON_CLICK.value()

    /**
     * Resolves a sound id, or null when it is malformed or names no registered sound. Exposed so a settings
     * field can report a bad id inline instead of the player discovering it when the reminder fires.
     *
     * The registry is only populated once the game has bootstrapped, so this must not be called at class-init
     * time — it is safe from a tick, a screen, or a config validator.
     */
    fun soundFor(id: String): SoundEvent? {
        val parsed = Identifier.tryParse(id.trim()) ?: return null
        return BuiltInRegistries.SOUND_EVENT.getOptional(parsed).orElse(null)
    }
}
