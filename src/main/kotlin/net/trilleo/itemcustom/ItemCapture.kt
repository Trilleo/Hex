package net.trilleo.itemcustom

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.trilleo.itemcustom.gui.ItemCustomizeScreen
import net.trilleo.skyblock.item.SkyblockItem
import net.trilleo.util.Notify

/**
 * Turns "this item, right here" into an open editor — the step every entry point shares.
 *
 * Both ways in end up here: the keybind pressed over a slot in a container GUI, and the management screen's
 * "add held item" button. Keeping the checks in one place is what makes them answer identically, and what
 * keeps the refusal messages honest — a stackable item and an empty slot fail for different reasons and say
 * so.
 *
 * **An item with no Skyblock uuid is refused rather than stored.** Only non-stackable items carry one, and it
 * is the only key that identifies a single item: an entry keyed on anything else would repaint every copy in
 * the game. Creating one and quietly never applying it would be worse than saying no.
 */
object ItemCapture {

    /**
     * Opens the editor for [stack], creating its customization if this is the first time. Returns whether it
     * opened; a refusal has already been reported in chat.
     *
     * The screen is opened through [Minecraft.execute] rather than directly, for the same reason the commands
     * do it: this can be called from a key handler inside another screen, and setting a screen while one is
     * being torn down loses the new one.
     */
    fun open(client: Minecraft, parent: Screen?, stack: ItemStack): Boolean {
        if (stack.isEmpty) {
            deny(client, "hex.item_custom.deny.no_item")
            return false
        }

        val uuid = ItemCustomLookup.skyblockUuidOf(stack)
        if (uuid == null) {
            deny(client, "hex.item_custom.deny.no_uuid")
            return false
        }

        // Read before the entry exists, so the captured label is Hypixel's own name rather than one an
        // existing customization already replaced.
        val label = SkyblockItem.displayName(stack)
        val customization = ItemCustomizeConfig.findOrCreate(uuid, label)
        val snapshot = stack.copy()
        client.execute { client.setScreen(ItemCustomizeScreen(parent, customization, snapshot)) }
        return true
    }

    /** Whether [stack] is something this feature can key a customization on. */
    fun customizable(stack: ItemStack): Boolean = uuidOf(stack) != null

    /** The Skyblock instance uuid of [stack], or null when it has none (or the stack is empty). */
    fun uuidOf(stack: ItemStack): String? =
        if (stack.isEmpty) null else ItemCustomLookup.skyblockUuidOf(stack)

    /** The main-hand stack, or an empty one when there is no player. */
    fun heldStack(client: Minecraft): ItemStack = client.player?.mainHandItem ?: ItemStack.EMPTY

    private fun deny(client: Minecraft, key: String) {
        Notify.chat(client, Component.translatable(key), ChatFormatting.RED)
        Notify.uiSound(client, PITCH_DENIED)
    }

    /** Matching the convention elsewhere in the mod: pitch alone says a keypress was refused. */
    private const val PITCH_DENIED = 0.7f
}
