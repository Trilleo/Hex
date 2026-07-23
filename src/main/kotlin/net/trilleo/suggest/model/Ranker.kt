package net.trilleo.suggest.model

import net.trilleo.suggest.SuggestConfig
import net.trilleo.suggest.context.ContextSnapshot
import net.trilleo.suggest.context.SessionMemory
import kotlin.math.ln

/**
 * One scored suggestion.
 *
 * [features] is kept rather than discarded after scoring because it is needed twice more: once by
 * [Weights.train] if this candidate is the one taken, and once by the "why" view if the player asks. Nine
 * doubles per shown candidate is a rounding error against keeping the model honest about its own arithmetic.
 */
class Candidate(
    /** The command line without its leading slash — what gets typed into the box if this is accepted. */
    val key: String,
    val features: DoubleArray,
    val score: Double,
) {
    val pinned: Boolean get() = features[Weights.PINNED] > 0.0

    /** The command word, for grouping in the popup. */
    val name: String get() = key.substringBefore(' ')
}

/** One row of the "why was this suggested" breakdown. */
class Term(val name: String, val value: Double, val weight: Double, val contribution: Double)

/** The full arithmetic behind one suggestion. */
class Explanation(
    val key: String,
    val score: Double,
    val probability: Double,
    val terms: List<Term>,
    /** Which context features pulled which way, from [NaiveBayes.contributions]. */
    val context: List<Pair<String, Double>>,
    /** A plain-English note about the routine term, or null when it had nothing to say. */
    val routine: String?,
)

/**
 * Turns what has been typed plus where the player is into a ranked list.
 *
 * **Retrieve, then rank.** Scoring a candidate fully means a naive-Bayes pass over seventeen context features,
 * which is four map lookups each — perfectly cheap for one candidate and about a millisecond for two thousand
 * of them, on a path that runs on every keystroke. So this does what every search system does: a cheap pass
 * over everything to find the plausible few dozen, then the expensive pass over only those. The cheap score
 * uses the two signals that need no context ([Fuzzy] and the raw count), which is enough to never lose a
 * candidate that had a real chance.
 *
 * The candidate pool is the union of three sources with quite different characters: what the player has
 * actually typed before, what the bundled catalogue knows Skyblock offers, and whatever the *server* has
 * suggested through vanilla's own completion. That last one is why this re-ranks rather than replaces —
 * Hypixel's command tree is the only source that knows about a command added last Tuesday, and folding it in
 * as candidates means it stays available and gets ordered by habit rather than alphabetically.
 */
object Ranker {

    /**
     * The catalogue's prior for a command line, from 0 to 1. Replaced by
     * [net.trilleo.suggest.model.CommandCatalog] once it has loaded.
     */
    @Volatile
    var cataloguePrior: (String) -> Double = { 0.0 }

    /** Every line the catalogue knows about. Also installed by the catalogue. */
    @Volatile
    var catalogueKeys: () -> Collection<String> = { emptyList() }

    /**
     * The best [limit] candidates for [typed], best first.
     *
     * @param typed the command line so far, without its leading slash. Blank means an empty chat box, which
     *   is the "what would I want next" surface rather than a completion.
     * @param extra candidate lines from outside the model — vanilla's own suggestions, folded in so the
     *   server's command tree is re-ranked rather than replaced.
     */
    fun rank(
        typed: String,
        context: ContextSnapshot,
        extra: Collection<String> = emptyList(),
        limit: Int,
        now: Long = System.currentTimeMillis(),
    ): List<Candidate> {
        if (limit <= 0) return emptyList()

        val shortlist = retrieve(typed, extra, now)
        if (shortlist.isEmpty()) return emptyList()

        return shortlist
            .map { key -> score(key, typed, context, now) }
            .sortedByDescending { it.score }
            .take(limit)
    }

    /**
     * The cheap first pass: everything that matches at all, cut to [SHORTLIST] by match quality and habit.
     *
     * The blend is deliberately crude — this decides only what gets *considered*, and the real ranking
     * happens over what survives. Weighting the match heavily here matters more than it looks: it is what
     * stops a player's twenty favourite commands from crowding out the one command they are actually
     * spelling out.
     */
    private fun retrieve(typed: String, extra: Collection<String>, now: Long): List<String> {
        val scored = ArrayList<Pair<String, Double>>(SHORTLIST * 2)

        fun consider(key: String, catalogue: Boolean) {
            if (key.isEmpty()) return
            val stat = SuggestModel.stat(key)
            if (stat?.blocked == true) return
            val match = Fuzzy.score(typed, key)
            if (match <= 0.0) return
            val habit = ln(1.0 + (stat?.count?.value(now, SuggestConfig.halfLifeMs) ?: 0.0))
            val bonus = if (stat?.pinned == true) PIN_SHORTLIST_BONUS else 0.0
            val seed = if (catalogue && stat == null) CATALOGUE_SHORTLIST_SEED else 0.0
            scored += key to (match * MATCH_SHORTLIST_WEIGHT + habit + bonus + seed)
        }

        SuggestModel.data.keys.keys.forEach { consider(it, catalogue = false) }
        if (SuggestConfig.cataloguePriors) {
            runCatching { catalogueKeys() }.getOrNull()?.forEach { consider(it, catalogue = true) }
        }
        extra.forEach { consider(it, catalogue = false) }

        if (scored.size <= SHORTLIST) return scored.map { it.first }.distinct()
        return scored.asSequence()
            .sortedByDescending { it.second }
            .map { it.first }
            .distinct()
            .take(SHORTLIST)
            .toList()
    }

