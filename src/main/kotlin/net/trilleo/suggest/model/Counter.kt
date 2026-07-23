package net.trilleo.suggest.model

/**
 * A count that forgets, and the primitive every learned number in this feature is built from.
 *
 * A plain tally cannot model a habit. Someone who ran `/warp dwarven_mines` two hundred times last month and
 * has been in the Rift ever since is not, in any useful sense, a dwarven-mines player any more — but a raw
 * count says they are, for months. Exponential decay fixes that: each count is worth
 * `2^(-age / halfLife)` of what it was when it was made, so a habit fades on its own and the model tracks
 * where the player actually is now.
 *
 * **The decay is lazy, and that is the whole trick.** Nothing sweeps the model on a timer; there is no
 * background job and no per-tick cost, however large the model grows. A counter simply stores *when* its
 * weight was last correct, and every read discounts from there. Writing decays first and then adds, which
 * keeps the stored pair exact rather than approximate — replaying the same events in the same order always
 * lands on the same number.
 *
 * The half-life is not stored per counter. It comes from the player's **adaptation** setting and is passed in
 * on every call, so turning adaptation up re-interprets the whole existing model instead of discarding it.
 *
 * Field names are one character because there are tens of thousands of these in the model file and the JSON
 * is written pretty-printed; `w`/`t` rather than `weight`/`updatedAt` is the difference between a model file
 * that is a few hundred kilobytes and one that is a few megabytes. Plain, `var`-only and no-arg constructible
 * for the same GSON reasons as [net.trilleo.reminder.model.Trigger].
 */
class Counter {
    /** The weight as of [t]. Not the current value — read it through [value]. */
    var w: Double = 0.0

    /** When [w] was last correct, as epoch milliseconds. Zero on a counter that has never been written. */
    var t: Long = 0L

    /**
     * This counter's weight now, discounted for the time since it was last written.
     *
     * Never throws and never returns a non-finite number: the model file is plain JSON a player may edit, and
     * one hand-typed `NaN` reaching the ranker would poison every comparison it takes part in — `NaN` is
     * neither greater nor less than anything, so a sort over it produces an arbitrary order rather than an
     * error anyone could diagnose.
     */
    fun value(now: Long, halfLifeMs: Long): Double {
        if (!w.isFinite() || w <= 0.0) return 0.0
        if (t <= 0L || halfLifeMs <= 0L) return w
        // A clock that moved backwards (a manual change, or a resumed laptop) must not *inflate* a weight.
        val elapsed = (now - t).coerceAtLeast(0L)
        if (elapsed == 0L) return w
        val decayed = w * Math.pow(2.0, -elapsed.toDouble() / halfLifeMs.toDouble())
        return if (decayed.isFinite()) decayed else 0.0
    }

    /** Discounts [w] to what it is worth at [now] and stamps [t]. Idempotent for a given [now]. */
    fun decayTo(now: Long, halfLifeMs: Long) {
        w = value(now, halfLifeMs)
        t = now
    }

    /** Decays to [now], then adds [amount]. This is how every observation enters the model. */
    fun bump(now: Long, halfLifeMs: Long, amount: Double = 1.0) {
        decayTo(now, halfLifeMs)
        w += amount
    }

    /** Whether this counter has decayed to nothing worth keeping — the test [ModelStore] prunes on. */
    fun negligible(now: Long, halfLifeMs: Long): Boolean = value(now, halfLifeMs) < PRUNE_FLOOR

    companion object {
        /**
         * Below this a counter is dropped at save time.
         *
         * One observation decays to this after about seven half-lives, so at the default fourteen-day setting
         * a command used once and never again survives roughly three months — long enough that an occasional
         * habit is not forgotten between sessions, short enough that a typo does not live in the file forever.
         */
        const val PRUNE_FLOOR: Double = 0.008

        /** A counter already holding one observation at [now], for seeding priors from the catalogue. */
        fun of(weight: Double, now: Long): Counter = Counter().also { it.w = weight; it.t = now }
    }
}
