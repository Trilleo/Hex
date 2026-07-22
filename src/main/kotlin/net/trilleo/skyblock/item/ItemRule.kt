package net.trilleo.skyblock.item

import java.util.*

/**
 * How an [ItemRule] identifies an item.
 *
 * Persisted by name, so constants may be appended but not renamed or reordered. A name this build does not
 * know deserializes to null and is repaired to [SKYBLOCK_ID] by the reading config's normalizer, which is
 * what lets a newer Hex's file load in an older one instead of failing.
 */
enum class ItemRuleKind {
    /** Match [ItemRule.value] against the Skyblock item id — every copy of that item. */
    SKYBLOCK_ID,

    /** Match [ItemRule.value] against the Skyblock item uuid — one specific item. */
    UUID,
}

/**
 * One entry in a user-curated item list: a [kind] saying which identity to compare, and the [value] to
 * compare it to.
 *
 * A deliberately plain class rather than a data class, so equality is identity — the editor screen holds rows
 * by reference to delete them, exactly as [net.trilleo.keybind.Keybind] does. It is also `var`-only with a
 * no-arg constructor because GSON instantiates it reflectively and never runs Kotlin's defaults.
 *
 * **Adding a third way to match is an enum constant and a [matches] branch, with no file migration.** That
 * falls out of the payload being one generic [value] string rather than a field per kind: an older build
 * reading a newer file sees an unknown [kind], normalizes it to [ItemRuleKind.SKYBLOCK_ID], and carries on.
 */
class ItemRule {
    var kind: ItemRuleKind = ItemRuleKind.SKYBLOCK_ID

    /**
     * The id or uuid to match, already case-folded to suit [kind] by the owning config's normalizer — so
     * matching is a plain `==` with no per-call case conversion on what may be a hot path.
     */
    var value: String = ""

    /**
     * The item's name as it read when the rule was created, shown beside the value in the editor. Purely
     * cosmetic and never matched on: a raw uuid says nothing to a human, and this is what makes such a row
     * readable.
     */
    var label: String = ""

    /**
     * Whether this rule matches an item with the given Skyblock [id] and [uuid], either of which may be null.
     * An empty [value] never matches, so a half-typed row in the editor cannot silently match everything.
     */
    fun matches(id: String?, uuid: String?): Boolean = when (kind) {
        ItemRuleKind.SKYBLOCK_ID -> value.isNotEmpty() && value == id
        ItemRuleKind.UUID -> value.isNotEmpty() && value == uuid
    }

    /** Case-folds [value] to the convention [kind] matches against. Called whenever either one changes. */
    fun normalizeValue() {
        value = when (kind) {
            ItemRuleKind.SKYBLOCK_ID -> value.trim().uppercase(Locale.ROOT)
            ItemRuleKind.UUID -> value.trim().lowercase(Locale.ROOT)
        }
    }
}
