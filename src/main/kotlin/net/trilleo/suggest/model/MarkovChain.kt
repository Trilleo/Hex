package net.trilleo.suggest.model

import net.trilleo.suggest.SuggestConfig
import net.trilleo.suggest.context.ContextSnapshot
import kotlin.math.ln

/**
 * What usually comes next, given the last two commands.
 *
 * **Only order 2 lives here.** Order 1 — "what follows `/warp hub`" — is already in the model as an ordinary
 * context feature, [ContextSnapshot.PREV1], and naive Bayes scores it alongside island and held item without
 * any special machinery. Duplicating it would be double-counting the same evidence under two names.
 *
 * What Bayes genuinely cannot express is the *joint*. It treats "you just ran `/warp crimson_isle`" and
 * "before that you ran `/pv`" as two independent pieces of evidence and multiplies them, when the entire
 * content of a routine is that the pair means something neither half does. `/warp dungeon_hub` after
 * `/party accept` is a run about to start; the same warp after `/is` is somebody wandering. Storing the pair
 * as its own context is the cheapest possible way to know the difference, and it is the reason this file
 * exists at all rather than being one more entry in [ContextSnapshot.ALL].
 *
 * The cost of that specificity is sparsity — most pairs are seen once or never — which is what [shrinkage]
 * and the back-off to silence are for.
 */
object MarkovChain {

    /**
     * How much the last two commands favour [key], as log-odds against its unconditional frequency.
     *
     * Zero whenever there is nothing to say: fewer than two commands this session, a pair never seen before,
     * or too little evidence behind the pair to be worth acting on. Backing off to silence rather than to the
     * order-1 estimate is deliberate — order 1 is already being scored by [NaiveBayes], so falling back to it
     * here would weight it twice for exactly the players whose routines are least established.
     */
    fun logOdds(key: String, ctx: ContextSnapshot, now: Long): Double {
        val prev1 = ctx[ContextSnapshot.PREV1]
        val prev2 = ctx[ContextSnapshot.PREV2]
        if (prev1 == ContextSnapshot.UNKNOWN || prev2 == ContextSnapshot.UNKNOWN) return 0.0

        val transitions = SuggestModel.transitions(prev2, prev1) ?: return 0.0
        val halfLife = SuggestConfig.halfLifeMs

        var observed = 0.0
        var following = 0.0
        transitions.forEach { (next, counter) ->
            val weight = counter.value(now, halfLife)
            observed += weight
            if (next == key) following = weight
        }
        if (observed <= 0.0) return 0.0

        val vocabulary = transitions.size.coerceAtLeast(1)
        val conditional = (following + ALPHA) / (observed + ALPHA * vocabulary)

        val total = SuggestModel.total(now)
        val keys = SuggestModel.data.keys.size.coerceAtLeast(1)
        val unconditional = (SuggestModel.keyCount(key, now) + ALPHA) / (total + ALPHA * keys)
        if (conditional <= 0.0 || unconditional <= 0.0) return 0.0

        return ln(conditional / unconditional).coerceIn(-MAX_TERM, MAX_TERM) * shrinkage(observed)
    }

    /** A one-line explanation of this term for the "why" view, or null when it had nothing to say. */
    fun explain(key: String, ctx: ContextSnapshot, now: Long): String? {
        val prev1 = ctx[ContextSnapshot.PREV1]
        val prev2 = ctx[ContextSnapshot.PREV2]
        if (prev1 == ContextSnapshot.UNKNOWN || prev2 == ContextSnapshot.UNKNOWN) return null
        val transitions = SuggestModel.transitions(prev2, prev1) ?: return null
        if (transitions[key] == null) return null
        return "after /$prev2 then /$prev1"
    }

    /** See [NaiveBayes]; the same reasoning, and the same need, only more acute because pairs are rarer. */
    private fun shrinkage(observations: Double): Double =
        observations / (observations + PRIOR_STRENGTH)

    private const val ALPHA = 1.0

    private const val MAX_TERM = 3.0

    /**
     * Lower than [NaiveBayes]'s, because a transition observed twice is much stronger evidence than a context
     * value observed twice: the context features are things that happen to be true, and a transition is
     * something the player did on purpose, in order, more than once.
     */
    private const val PRIOR_STRENGTH = 2.0
}
