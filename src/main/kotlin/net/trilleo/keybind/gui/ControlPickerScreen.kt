package net.trilleo.keybind.gui

import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.keybind.Keybind

/**
 * Picks the Minecraft control a [net.trilleo.keybind.KeybindType.CONTROL_SWITCH] binding targets.
 *
 * Lists every registered `KeyMapping` — Fabric appends modded bindings into `Options.keyMappings`, so this
 * covers vanilla, other mods, and Hex's own — grouped under its category. Paged rather than scrolled, and
 * built from plain widgets, for the same reason as the rest of these screens: this build renders via
 * `extractRenderState` and has no `render(GuiGraphics)` to override.
 */
class ControlPickerScreen(private val parent: Screen, private val kb: Keybind) :
    Screen(Component.literal("Select Control")) {

    /** One flattened list row: either a category heading or a selectable mapping. */
    private sealed interface Row {
        data class Header(val label: String) : Row
        data class Mapping(val mapping: KeyMapping) : Row
    }

    private val margin = 24
    private val listTop = 40
    private val rowH = 22

    private var page = 0

    /** Built once in [init] — the registered mappings don't change while the screen is open. */
    private var rows: List<Row> = emptyList()

    override fun init() {
        rows = buildRows()
        // Open on the page holding the current target, so re-picking starts where the player left off.
        val selected = rows.indexOfFirst { it is Row.Mapping && it.mapping.name == kb.switchTarget }
        if (selected >= 0) page = selected / rowsPerPage()
        rebuild()
    }

    /** All mappings sorted by category then display name, with a heading inserted before each group. */
    private fun buildRows(): List<Row> {
        val out = mutableListOf<Row>()
        minecraft.options.keyMappings
            .groupBy { it.category.label().string }
            .toSortedMap()
            .forEach { (category, mappings) ->
                out.add(Row.Header(category))
                mappings
                    .sortedBy { Component.translatable(it.name).string }
                    .forEach { out.add(Row.Mapping(it)) }
            }
        return out
    }

    private fun rowsPerPage(): Int = maxOf(1, ((height - 28) - listTop) / rowH)

    private fun pageCount(): Int = maxOf(1, (rows.size + rowsPerPage() - 1) / rowsPerPage())

    private fun rebuild() {
        clearWidgets()
        page = page.coerceIn(0, pageCount() - 1)

        val contentW = width - margin * 2

        addRenderableWidget(
            StringWidget(
                margin, 12, contentW, 12,
                Component.literal("Select a control to switch"), font
            )
        )

        val perPage = rowsPerPage()
        val start = page * perPage
        val end = minOf(start + perPage, rows.size)
        for (i in start until end) {
            val y = listTop + (i - start) * rowH
            when (val row = rows[i]) {
                is Row.Header -> addRenderableWidget(
                    StringWidget(
                        margin, y + 6, contentW, 12,
                        Component.literal("— ${row.label} —"), font
                    )
                )

                is Row.Mapping -> {
                    val name = Component.translatable(row.mapping.name).string
                    val marker = if (row.mapping.name == kb.switchTarget) "> " else ""
                    addRenderableWidget(Button.builder(Component.literal("$marker$name")) { _ ->
                        kb.switchTarget = row.mapping.name
                        onClose()
                    }.bounds(margin, y, contentW, 20).tooltip(TIP_SELECT).build())
                }
            }
        }

        buildFooter()
    }

    private fun buildFooter() {
        val bottomY = height - 28

        if (pageCount() > 1) {
            addRenderableWidget(Button.builder(Component.literal("<")) { _ ->
                if (page > 0) {
                    page--; rebuild()
                }
            }.bounds(margin, bottomY, 20, 20).tooltip(TIP_PREV).build())
            addRenderableWidget(
                StringWidget(
                    margin + 26, bottomY + 6, 90, 12,
                    Component.literal("Page ${page + 1} / ${pageCount()}"), font
                )
            )
            addRenderableWidget(Button.builder(Component.literal(">")) { _ ->
                if (page < pageCount() - 1) {
                    page++; rebuild()
                }
            }.bounds(margin + 122, bottomY, 20, 20).tooltip(TIP_NEXT).build())
        }

        addRenderableWidget(Button.builder(Component.literal("Cancel")) { _ ->
            onClose()
        }.bounds(width / 2 - 50, bottomY, 100, 20).tooltip(TIP_CANCEL).build())
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    private companion object {
        val TIP_SELECT: Tooltip = Tooltip.create(Component.literal("Switch this control."))
        val TIP_CANCEL: Tooltip = Tooltip.create(Component.literal("Back without changing the control."))
        val TIP_PREV: Tooltip = Tooltip.create(Component.literal("Previous page."))
        val TIP_NEXT: Tooltip = Tooltip.create(Component.literal("Next page."))
    }
}
