package net.trilleo.sound

import net.minecraft.core.registries.BuiltInRegistries

/**
 * Every sound id this client knows, for the picker's list and the completion popup.
 *
 * Read from the registry rather than written down, for the reasons [net.trilleo.highlight.EntityTypes] gives:
 * a hardcoded list would drift from the game on the next update and would silently omit anything another mod
 * registers. Cached on first use rather than at class init, because the registry is only populated once the
 * game has bootstrapped — the first ask comes from a screen opening, which is long past that.
 */
object SoundIds {

    @Volatile
    private var cached: List<String>? = null

    @Volatile
    private var groupedCache: Map<String, List<String>>? = null

    /** Every registered sound id, sorted, e.g. `minecraft:block.note_block.pling`. */
    fun ids(): List<String> {
        cached?.let { return it }
        // Sorted so walking the list with the arrow keys is walking something with an order, and so two
        // clients offer the same list in the same sequence.
        val ids = BuiltInRegistries.SOUND_EVENT.keySet().map { it.toString() }.sorted()
        cached = ids
        return ids
    }

    /**
     * The same ids, bucketed by the leading segment of their path — `block`, `entity`, `ui`, `music`, and so
     * on — so the picker can offer something browsable to someone who does not know what they are looking
     * for. Vanilla has about a dozen buckets; a modded namespace becomes one bucket of its own, because its
     * paths follow nobody's convention and splitting them further would produce noise.
     *
     * Keys are sorted, and so is each bucket, for the same reason [ids] is.
     */
    fun groups(): Map<String, List<String>> {
        groupedCache?.let { return it }
        val grouped = ids()
            .groupBy { id ->
                val namespace = id.substringBefore(':', "")
                val path = id.substringAfter(':')
                if (namespace == VANILLA_NAMESPACE) path.substringBefore('.') else namespace
            }
            .toSortedMap()
        groupedCache = grouped
        return grouped
    }

    /** The bucket names, in the order the picker's filter cycles through them. */
    fun groupNames(): List<String> = groups().keys.toList()

    /**
     * The short form of [id] for a label: the path without its namespace, e.g. `block.note_block.pling`.
     *
     * A modded id keeps its namespace, because there the namespace is the only thing distinguishing it from a
     * vanilla sound with the same path.
     */
    fun shortName(id: String): String {
        val trimmed = id.trim()
        return if (trimmed.startsWith(VANILLA_PREFIX)) trimmed.removePrefix(VANILLA_PREFIX) else trimmed
    }

    /** The last segment of [id], e.g. `pling` — as much as fits on a clip in the timeline. */
    fun tinyName(id: String): String = id.trim().substringAfterLast('.').ifBlank { id.trim() }

    /** Drops both caches. For a resource reload, which can register sounds a pack brings with it. */
    fun invalidate() {
        cached = null
        groupedCache = null
    }

    private const val VANILLA_NAMESPACE: String = "minecraft"
    private const val VANILLA_PREFIX: String = "$VANILLA_NAMESPACE:"
}
