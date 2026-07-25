package net.trilleo.itemcustom

import net.trilleo.util.Chroma
import java.util.*

/**
 * Whether an item's enchantment glint is forced on, forced off, or left to vanilla.
 *
 * Three states rather than a boolean because "leave it alone" is a real answer and the common one: a plain
 * toggle would have to pick either "always glinting" or "never glinting" as its off position, and both are
 * wrong for an item the player only wanted to rename.
 *
 * Persisted by name, so constants may be appended but not renamed or reordered — a name this build does not
 * know deserializes to null and is repaired to [DEFAULT] by [ItemCustomizeConfig]'s normalizer, which is what
 * lets a newer Hex's file load in an older one instead of failing. Same contract as
 * [net.trilleo.skyblock.item.ItemRuleKind].
 */
enum class GlintOverride {
    /** Vanilla decides — the item glints exactly when Hypixel says it should. */
    DEFAULT,

    /** Always glint. */
    ON,

    /** Never glint. */
    OFF,
}

/**
 * How one specific Skyblock item should be drawn on this client, keyed by its instance [uuid].
 *
 * **Only the uuid identifies a customization, never the Skyblock id.** Hypixel writes a `uuid` for
 * non-stackable items alone, which is exactly the set of items worth customizing — and it is the only key that
 * can tell *your* Hyperion from every other one. Matching on the id would repaint every copy in the game, so a
 * stack with no uuid is refused at the point of capture rather than stored and silently ignored here.
 *
 * Every appearance field is "blank means untouched", so a customization that only renames an item leaves its
 * model, glint and colours entirely alone. That is what makes the whole thing composable: nothing is captured
 * from the item at edit time, so a customization keeps working after a reforge, a stat change, or a Hypixel
 * update that changes the item's stock appearance.
 *
 * A deliberately plain class rather than a data class, so equality is identity — the editor and the list screen
 * hold entries by reference to delete them, exactly as [net.trilleo.skyblock.item.ItemRule] and
 * [net.trilleo.keybind.Keybind] do. It is also `var`-only with a no-arg constructor because GSON instantiates
 * it reflectively and never runs Kotlin's defaults.
 */
class ItemCustomization {

    /**
     * The Skyblock item instance uuid this applies to, lowercased. The map key, and the only field that is
     * matched on; an entry whose uuid is blank is dropped at load, since it could never apply to anything.
     */
    var uuid: String = ""

    /**
     * The item's name as it read when the customization was created, shown in the list screen. Purely
     * cosmetic and never matched on: a raw uuid says nothing to a human, and this is what makes such a row
     * readable. Same role as [net.trilleo.skyblock.item.ItemRule.label].
     */
    var label: String = ""

    /**
     * Whether this customization applies. Nullable on purpose: GSON leaves an absent `boolean` at the JVM
     * default of `false`, so a hand-written entry that omits the key would load as *disabled*. Read it through
     * [active] rather than directly — the same asymmetry
     * [net.trilleo.hand.SwingItemSettings.enabled] documents.
     */
    var enabled: Boolean? = null

    /**
     * The replacement display name, with `&` colour codes (`&6`, `&l`) and `&z` for chroma. Blank keeps
     * Hypixel's own name, which is what makes a colour-only, chroma-only or glint-only customization possible.
     */
    var name: String = ""

    /**
     * `"#RRGGBB"` applied as the name's base colour, or blank to leave the name's own styling alone. Codes
     * inside [name] still win over this per segment, so it sets the colour of everything not coloured already.
     */
    var color: String = ""

    /**
     * Whether the whole name flows through the rainbow — the same thing `&z` does, without having to type it.
     *
     * A plain [Boolean] rather than the nullable [enabled] uses, because here GSON's default happens to be the
     * right one: a file that omits the key means chroma is off, and `false` is exactly that.
     */
    var chroma: Boolean = false

    /** Whether to force the enchantment glint. Null is repaired to [GlintOverride.DEFAULT] at load. */
    var glint: GlintOverride? = null

    /**
     * An item model id (`"minecraft:diamond_sword"`) to draw instead of the item's own, or blank for no
     * change. Any model this client has loaded works, resource-pack models included.
     */
    var model: String = ""

    /**
     * A player-head skin texture hash to draw, or blank for no change. Stored as the bare hash; the editor
     * accepts a full `textures.minecraft.net` URL or a base64 value blob and reduces it to this.
     */
    var texture: String = ""

    /** `"#RRGGBB"` for the dye colour of a dyeable model (leather armour), or blank for no change. */
    var dye: String = ""

    /** Whether this customization is switched on, treating an absent key as on. See [enabled]. */
    val active: Boolean get() = enabled != false

    /**
     * [glint], with a missing value read as [GlintOverride.DEFAULT].
     *
     * Read through this rather than the field directly. A null reaches here two ways — a file that omits the
     * key, and a freshly created entry, which starts at the field's own `null` — and both mean "vanilla
     * decides". Comparing the raw field against [GlintOverride.DEFAULT] would call a brand-new, entirely blank
     * customization a change to the item.
     */
    val glintOrDefault: GlintOverride get() = glint ?: GlintOverride.DEFAULT

    /**
     * Whether anything here would actually change how the item looks. An entry that is switched off, or whose
     * every field is blank, resolves to nothing — checked before any work is done on the render path, and used
     * by the list screen to mark an entry that has been created but not yet filled in.
     */
    fun hasEffect(): Boolean = active &&
        (name.isNotEmpty() ||
            color.isNotEmpty() ||
            chroma ||
            glintOrDefault != GlintOverride.DEFAULT ||
            model.isNotEmpty() ||
            texture.isNotEmpty() ||
            dye.isNotEmpty())

    /**
     * Whether this item's name changes colour over time, and so must be re-rendered every frame rather than
     * held in a cache until the config next changes.
     *
     * Deliberately a question about the *settings* rather than about a built name: the render path asks it
     * before deciding whether a cached value is still good, and would defeat the point if it had to build the
     * name to find out.
     */
    fun nameAnimates(): Boolean = active && Chroma.uses(name, chroma)

    /** Case-folds [uuid] to the convention Hypixel writes and [ItemCustomizeConfig] indexes on. */
    fun normalizeUuid() {
        uuid = uuid.trim().lowercase(Locale.ROOT)
    }
}
