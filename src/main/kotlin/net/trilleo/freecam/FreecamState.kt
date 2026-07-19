package net.trilleo.freecam

import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import net.trilleo.freecam.FreecamState.pos
import net.trilleo.freecam.FreecamState.prevPos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Runtime state of the freecam, shared between [FreecamFeature] (which drives it each client tick) and the
 * camera / input / mouse mixins (which read it every frame). It is a single `object` so the Java mixins can
 * reach it through `FreecamState.INSTANCE`.
 *
 * The camera is a purely client-side observer: while [active] the real player is frozen and sends no
 * movement or rotation (see the input / mouse mixins), and the camera flies free with no block collision
 * (noclip) because its position is written directly. Position is integrated at tick rate into [pos]/
 * [prevPos] and the camera mixin interpolates between them by the frame's partial tick for smooth motion;
 * rotation ([yaw]/[pitch]) is updated straight from mouse events, so it is not interpolated.
 */
object FreecamState {
    /** True while the freecam is engaged. Every mixin reads this first and early-outs when it is false. */
    var active: Boolean = false
        private set

    /** Look direction in degrees (Minecraft convention): [yaw] 0 = +Z, [pitch] -90 up .. +90 down. */
    var yaw: Float = 0f
        private set

    var pitch: Float = 0f
        private set

    // Interpolated fly position: prevPos -> pos across the current tick.
    private var pos: Vec3 = Vec3.ZERO
    private var prevPos: Vec3 = Vec3.ZERO

    /** Fly-speed multiplier nudged by the scroll wheel; reset to 1 on each activation. */
    private var speedMultiplier: Double = 1.0

    /** Camera type to restore when the freecam is disengaged. */
    private var savedCameraType: CameraType? = null

    /** Detach the camera at the player's eye. Forces third person so the player body stays visible. */
    fun activate(client: Minecraft) {
        if (active) return
        val player = client.player ?: return
        pos = player.eyePosition
        prevPos = pos
        yaw = player.yRot
        pitch = player.xRot
        speedMultiplier = 1.0
        savedCameraType = client.options.cameraType
        client.options.cameraType = CameraType.THIRD_PERSON_BACK
        active = true
    }

    /** Reattach the camera to the player and restore the previous perspective. Safe to call when inactive. */
    fun deactivate(client: Minecraft) {
        if (!active) return
        active = false
        savedCameraType?.let { client.options.cameraType = it }
        savedCameraType = null
    }

    /**
     * Advance the fly position by one tick from the currently-held movement keys. Called from the feature's
     * client-tick hook. Movement pauses while a screen/chat is focused (the key mappings report released
     * there anyway). Reads the vanilla movement binds so it respects the player's rebinds.
     */
    fun tick(client: Minecraft) {
        prevPos = pos
        if (client.screen != null) return

        val options = client.options
        var strafe = 0.0 // +left, -right (matches Minecraft leftImpulse)
        var forward = 0.0 // +forward, -back
        var vertical = 0.0 // +up, -down (world axis)
        if (options.keyUp.isDown) forward += 1.0
        if (options.keyDown.isDown) forward -= 1.0
        if (options.keyLeft.isDown) strafe += 1.0
        if (options.keyRight.isDown) strafe -= 1.0
        if (options.keyJump.isDown) vertical += 1.0
        if (options.keyShift.isDown) vertical -= 1.0

        val length = sqrt(strafe * strafe + forward * forward + vertical * vertical)
        if (length < 1e-6) return

        // Normalize so diagonals are not faster, then apply the base + scroll speed.
        strafe /= length
        forward /= length
        vertical /= length
        val speed = FreecamConfig.settings.flySpeed.baseSpeed * speedMultiplier

        // Rotate the horizontal input by yaw (same transform as Entity movement).
        val f = sin(Math.toRadians(yaw.toDouble()))
        val g = cos(Math.toRadians(yaw.toDouble()))
        val dx = (strafe * g - forward * f) * speed
        val dz = (forward * g + strafe * f) * speed
        pos = pos.add(dx, vertical * speed, dz)
    }

    /** Feed a raw mouse delta (already scaled by sensitivity) into the look direction. Clamps pitch. */
    fun applyLook(deltaX: Double, deltaY: Double) {
        yaw += (deltaX * LOOK_FACTOR).toFloat()
        pitch = (pitch + (deltaY * LOOK_FACTOR).toFloat()).coerceIn(-90f, 90f)
    }

    /** Scale the fly speed from a scroll-wheel delta (up = faster). */
    fun adjustSpeed(scrollDelta: Double) {
        if (scrollDelta == 0.0) return
        val factor = if (scrollDelta > 0) SPEED_STEP else 1.0 / SPEED_STEP
        speedMultiplier = (speedMultiplier * factor).coerceIn(MIN_SPEED_MULT, MAX_SPEED_MULT)
    }

    /** Camera position for this frame, interpolated between the previous and current tick positions. */
    fun interpolatedPos(partialTick: Float): Vec3 {
        val t = partialTick.toDouble()
        return Vec3(
            prevPos.x + (pos.x - prevPos.x) * t,
            prevPos.y + (pos.y - prevPos.y) * t,
            prevPos.z + (pos.z - prevPos.z) * t,
        )
    }

    private const val LOOK_FACTOR = 0.15
    private const val SPEED_STEP = 1.1
    private const val MIN_SPEED_MULT = 0.1
    private const val MAX_SPEED_MULT = 20.0
}
