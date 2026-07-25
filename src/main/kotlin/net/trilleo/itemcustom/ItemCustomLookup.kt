package net.trilleo.itemcustom

import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.trilleo.duck.HexItemStack
import net.trilleo.skyblock.item.SkyblockItem
import net.trilleo.util.Chroma

/**
 * The render path's entry point into item customization: everything the mixins call, and the only place the
 * per-stack cache described by [HexItemStack] is read or written.
 *
 * Split out from [ItemCustomizer] on purpose. That object is pure — give it a customization and a stack and it
 * hands back a name or a copy — while this one is all about *not* doing that work: it owns the gates, the
 * lookups and the caching that keep a hook running dozens of times a frame down to a couple of field reads.
 *
 * Every method here must be cheap in the common case, because the common case is a vanilla item on a client
 * that has never customized anything. That case costs one volatile boolean read.
 */
object ItemCustomLookup {

    /** Latched on a stack that carries no Skyblock uuid, so the tag is never read for it twice. */
    private const val NO_UUID = ""

    /**
     * The name to show for [stack] in place of [original], or null to leave vanilla's answer alone.
     *
     * [original] is what `getHoverName` was about to return. It is passed in rather than read back off the
     * stack because this runs *inside* that method — asking the stack for its name here would recurse.
     *
     * The cache is keyed on two things, because a name goes stale two ways. The config generation covers an
     * edit in the GUI. The [Chroma] frame covers a chroma name, whose colours change with the clock while the
     * config sits still; a name that does not flow is stamped [Chroma.STATIC] and never expires on time. That
     * distinction is what keeps chroma from costing every *other* item a rebuild every frame — and it still
     * pays off for a chroma item, since a single hover asks for the name several times over (the tooltip's
     * first line, the slot label, the hotbar popup) and only the first of them does any work.
     */
    fun nameFor(stack: ItemStack, original: Component): Component? {
        if (!ItemCustomizeConfig.anyActive) return null
        val duck = duckOf(stack)

        val generation = ItemCustomizeConfig.generation
        if (duck.hexNameGeneration() == generation) {
            val stamp = duck.hexNameFrame()
            if (stamp == Chroma.STATIC || stamp == Chroma.frame()) return duck.hexCustomName()
        }

        val customization = ItemCustomizer.customizationFor(uuidOf(stack, duck))
        val name = customization?.let { ItemCustomizer.nameFor(it, original) }
        val stamp = if (customization != null && customization.nameAnimates()) Chroma.frame() else Chroma.STATIC
        duck.hexSetCustomName(generation, stamp, name)
        return name
    }

    /**
     * The stack to draw in place of [stack] — a customized copy when one applies, otherwise [stack] itself.
     *
     * Returning the original rather than null keeps the mixin that calls this a one-liner, and means the
     * uncustomized case allocates nothing at all.
     *
     * The copy is cached and reused until the next config edit, so components that change under a live stack
     * without replacing it — durability, most obviously — are frozen in the copy as of the last resolve. That
     * is a fair trade here: nothing on Hypixel drives an item's *model* from those components, and the
     * alternative is rebuilding the copy every frame for every drawn item.
     */
    fun renderStack(stack: ItemStack): ItemStack {
        if (!ItemCustomizeConfig.anyActive) return stack
        val duck = duckOf(stack)

        val generation = ItemCustomizeConfig.generation
        if (duck.hexRenderGeneration() == generation) return duck.hexRenderStack() ?: stack

        val customization = ItemCustomizer.customizationFor(uuidOf(stack, duck))
        val custom = customization?.let { ItemCustomizer.renderStackFor(it, stack) }
        duck.hexSetRenderStack(generation, custom)
        return custom ?: stack
    }

    /**
     * The customization that applies to [stack], or null when there is none. For the GUI, which wants the
     * entry itself rather than what it renders to — and which is off the render path, so it reads through the
     * same latch without any of the generation bookkeeping.
     */
    fun customizationFor(stack: ItemStack): ItemCustomization? {
        val uuid = skyblockUuidOf(stack) ?: return null
        return ItemCustomizeConfig.find(uuid)
    }

    /**
     * This stack's Skyblock instance uuid, or null when it has none — the test for whether an item can be
     * customized at all. Public for the capture path, which has to refuse a stackable item by name rather than
     * silently creating an entry that could never match.
     */
    fun skyblockUuidOf(stack: ItemStack): String? = uuidOf(stack, duckOf(stack))

    /**
     * The mixin-installed scratch space on [stack].
     *
     * Routed through `Any` because the compiler cannot see the interface being added: `ItemStack` implements
     * [HexItemStack] only after mixin has applied [net.trilleo.mixin.ItemStackMixin] at load time, so a direct
     * cast reads as impossible at compile time and is fine at run time. Every duck cast in the feature goes
     * through here, so the explanation lives in one place rather than at each call site.
     */
    private fun duckOf(stack: ItemStack): HexItemStack = stack as Any as HexItemStack

    /**
     * Reads the uuid through the per-stack latch, filling it on the first ask.
     *
     * This is the one place [SkyblockItem.attributes] is called from anything render-adjacent, and it is
     * called at most once per stack instance for the life of that instance — see [HexItemStack] for why that
     * is exact rather than approximate.
     */
    private fun uuidOf(stack: ItemStack, duck: HexItemStack): String? {
        duck.hexSkyblockUuid()?.let { return it.takeIf { cached -> cached.isNotEmpty() } }

        val uuid = SkyblockItem.attributes(stack)?.let(SkyblockItem::uuidIn)
        duck.hexSetSkyblockUuid(uuid ?: NO_UUID)
        return uuid
    }
}
