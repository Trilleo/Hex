package net.trilleo.sensitivity

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.DeltaTracker
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.trilleo.Hex
import net.trilleo.config.ConfigCategory
import net.trilleo.feature.Feature
import net.trilleo.sensitivity.gui.StickyAnglesScreen
import java.util.*

/**
 * Mouse sensitivity on a held key: while it is down the scroll wheel changes how fast you turn, and letting
 * go puts your own sensitivity back. Aiming a bow at something far away, lining up a click on a small NPC and
 * spinning to face a mob behind you all want different sensitivities, and the vanilla answer is three trips
 * into Options → Controls → Mouse Settings.
 *
 * The same hold also arms [StickyAim], which pulls the view onto round angles it comes near — the other half
 * of aiming precisely, and one no amount of slowing the mouse down can do on its own.
 *
 * The hold itself lives in [SensitivityState], which the mouse mixin feeds; this drives it from the key.
 *
 * Like [net.trilleo.region.RegionFeature], this leaves [enabled] at `true` and gates on
 * [SensitivitySettings.enabled] instead: [net.trilleo.feature.Features.categories] hides a disabled feature's
 * tab, so wiring the master switch to [enabled] would make it impossible to switch back on from the menu.
 */
object SensitivityFeature : Feature {
    override val id: String = "sensitivity"

    /** The key that is held to take over the wheel. Unbound by default. */
    private lateinit var adjustKey: KeyMapping

    override fun onInit() {
        SensitivityConfig.load()

        adjustKey = KeyMapping(
            "key.hex.sensitivity.adjust",
            InputConstants.UNKNOWN.value,
            Hex.KEY_CATEGORY,
        )
        KeyMappingHelper.registerKeyMapping(adjustKey)
    }

    override fun onClientTick(client: Minecraft) {
        // A hold, not a press, so this reads the key's live state rather than draining its click queue.
        // Screens are excluded on purpose: the wheel belongs to whatever is open, and a value borrowed while
        // an inventory is up could not be handed back by a key release nobody sees.
        val held = SensitivityConfig.settings.enabled &&
                client.screen == null &&
                client.player != null &&
                adjustKey.isDown

        if (held) SensitivityState.begin(client) else SensitivityState.end(client)
    }

    override fun onHudRender(extractor: GuiGraphicsExtractor, delta: DeltaTracker) {
        SensitivityHud.draw(extractor)
    }

    override fun onWorldLeave(client: Minecraft) {
        // Never leave a borrowed sensitivity behind on the way out of a world — the key release that would
        // have restored it happens on a screen, where it is not read.
        SensitivityState.end(client)
    }

    override fun onShutdown() {
        // Minecraft writes options.txt as it closes, so a hold still running at that moment would persist the
        // borrowed value for good.
        SensitivityState.end(Minecraft.getInstance())
    }

    /** Default values for the settings rows; a renderer offers "reset" against these. */
    private val defaults = SensitivitySettings()

    override fun settingsCategory(): ConfigCategory = ConfigCategory.build("sensitivity") {
        toggle(
            "enabled",
            default = defaults.enabled,
            get = { SensitivityConfig.settings.enabled },
            set = {
                SensitivityConfig.settings.enabled = it
                SensitivityConfig.save()
                // Switching it off mid-hold would strand the borrowed sensitivity: the tick stops looking at
                // the key, so the release that puts it back is never seen.
                if (!it) SensitivityState.end(Minecraft.getInstance())
            },
        )
        // markDirty, not save: a slider fires its setter on every frame of a drag.
        slider(
            "step",
            min = SensitivityConfig.STEP_MIN,
            max = SensitivityConfig.STEP_MAX,
            step = 1.0,
            default = defaults.stepPercent,
            get = { SensitivityConfig.settings.stepPercent },
            set = { SensitivityConfig.settings.stepPercent = it; SensitivityConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.0f%%", it) },
        )
        slider(
            "snap",
            min = SensitivityConfig.SNAP_MIN,
            max = SensitivityConfig.SNAP_MAX,
            step = 5.0,
            default = defaults.snapPercent,
            get = { SensitivityConfig.settings.snapPercent },
            set = { SensitivityConfig.settings.snapPercent = it; SensitivityConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.0f%%", it) },
        )
        toggle(
            "hud",
            // The nullable settings default to "absent means on", so the row's own default is `true` rather
            // than the null the field starts at — see SensitivitySettings.
            default = true,
            get = { SensitivityConfig.hud },
            set = { SensitivityConfig.settings.hud = it; SensitivityConfig.save() },
        )

        toggle(
            "sticky",
            default = true,
            get = { SensitivityConfig.sticky },
            set = {
                SensitivityConfig.settings.sticky = it
                SensitivityConfig.save()
                // The readout draws whatever lock the magnet last reported, and switching it off stops it
                // reporting rather than clearing what it said.
                if (!it) StickyAim.clear()
            },
        )
        enum(
            "sticky_yaw",
            default = defaults.stickyYaw,
            get = { SensitivityConfig.settings.stickyYaw },
            set = { SensitivityConfig.settings.stickyYaw = it; SensitivityConfig.save() },
        )
        enum(
            "sticky_pitch",
            default = defaults.stickyPitch,
            get = { SensitivityConfig.settings.stickyPitch },
            set = { SensitivityConfig.settings.stickyPitch = it; SensitivityConfig.save() },
        )
        slider(
            "sticky_zone",
            min = SensitivityConfig.ZONE_MIN,
            max = SensitivityConfig.ZONE_MAX,
            step = 1.0,
            default = defaults.stickyZone,
            get = { SensitivityConfig.settings.stickyZone },
            set = { SensitivityConfig.settings.stickyZone = it; SensitivityConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.0f°", it) },
        )
        slider(
            "sticky_strength",
            min = SensitivityConfig.STRENGTH_MIN,
            max = SensitivityConfig.STRENGTH_MAX,
            step = 5.0,
            default = defaults.stickyStrength,
            get = { SensitivityConfig.settings.stickyStrength },
            set = { SensitivityConfig.settings.stickyStrength = it; SensitivityConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.0f%%", it) },
        )
        toggle(
            "sticky_scale",
            default = true,
            get = { SensitivityConfig.stickyScale },
            set = { SensitivityConfig.settings.stickyScale = it; SensitivityConfig.save() },
        )
        action("sticky_angles") { screen -> Minecraft.getInstance().setScreen(StickyAnglesScreen(screen)) }

        resetsTo(SensitivityConfig.handle)
    }
}
