package net.trilleo.suggest.model

import net.trilleo.suggest.model.Weights.LAMBDA
import net.trilleo.suggest.model.Weights.LEARNABLE
import net.trilleo.suggest.model.Weights.w
import kotlin.math.exp

/**
 * How much each signal is worth, learned from what the player actually picks.
 *
 * This is the discriminative half of the feature, and the reason it is not just a pile of counters. The
 * counting models ([NaiveBayes], [MarkovChain], the raw frequencies) each answer a different question about a
 * candidate, and how much any of them deserves to be trusted is a property of the *player*, not of the
 * algorithm. Someone who plays one island for months wants habit to dominate; someone hopping between
 * activities wants the recent routine to. Hard-coding that trade-off means being wrong for most people; this
 * learns it, per install, from the ordinary act of choosing a suggestion.
 *
 * **Softmax cross-entropy over the shown list.** Every time a list is shown and something is taken from it,
 * that is one labelled example: these were the options, that was the answer. One gradient step on
 * `p = softmax(w·x)` moves the weights so the accepted candidate would have scored higher relative to the
 * ones that were shown next to it and passed over. Crucially the loss is over the *shown set* rather than the
 * whole model — the player only rejected what they could see, and treating the other two thousand keys as
 * negatives would be inventing evidence.
 *
 * **Nine parameters, and that is the design.** A model this small converges on tens of examples rather than
 * thousands, cannot meaningfully overfit, costs nine multiplies per candidate, and — the part that matters
 * most for a feature the player is supposed to trust — is completely legible: every number in it has a name,
 * and the dashboard can print the arithmetic that produced any suggestion.
 *
 * Two things keep it from running away on a strange week. [LAMBDA] pulls every weight back towards
 * [DEFAULTS], so the shipped behaviour is an attractor rather than merely a starting point, and the range
 * clamp bounds the damage from any single pathological session.
 */
object Weights {

    /** Decayed frequency of this exact line — plain habit. */
    const val PRIOR: Int = 0

    /** [NaiveBayes] over the context snapshot: where you are, what you hold, what just happened in chat. */
    const val CONTEXT: Int = 1

    /** [MarkovChain]: what usually follows the last two commands. */
    const val MARKOV: Int = 2

    /** [Fuzzy]: how well it matches what has been typed so far. */
    const val MATCH: Int = 3

    /** Decayed frequency of the command *name*, across every argument it has been used with. */
    const val FAMILY: Int = 4

    /** The bundled catalogue's weak prior — the only thing keeping day one from being blank. */
    const val CATALOGUE: Int = 5

    /** How recently this exact line was last used, which is a different claim from how often. */
    const val RECENCY: Int = 6

    /** Pinned from the dashboard. Not learned — see [LEARNABLE]. */
    const val PINNED: Int = 7

    /** Already used since joining. Whether that argues for or against is exactly what gets learned. */
    const val SESSION: Int = 8

    const val COUNT: Int = 9

    /** Names for the dashboard and the "why" view. Player-facing, so no jargon. */
    val NAMES: List<String> = listOf(
        "habit", "context", "routine", "what you typed",
        "command family", "known command", "used recently", "pinned", "this session",
    )

    /**
     * The shipped starting point, and the anchor [LAMBDA] pulls back towards.
     *
     * [MATCH] dominates on purpose. Everything else describes what the player usually does; that one
     * describes what they are doing right now, and a suggester that lets a strong habit outrank the letters
     * on screen is not a suggester, it is an argument. [PINNED] is larger still because a pin is an
     * instruction rather than a signal.
     */
    val DEFAULTS: DoubleArray = doubleArrayOf(
        1.0,  // PRIOR
        0.6,  // CONTEXT
        0.5,  // MARKOV
        3.0,  // MATCH
        0.35, // FAMILY
        1.2,  // CATALOGUE
        0.8,  // RECENCY
        6.0,  // PINNED
        0.15, // SESSION
    )

    /**
     * [PINNED] is excluded from learning, and it is the only one.
     *
     * A pin is a promise the player made to themselves through the dashboard, and a weight the ranker is free
     * to move is a promise it can quietly withdraw — which it would, since a pinned entry appearing at the top
     * and then *not* being chosen is exactly the pattern that trains a weight downwards. Everything that is a
     * genuine signal is learnable; the one control that is an instruction is not.
     */
    private val LEARNABLE: BooleanArray = booleanArrayOf(
        true, true, true, true, true, true, true, false, true,
    )

