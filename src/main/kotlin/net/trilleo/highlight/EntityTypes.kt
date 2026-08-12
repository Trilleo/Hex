package net.trilleo.highlight

import net.minecraft.core.registries.BuiltInRegistries

/**
 * Every entity id this client knows, for the editor's completion popup and nothing else.
 *
 * Read from the registry rather than written down, so the list is exactly what an `ENTITY_TYPE` rule can
 * actually match — a hardcoded list would drift from the game on the next update, and would silently omit
 * anything another mod registers.
 *
 * Cached on first use rather than at class init: the registry is only populated once the game has
 * bootstrapped, so reading it too early would produce an empty list and then never look again. The first ask
 * comes from a screen being opened, which is long past that.
 */
object EntityTypes {

    @Volatile
    private var cached: List<String>? = null

    /** Every registered entity id, sorted, e.g. `minecraft:zombie`. */
    fun ids(): List<String> {
        cached?.let { return it }
        // Sorted so walking the popup with the arrow keys is walking something with an order, and so two
        // clients offer the same list in the same sequence.
        val ids = BuiltInRegistries.ENTITY_TYPE.keySet().map { it.toString() }.sorted()
        cached = ids
        return ids
    }
}