    /** The full feature vector for one candidate, and its score. */
    private fun score(key: String, typed: String, context: ContextSnapshot, now: Long): Candidate {
        val features = features(key, typed, context, now)
        return Candidate(key, features, Weights.score(features))
    }

    private fun features(key: String, typed: String, context: ContextSnapshot, now: Long): DoubleArray {
        val stat = SuggestModel.stat(key)
        val features = DoubleArray(Weights.COUNT)

        features[Weights.PRIOR] = ln(1.0 + SuggestModel.keyCount(key, now))
        features[Weights.CONTEXT] = NaiveBayes.logOdds(key, context, now)
        features[Weights.MARKOV] = MarkovChain.logOdds(key, context, now)
        features[Weights.MATCH] = Fuzzy.score(typed, key)
        features[Weights.FAMILY] = ln(1.0 + SuggestModel.nameCount(key.substringBefore(' '), now))
        features[Weights.CATALOGUE] = if (SuggestConfig.cataloguePriors) {
            runCatching { cataloguePrior(key) }.getOrDefault(0.0).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        features[Weights.RECENCY] = recency(stat?.last ?: 0L, now)
        features[Weights.PINNED] = if (stat?.pinned == true) 1.0 else 0.0
        features[Weights.SESSION] = if (SessionMemory.used(key)) 1.0 else 0.0

        return features
    }

    /**
     * How recently this line was used, on a 0-to-1 scale.
     *
     * Hyperbolic rather than exponential, because the shape wanted here is "very sharp at first, then a long
     * tail": something used a minute ago is overwhelmingly likely to be wanted again, something used an hour
     * ago is mildly interesting, and beyond a day it stops mattering — at which point the decayed frequency
     * is the better description anyway. An exponential would fall off a cliff and take the middle ground with
     * it. Half at one hour, a twentieth at a day.
     */
    private fun recency(last: Long, now: Long): Double {
        if (last <= 0L || now < last) return 0.0
        val hours = (now - last).toDouble() / 3_600_000.0
        return 1.0 / (1.0 + hours)
    }

    /**
     * How confident the ranker is in its top pick, as a probability over the shown list.
     *
     * This is what the inline ghost text gates on. A softmax over scores is a calibrated-*enough* answer for
     * the question actually being asked — "is the leader clearly ahead of the field" — and it is the same
     * quantity [Weights.train] optimises, so the number the player sees is the number the model is learning
     * to be right about.
     */
    fun confidence(ranked: List<Candidate>): Double {
        if (ranked.isEmpty()) return 0.0
        if (ranked.size == 1) return 1.0
        return Weights.softmax(DoubleArray(ranked.size) { ranked[it].score }).max()
    }

    /** The full arithmetic behind one candidate, for `/hexa suggest why` and the dashboard. */
    fun explain(candidate: Candidate, ranked: List<Candidate>, context: ContextSnapshot, now: Long): Explanation {
        val contributions = Weights.contributions(candidate.features)
        val weights = Weights.current()
        val terms = (0 until Weights.COUNT).map { i ->
            Term(Weights.NAMES[i], candidate.features[i], weights[i], contributions[i])
        }
        val probabilities = Weights.softmax(DoubleArray(ranked.size) { ranked[it].score })
        val index = ranked.indexOfFirst { it.key == candidate.key }

        return Explanation(
            key = candidate.key,
            score = candidate.score,
            probability = if (index >= 0) probabilities[index] else 0.0,
            terms = terms.sortedByDescending { kotlin.math.abs(it.contribution) },
            context = NaiveBayes.contributions(candidate.key, context, now),
            routine = MarkovChain.explain(candidate.key, context, now),
        )
    }

    /**
     * How many candidates survive the cheap pass.
     *
     * Forty rather than the five or so that get shown, because the expensive pass exists precisely to
     * reorder — a candidate that the crude blend ranks thirtieth and the full model ranks first is the case
     * this whole design is for, and a shortlist of ten would throw it away before it was ever looked at.
     */
    private const val SHORTLIST = 40

    /** Mirrors [Weights.DEFAULTS]'s emphasis on the match, for the same reason. */
    private const val MATCH_SHORTLIST_WEIGHT = 3.0

    /** Enough to get an unused catalogue entry looked at properly, not enough to beat a real habit. */
    private const val CATALOGUE_SHORTLIST_SEED = 0.5

    private const val PIN_SHORTLIST_BONUS = 10.0
}
