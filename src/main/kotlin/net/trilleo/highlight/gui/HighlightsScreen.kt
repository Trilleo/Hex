package net.trilleo.highlight.gui

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.highlight.HighlightCapture
import net.trilleo.highlight.HighlightConfig
import net.trilleo.highlight.model.Highlight
import net.trilleo.skyblock.SkyblockLocation
import java.util.*

/**
 * The highlight-rule editor: a scrolling [HighlightList] plus a footer for creating rules.
 *
 * Reachable from the **Highlights** tab of `/hexa config`, from `/hexa highlight edit`, and from its own
 * keybind.
 *
 * Unlike regions, rules are not inherently per island — a mob name means the same thing wherever it appears —
 * so the list shows everything by default and the island filter is the opt-in. Hiding rules by default would be
 * wrong here in the way it is right there: a rule with no island is the normal case, not the exception.
 */
class HighlightsScreen(private val parent: Screen?) :
    Screen(Component.translatable("hex.highlights.title")) {

    private var list: HighlightList? = null

    /** Whether to show only the rules that can fire where the player is standing. */
    private var hereOnly: Boolean = false

    private var filterButton: Button? = null

    override fun init() {
        val listHeight = height - TOP - FOOTER_HEIGHT
        list = addRenderableWidget(HighlightList(minecraft, width, listHeight, TOP, this))

        addRenderableWidget(StringWidget(MARGIN, 12, width - MARGIN * 2, 12, title, font))

        val y = height - 28
        var x = width / 2 - (BUTTON_WIDTH * 4 + GAP * 3) / 2

        addRenderableWidget(
            Button.builder(Component.translatable("hex.highlights.add_this")) { addFromCrosshair() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.highlights.add_this.tooltip")))
                .build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.highlights.add_empty")) { addEmpty() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.highlights.add_empty.tooltip")))
                .build(),
        )
        x += BUTTON_WIDTH + GAP

        filterButton = addRenderableWidget(
            Button.builder(filterLabel()) { toggleFilter() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.highlights.done")) { onClose() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )

        refreshRows()
    }

    /** Re-reads the rules into the list. Called after every add, delete, and return from the sub-editor. */
    fun refreshRows() {
        val island = SkyblockLocation.current
        val all = HighlightConfig.settings.highlights
        val shown = if (hereOnly) all.filter { it.island.isEmpty() || it.island == island } else all
        val hint = if (all.isEmpty() || !hereOnly) {
            Component.translatable("hex.highlights.empty")
        } else {
            Component.translatable("hex.highlights.empty_here")
        }
        list?.show(shown, hint)
    }

    /**
     * Creates a rule from the entity under the crosshair and opens it.
     *
     * The crosshair keeps pointing where it was while a screen is up, so this works from inside the menu —
     * which matters, because "open the list, notice the mob you wanted, add it" is the obvious sequence and
     * having to close the screen to press a keybind would make it the awkward one.
     */
    private fun addFromCrosshair() {
        val highlight = HighlightCapture.fromCrosshair(minecraft) ?: return
        install(highlight)
    }

    /** Creates a blank rule for a player who knows what they want to type. */
    private fun addEmpty() {
        install(
            Highlight().apply {
                id = UUID.randomUUID().toString()
                name = HighlightConfig.uniqueName(Component.translatable("hex.highlights.new_name").string)
            },
        )
    }

    private fun install(highlight: Highlight) {
        HighlightConfig.settings.highlights.add(highlight)
        HighlightConfig.normalizeNow()
        HighlightConfig.save()
        refreshRows()
        list?.scrollToBottom()
        // Straight into the editor — a rule with no colour and no message is never what anyone wanted.
        minecraft.setScreen(HighlightEditScreen(this, highlight))
    }

    private fun toggleFilter() {
        hereOnly = !hereOnly
        filterButton?.message = filterLabel()
        refreshRows()
    }

    private fun filterLabel(): Component = Component.translatable(
        if (hereOnly) "hex.highlights.filter.here" else "hex.highlights.filter.all",
    )

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun removed() {
        // Edits mark the config dirty as they happen; this makes leaving the screen a definite save point
        // rather than waiting on the debounce.
        HighlightConfig.save()
    }

    private companion object {
        const val MARGIN = 24
        const val TOP = 32
        const val FOOTER_HEIGHT = 40
        const val BUTTON_WIDTH = 88
        const val BUTTON_HEIGHT = 20
        const val GAP = 6
    }
}
