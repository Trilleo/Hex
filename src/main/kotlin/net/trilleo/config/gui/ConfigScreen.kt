package net.trilleo.config.gui

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.config.ActionEntry
import net.trilleo.config.BooleanEntry
import net.trilleo.config.ConfigCategory
import net.trilleo.config.CycleEntry
import net.trilleo.feature.Features
import net.trilleo.keybind.gui.KeybindScreen

/**
 * The universal config menu, opened with `/hexa config`. A left sidebar lists one tab per
 * [ConfigCategory] contributed by an enabled feature (via [Features.categories]); the right panel shows
 * the selected category's entries. Built entirely from standard widgets with manual paging, mirroring
 * [KeybindScreen] — this build renders via `extractRenderState`, so there is no `render(GuiGraphics)` to
 * override.
 *
 * The categories are collected once in [init]; a feature adds a whole tab just by overriding
 * [net.trilleo.feature.Feature.settingsCategory], with no changes here.
 */
class ConfigScreen(private val parent: Screen?) : Screen(Component.literal("Hex Config")) {

    private val categories: List<ConfigCategory> = Features.categories()

    /** Selected sidebar tab. */
    private var selected = 0

    /** Current page within the selected category's entries. */
    private var page = 0

    override fun init() {
        rebuild()
    }

    private fun currentEntries() = categories.getOrNull(selected)?.entries ?: emptyList()

    private fun rowsPerPage(): Int = maxOf(1, ((height - 36) - TOP) / ROW_H)

    private fun pageCount(): Int {
        val n = currentEntries().size
        return maxOf(1, (n + rowsPerPage() - 1) / rowsPerPage())
    }

    private fun rebuild() {
        clearWidgets()

        selected = selected.coerceIn(0, maxOf(0, categories.size - 1))
        page = page.coerceIn(0, pageCount() - 1)

        addRenderableWidget(StringWidget(MARGIN, 12, width - MARGIN * 2, 12, title, font))

        buildSidebar()
        buildEntries()
        buildFooter()
    }

    /** One tab button per category; the active tab is disabled so it reads as selected. */
    private fun buildSidebar() {
        for ((i, category) in categories.withIndex()) {
            val y = TOP + i * (SIDEBAR_ROW_H)
            val button = Button.builder(category.title) { _ ->
                if (selected != i) {
                    selected = i
                    page = 0
                    rebuild()
                }
            }.bounds(MARGIN, y, SIDEBAR_W, 20).build()
            button.active = i != selected
            addRenderableWidget(button)
        }
    }

    /** The right panel: one row per entry of the selected category, paged. */
    private fun buildEntries() {
        val contentX = MARGIN + SIDEBAR_W + GAP
        val contentW = (width - contentX - MARGIN).coerceAtLeast(80)

        val entries = currentEntries()
        if (entries.isEmpty()) {
            addRenderableWidget(
                StringWidget(
                    contentX, TOP + 6, contentW, 12,
                    Component.literal("No settings available."), font
                )
            )
            return
        }

        val perPage = rowsPerPage()
        val start = page * perPage
        val end = minOf(start + perPage, entries.size)
        for (i in start until end) {
            val entry = entries[i]
            val y = TOP + (i - start) * ROW_H

            when (entry) {
                is BooleanEntry -> {
                    val toggleW = 44
                    val labelW = (contentW - toggleW - GAP).coerceAtLeast(40)
                    addRenderableWidget(StringWidget(contentX, y + 5, labelW, 12, entry.label, font))
                    val button = Button.builder(onOff(entry.get())) { _ ->
                        entry.set(!entry.get())
                        rebuild()
                    }.bounds(contentX + labelW + GAP, y, toggleW, 20).build()
                    entry.tooltip?.let { button.setTooltip(Tooltip.create(it)) }
                    addRenderableWidget(button)
                }

                is ActionEntry -> {
                    val button = Button.builder(entry.label) { _ ->
                        entry.onClick(this)
                    }.bounds(contentX, y, contentW, 20).build()
                    entry.tooltip?.let { button.setTooltip(Tooltip.create(it)) }
                    addRenderableWidget(button)
                }

                is CycleEntry -> {
                    val valueW = 80
                    val labelW = (contentW - valueW - GAP).coerceAtLeast(40)
                    addRenderableWidget(StringWidget(contentX, y + 5, labelW, 12, entry.label, font))
                    val current = entry.get().coerceIn(0, entry.options.size - 1)
                    val button = Button.builder(entry.options[current]) { _ ->
                        entry.set((current + 1) % entry.options.size)
                        rebuild()
                    }.bounds(contentX + labelW + GAP, y, valueW, 20).build()
                    entry.tooltip?.let { button.setTooltip(Tooltip.create(it)) }
                    addRenderableWidget(button)
                }
            }
        }
    }

    private fun buildFooter() {
        val bottomY = height - 28

        if (pageCount() > 1) {
            addRenderableWidget(Button.builder(Component.literal("<")) { _ ->
                if (page > 0) {
                    page--; rebuild()
                }
            }.bounds(MARGIN, bottomY, 20, 20).tooltip(TIP_PREV).build())
            addRenderableWidget(
                StringWidget(
                    MARGIN + 26, bottomY + 6, 90, 12,
                    Component.literal("Page ${page + 1} / ${pageCount()}"), font
                )
            )
            addRenderableWidget(Button.builder(Component.literal(">")) { _ ->
                if (page < pageCount() - 1) {
                    page++; rebuild()
                }
            }.bounds(MARGIN + 122, bottomY, 20, 20).tooltip(TIP_NEXT).build())
        }

        val doneX = width - MARGIN - 100
        val keybindsX = doneX - GAP - 100

        addRenderableWidget(Button.builder(Component.literal("Keybinds…")) { _ ->
            minecraft.setScreen(KeybindScreen(this))
        }.bounds(keybindsX, bottomY, 100, 20).tooltip(TIP_KEYBINDS).build())

        addRenderableWidget(Button.builder(Component.literal("Done")) { _ ->
            onClose()
        }.bounds(doneX, bottomY, 100, 20).tooltip(TIP_DONE).build())
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    private companion object {
        const val MARGIN = 24
        const val SIDEBAR_W = 100
        const val SIDEBAR_ROW_H = 24
        const val GAP = 8
        const val TOP = 40
        const val ROW_H = 24

        fun onOff(value: Boolean): Component = Component.literal(if (value) "On" else "Off")

        val TIP_KEYBINDS: Tooltip = Tooltip.create(Component.literal("Open the Hex Keybinds screen."))
        val TIP_DONE: Tooltip = Tooltip.create(Component.literal("Close the config menu."))
        val TIP_PREV: Tooltip = Tooltip.create(Component.literal("Previous page."))
        val TIP_NEXT: Tooltip = Tooltip.create(Component.literal("Next page."))
    }
}
