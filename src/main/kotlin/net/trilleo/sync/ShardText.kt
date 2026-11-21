package net.trilleo.sync

import java.util.*

/** The two Skyblock menus the sync reads. */
enum class ShardMenu {
    /** `/attributemenu`: one item per attribute, its level in the item name. */
    ATTRIBUTES,

    /** `/huntingbox`: one item per held shard type, its count in the lore. */
    HUNTING_BOX,
}

/** One page of a [ShardMenu], from a title such as `(2/3) Attribute Menu`. A title with no counter is 1 of 1. */
data class MenuPage(val menu: ShardMenu, val page: Int, val pages: Int)

/**
 * The pure text half of reading the two shard menus: titles, item names and lore lines in, shard names and
 * numbers out. Free of Minecraft types so every pattern here is tested without a running game.
 *
 * Every input is a line already passed through [net.trilleo.util.TextClean.strip].
 *
 * **Why shards are read by name.** Neither menu's items carry a `SHARD_…` item ID: the Attribute Menu's
 * items carry none at all, and the Hunting Box's carry the generic `ATTRIBUTE_SHARD`. So each item is
 * identified by the shard's *name* — the Attribute Menu's `Source: Drowned Shard (R18)` lore line, and the
 * Hunting Box's item name — and [ShardCatalog] turns the name into the ID with the site's own shard list.
 */
object ShardText {

    private val PAGE_TITLE = Regex("""^(?:\((\d+)/(\d+)\) )?(Attribute Menu|Hunting Box)$""")

    /** `Source: Drowned Shard (R18)` — the shard an attribute comes from, and its rarity-and-number tag. */
    private val SOURCE = Regex("""^Source: (.+?) Shard \(\w\d+\)$""")

    /** An attribute item's name: the attribute, then its level as a Roman numeral when above 0. */
    private val NAME_TIER = Regex("""^(.+?)(?: ([IVXL]+))?$""")

    /** `Owned: 1,729 Shards` on a Hunting Box item. */
    private val OWNED = Regex("""^Owned: ([\d,]+) Shards?$""")

    /** Shown on the Hunting Box's search item while a search narrows what it lists. */
    private const val QUERY_PREFIX = "Query: "

    /** Attribute levels run from 0 to 10. */
    const val MAX_LEVEL: Int = 10

    /** The API's cap on one Hunting Box count. */
    const val MAX_COUNT: Int = 1_000_000

    /** Which shard menu page [title] names, or null for any other screen. */
    fun menuPage(title: String): MenuPage? {
        val m = PAGE_TITLE.matchEntire(title.trim()) ?: return null
        val menu = if (m.groupValues[3] == "Attribute Menu") ShardMenu.ATTRIBUTES else ShardMenu.HUNTING_BOX
        val page = m.groupValues[1].toIntOrNull() ?: 1
        val pages = m.groupValues[2].toIntOrNull() ?: 1
        if (page < 1 || pages < page) return null
        return MenuPage(menu, page, pages)
    }

    /**
     * The shard name and attribute level of an Attribute Menu item, or null for an item that is not an
     * attribute (a border pane, a button). An attribute not yet unlocked has no numeral and reads as level 0.
     */
    fun attributeEntry(name: String, lore: List<String>): Pair<String, Int>? {
        val shard = lore.firstNotNullOfOrNull { SOURCE.matchEntire(it.trim())?.groupValues?.get(1) }
            ?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val numeral = NAME_TIER.matchEntire(name.trim())?.groupValues?.get(2).orEmpty()
        val level = if (numeral.isEmpty()) 0 else roman(numeral) ?: return null
        return shard to level.coerceIn(0, MAX_LEVEL)
    }

    /** The shard name and held count of a Hunting Box item, or null for anything else in the menu. */
    fun heldEntry(name: String, lore: List<String>): Pair<String, Int>? {
        val owned = lore.firstNotNullOfOrNull { OWNED.matchEntire(it.trim())?.groupValues?.get(1) } ?: return null
        val count = owned.replace(",", "").toIntOrNull() ?: return null
        val shard = name.trim().removeSuffix(" Shards").removeSuffix(" Shard").trim()
        if (shard.isEmpty()) return null
        return shard to count.coerceIn(0, MAX_COUNT)
    }

    /** Whether an item's lore shows a search narrowing the menu. */
    fun isSearchActive(lore: List<String>): Boolean = lore.any { it.trim().startsWith(QUERY_PREFIX) }

    /**
     * Whether a chest slot is one of the Hunting Box's shard slots: the middle seven columns of rows two to
     * five. The edges hold navigation, the search and decoration.
     */
    fun isContentSlot(slot: Int): Boolean {
        if (slot !in 9..44) return false
        val column = slot % 9
        return column != 0 && column != 8
    }

    /** A Roman numeral from I to L, or null when [text] is not one. */
    fun roman(text: String): Int? {
        if (text.isEmpty()) return null
        var total = 0
        var previous = 0
        for (ch in text.reversed()) {
            val value = when (ch) {
                'I' -> 1
                'V' -> 5
                'X' -> 10
                'L' -> 50
                else -> return null
            }
            total += if (value < previous) -value else value
            previous = maxOf(previous, value)
        }
        return total.takeIf { it > 0 }
    }

    /** The lookup key for a shard name: lowercase, single-spaced. */
    fun nameKey(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    /**
     * The item ID a shard name most likely has when the site's list does not know it: `Voracious Spider`
     * becomes `SHARD_VORACIOUS_SPIDER`. Only a fallback — a name that Hypixel spells differently from its ID
     * comes out wrong, and the site then reports it as unknown rather than storing it.
     */
    fun fallbackId(name: String): String {
        val body = name.trim().uppercase(Locale.ROOT)
            .replace(Regex("[\\s-]+"), "_")
            .replace(Regex("[^A-Z0-9_]"), "")
        return "SHARD_$body"
    }
}
