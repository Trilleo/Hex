package net.trilleo.sensitivity

import net.minecraft.client.Minecraft

/**
 * Runtime state of the sensitivity hold, shared between [SensitivityFeature] (which starts and ends it from
 * the client tick) and [net.trilleo.mixin.MouseHandlerMixin] (which feeds it the wheel). It is a single
 * `object` so the Java mixin can reach it through `SensitivityState.INSTANCE`.
 *
 * The hold borrows Minecraft's own sensitivity option and puts it back on release. Nothing here writes
 * `options.txt`: the value is set on the live [net.minecraft.client.OptionInstance], which mouse look reads
 * every frame, so the change is felt immediately and forgotten just as fast. That is also why [end] has to
 * run on every exit — a world leave, a shutdown, the feature being switched off underneath it — rather than
 * only on the key coming back up.
 */
object SensitivityState {
    /** True while the key is held. The mouse mixin reads this first and early-outs when it is false. */
    var active: Boolean = false
        private set

    /** The player's own sensitivity, saved on key-down and restored on key-up. */
    private var base: Double = 0.0

    /** Where the wheel has moved the sensitivity to, in option units. */
    private var current: Double = 0.0

    /**
     * Take over the sensitivity, applying the snap multiplier if one is configured. Safe to call every tick;
     * only the first call while the key is down does anything.
     */
    fun begin(client: Minecraft) {
        if (active) return
        base = client.options.sensitivity().get()
        active = true
        // A sensitivity of exactly 0 would leave every multiplication at 0, so the hold would be dead where
        // it is most wanted. Start from the floor instead; [end] still restores the real 0.
        apply(client, base.coerceAtLeast(MIN) * SensitivityConfig.settings.snapPercent / 100.0)
    }

    /** Give the player their own sensitivity back. Safe to call when inactive. */
    fun end(client: Minecraft) {
        if (!active) return
        active = false
        client.options.sensitivity().set(base)
    }

    /**
     * Scale the sensitivity from a scroll-wheel delta (up = more sensitive).
     *
     * One notch is worth a percentage of the current value, not a fixed amount: a step down from a high
     * sensitivity is a big move and a step down from a low one is a small one, which is exactly the resolution
     * you want in each case.
     */
    fun adjust(scrollDelta: Double) {
        if (!active || scrollDelta == 0.0) return
        val step = 1.0 + SensitivityConfig.settings.stepPercent / 100.0
        val factor = if (scrollDelta > 0) step else 1.0 / step
        apply(Minecraft.getInstance(), current * factor)
    }

    private fun apply(client: Minecraft, value: Double) {
        // Minecraft's own option is a 0..1 unit double (its slider shows twice that as a percentage), and it
        // has no idea a mod is writing to it, so the clamp has to happen here.
        current = value.coerceIn(MIN, MAX)
        client.options.sensitivity().set(current)
    }

    /** Not 0: a sensitivity of zero cannot be scrolled back off, since every step multiplies. */
    private const val MIN = 0.001

    /** Vanilla's own ceiling — the value its slider labels *HYPERSPEED!!!* rather than a percentage. */
    private const val MAX = 1.0
}
