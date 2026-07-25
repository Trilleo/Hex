package net.trilleo.itemcustom

import com.google.gson.reflect.TypeToken
import net.trilleo.config.ConfigHandle
import net.trilleo.config.ConfigRegistry
import net.trilleo.config.JsonConfig
import net.trilleo.itemcustom.ItemCustomizeConfig.byUuid
import net.trilleo.itemcustom.ItemCustomizeConfig.reindex
import net.trilleo.util.Chroma

/**
 * The item customization list, persisted at `config/hex/item_custom.json`.
 *
 * @property enabled the feature's master switch. Nullable on purpose: GSON leaves an absent `boolean` at the
 *   JVM default of `false`, so a hand-written file that omits the key would load as *disabled* — the opposite
 *   of what omitting a setting should mean. Read it through [ItemCustomizeConfig.active].
 * @property markSlots whether a customized item is marked with a `✎` in container GUIs, same nullability rule.
 * @property chromaSeconds how long one trip through the rainbow takes, for every chroma name.
 * @property chromaWidth how many characters one full rainbow spans.
 * @property items the customizations, in the order the list screen shows them.
 */
data class ItemCustomizeSettings(
    var enabled: Boolean? = null,
    var markSlots: Boolean? = null,
    // Shared by every customization rather than set per item: chroma is a look, and one item flowing at a
    // different rate from its neighbour reads as a glitch rather than a choice.
    var chromaSeconds: Double = Chroma.SECONDS_DEFAULT,
    var chromaWidth: Double = Chroma.WIDTH_DEFAULT,
    var items: MutableList<ItemCustomization> = mutableListOf(),
)

/**
 * Loads and holds the singleton [ItemCustomizeSettings], plus the two derived pieces the render path needs:
 * a uuid index and a change counter.
 *
 * **Registered as `global`**, unlike every other list this mod persists. Config profiles are loadouts — one
 * for the Dwarven Mines, one for slayers — while a customization describes an item that exists once on the
 * account. Capturing these into a profile would mean a player's item skins vanished and came back depending on
 * which loadout was active, which nobody wants and nobody would guess. They stay out of profile snapshots and
 * out of the clipboard blob for the same reason `update.json` does.
 *
 * ### Why the index and the counter exist
 *
 * Both consumers of this config sit on the render path — [net.trilleo.mixin.ItemStackMixin] on every
 * `getHoverName`, [net.trilleo.mixin.ItemModelResolverMixin] on every item drawn every frame. So:
 *
 *  - [byUuid] makes the lookup a hash probe rather than a scan of the list. With a couple of hundred
 *    customizations and a full inventory on screen, the difference is real.
 *  - [generation] is the cache-invalidation token. Every stack caches what it resolved to alongside the
 *    generation it resolved under, and re-resolves when they differ — so editing any field in the GUI updates
 *    every affected item on the very next frame without anything having to track *which* items were affected.
 *    It starts at 1 and never reaches 0, so a freshly allocated `ItemStack` (whose int field is zeroed) always
 *    reads as a miss.
 */
object ItemCustomizeConfig {
    private val config = JsonConfig(
        name = "item_custom",
        type = object : TypeToken<ItemCustomizeSettings>() {}.type,
        default = { ItemCustomizeSettings() },
        normalizer = ::normalize,
    )

    var settings: ItemCustomizeSettings = ItemCustomizeSettings()
        private set

    /** uuid → customization, rebuilt by [reindex]. Never exposed mutably; the list stays the source of truth. */
    private var byUuid: Map<String, ItemCustomization> = emptyMap()

    /**
     * Bumped on every change. Volatile because the render thread reads it while the GUI thread writes it; a
     * stale read costs one frame of an out-of-date appearance, which is why no stronger synchronisation is
     * warranted here.
     */
    @Volatile
    var generation: Int = 1
        private set

    /**
     * Fast "is there anything to do at all" gate, kept as a field so the render path answers it without
     * touching the map. False whenever the feature is off or no customization would change anything.
     */
    @Volatile
    var anyActive: Boolean = false
        private set

    /** Exposed so a reset can be offered; `global` keeps it out of profiles. */
    val handle = ConfigRegistry.register(
        ConfigHandle(
            config,
            adopt = { settings = it; reindex() },
            current = { settings },
            global = true,
        ),
    )

    /** Whether the feature is switched on, treating an absent key as on. See [ItemCustomizeSettings.enabled]. */
    val active: Boolean get() = settings.enabled != false

    /** Whether customized slots get a `✎` marker in container GUIs, treating an absent key as on. */
    val markSlots: Boolean get() = settings.markSlots != false

    /** Seconds for one full trip through the rainbow, for every chroma name. */
    val chromaSeconds: Double get() = settings.chromaSeconds

