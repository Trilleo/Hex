package net.trilleo.freecam

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.trilleo.Hex
import net.trilleo.config.ConfigCategory
import net.trilleo.feature.Feature

/**
 * The freecam feature: a rebindable key detaches the camera from the player and lets it fly freely (WASD +
 * mouse, Space/Shift for up/down, scroll to change speed) to observe the surroundings; pressing it again
 * returns to normal. The real player stays frozen and sends no movement — the camera takeover lives in the
 * [net.trilleo.mixin] camera/input/mouse mixins, driven by [FreecamState].
 *
 * Registers its toggle under a dedicated **"Hex"** keybind category (Options → Controls), unbound by
 * default. Persistence is [FreecamConfig]; the config tab is contributed via [settingsCategory].
 */
object FreecamFeature : Feature {
    override val id: String = "freecam"

    /** Master gate: when the config toggle is off, the feature receives no dispatch and hides its tab. */
    override val enabled: Boolean get() = FreecamConfig.settings.enabled

    /** The rebindable key that toggles the freecam. Unbound by default. */
    private lateinit var toggleKey: KeyMapping

    override fun onInit() {
        FreecamConfig.load()

        toggleKey = KeyMapping(
            "key.hex.freecam.toggle",
            InputConstants.UNKNOWN.value,
            Hex.KEY_CATEGORY,
        )
        KeyMappingHelper.registerKeyMapping(toggleKey)
    }

    override fun onClientTick(client: Minecraft) {
        while (toggleKey.consumeClick()) {
            if (FreecamState.active) FreecamState.deactivate(client) else FreecamState.activate(client)
        }
        if (FreecamState.active) FreecamState.tick(client)
    }

    override fun onWorldLeave(client: Minecraft) {
        // Never strand the camera when leaving a world.
        FreecamState.deactivate(client)
    }

    /** Default values for the settings rows; a renderer offers "reset" against these. */
    private val defaults = FreecamSettings()

    override fun settingsCategory(): ConfigCategory = ConfigCategory.build("freecam") {
        toggle(
            "enabled",
            default = defaults.enabled,
            get = { FreecamConfig.settings.enabled },
            set = {
                FreecamConfig.settings.enabled = it
                FreecamConfig.save()
                // Turning the feature off mid-flight would otherwise strand the detached camera.
                if (!it) FreecamState.deactivate(Minecraft.getInstance())
            },
        )
        enum(
            "fly_speed",
            default = defaults.flySpeed,
            get = { FreecamConfig.settings.flySpeed },
            set = {
                FreecamConfig.settings.flySpeed = it
                FreecamConfig.save()
            },
        )
    }
}
