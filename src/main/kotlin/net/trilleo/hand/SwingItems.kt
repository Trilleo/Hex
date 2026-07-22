package net.trilleo.hand

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.trilleo.skyblock.item.HeldItem
import net.trilleo.skyblock.item.ItemRule
import net.trilleo.skyblock.item.ItemRuleKind
import net.trilleo.skyblock.item.SkyblockItem
import net.trilleo.util.Notify

/**
 * Decides whether the item in the main hand is one whose swing animation should be hidden, and owns the
 * add/remove flow the keybind, the command and the editor's "add held item" button all share.
 *
 * Deliberately **not** gated on [net.trilleo.skyblock.SkyblockLocation.onSkyblock]. A rule can only match an
 * item that already carries an `ExtraAttributes` id or uuid, and nothing outside Skyblock writes those — so
 * the NBT is both a stronger and a cheaper signal than the scoreboard parse. Gating on the location reader as
 * well would inherit its weaknesses for nothing: it is null for the first seconds after joining, so the swing
 * would visibly flick back on at every login and server transfer, and it reads false whenever Hypixel changes
 * the sidebar layout.
 *
 * Note the suppression is not per-hand. `ItemInHandRendererMixin.hex$suppressSwing` modifies a
 * `swingProgress` argument with no view of which [net.minecraft.world.InteractionHand] it belongs to, so a
 * main-hand match quiets the off hand too. The global `disable_swing` setting already behaves that way, so
 * this is consistent rather than a gap worth threading the hand through the mixin to close.
 */
object SwingItems {

    /**
     * Whether the held item matches a rule.
     *
     * Called from the render path via [HandState.shouldSuppressSwing], so it early-outs three times before
     * walking the list: the common case of "switched on, no rules" is one boolean and one emptiness check.
     * The values it compares are [HeldItem]'s cached ones — no NBT is read here.
     */
    fun suppressesHeldItem(): Boolean {
        if (!SwingItemsConfig.active) return false
        val rules = SwingItemsConfig.settings.rules
        if (rules.isEmpty()) return false
        val id = HeldItem.id
        val uuid = HeldItem.uuid
        if (id == null && uuid == null) return false
        return rules.any { it.matches(id, uuid) }
    }

    /**
     * Adds the held item to the list if it is absent, removes it if present, and reports which in chat.
     *
     * Removal wins when the item matches, and it removes **every** rule that matches rather than the first.
     * With a broad id rule and a narrow uuid rule both covering the same item, taking only one away would
     * leave the swing still hidden and make the keypress look broken.
     */
    fun toggleHeld(client: Minecraft) {
        // Act on what is in hand at this instant, not on what the last tick happened to see.
        HeldItem.refresh(client)

        if (!HeldItem.present) {
            deny(client, "Hold an item to toggle its swing.")
            return
        }

        val id = HeldItem.id
        val uuid = HeldItem.uuid
        if (id == null && uuid == null) {
            // Honest for both "this is a vanilla item" and "you are not on Skyblock" without guessing which.
            deny(client, "That is not a Skyblock item — it has no ID Hex can match on.")
            return
        }

        val name = heldName(client)
        val rules = SwingItemsConfig.settings.rules
        if (rules.removeAll { it.matches(id, uuid) }) {
            SwingItemsConfig.save()
            Notify.chat(client, "Removed $name — swing restored.")
            Notify.uiSound(client, PITCH_REMOVED)
            return
        }

        rules.add(ruleFor(id, uuid, name))
        SwingItemsConfig.save()
        Notify.chat(client, "Added $name — swing hidden.")
        Notify.uiSound(client, PITCH_ADDED)
    }

    /**
     * The add-only half of [toggleHeld], for the editor's button — where a press that silently deleted the
     * row you were trying to create would be the wrong reading of "add". Returns whether anything was added.
     */
    fun addHeld(client: Minecraft): Boolean {
        HeldItem.refresh(client)
        val id = HeldItem.id
        val uuid = HeldItem.uuid
        if (!HeldItem.present || (id == null && uuid == null)) return false

        val rules = SwingItemsConfig.settings.rules
        if (rules.any { it.matches(id, uuid) }) return false

        rules.add(ruleFor(id, uuid, heldName(client)))
        SwingItemsConfig.save()
        return true
    }

    /**
     * A rule for the held item, preferring the uuid when there is one.
     *
     * Only unique, non-stackable items carry a uuid, and someone pressing the key while holding *that* sword
     * means that sword rather than every copy of it. A stackable has no uuid at all, so its id is the only
     * key available and the fallback is not a compromise.
     */
    private fun ruleFor(id: String?, uuid: String?, name: String): ItemRule = ItemRule().apply {
        if (uuid != null) {
            kind = ItemRuleKind.UUID
            value = uuid
        } else {
            kind = ItemRuleKind.SKYBLOCK_ID
            value = id.orEmpty()
        }
        label = name
    }

    private fun heldName(client: Minecraft): String =
        client.player?.mainHandItem?.let(SkyblockItem::displayName).orEmpty()

    private fun deny(client: Minecraft, message: String) {
        Notify.chat(client, message, ChatFormatting.RED)
        Notify.uiSound(client, PITCH_DENIED)
    }

    // Matching AttackModeFeature's convention: pitch alone tells you which way a toggle went, so the chat
    // line is confirmation rather than the only signal.
    private const val PITCH_ADDED = 1.4f
    private const val PITCH_REMOVED = 0.8f
    private const val PITCH_DENIED = 0.7f
}