    /** Characters one full rainbow spans, for every chroma name. */
    val chromaWidth: Double get() = settings.chromaWidth

    /**
     * The customization for [uuid], or null when there is none.
     *
     * Returns the entry whether or not it would change anything — callers on the render path check
     * [anyActive] first and [ItemCustomization.hasEffect] after, while the GUI wants the entry regardless.
     */
    fun find(uuid: String): ItemCustomization? = byUuid[uuid]

    /**
     * The existing customization for [uuid], or a new blank one appended to the list.
     *
     * [label] is only written when the entry is created: it records what the item was called at capture time,
     * and overwriting it later would replace that with whatever the customization itself renamed the item to.
     */
    fun findOrCreate(uuid: String, label: String): ItemCustomization {
        find(uuid)?.let { return it }
        val created = ItemCustomization().apply {
            this.uuid = uuid
            this.label = label
        }
        created.normalizeUuid()
        settings.items += created
        save()
        return created
    }

    /** Removes [customization] and persists. Returns whether it was there to remove. */
    fun remove(customization: ItemCustomization): Boolean {
        if (!settings.items.remove(customization)) return false
        save()
        return true
    }

    /**
     * Repairs a loaded value.
     *
     * Every step covers a way GSON's reflective construction differs from Kotlin's: absent objects arrive
     * null, absent primitives arrive zeroed, and an enum whose name this build does not know arrives null
     * exactly like an absent one. Beyond that it drops entries that could never apply — a blank uuid matches
     * nothing — and collapses duplicates so the index cannot silently discard one of two entries claiming the
     * same item. Last wins, matching what the index would have done anyway.
     */
    private fun normalize(settings: ItemCustomizeSettings) {
        @Suppress("SENSELESS_COMPARISON")
        if (settings.items == null) settings.items = mutableListOf()

        // A `double` absent from the file arrives as 0.0, which for these two means a rainbow of no length
        // completed in no time — a divide by zero and a strobe. Non-positive therefore reads as "absent".
        settings.chromaSeconds = if (settings.chromaSeconds <= 0.0) {
            Chroma.SECONDS_DEFAULT
        } else {
            settings.chromaSeconds.coerceIn(Chroma.SECONDS_MIN, Chroma.SECONDS_MAX)
        }
        settings.chromaWidth = if (settings.chromaWidth <= 0.0) {
            Chroma.WIDTH_DEFAULT
        } else {
            settings.chromaWidth.coerceIn(Chroma.WIDTH_MIN, Chroma.WIDTH_MAX)
        }

        settings.items.forEach { item ->
            @Suppress("SENSELESS_COMPARISON")
            if (item.uuid == null) item.uuid = ""
            @Suppress("SENSELESS_COMPARISON")
            if (item.label == null) item.label = ""
            @Suppress("SENSELESS_COMPARISON")
            if (item.name == null) item.name = ""
            @Suppress("SENSELESS_COMPARISON")
            if (item.color == null) item.color = ""
            @Suppress("SENSELESS_COMPARISON")
            if (item.model == null) item.model = ""
            @Suppress("SENSELESS_COMPARISON")
            if (item.texture == null) item.texture = ""
            @Suppress("SENSELESS_COMPARISON")
            if (item.dye == null) item.dye = ""
            if (item.glint == null) item.glint = GlintOverride.DEFAULT
            item.normalizeUuid()
            item.model = item.model.trim()
            item.texture = ItemCustomizer.normalizeTexture(item.texture)
            item.color = item.color.trim()
            item.dye = item.dye.trim()
        }

        settings.items.removeAll { it.uuid.isEmpty() }

        // Walked back to front, so that when two entries claim one item it is the *later* one that survives —
        // the same entry the index would have kept — then flipped back into file order.
        val seen = HashSet<String>()
        settings.items = settings.items.asReversed()
            .filter { seen.add(it.uuid) }
            .asReversed()
            .toMutableList()
    }

    /**
     * Rebuilds [byUuid] and [anyActive] and bumps [generation].
     *
     * Called for every edit, including each keystroke in a text field. That is affordable because the list is
     * small and the alternative — working out which cached stacks a given edit invalidated — is the kind of
     * bookkeeping that goes wrong quietly.
     */
    private fun reindex() {
        byUuid = settings.items.associateBy { it.uuid }
        anyActive = active && settings.items.any { it.hasEffect() }
        generation = if (generation == Int.MAX_VALUE) 1 else generation + 1
    }

    fun load() = handle.loadInitial()

    /**
     * Re-indexes and writes immediately. Prefer [markDirty] from anything that fires repeatedly, such as a
     * text field responder.
     */
    fun save() {
        reindex()
        handle.saveNow()
    }

    /** Re-indexes now — so the change is visible this frame — and batches the write for about a second later. */
    fun markDirty() {
        reindex()
        handle.markDirty()
    }
}
