package net.trilleo.hand

import com.google.gson.reflect.TypeToken
import net.trilleo.config.ConfigHandle
import net.trilleo.config.ConfigRegistry
import net.trilleo.config.JsonConfig
import net.trilleo.skyblock.item.ItemRule
import net.trilleo.skyblock.item.ItemRuleKind

/**
 * The per-item swing list, persisted at `config/hex/swing_items.json`.
 *
 * @property enabled the list's own master switch. Nullable on purpose: GSON leaves an absent `boolean` at the
 *   JVM default of `false`, so a hand-written file that omits the key would load as *disabled* — the opposite
 *   of what omitting a setting should mean. A nullable [Boolean] makes "absent" distinguishable from
 *   "explicitly false"; read it through [SwingItemsConfig.active] rather than directly.
 * @property rules the items whose swing is hidden, in the order the editor shows them.
 */
data class SwingItemSettings(
    var enabled: Boolean? = null,
    var rules: MutableList<ItemRule> = mutableListOf(),
)

/**
 * Loads and holds the singleton [SwingItemSettings]. Call [load] once at feature init.
 *
 * Kept in its own file rather than folded into [HandConfig] so that the Hand tab's "reset tab" button
 * restores the sliders without also destroying a hand-curated item list — the same separation the Keybinds
 * tab has. Registering with [ConfigRegistry] means the list joins config profiles and clipboard
 * export/import at no extra cost.
 */
object SwingItemsConfig {
    private val config = JsonConfig(
        name = "swing_items",
        type = object : TypeToken<SwingItemSettings>() {}.type,
        default = { SwingItemSettings() },
        normalizer = ::normalize,
    )

    var settings: SwingItemSettings = SwingItemSettings()
        private set

    /** Exposed so a reset can be offered, and so profiles can snapshot this config. */
    val handle = ConfigRegistry.register(
        ConfigHandle(config, adopt = { settings = it }, current = { settings }),
    )

    /**
     * Whether the list is switched on, treating an absent key as on. See [SwingItemSettings.enabled] for why
     * that asymmetry exists.
     */
    val active: Boolean get() = settings.enabled != false

    /**
     * Repairs a loaded value.
     *
     * This is the whole of the file's hand-edit safety, and every step covers a way GSON's reflective
     * construction differs from Kotlin's: absent objects arrive null, absent primitives arrive zeroed, and an
     * enum whose name this build does not know arrives null exactly like an absent one. Beyond that it folds
     * case per kind, so a hand-typed `hyperion` still matches and [ItemRule.matches] can stay a plain `==`,
     * and drops valueless rules, which can never match and would otherwise show up as an invisible row.
     */
    private fun normalize(settings: SwingItemSettings) {
        @Suppress("SENSELESS_COMPARISON")
        if (settings.rules == null) settings.rules = mutableListOf()

        settings.rules.forEach { rule ->
            @Suppress("SENSELESS_COMPARISON")
            if (rule.kind == null) rule.kind = ItemRuleKind.SKYBLOCK_ID
            @Suppress("SENSELESS_COMPARISON")
            if (rule.value == null) rule.value = ""
            @Suppress("SENSELESS_COMPARISON")
            if (rule.label == null) rule.label = ""
            rule.normalizeValue()
        }

        settings.rules.removeAll { it.value.isEmpty() }
    }

    fun load() = handle.loadInitial()

    /** Writes immediately. Prefer [markDirty] from anything that fires repeatedly, such as a text field. */
    fun save() = handle.saveNow()

    /** Records that the list changed; the write is batched and lands about a second later. */
    fun markDirty() = handle.markDirty()
}
