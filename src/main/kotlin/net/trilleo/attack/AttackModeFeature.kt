package net.trilleo.attack

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.trilleo.Hex
import net.trilleo.feature.Feature
import net.trilleo.util.Notify

/**
 * A rebindable key that flips Minecraft's Attack/Destroy control between **Hold** and **Toggle** without a
 * trip through Options → Controls — useful mid-session, where the right mode depends on what you are doing
 * (holding to break a long line of blocks, toggling for a sustained fight).
 *
 * This drives vanilla's own setting rather than intercepting input: `options.keyAttack` is a
 * [net.minecraft.client.ToggleKeyMapping] whose toggle behaviour is read from the `toggleAttack` option, so
 * flipping that option is exactly what the vanilla control does. Nothing here mixes into the input path,
 * the change shows up in the vanilla Controls screen, and it persists through `options.txt` like any other
 * setting.
 *
 * Registered under the shared **"Hex"** keybind category (Options → Controls) and unbound by default — an
 * unbound key is the feature's off switch, so it needs no config tab of its own.
 */
object AttackModeFeature : Feature {
    override val id: String = "attack"

    /** Vanilla's name for the control being switched, so chat matches the Controls screen. */
    private const val CONTROL_KEY = "key.attack"

    /** Vanilla's own wording for the two modes — reused so this localises with the game. */
    private const val MODE_TOGGLE_KEY = "options.key.toggle"
    private const val MODE_HOLD_KEY = "options.key.hold"

    /**
     * Confirmation pitches. Two distinct values rather than one, so the mode you landed on is audible
     * without reading chat: rising for toggle, falling for hold.
     */
    private const val PITCH_TOGGLE = 1.4f
    private const val PITCH_HOLD = 0.8f

    /** The rebindable key that cycles the mode. Unbound by default. */
    private lateinit var cycleKey: KeyMapping

    override fun onInit() {
        cycleKey = KeyMapping(
            "key.hex.attack.cycle_mode",
            InputConstants.UNKNOWN.value,
            Hex.KEY_CATEGORY,
        )
        KeyMappingHelper.registerKeyMapping(cycleKey)
    }

    override fun onClientTick(client: Minecraft) {
        while (cycleKey.consumeClick()) cycle(client)
    }

    /** Flip Attack/Destroy to the other mode, persist it, and tell the player. */
    private fun cycle(client: Minecraft) {
        val option = client.options.toggleAttack()
        val toggle = !option.get()
        option.set(toggle)

        // Switching away from toggle mode while attack is latched down would otherwise leave it stuck
        // attacking, since nothing is holding the button to release it. Clearing the toggle state also
        // means the new mode always starts from "not attacking", whichever direction the switch went.
        KeyMapping.resetToggleKeys()
        client.options.save()

        val mode = if (toggle) MODE_TOGGLE_KEY else MODE_HOLD_KEY
        Notify.send(
            client,
            Notify.line("", ChatFormatting.AQUA)
                .append(Component.translatable(CONTROL_KEY).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" → ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable(mode).withStyle(ChatFormatting.GREEN)),
        )
        Notify.uiSound(client, if (toggle) PITCH_TOGGLE else PITCH_HOLD)
    }
}
