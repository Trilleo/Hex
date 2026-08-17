package net.trilleo.sensitivity

import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.trilleo.sensitivity.model.StickyAxis
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.round
import kotlin.math.sqrt

/**
 * The magnet that pulls the view onto round angles while the sensitivity key is held.
 *
 * Lining up on an axis by hand is the one aiming job a mouse is bad at: yaw is a float, so "facing south" is
 * 180.000 and nothing you can hold your hand still enough to land on. Every build, every portal, every wall
 * you want to run along is on one of those angles, and the vanilla answer is to eyeball it.
 *
 * So while the key is down — and only then, so ordinary aiming is never touched — each of yaw and pitch is
 * drawn towards the nearest angle it is *already close to*. Nothing is redirected: the pull fades to nothing
 * at the edge of its zone, so an angle you are turning past exerts less as you leave it, and a turn faster
 * than the pull simply carries through. Stop moving inside the zone and it settles, exactly, on the angle.
 *
 * A single `object` so [net.trilleo.mixin.MouseHandlerMixin] can reach it through `StickyAim.INSTANCE`.
 */
object StickyAim {

    /**
     * The yaw the view is currently sitting on, or null when it is not on one. Read by [SensitivityHud].
     *
     * "Sitting on" is generous by [SETTLED] rather than exact, because the last step of the approach is a
     * float addition: it lands within an unrepresentable hair of the angle, not necessarily on the same bit
     * pattern, and a readout that flickered on that would be reporting arithmetic rather than aim.
     */
    var lockedYaw: Double? = null
        private set

    /** The pitch the view is currently sitting on, or null. */
    var lockedPitch: Double? = null
        private set

    /** Forget any lock. Called when the hold ends, so the readout cannot outlive the magnet that set it. */
    fun clear() {
        lockedYaw = null
        lockedPitch = null
    }

    /**
     * Pull the view towards whatever it is near, once per frame.
     *
     * @param seconds real time since the last look update — [net.minecraft.client.MouseHandler] already has
     *   it, and passing it through is what keeps the pull the same at 30 fps and at 300.
     */
    fun apply(client: Minecraft, seconds: Double) {
        val player = client.player
        if (player == null || !SensitivityState.active || !SensitivityConfig.sticky) {
            clear()
            return
        }

        // A stall — a chunk build, an alt-tab, the first frame after the world loads — hands over a delta of
        // whole seconds. Uncapped, that one frame would swallow the entire distance to the angle at once,
        // which is the one thing this is supposed never to do.
        val dt = seconds.coerceIn(0.0, MAX_STEP)
        if (dt <= 0.0) return

        val settings = SensitivityConfig.settings
        // The square root, not the ratio: at a quarter of your normal sensitivity the magnet is twice the
        // size, not four times. Scaling it as fast as the slowdown makes the bottom of the wheel unusable for
        // anything but the angles.
        val boost = if (SensitivityConfig.stickyScale) sqrt(SensitivityState.slowdown) else 1.0
        val zone = settings.stickyZone * boost
        // Per second, so `rate * dt` is the share of the remaining distance closed this frame.
        val rate = settings.stickyStrength / 100.0 * RATE_MAX * boost

        val yaw = player.yRot.toDouble()
        val yawDelta = delta(yaw, settings.stickyYaw.degrees, StickyAxis.YAW, wrap = true)
        val pitch = player.xRot.toDouble()
        val pitchDelta = delta(pitch, settings.stickyPitch.degrees, StickyAxis.PITCH, wrap = false)

        val yawPull = pull(yawDelta, zone, rate, dt)
        val pitchPull = pull(pitchDelta, zone, rate, dt)

        lockedYaw = lockOn(yaw, yawDelta, yawPull)
        lockedPitch = lockOn(pitch, pitchDelta, pitchPull)

        if (yawPull != 0.0 || pitchPull != 0.0) turn(player, yawPull, pitchPull)
    }

