package net.trilleo.skyblock.item

import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import java.util.*

/**
 * Reads Hypixel Skyblock's own metadata off an [ItemStack].
 *
 * Skyblock ships every item with its own metadata inside vanilla's custom-data component. Two keys identify
 * an item, and they answer different questions:
 *
 *  - `id` — the item *type* (`"HYPERION"`). Every copy in the game shares it.
 *  - `uuid` — the item *instance*. Only non-stackable items carry one, so a stack of enchanted redstone has
 *    an `id` and no `uuid`, while your particular Hyperion has both.
 *
 * Nothing outside Skyblock writes those, which makes their presence a cheaper and more reliable
 * "is this a Skyblock item" test than anything a client can read off the scoreboard.
 *
 * **Where they live.** On the 1.8 item format Hypixel nested everything under an `ExtraAttributes` compound
 * inside the item tag. Since Hypixel began serving the component-based format natively, the same keys sit at
 * the **root** of `minecraft:custom_data` instead — which is why Skyblocker's current `ItemUtils` reads
 * `customData.getString("id")` with no `ExtraAttributes` step anywhere in the file. Reading only the nested
 * compound made every item on a modern server look like a vanilla one. [attributes] therefore returns
 * whichever of the two layouts the stack actually uses, so both a live server and an old inventory dump work.
 *
 * **On cost.** [net.minecraft.world.item.component.CustomData.copyTag] deep-copies the whole tag on every
 * call, and a Skyblock item's tag is not small — `enchantments` alone can hold dozens of entries. That is why
 * this object splits into stack-level and tag-level accessors: a caller that wants both the id and the uuid
 * reads [attributes] **once** and then calls [idIn]/[uuidIn] on the result. [idOf] and [uuidOf] are
 * conveniences for the genuinely single-field case and cost one copy each. Anything on a per-frame path
 * should read [HeldItem] instead of calling in here at all.
 */
object SkyblockItem {

    /** The compound Skyblock's metadata used to be nested under, back on the 1.8 item format. */
    private const val EXTRA_ATTRIBUTES = "ExtraAttributes"

    private const val KEY_ID = "id"
    private const val KEY_UUID = "uuid"

    /**
     * The compound carrying this stack's Skyblock attributes, or null when it has none — a vanilla item, an
     * item from another server, or an empty stack.
     *
     * The nested `ExtraAttributes` wins when present because a stack that has one was written by the legacy
     * format, where the root holds unrelated bookkeeping; the root is the modern layout and the common case.
     */
    fun attributes(stack: ItemStack): CompoundTag? {
        if (stack.isEmpty) return null
        // Checked before copyTag so an item with an empty component never pays for a copy.
        val custom = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        if (custom.isEmpty) return null
        val root = custom.copyTag()
        val nested = root.getCompoundOrEmpty(EXTRA_ATTRIBUTES)
        if (!nested.isEmpty) return nested
        // Not "is the root non-empty": plenty of non-Skyblock items carry custom data of their own.
        return root.takeIf { identifies(it) }
    }

    /** Whether [tag] carries either of the keys that make an item a Skyblock one. */
    private fun identifies(tag: CompoundTag): Boolean =
        stringIn(tag, KEY_ID) != null || stringIn(tag, KEY_UUID) != null

    /**
     * A string attribute out of an already-read [extra] compound, or null when absent or blank.
     *
     * Backs [idIn] and [uuidIn], and is public so a future consumer can reach the keys they do not cover —
     * `"modifier"` (the reforge), `"petInfo"`, `"rarity_upgrades"` — without another accessor being added
     * here for each one.
     */
    fun stringIn(extra: CompoundTag, key: String): String? =
        extra.getStringOr(key, "").trim().takeIf { it.isNotEmpty() }

    /**
     * The Skyblock item id in [extra] (`"HYPERION"`), uppercased so it compares equal to a hand-typed value.
     * Hypixel already writes these uppercase; the fold exists for the config file's benefit, not the game's.
     */
    fun idIn(extra: CompoundTag): String? = stringIn(extra, KEY_ID)?.uppercase(Locale.ROOT)

    /** The Skyblock item instance uuid in [extra], lowercased for the same reason as [idIn]. */
    fun uuidIn(extra: CompoundTag): String? = stringIn(extra, KEY_UUID)?.lowercase(Locale.ROOT)

    /** This stack's Skyblock id. Prefer [attributes] + [idIn] when the uuid is wanted as well. */
    fun idOf(stack: ItemStack): String? = attributes(stack)?.let(::idIn)

    /** This stack's Skyblock uuid. Prefer [attributes] + [uuidIn] when the id is wanted as well. */
    fun uuidOf(stack: ItemStack): String? = attributes(stack)?.let(::uuidIn)

    /**
     * The item's name with Hypixel's `§` colour codes removed, for a GUI row or a chat line.
     *
     * Deliberately not [net.trilleo.skyblock.SkyblockLocation]'s `clean`: that one also strips spaces, which
     * is right for comparing scoreboard lines and wrong here — it would render "Aspect of the Dragon" as
     * "AspectoftheDragon".
     */
    fun displayName(stack: ItemStack): String = strip(stack.hoverName.string)

    /** Removes `§x` pairs and nothing else. */
    private fun strip(raw: String): String {
        if (raw.indexOf('§') < 0) return raw.trim()
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            if (raw[i] == '§') {
                i += 2 // skip the marker and the code character after it
                continue
            }
            out.append(raw[i])
            i++
        }
        return out.toString().trim()
    }
}
