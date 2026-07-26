package net.trilleo.notebook

import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.trilleo.notebook.NoteIcon.DEFAULT

/**
 * Turning a note's icon id into something drawable.
 *
 * A note's icon is stored as a plain item id string rather than an enum of blessed choices, so the whole item
 * registry — including anything a resource pack or another mod adds — is available without this having to know
 * about any of it. The cost is that the id can be wrong, which [resolve] answers with the default book rather
 * than with nothing: an icon column that is sometimes blank reads as a rendering bug, while a book that is not
 * the one you asked for reads as a typo you can go and fix.
 *
 * What is cached is the *holder*, not a stack. Resolving happens per visible row per frame, so the registry
 * parse and lookup are worth keeping; the [ItemStack] itself is not, because a stack snapshots the item's
 * components at construction and those are bound per world — see [resolve].
 */
object NoteIcon {

    /** The icon a note gets when it names none, and the fallback for one that names something unknown. */
    private val DEFAULT: Holder.Reference<Item> = Items.WRITABLE_BOOK.builtInRegistryHolder()

    private val cache = HashMap<String, Holder.Reference<Item>>()

    /**
     * The stack to draw for [id], or null when there is nothing safe to draw yet.
     *
     * Item components are data-driven, so they are bound when a world's data packs load and are *unbound*
     * before that — on the title screen, which the notebook can be opened from. Constructing an [ItemStack]
     * there throws (`Components not bound yet`), and inside a screen's extract pass that throw is a client
     * crash, so this reports "no icon" instead and the row simply leaves the slot empty. The next time the
     * screen is opened in a world the icons are back.
     *
     * A fresh stack per call rather than a cached one for the same reason: a stack copies the item's
     * components as they were bound when it was built, so one kept across a world change would be drawn with
     * another world's data.
     */
    fun resolve(id: String): ItemStack? {
        val holder = holderFor(id)
        if (!holder.areComponentsBound()) return null
        return ItemStack(holder)
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

    /** The holder [id] names, or [DEFAULT] when it is blank, malformed, or names nothing this client has. */
    private fun holderFor(id: String): Holder.Reference<Item> {
        val trimmed = id.trim()
        if (trimmed.isEmpty()) return DEFAULT
        return cache.getOrPut(trimmed) {
            val parsed = Identifier.tryParse(trimmed) ?: return@getOrPut DEFAULT
            BuiltInRegistries.ITEM.get(parsed).orElse(DEFAULT)
        }
    }
}