    /** The live weights. Kept as an array because it is read once per candidate per keystroke. */
    private var w: DoubleArray = DEFAULTS.copyOf()

    /**
     * The [ModelData] instance [w] was last read out of.
     *
     * Identity, not equality: [SuggestModel] replaces the whole object on load and on wipe, so comparing
     * references is a complete and self-healing test for "these weights belong to a model that no longer
     * exists" — no coordination, no invalidation call anybody can forget to make.
     */
    private var boundTo: ModelData? = null

    /** A copy of the live weights, for display. */
    fun current(): DoubleArray {
        sync()
        return w.copyOf()
    }

    /** `w · x` — the candidate's score, and the only thing the ordering depends on. */
    fun score(features: DoubleArray): Double {
        sync()
        var sum = 0.0
        for (i in 0 until COUNT) sum += w[i] * features[i]
        return sum
    }

    /** Each feature's signed contribution to a candidate's score, for the "why" view. */
    fun contributions(features: DoubleArray): DoubleArray {
        sync()
        return DoubleArray(COUNT) { w[it] * features[it] }
    }

    /**
     * One learning step: [accepted] was chosen out of [shown].
     *
     * Does nothing when there was no real choice to observe. A list of one teaches nothing — the gradient of
     * a softmax over a single option is exactly zero — and spending a step on it would only decay the weights
     * towards their defaults for no reason.
     */
    fun train(shown: List<Candidate>, accepted: Candidate) {
        if (shown.size < 2) return
        val index = shown.indexOfFirst { it.key == accepted.key }
        if (index < 0) return

        sync()
        val probabilities = softmax(DoubleArray(shown.size) { shown[it].score })

        for (k in 0 until COUNT) {
            if (!LEARNABLE[k]) continue
            var expected = 0.0
            for (j in shown.indices) expected += probabilities[j] * shown[j].features[k]

            val gradient = shown[index].features[k] - expected
            var next = w[k] + ETA * gradient
            next -= LAMBDA * (next - DEFAULTS[k])
            w[k] = next.coerceIn(MIN_WEIGHT, MAX_WEIGHT)
        }

        SuggestModel.withLock { it.trainingSteps++ }
        persist()
    }

    /** Restores the shipped weights, leaving the counts alone. */
    fun reset() {
        sync()
        w = DEFAULTS.copyOf()
        SuggestModel.withLock { it.trainingSteps = 0 }
        persist()
    }

    /** Numerically stable softmax — the shift by the maximum is what keeps `exp` off infinity. */
    fun softmax(scores: DoubleArray): DoubleArray {
        if (scores.isEmpty()) return scores
        val peak = scores.max()
        val out = DoubleArray(scores.size) { exp(scores[it] - peak) }
        val sum = out.sum()
        if (sum <= 0.0 || !sum.isFinite()) return DoubleArray(scores.size) { 1.0 / scores.size }
        for (i in out.indices) out[i] /= sum
        return out
    }

    /** Re-reads the weights whenever the model object underneath them has been replaced. */
    private fun sync() {
        val data = SuggestModel.data
        if (boundTo === data) return
        boundTo = data
        val stored = data.weights
        w = if (stored.size == COUNT && stored.all { it.isFinite() }) {
            DoubleArray(COUNT) { stored[it].coerceIn(MIN_WEIGHT, MAX_WEIGHT) }
        } else {
            DEFAULTS.copyOf()
        }
        persist()
    }

    /**
     * Writes the live weights back into the model, so the next save carries them.
     *
     * Under the model lock, because this clears and refills a list that the background writer walks while
     * serialising. Without it a training step landing during a save would leave the writer iterating a list
     * being emptied underneath it — which the save path catches and logs, but at the cost of silently losing
     * that write.
     */
    private fun persist() {
        SuggestModel.withLock { model ->
            model.weights.clear()
            w.forEach { model.weights.add(it) }
        }
    }

    /**
     * The step size.
     *
     * Small because the features are unnormalised log-odds that reach single digits, so a step of one is a
     * violent move; at this rate a consistently-chosen signal takes a few dozen picks to gain a point of
     * weight, which is about how long it takes a player to establish that they meant it.
     */
    private const val ETA = 0.02

    /**
     * The pull back towards [DEFAULTS], per step. Its reciprocal is the memory: roughly five hundred picks,
     * so a fortnight of unusual play bends the weights and a single odd evening does not.
     */
    private const val LAMBDA = 0.002

    private const val MIN_WEIGHT = -6.0
    private const val MAX_WEIGHT = 12.0
}
