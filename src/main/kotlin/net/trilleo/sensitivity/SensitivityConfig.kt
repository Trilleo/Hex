package net.trilleo.sensitivity

import com.google.gson.reflect.TypeToken
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.trilleo.config.ConfigHandle
import net.trilleo.config.ConfigRegistry
import net.trilleo.config.JsonConfig
import net.trilleo.sensitivity.model.StickyAngle
import net.trilleo.sensitivity.model.StickyAxis
import net.trilleo.sensitivity.model.StickyInterval

/**
 * User-facing settings for the sensitivity hold, persisted at `config/hex/sensitivity.json`.
 *
 * The two original numbers are percentages rather than raw option values, because that is the unit the player
 * already reads sensitivity in — Minecraft's own slider says "100%", not "0.5".
 *
 * The `sticky*` half describes the magnet that pulls the view onto round angles while the key is held; see
 * [StickyAim] for what it does with them.
 *
 * The three [Boolean]`?` switches are nullable for the reason [net.trilleo.region.RegionSettings.enabled] is:
 * GSON leaves an absent `boolean` at the JVM default of `false`, so every `sensitivity.json` written before
 * these settings existed would load as *off* — which is the opposite of what omitting a setting should mean,
 * and would silently take a default-on feature away from everyone upgrading. Read them through
 * [SensitivityConfig.sticky], [SensitivityConfig.stickyScale] and [SensitivityConfig.hud].
 *
 * @property enabled master switch; when off the keybind is inert and the wheel goes back to the hotbar.
 * @property stepPercent how much one wheel notch changes the sensitivity, as a percentage of the current
 *   value. Multiplicative rather than additive so a notch is worth the same everywhere: at 10% a step down
 *   from 100% lands on 90%, and a step down from 10% lands on 9% — still a usable move, where subtracting a
 *   fixed amount would have hit zero.
 * @property snapPercent the multiplier applied the instant the key goes down, before any scrolling, as a
 *   percentage of your normal sensitivity. 100 means "start where I already was".
 * @property hud whether the readout under the crosshair is drawn while the key is held.
 * @property sticky master switch for the whole magnet, so it can be put away without clearing the intervals
 *   and the custom angles that describe it.
 * @property stickyYaw how far apart the yaw angles that catch are — every 90° is the four block faces.
 * @property stickyPitch the same for pitch, where 90 means level and straight up/down.
 * @property stickyZone how far from an angle, in degrees, the magnet reaches at all. Nothing outside it is
 *   touched, which is what keeps ordinary aiming ordinary.
 * @property stickyStrength how hard it pulls inside that zone, as a percentage. Higher takes a faster turn to
 *   break out of; it never stops one, since the pull fades to nothing at the edge of the zone.
 * @property stickyScale whether the magnet grows as the wheel takes you further below your own sensitivity —
 *   "slow down to aim" then also means "slow down to lock on".
 * @property stickyAngles extra angles that catch, on top of the two intervals.
 */
data class SensitivitySettings(
    var enabled: Boolean = true,
    var stepPercent: Double = 10.0,
    var snapPercent: Double = 100.0,
    var hud: Boolean? = null,
    var sticky: Boolean? = null,
    var stickyYaw: StickyInterval = StickyInterval.DIAGONAL,
    var stickyPitch: StickyInterval = StickyInterval.DIAGONAL,
    var stickyZone: Double = 6.0,
    var stickyStrength: Double = 50.0,
    var stickyScale: Boolean? = null,
    var stickyAngles: MutableList<StickyAngle> = mutableListOf(),
)

/** Loads and holds the singleton [SensitivitySettings]. Call [load] once at feature init. */
object SensitivityConfig {
    private val config = JsonConfig(
        name = "sensitivity",
        type = object : TypeToken<SensitivitySettings>() {}.type,
        default = { SensitivitySettings() },
        normalizer = ::normalize,
    )

    const val STEP_MIN: Double = 1.0
    const val STEP_MAX: Double = 50.0
    const val SNAP_MIN: Double = 10.0
    const val SNAP_MAX: Double = 200.0

    const val ZONE_MIN: Double = 1.0
    const val ZONE_MAX: Double = 30.0
    const val STRENGTH_MIN: Double = 5.0
    const val STRENGTH_MAX: Double = 100.0

    /**
     * How many custom angles a file may hold.
     *
     * [StickyAim] walks the whole list twice per frame, and while a couple of dozen entries cost nothing, a
     * hand-edited file with ten thousand would be felt. Well above what the editor is for.
     */
    const val ANGLES_MAX: Int = 32

