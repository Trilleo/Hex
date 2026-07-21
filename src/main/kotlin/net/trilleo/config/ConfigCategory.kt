package net.trilleo.config

import net.minecraft.client.gui.screens.Screen
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import java.util.Locale

/**
 * A named group of [ConfigEntry] rows — one tab in the `/hexa config` menu. A [net.trilleo.feature.Feature]
 * contributes one by overriding [net.trilleo.feature.Feature.settingsCategory]; the menu collects every
 * enabled feature's category into the sidebar automatically.
 *
 * Labels are translation keys rather than literal English, derived from the category [id] and each entry's
 * key so call sites never repeat a prefix:
 * ```
 * hex.config.category.<id>            the tab title
 * hex.config.<id>.<key>               an entry's label
 * hex.config.<id>.<key>.tooltip       its tooltip, if the lang file defines one
 * ```
 * Build one with the [build] DSL rather than the constructor:
 * ```
 * ConfigCategory.build("updates") {
 *     toggle("enabled", default = true,
 *            get = { UpdateConfig.settings.enabled },
 *            set = { UpdateConfig.settings.enabled = it; UpdateConfig.save() })
 *     action("check_now") { UpdateFeature.checkNow() }
 * }
 * ```
 *
 * @param id stable identifier for the category, e.g. `"updates"`; also the translation-key prefix.
 * @param title the tab label shown in the sidebar.
 * @param entries the rows, in display order.
 */
class ConfigCategory(
    val id: String,
    val title: Component,
    val entries: List<ConfigEntry>,
) {
    companion object {
        /** Build a category with the [Builder] DSL. The title comes from `hex.config.category.<id>`. */
        fun build(id: String, block: Builder.() -> Unit): ConfigCategory =
            ConfigCategory(id, Component.translatable("hex.config.category.$id"), Builder(id).apply(block).entries)
    }

    /**
     * Collects [ConfigEntry] rows for [build]; one call per row.
     *
     * Every method takes the entry's key, not its text. Tooltips are implicit: a row gets one exactly when
     * the language file defines `<label key>.tooltip`, so adding help text is a lang-file edit with no code
     * change. Overloads taking a [Component] outright exist for genuinely dynamic labels, such as a saved
     * profile's name, which have no translation key.
     */
    class Builder(private val categoryId: String) {
        val entries: MutableList<ConfigEntry> = mutableListOf()

        /** The translation key for an entry, e.g. `hex.config.hand.offset_x`. */
        private fun keyOf(key: String) = "hex.config.$categoryId.$key"

        private fun label(key: String): Component = Component.translatable(keyOf(key))

        /**
         * The tooltip for an entry, or null when the language file has no `.tooltip` key for it.
         * [Component.translatable] renders the raw key when a translation is missing, so absence has to be
         * detected up front rather than discovered as a stray `hex.config.…` string on screen.
         */
        private fun tooltip(key: String): Component? {
            val tooltipKey = "${keyOf(key)}.tooltip"
            return if (Language.getInstance().has(tooltipKey)) Component.translatable(tooltipKey) else null
        }

        /** Add a boolean toggle. [set] runs on each flip, so persist (or mark dirty) there. */
        fun toggle(key: String, default: Boolean, get: () -> Boolean, set: (Boolean) -> Unit) {
            entries += BooleanEntry(label(key), tooltip(key), default, get, set)
        }

        /** Add an action button. [onClick] receives the live [Screen] for opening sub-screens. */
        fun action(key: String, onClick: (Screen) -> Unit) {
            entries += ActionEntry(label(key), tooltip(key), onClick)
        }

        /** Add an action button whose label is computed rather than translated. */
        fun action(label: Component, tooltip: Component? = null, onClick: (Screen) -> Unit) {
            entries += ActionEntry(label, tooltip, onClick)
        }

        /**
         * Add a multiple-choice cycler over pre-rendered [options]. Prefer [enum] for a fixed set of
         * choices; this exists for dynamic ones such as saved profile names.
         */
        fun cycle(
            label: Component,
            tooltip: Component? = null,
            options: List<Component>,
            default: Int,
            get: () -> Int,
            set: (Int) -> Unit,
        ) {
            entries += CycleEntry(label, tooltip, options, default, get, set)
        }

        /**
         * Add a numeric slider over `[min, max]`, snapped to [step]. [set] can fire continuously while the
         * handle is dragged, so mark the config dirty there rather than writing the file. [format] renders
         * the value on the handle; it defaults to two decimals.
         */
        fun slider(
            key: String,
            min: Double,
            max: Double,
            step: Double,
            default: Double,
            get: () -> Double,
            set: (Double) -> Unit,
            // Locale.ROOT so the decimal separator is a dot regardless of the client's language.
            format: (Double) -> String = { String.format(Locale.ROOT, "%.2f", it) },
        ) {
            entries += SliderEntry(
                label(key),
                tooltip(key),
                min,
                max,
                step,
                default,
                get,
                set,
                { Component.literal(format(it)) },
            )
        }

        /** Add a free-text field. [validate] returns an error message for a bad value, or null. */
        fun text(
            key: String,
            default: String,
            get: () -> String,
            set: (String) -> Unit,
            validate: (String) -> Component? = { null },
        ) {
            entries += TextEntry(label(key), tooltip(key), default, get, set, validate)
        }

        /** Add a colour picker over an `"#RRGGBB"` (or `"#AARRGGBB"`) string. */
        fun color(
            key: String,
            default: String,
            alpha: Boolean = false,
            get: () -> String,
            set: (String) -> Unit,
        ) {
            entries += ColorEntry(label(key), tooltip(key), default, alpha, get, set)
        }

        /** Add a key-combination capture row. */
        fun keybind(key: String, default: KeyCombo, get: () -> KeyCombo, set: (KeyCombo) -> Unit) {
            entries += KeybindEntry(label(key), tooltip(key), default, get, set)
        }

        /**
         * Add a choice over an enum's constants. Each constant is named by
         * `hex.config.<category>.<key>.<constant lowercased>`, so the options translate alongside the label.
         */
        inline fun <reified T : Enum<T>> enum(
            key: String,
            default: T,
            noinline get: () -> T,
            noinline set: (T) -> Unit,
        ) = enumOf(T::class.java, key, default, get, set)

        /** Non-reified backing for [enum]; call that instead. */
        fun <T : Enum<T>> enumOf(
            type: Class<T>,
            key: String,
            default: T,
            get: () -> T,
            set: (T) -> Unit,
        ) {
            entries += EnumEntry(
                label(key),
                tooltip(key),
                type,
                default,
                get,
                set,
                { Component.translatable("${keyOf(key)}.${it.name.lowercase(Locale.ROOT)}") },
            )
        }
    }
}
