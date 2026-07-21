package net.trilleo.hand

import net.minecraft.client.Minecraft
import net.trilleo.config.ConfigCategory
import net.trilleo.feature.Feature
import java.util.Locale

/**
 * Customises how your held item is drawn in first person: where the main hand sits (position, scale,
 * rotation), how fast the swing animation plays, and whether it plays at all. The rendering itself lives in
 * the [net.trilleo.mixin] hand mixins, driven by [HandState]; this class owns only the settings tab.
 *
 * Note this feature deliberately leaves [enabled] at its default `true` and gates behaviour on
 * [HandSettings.enabled] instead. [net.trilleo.feature.Features.categories] hides the tab of a disabled
 * feature, so wiring the master switch to [enabled] would make it impossible to switch back on from the
 * menu.
 */
object HandFeature : Feature {
    override val id: String = "hand"

    override fun onInit() {
        HandConfig.load()
    }

    override fun onShutdown() {
        HandConfig.save()
    }

    override fun settingsCategory(): ConfigCategory = ConfigCategory.build("hand", "Hand") {
        toggle(
            "Enabled",
            "Master switch. When off, your hand is drawn exactly as vanilla and the settings below are " +
                "kept but ignored.",
            get = { HandConfig.settings.enabled },
            set = { HandConfig.settings.enabled = it; HandConfig.save() },
        )

        offset("Position X", "Moves the main hand right (positive) or left (negative).",
            get = { HandConfig.settings.offsetX }, set = { HandConfig.settings.offsetX = it })
        offset("Position Y", "Moves the main hand up (positive) or down (negative).",
            get = { HandConfig.settings.offsetY }, set = { HandConfig.settings.offsetY = it })
        offset("Position Z", "Moves the main hand away from (positive) or towards (negative) the camera.",
            get = { HandConfig.settings.offsetZ }, set = { HandConfig.settings.offsetZ = it })

        slider(
            "Scale",
            "Size of the main-hand arm and item.",
            min = HandSettings.SCALE_MIN,
            max = HandSettings.SCALE_MAX,
            step = HandSettings.SCALE_STEP,
            get = { HandConfig.settings.scale },
            set = { HandConfig.settings.scale = it; HandConfig.save() },
            format = { String.format(Locale.ROOT, "%.2fx", it) },
        )

        rotation("Rotation X", "Tilts the main hand up and down.",
            get = { HandConfig.settings.rotationX }, set = { HandConfig.settings.rotationX = it })
        rotation("Rotation Y", "Turns the main hand left and right.",
            get = { HandConfig.settings.rotationY }, set = { HandConfig.settings.rotationY = it })
        rotation("Rotation Z", "Rolls the main hand around its own axis.",
            get = { HandConfig.settings.rotationZ }, set = { HandConfig.settings.rotationZ = it })

        toggle(
            "Disable swing animation",
            "Hides the first-person swing entirely. Attacking, mining and item use are unaffected — only " +
                "the animation is hidden.",
            get = { HandConfig.settings.disableSwing },
            set = { HandConfig.settings.disableSwing = it; HandConfig.save() },
        )

        slider(
            "Swing speed",
            "How fast the swing animation plays. This is cosmetic: your attack cooldown and mining speed " +
                "are unchanged. Has no effect while the swing animation is disabled, and also applies to " +
                "your own third-person model.",
            min = HandSettings.SWING_SPEED_MIN,
            max = HandSettings.SWING_SPEED_MAX,
            step = HandSettings.SWING_SPEED_STEP,
            get = { HandConfig.settings.swingSpeed },
            set = { HandConfig.settings.swingSpeed = it; HandConfig.save() },
            format = { String.format(Locale.ROOT, "%.2fx", it) },
        )

        action("Reset to defaults", "Restores every display setting above to its default value.") { screen ->
            HandConfig.settings.resetToDefaults()
            HandConfig.save()
            // Re-init the open screen so every slider handle jumps back to its default position; the
            // sliders read their value only when built. Screen.rebuildWidgets is protected, so go through
            // setScreen with the same instance.
            Minecraft.getInstance().setScreen(screen)
        }
    }

    /** A position slider — the three axes differ only in label, getter and setter. */
    private fun ConfigCategory.Builder.offset(
        label: String,
        tooltip: String,
        get: () -> Double,
        set: (Double) -> Unit,
    ) = slider(
        label,
        tooltip,
        min = HandSettings.OFFSET_MIN,
        max = HandSettings.OFFSET_MAX,
        step = HandSettings.OFFSET_STEP,
        get = get,
        set = { set(it); HandConfig.save() },
    )

    /** A rotation slider, shown in whole degrees. */
    private fun ConfigCategory.Builder.rotation(
        label: String,
        tooltip: String,
        get: () -> Double,
        set: (Double) -> Unit,
    ) = slider(
        label,
        tooltip,
        min = HandSettings.ROTATION_MIN,
        max = HandSettings.ROTATION_MAX,
        step = HandSettings.ROTATION_STEP,
        get = get,
        set = { set(it); HandConfig.save() },
        format = { "${it.toInt()}°" },
    )
}