    var settings: SensitivitySettings = SensitivitySettings()
        private set

    /** Whether the magnet is switched on, treating an absent key as on. */
    val sticky: Boolean get() = settings.sticky != false

    /** Whether the magnet grows as the wheel slows you down, treating an absent key as on. */
    val stickyScale: Boolean get() = settings.stickyScale != false

    /** Whether the readout is drawn, treating an absent key as on. */
    val hud: Boolean get() = settings.hud != false

    /** Exposed so the settings menu can offer this tab a reset button. */
    val handle = ConfigRegistry.register(
        ConfigHandle(
            config,
            adopt = { settings = it },
            current = { settings },
            // A profile switch or a clipboard import can switch the feature off while the key is held down,
            // which would otherwise leave the borrowed sensitivity in place with nothing left to restore it.
            afterReload = {
                if (!settings.enabled) SensitivityState.end(Minecraft.getInstance())
            },
        ),
    )

    fun load() = handle.loadInitial()

    /** Writes immediately. Prefer [markDirty] from anything that fires repeatedly. */
    fun save() = handle.saveNow()

    /** Records that settings changed; the write is batched and lands about a second later. */
    fun markDirty() = handle.markDirty()

    /** Repairs the live settings in place — for an angle added by the editor, which never went through a load. */
    fun normalizeNow() = handle.json.normalize(settings)

    /**
     * Repairs a loaded value.
     *
     * A step of 0 would make the wheel do nothing at all, and a negative one would invert it in a way no
     * setting offers; a hand-edited file must not be able to produce either. The same reasoning covers the
     * magnet: zone and strength are read as "absent" at zero — which is how a `sensitivity.json` written
     * before they existed arrives — and take their defaults rather than freezing the magnet solid.
     */
    private fun normalize(settings: SensitivitySettings) {
        settings.stepPercent = settings.stepPercent.sane(SensitivitySettings().stepPercent)
            .coerceIn(STEP_MIN, STEP_MAX)
        settings.snapPercent = settings.snapPercent.sane(SensitivitySettings().snapPercent)
            .coerceIn(SNAP_MIN, SNAP_MAX)

        // GSON leaves an enum it has no name for at null, exactly as it leaves an absent one, so both the
        // pre-magnet file and a renamed constant land back on the default rather than on a crash at read.
        @Suppress("SENSELESS_COMPARISON")
        if (settings.stickyYaw == null) settings.stickyYaw = SensitivitySettings().stickyYaw
        @Suppress("SENSELESS_COMPARISON")
        if (settings.stickyPitch == null) settings.stickyPitch = SensitivitySettings().stickyPitch

        settings.stickyZone = settings.stickyZone.sane(SensitivitySettings().stickyZone)
            .takeIf { it > 0.0 }?.coerceIn(ZONE_MIN, ZONE_MAX) ?: SensitivitySettings().stickyZone
        settings.stickyStrength = settings.stickyStrength.sane(SensitivitySettings().stickyStrength)
            .takeIf { it > 0.0 }?.coerceIn(STRENGTH_MIN, STRENGTH_MAX) ?: SensitivitySettings().stickyStrength

        @Suppress("SENSELESS_COMPARISON")
        if (settings.stickyAngles == null) settings.stickyAngles = mutableListOf()
        settings.stickyAngles.forEach(::normalizeAngle)
        if (settings.stickyAngles.size > ANGLES_MAX) {
            settings.stickyAngles = settings.stickyAngles.take(ANGLES_MAX).toMutableList()
        }
    }

    /**
     * Puts one custom angle into the range its axis is actually read in.
     *
     * Yaw is wrapped rather than rejected because "270" and "−90" are the same direction and a player may well
     * type either; pitch is clamped because the game itself will not look past straight up or straight down,
     * so an angle beyond that could never be reached and would sit in the list never catching.
     */
    private fun normalizeAngle(angle: StickyAngle) {
        @Suppress("SENSELESS_COMPARISON")
        if (angle.axis == null) angle.axis = StickyAxis.YAW
        angle.degrees = when (angle.axis) {
            StickyAxis.YAW -> Mth.wrapDegrees(angle.degrees.sane(0.0))
            StickyAxis.PITCH -> angle.degrees.sane(0.0).coerceIn(-90.0, 90.0)
        }
    }

    /** Replaces a NaN or infinite value — which no control can produce but a hand-edited file can. */
    private fun Double.sane(fallback: Double): Double = if (isFinite()) this else fallback
}
