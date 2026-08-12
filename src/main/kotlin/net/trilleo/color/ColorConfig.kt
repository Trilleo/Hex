package net.trilleo.color

import com.google.gson.reflect.TypeToken
import net.trilleo.config.ConfigHandle
import net.trilleo.config.ConfigRegistry
import net.trilleo.config.JsonConfig

/**
 * The colour picker's own memory, persisted at `config/hex/colors.json`.
 *
 * @property recent colours chosen anywhere in the mod, most recent first, capped at [ColorConfig.MAX_RECENT].
 */
data class ColorSettings(
    var recent: MutableList<String> = mutableListOf(),
)

/**
 * Holds the colours the player has picked lately, shared by every colour setting in the mod.
 *
 * The point is that it is *one* list. A region drawn to match a chat highlight, an item renamed in the same
 * shade as the glow on the mob that drops it — those are the ordinary reasons to open the picker twice, and
 * without a shared list the second visit means finding the colour again from the digits.
 *
 * **Registered as `global`**, so profiles neither capture nor restore it. A recent-colour list is a record of
 * what the player has been doing, not a setting: captured in a profile it would be rolled back on every switch,
 * and the list would forget colours chosen since the snapshot for no reason a player could explain. Same
 * reasoning [net.trilleo.update.UpdateConfig] gives for its own `global`.
 *
 * Not a [net.trilleo.feature.Feature] — there is nothing to switch on and no settings tab — so
 * [net.trilleo.feature.Features.bootstrap] loads it alongside
 * [net.trilleo.config.VanillaKeysConfig], the other config that belongs to the mod rather than to a feature.
 */
object ColorConfig {

    /** How many colours are remembered. One row of swatches in the picker, which is what makes it scannable. */
    const val MAX_RECENT: Int = 12

    private val config = JsonConfig(
        name = "colors",
        type = object : TypeToken<ColorSettings>() {}.type,
        default = { ColorSettings() },
        normalizer = ::normalize,
    )

    var settings: ColorSettings = ColorSettings()
        private set

    val handle = ConfigRegistry.register(
        ConfigHandle(config, adopt = { settings = it }, current = { settings }, global = true),
    )

    fun load() = handle.loadInitial()

    /** The remembered colours, most recent first. */
    val recent: List<String> get() = settings.recent

    /**
     * Records that [spec] was chosen.
     *
     * Chroma and "no colour" are deliberately not remembered: neither is a colour that would be hard to find
     * again — both are a single button in the picker — so keeping them would only push real colours off the
     * end of the list.
     *
     * Re-picking a colour already in the list moves it to the front rather than adding a second copy, so the
     * row stays twelve *different* colours however often one of them is used.
     */
    fun remember(spec: String) {
        if (ColorValue.isChroma(spec) || ColorValue.isNone(spec)) return
        val value = ColorValue.parse(spec)?.let { ColorValue.format(it, alpha = false) } ?: return
        val list = settings.recent
        list.removeAll { it.equals(value, ignoreCase = true) }
        list.add(0, value)
        while (list.size > MAX_RECENT) list.removeAt(list.size - 1)
        handle.markDirty()
    }

    /** Forgets every remembered colour. Reachable from the picker, because a shared list wants a way back. */
    fun clearRecent() {
        if (settings.recent.isEmpty()) return
        settings.recent.clear()
        handle.saveNow()
    }

    /**
     * Repairs a loaded value: GSON leaves an absent list null, and a hand-edited file can hold anything at
     * all. Entries that are not colours are dropped rather than kept as dead swatches.
     */
    private fun normalize(settings: ColorSettings) {
        @Suppress("SENSELESS_COMPARISON")
        if (settings.recent == null) settings.recent = mutableListOf()
        val cleaned = settings.recent
            .mapNotNull { spec -> ColorValue.parse(spec)?.let { ColorValue.format(it, alpha = false) } }
            .distinct()
            .take(MAX_RECENT)
        settings.recent = cleaned.toMutableList()
    }
}
