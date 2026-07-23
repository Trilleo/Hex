package net.trilleo.suggest.model

/**
 * How well what the player has typed matches a candidate line.
 *
 * The one signal here that is **not** learned, and that is the point: it is the only thing in the ranker that
 * expresses what the player has said out loud rather than what they have done in the past. Habit is allowed
 * to reorder the things that match; it is never allowed to promote something that does not.
 *
 * Scoring is by tier rather than by an edit distance. Tiers are cheap, they are stable — a candidate cannot
 * drift between ranks because one character changed somewhere else — and above all they are *explicable*: the
 * "why" view can say "you typed the start of this" instead of quoting a similarity coefficient nobody can
 * check. The gaps between tiers are what the ranker's learned weight scales, so how much a better match is
 * worth relative to a stronger habit remains something the player teaches it.
 */
object Fuzzy {

    /** Exactly what was typed. */
    private const val EXACT = 1.0

    /** The candidate begins with what was typed — the ordinary completion case. */
    private const val PREFIX = 0.95

    /** Some later word of the candidate begins with it: `dun` finding `warp dungeon_hub`. */
    private const val WORD_PREFIX = 0.7

    /** It appears somewhere inside, not at a word boundary. */
    private const val CONTAINS = 0.55

    /** The characters appear in order but not together. Scaled by how tightly packed they are. */
    private const val SUBSEQUENCE_BASE = 0.2
    private const val SUBSEQUENCE_SPAN = 0.25

    /** Characters that begin a new word in a command line. */
    private val WORD_BREAKS = charArrayOf(' ', '_', '-', '.', ':')

    /**
     * How well [candidate] matches [typed], from 0 (not at all) to 1 (exactly).
     *
     * A zero is a veto, not a low score: [Ranker] drops those candidates before scoring rather than letting a
     * strong enough habit outweigh them. Blank input matches everything equally, which is what makes the
     * empty-chat-box surface rank on context alone.
     *
     * Case-insensitive throughout, and done with the `ignoreCase` overloads rather than by folding the
     * strings: this runs against every candidate on every keystroke, and lower-casing both sides each time
     * would allocate a few thousand short-lived strings per character typed. Case matters to the *result* —
     * a player name has to be suggested as it is spelled — which is why the candidate is never folded, only
     * compared loosely.
     */
    fun score(typed: String, candidate: String): Double {
        if (typed.isEmpty()) return EXACT
        if (candidate.length < typed.length) return 0.0
        if (candidate.equals(typed, ignoreCase = true)) return EXACT
        if (candidate.startsWith(typed, ignoreCase = true)) return PREFIX

        val at = candidate.indexOf(typed, ignoreCase = true)
        if (at > 0) {
            return if (candidate[at - 1] in WORD_BREAKS) WORD_PREFIX else CONTAINS
        }

        return subsequence(typed, candidate)
    }

    /**
     * The score for [typed]'s characters appearing in [candidate] in order but apart, or 0 if they do not.
     *
     * Weighted by how much of the candidate the match had to span: `hb` finding `hub` at the front is a far
     * better guess than `hb` finding the `h` of `hub` and the `b` of `bazaar` eleven characters later, and
     * without that distinction this tier would match almost everything against almost everything.
     */
    private fun subsequence(typed: String, candidate: String): Double {
        var t = 0
        var first = -1
        var last = -1
        for (c in candidate.indices) {
            if (!candidate[c].equals(typed[t], ignoreCase = true)) continue
            if (first < 0) first = c
            last = c
            if (++t == typed.length) break
        }
        if (t < typed.length) return 0.0

        val span = (last - first + 1).coerceAtLeast(typed.length)
        val density = typed.length.toDouble() / span.toDouble()
        return SUBSEQUENCE_BASE + SUBSEQUENCE_SPAN * density
    }

    /**
     * The part of [candidate] that would be appended to complete [typed], or null when it is not a prefix.
     *
     * The inline ghost text can only ever show a *suffix* — it is drawn after the cursor, so a candidate that
     * does not literally continue what was typed cannot be rendered that way, however well it scores. This is
     * the test for that, kept next to the scoring it has to agree with.
     */
    fun completionOf(typed: String, candidate: String): String? {
        if (!candidate.startsWith(typed, ignoreCase = true) || candidate.length <= typed.length) return null
        return candidate.substring(typed.length)
    }
}
