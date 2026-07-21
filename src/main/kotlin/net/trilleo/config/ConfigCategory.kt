package net.trilleo.config

import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.Locale

/**
 * A named group of [ConfigEntry] rows — one tab in the `/hexa config` menu. A [net.trilleo.feature.Feature]
 * contributes one by overriding [net.trilleo.feature.Feature.settingsCategory]; the menu collects every
 * enabled feature's category into the sidebar automatically.
 *
 * Build one with the [build] DSL rather than the constructor:
 * ```
 * ConfigCategory.build("updates", "Updates") {
 *     toggle("Auto-update on startup", "Check GitHub for a newer release on launch.",
 *            get = { UpdateConfig.settings.enabled },
 *            set = { UpdateConfig.settings.enabled = it; UpdateConfig.save() })
 *     action("Check for updates now", "Run an update check immediately.") { UpdateFeature.checkNow() }
 * }
 * ```
 *
 * @param id stable identifier for the category (logging / future addressing), e.g. `"updates"`.
 * @param title the tab label shown in the sidebar.
 * @param entries the rows, in display order.
 */
class ConfigCategory(
    val id: String,
    val title: Component,
    val entries: List<ConfigEntry>,
) {
    companion object {
        /** Build a category with the [Builder] DSL. */
        fun build(id: String, title: String, block: Builder.() -> Unit): ConfigCategory {
            val builder = Builder()
            builder.block()
            return ConfigCategory(id, Component.literal(title), builder.entries)
        }
    }

    /** Collects [ConfigEntry] rows for [build]; one call per row. */
    class Builder {
        val entries: MutableList<ConfigEntry> = mutableListOf()

        /** Add a boolean toggle. [set] runs on each flip, so persist there if the change should stick. */
        fun toggle(label: String, tooltip: String? = null, get: () -> Boolean, set: (Boolean) -> Unit) {
            entries += BooleanEntry(Component.literal(label), tooltip?.let(Component::literal), get, set)
        }

        /** Add an action button. [onClick] receives the live [Screen] for opening sub-screens. */
        fun action(label: String, tooltip: String? = null, onClick: (Screen) -> Unit) {
            entries += ActionEntry(Component.literal(label), tooltip?.let(Component::literal), onClick)
        }

        /**
         * Add a multiple-choice cycler. [get] returns the current option index, [set] receives the next
         * index on click; persist in [set] if the change should stick.
         */
        fun cycle(
            label: String,
            tooltip: String? = null,
            options: List<String>,
            get: () -> Int,
            set: (Int) -> Unit,
        ) {
            entries += CycleEntry(
                Component.literal(label),
                tooltip?.let(Component::literal),
                options.map(Component::literal),
                get,
                set,
            )
        }

        /**
         * Add a numeric slider over `[min, max]`, snapped to [step]. [set] fires continuously while the
         * handle is dragged, so keep it cheap. [format] renders the value on the handle; it defaults to
         * two decimals.
         */
        fun slider(
            label: String,
            tooltip: String? = null,
            min: Double,
            max: Double,
            step: Double,
            get: () -> Double,
            set: (Double) -> Unit,
            // Locale.ROOT so the decimal separator is a dot regardless of the client's language.
            format: (Double) -> String = { String.format(Locale.ROOT, "%.2f", it) },
        ) {
            entries += SliderEntry(
                Component.literal(label),
                tooltip?.let(Component::literal),
                min,
                max,
                step,
                get,
                set,
                { Component.literal(format(it)) },
            )
        }
    }
}
