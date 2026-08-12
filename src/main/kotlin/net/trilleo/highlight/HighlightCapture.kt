package net.trilleo.highlight

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.trilleo.highlight.model.Highlight
import net.trilleo.highlight.model.HighlightMatch
import net.trilleo.skyblock.SkyblockLocation
import java.util.*

/**
 * Builds a rule from an entity the player is pointing at.
 *
 * The counterpart of `/hexa region here`: writing a rule by hand means knowing how Hypixel spells a mob's name,
 * including whichever of its decorations are stable, and that is exactly what a player does not know before
 * they have one to look at. Reading it off the world removes the guess entirely.
 */
object HighlightCapture {

    /**
     * A rule for whatever the player's crosshair is on, or null when it is on nothing.
     *
     * The rule is not added to the config — the caller decides that, because the keybind adds it and the
     * editor's **Pick** button only refills the rule already open.
     */
    fun fromCrosshair(client: Minecraft): Highlight? {
        val entity = client.crosshairPickEntity ?: return null
        return from(client, entity)
    }

    /**
     * A rule matching [entity].
     *
     * **Prefers the name over the type when the entity has one**, because a name is what the player was
     * pointing at: an entity worth highlighting individually is usually one Hypixel has named, and a rule for
     * `minecraft:zombie` on an island made of zombies is not what anyone meant. The kind is a one-click change
     * in the editor that opens straight afterwards, so guessing wrong costs nothing.
     *
     * The island is captured too, for the same reason a region's is — except that here it is a convenience
     * rather than a correctness requirement, and the editor shows it as a field to clear.
     */
    fun from(client: Minecraft, entity: Entity): Highlight {
        val level = client.level
        val tag = level?.let { HighlightTracker.tagOf(it, entity) }
        val typeId = HighlightTracker.typeIdOf(entity)

        return Highlight().apply {
            id = UUID.randomUUID().toString()
            if (tag != null) {
                kind = HighlightMatch.NAME_CONTAINS
                value = tag
            } else {
                kind = HighlightMatch.ENTITY_TYPE
                value = typeId
            }
            // The name is the rule's own label, so it starts as what the rule is about; the normalizer folds it
            // and adds a suffix if another rule already holds it.
            name = HighlightConfig.uniqueName(tag ?: shortNameOf(typeId))
            island = SkyblockLocation.current.orEmpty()
        }
    }

    /** `minecraft:cave_spider` → `cave_spider`, so a type-matched rule is not named after a namespace. */
    private fun shortNameOf(typeId: String): String = typeId.substringAfter(':').ifBlank { typeId }
}