    /**
     * The signed distance from [value] to the nearest angle that catches, or null when none is near enough to
     * matter. Negative means the angle is behind the current one.
     *
     * The regular interval and the player's own angles are considered together rather than in turn, so two
     * angles a few degrees apart cannot fight over the view: exactly one of them wins, every frame.
     */
    private fun delta(value: Double, interval: Double, axis: StickyAxis, wrap: Boolean): Double? {
        // NaN as "nothing yet", so the running best can stay a primitive inside the loop below.
        var best = Double.NaN

        fun consider(target: Double) {
            // Wrapped for yaw so the magnet still works at the seam, where the player's yaw may be 359 (or
            // 719, since vanilla lets it run on as you spin) and the angle is written as 0.
            val d = if (wrap) Mth.wrapDegrees(target - value) else target - value
            if (best.isNaN() || abs(d) < abs(best)) best = d
        }

        if (interval > 0.0) consider(round(value / interval) * interval)
        SensitivityConfig.settings.stickyAngles.forEach {
            if (it.enabled && it.axis == axis) consider(it.degrees)
        }
        return best.takeUnless { it.isNaN() }
    }

    /**
     * How far to move this frame, given [delta] to the angle.
     *
     * The share of the remaining distance closed each second is fixed, which alone would make the pull an
     * exponential approach with a hard edge where the zone ends — crossing that edge would tug. The squared
     * falloff removes the edge: the pull is zero exactly at the boundary and grows inward, so an angle is
     * something the view leans into rather than something it hits.
     *
     * It also sets what "sticky" means. Sustained mouse movement settles at whatever offset the pull matches,
     * and past a turn rate the falloff can no longer keep up with, the angle is simply left behind.
     */
    private fun pull(delta: Double?, zone: Double, rate: Double, dt: Double): Double {
        if (delta == null) return 0.0
        val distance = abs(delta)
        if (distance >= zone) return 0.0
        // The last hair, closed in one step. A geometric approach never actually arrives, and "almost 180"
        // is exactly what this feature exists to stop the player having to accept.
        if (distance <= SETTLED) return delta
        val falloff = (1.0 - distance / zone).let { it * it }
        // The exponential, not `rate * dt` itself: the share left after a second is what is fixed, and
        // compounding the raw product per frame would make the same setting pull harder at 30 fps than at 300
        // — and past a rate of one, overshoot the angle and come back, which reads as a wobble.
        return delta * falloff * (1.0 - exp(-rate * dt))
    }

    /** The angle being sat on, for the readout: near enough, and no longer being moved towards. */
    private fun lockOn(value: Double, delta: Double?, pull: Double): Double? {
        if (delta == null || abs(delta) > SETTLED) return null
        return value + pull
    }

    /**
     * Hands the pull to the game the same way the mouse itself does.
     *
     * [LocalPlayer.turn] divides by the 0.15 it is about to multiply back, which looks like a detour but is
     * the point: it is the one path that also carries the previous-tick rotation along, and rotating without
     * that would leave the camera interpolating from where the view no longer is — a judder on every frame
     * the magnet moved. It clamps pitch and tells a vehicle its passenger turned, for free.
     */
    private fun turn(player: LocalPlayer, yaw: Double, pitch: Double) {
        player.turn(yaw / TURN_FACTOR, pitch / TURN_FACTOR)
    }

    /** Vanilla's degrees-per-unit inside [LocalPlayer.turn]. */
    private const val TURN_FACTOR = 0.15

    /**
     * Below this many degrees from an angle, the view is on it — the last step is taken whole rather than
     * approached forever. A quarter of a screen pixel at any ordinary field of view, so the step it saves is
     * one nobody can see, and what it buys is a yaw that reads 180 rather than 179.997.
     */
    private const val SETTLED = 0.01

    /**
     * What a strength of 100% closes per second, as a share of the distance left.
     *
     * Tuned against how fast a turn has to be to pull free: at the default reach and strength that is around
     * 25°/s — a deliberate turn passes straight through an angle and only a careful one is caught — and at the
     * widest reach and full strength it is nearer 200°/s, which is a magnet you have to mean to leave.
     */
    private const val RATE_MAX = 80.0

    /** The longest frame the pull is allowed to act over, in seconds. */
    private const val MAX_STEP = 0.1
}
