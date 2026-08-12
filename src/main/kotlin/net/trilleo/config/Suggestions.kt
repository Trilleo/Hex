package net.trilleo.config

import java.util.*

/**
 * Ranks a field's possible values against what has been typed so far, for the completion popup
 * [net.trilleo.config.gui.ConfigEntryList] draws on a [TextEntry] that offers one.
 *
 * Deliberately free of any GUI type, like the rest of this package: it says which candidates are worth
 * offering and in what order, not how they are drawn.
 *
 * **Three tiers, in the order a player expects to see them.** The values this is built for are namespaced ids
 * such as `minecraft:zombie`, and nobody types the namespace — they type `zomb`. So a match on the part after
 * the colon comes first, a match on the whole id second, and a match anywhere inside it last. Without the
 * third tier, looking for `cave_spider` by typing `spider` would find nothing at all, which reads as a field
 * that has no completion rather than one that disagrees about where the word starts.
 */
object Suggestions {

    /**
     * How many candidates are ever returned.
     *
     * The popup shows a handful at a time and the rest are reached with the arrow keys, so this is about how
     * far walking the list is worth going, not about what fits on screen. An empty field offers the first
     * [LIMIT] of everything, which is what makes the vocabulary browsable before you know what is in it.
     */
    const val LIMIT: Int = 64

    /** [candidates] worth offering for [typed], best first, at most [limit] of them. */
    fun rank(candidates: List<String>, typed: String, limit: Int = LIMIT): List<String> {
        if (candidates.isEmpty()) return emptyList()

        val needle = typed.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return candidates.take(limit)

        val pathPrefix = ArrayList<String>()
        val idPrefix = ArrayList<String>()
        val anywhere = ArrayList<String>()

        candidates.forEach { candidate ->
            // A candidate lands in exactly one tier — the first that accepts it — so the concatenation below
            // cannot repeat one.
            when {
                candidate.substringAfter(':').startsWith(needle) -> pathPrefix
                candidate.startsWith(needle) -> idPrefix
                candidate.contains(needle) -> anywhere
                else -> null
            }?.add(candidate)
        }

        return (pathPrefix + idPrefix + anywhere).take(limit)
    }
}
