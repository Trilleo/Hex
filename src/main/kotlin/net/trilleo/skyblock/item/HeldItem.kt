package net.trilleo.skyblock.item

import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack

/**
 * The Skyblock identity of whatever is in the local player's main hand, re-read at most once per tick.
 *
 * This exists because its consumers sit on the render path. `ItemInHandRendererMixin.hex$suppressSwing` runs
 * once per frame per hand, and [SkyblockItem.extraAttributes] deep-copies the item's tag — asking it directly
 * would mean a fresh `CompoundTag` several hundred times a second, all to answer a question whose answer
 * changes when the player switches slots.
 *
 * **Invalidation is [ItemStack] reference identity, and that is exact rather than approximate here.**
 * Everything that can change the two attributes this caches hands back a *different* stack object: switching
 * hotbar slot returns another slot's instance, and a server inventory update calls `Inventory.setItem` with a
 * freshly deserialized one. The mutations that do happen in place — `shrink`, durability damage — cannot
 * change `ExtraAttributes.id` or `.uuid`. So a reference compare is the whole check, and it costs nothing.
 *
 * **Staleness.** [tick] runs on `END_CLIENT_TICK`, which fires after hotbar input and `player.tick()`, so a
 * slot change made this tick is cached before the next frame draws. A server packet landing between ticks can
 * leave this up to one tick (50 ms, roughly three frames) behind. That is acceptable precisely because the
 * only consumer is cosmetic; anything that must act on the current instant calls [refresh] instead.
 *
 * Ticked from the `END_CLIENT_TICK` block in [net.trilleo.feature.Features], next to `ProfileAutoSwitch.tick`
 * and outside the per-feature `enabled` check. It began life ticked by [net.trilleo.hand.HandFeature], its
 * only consumer then; the reminder engine is the second, so ownership moved here rather than leaving a shared
 * cache at the mercy of one feature's master switch.
 */
object HeldItem {

    /** The held item's Skyblock id, uppercased, or null when it has none. */
    @Volatile
    var id: String? = null
        private set

    /** The held item's Skyblock uuid, lowercased, or null when it has none (any stackable item). */
    @Volatile
    var uuid: String? = null
        private set

    /** Whether the main hand holds anything at all — distinct from "holds something Skyblock knows". */
    @Volatile
    var present: Boolean = false
        private set

    /**
     * The stack the current values were read from, kept purely as an identity key. Never read for data:
     * holding a reference to a live stack and querying it later would reintroduce the cost this class exists
     * to avoid, and would read through mutations the cache deliberately ignores.
     */
    private var cachedStack: ItemStack? = null

    /** Re-reads only when the main hand holds a different stack object than last time. */
    fun tick(client: Minecraft) {
        val stack = client.player?.mainHandItem
        if (stack == null) {
            reset()
            return
        }
        if (stack === cachedStack) return
        read(stack)
    }

    /**
     * Re-reads unconditionally, ignoring the identity gate. For a keypress, which must act on what is in hand
     * at that instant rather than on what the last tick saw; costs one tag copy.
     */
    fun refresh(client: Minecraft) {
        val stack = client.player?.mainHandItem
        if (stack == null) reset() else read(stack)
    }

    /** Forgets everything, so an id cannot survive into the next world or server. */
    fun reset() {
        cachedStack = null
        id = null
        uuid = null
        present = false
    }

    private fun read(stack: ItemStack) {
        cachedStack = stack
        present = !stack.isEmpty
        val extra = SkyblockItem.extraAttributes(stack)
        if (extra == null) {
            id = null
            uuid = null
            return
        }
        // One copy of the tag, both fields off it — see SkyblockItem's note on cost.
        id = SkyblockItem.idIn(extra)
        uuid = SkyblockItem.uuidIn(extra)
    }
}
