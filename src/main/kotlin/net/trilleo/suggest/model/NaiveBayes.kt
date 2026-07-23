package net.trilleo.suggest.model

import net.trilleo.suggest.context.ContextSnapshot
import kotlin.math.ln

/**
 * How much more likely a command is *here* than it is in general.
 *
 * Naive Bayes is the right tool for this despite its reputation, for a reason specific to the problem: the
 * model has to be useful after twenty commands, not after twenty thousand. Anything with parameters to fit —
 * a tree, a factorisation, an embedding — spends its first several hundred observations being worse than
 * counting, and several hundred commands is most of a week's play. Counting is useful from the third one.
 *
 * The independence assumption is of course false here (island and area cell are almost the same fact stated
 * twice, and hotbar and armour move together). What saves it is that the output is only ever used as *one
 * feature of a ranking*, never as a probability anybody acts on. Correlated features double-count, the sum
 * comes out too confident, and then [Ranker]'s learned weight scales the whole thing down to whatever it is
 * actually worth for this player. The naivety is absorbed downstream by design rather than ignored.
 *
 * **The PMI form is deliberate.** This returns `Σ log( P(v|k) / P(v) )` rather than `log P(k) + Σ log P(v|k)`.
 * The two rank identically — they differ by a term constant across candidates — but the ratio form has the
 * property the dashboard needs: a feature that carries no information about a command contributes *zero*
 * rather than some large negative number that happens to be equally large for everyone. That is what makes
 * "why was this suggested" a readable list instead of a column of noise.
 */
object NaiveBayes {

    /**
     * The total context log-odds for [key], the ranker's `context` feature.
     *
     * Zero means "the context says nothing about this command", which is both the honest answer for a command
     * with no history and the neutral value for the ranker.
     */
    fun logOdds(key: String, ctx: ContextSnapshot, now: Long): Double {
        var sum = 0.0
        for (feature in ContextSnapshot.ALL) {
            sum += term(key, feature, ctx[feature], now)
        }
        return sum * shrinkage(SuggestModel.keyCount(key, now))
    }

    /**
     * Every feature's contribution, largest first, for the "why was this suggested" view.
     *
     * Recomputed rather than cached from [logOdds]: this runs when a player opens one screen, and keeping the
     * fourteen intermediate terms alive for every candidate of every keystroke to save that would be a poor
     * trade.
     */
    fun contributions(key: String, ctx: ContextSnapshot, now: Long): List<Pair<String, Double>> {
        val scale = shrinkage(SuggestModel.keyCount(key, now))
        return ContextSnapshot.ALL
            .map { feature -> feature to term(key, feature, ctx[feature], now) * scale }
            .filter { kotlin.math.abs(it.second) > NEGLIGIBLE }
            .sortedByDescending { kotlin.math.abs(it.second) }
    }

    /**
     * One feature's log-odds: how much more often this command runs when this feature holds this value than
     * the value occurs at all.
     *
     * Both sides are Laplace-smoothed over the feature's observed vocabulary, which is what keeps a value seen
     * once from producing an infinite ratio. The clamp on top is not smoothing but insurance: a single sparse
     * feature must not be able to swamp the other thirteen, and without a bound the first command ever run in
     * a brand-new area would look like the most contextually certain thing in the model.
     */
    private fun term(key: String, feature: String, value: String, now: Long): Double {
        val vocabulary = SuggestModel.vocabulary(feature)
        val alphaV = ALPHA * vocabulary

        val givenKey = (SuggestModel.jointCount(key, feature, value, now) + ALPHA) /
            (SuggestModel.keyCount(key, now) + alphaV)
        val overall = (SuggestModel.marginalCount(feature, value, now) + ALPHA) /
            (SuggestModel.total(now) + alphaV)

        if (givenKey <= 0.0 || overall <= 0.0) return 0.0
        return ln(givenKey / overall).coerceIn(-MAX_TERM, MAX_TERM)
    }

    /**
     * How much of the context opinion to believe, given how much evidence there is behind it.
     *
     * A command run once has exactly one observation of every context feature, so its "context profile" is
     * indistinguishable from a coincidence — it was run *somewhere*, and every feature therefore correlates
     * with it perfectly. Shrinking towards zero by `n / (n + k)` is the standard empirical-Bayes answer and
     * it is worth more here than anywhere else in the model, because without it a command typed once by
     * accident in the hub would be the hub's most contextually confident suggestion forever.
     *
     * At the default [PRIOR_STRENGTH] a command seen once is believed at a quarter, five times at 62%, and
     * twenty times at 87%.
     */
    private fun shrinkage(observations: Double): Double =
        observations / (observations + PRIOR_STRENGTH)

    /** Laplace's one. */
    private const val ALPHA = 1.0

    /** About a 20:1 odds ratio — generous for real evidence, a hard ceiling on a coincidence. */
    private const val MAX_TERM = 3.0

    private const val PRIOR_STRENGTH = 3.0

    private const val NEGLIGIBLE = 0.01
}
