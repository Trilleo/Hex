package net.trilleo.notebook

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Turning a note's icon id into something drawable.
 *
 * A note's icon is stored as a plain item id string rather than an enum of blessed choices, so the whole item
 * registry — including anything a resource pack or another mod adds — is available without this having to know
 * about any of it. The cost is that the id can be wrong, which [resolve] answers with the default book rather
 * than with nothing: an icon column that is sometimes blank reads as a rendering bug, while a book that is not
 * the one you asked for reads as a typo you can go and fix.
 *
 * Resolution is cached because it happens per visible row per frame, and a registry lookup plus an [ItemStack]
 * allocation for a list that is not changing is pure waste.
 */
object NoteIcon {

    /** The icon a note gets when it names none, and the fallback for one that names something unknown. */
    val DEFAULT: ItemStack = ItemStack(Items.WRITABLE_BOOK)

    private val cache = HashMap<String, ItemStack>()

    /** The stack to draw for [id], or [DEFAULT] when it is blank or names nothing this client has. */
    fun resolve(id: String): ItemStack {
        val trimmed = id.trim()
        if (trimmed.isEmpty()) return DEFAULT
        return cache.getOrPut(trimmed) {
            val parsed = Identifier.tryParse(trimmed) ?: return@getOrPut DEFAULT
            val item = BuiltInRegistries.ITEM.getOptional(parsed).orElse(null) ?: return@getOrPut DEFAULT
            ItemStack(item)
        }
    }

    /**
     * Whether [id] names an item this client has loaded. Used to warn in the meta editor.
     *
     * Blank counts as known: leaving the field empty is how a note asks for the default, not a mistake.
     */
    fun known(id: String): Boolean {
        val trimmed = id.trim()
        if (trimmed.isEmpty()) return true
        val parsed = Identifier.tryParse(trimmed) ?: return false
        return BuiltInRegistries.ITEM.getOptional(parsed).isPresent
    }
}
