package net.trilleo.hand

import net.trilleo.config.ConfigCategory
import net.trilleo.feature.Feature
import java.util.*

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

    /** Default values for the settings rows; a renderer offers "reset" against these. */
    private val defaults = HandSettings()

    override fun onInit() {
        HandConfig.load()
    }

    override fun settingsCategory(): ConfigCategory = ConfigCategory.build("hand") {
        toggle(
            "enabled",
            default = defaults.enabled,
            get = { HandConfig.settings.enabled },
            set = { HandConfig.settings.enabled = it; HandConfig.save() },
        )

        offset(
            "offset_x", defaults.offsetX,
            get = { HandConfig.settings.offsetX }, set = { HandConfig.settings.offsetX = it })
        offset(
            "offset_y", defaults.offsetY,
            get = { HandConfig.settings.offsetY }, set = { HandConfig.settings.offsetY = it })
        offset(
            "offset_z", defaults.offsetZ,
            get = { HandConfig.settings.offsetZ }, set = { HandConfig.settings.offsetZ = it })

        slider(
            "scale",
            min = HandSettings.SCALE_MIN,
            max = HandSettings.SCALE_MAX,
            step = HandSettings.SCALE_STEP,
            default = defaults.scale,
            get = { HandConfig.settings.scale },
            set = { HandConfig.settings.scale = it; HandConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.2fx", it) },
        )

        rotation(
            "rotation_x", defaults.rotationX,
            get = { HandConfig.settings.rotationX }, set = { HandConfig.settings.rotationX = it })
        rotation(
            "rotation_y", defaults.rotationY,
            get = { HandConfig.settings.rotationY }, set = { HandConfig.settings.rotationY = it })
        rotation(
            "rotation_z", defaults.rotationZ,
            get = { HandConfig.settings.rotationZ }, set = { HandConfig.settings.rotationZ = it })

        toggle(
            "disable_swing",
            default = defaults.disableSwing,
            get = { HandConfig.settings.disableSwing },
            set = { HandConfig.settings.disableSwing = it; HandConfig.save() },
        )

        slider(
            "swing_speed",
            min = HandSettings.SWING_SPEED_MIN,
            max = HandSettings.SWING_SPEED_MAX,
            step = HandSettings.SWING_SPEED_STEP,
            default = defaults.swingSpeed,
            get = { HandConfig.settings.swingSpeed },
            set = { HandConfig.settings.swingSpeed = it; HandConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.2fx", it) },
        )

        // The tab's reset button, drawn in the menu footer alongside every other tab's. This replaces the
        // hand-rolled reset row that used to sit at the bottom of this list, and unlike that one it also
        // resets the enabled toggle — a reset that quietly leaves some settings behind is the more
        // surprising of the two behaviours.
        resetsTo(HandConfig.handle)
    }

    /** A position slider — the three axes differ only in key, default, getter and setter. */
    private fun ConfigCategory.Builder.offset(
        key: String,
        default: Double,
        get: () -> Double,
        set: (Double) -> Unit,
    ) = slider(
        key,
        min = HandSettings.OFFSET_MIN,
        max = HandSettings.OFFSET_MAX,
        step = HandSettings.OFFSET_STEP,
        default = default,
        get = get,
        // markDirty, not save: a slider setter fires every frame of a drag, and writing the file that often
        // would hammer the disk. ConfigRegistry batches it into one write once the drag settles.
        set = { set(it); HandConfig.markDirty() },
    )

    /** A rotation slider, shown in whole degrees. */
    private fun ConfigCategory.Builder.rotation(
        key: String,
        default: Double,
        get: () -> Double,
        set: (Double) -> Unit,
    ) = slider(
        key,
        min = HandSettings.ROTATION_MIN,
        max = HandSettings.ROTATION_MAX,
        step = HandSettings.ROTATION_STEP,
        default = default,
        get = get,
        set = { set(it); HandConfig.markDirty() },
        format = { "${it.toInt()}°" },
    )
}
