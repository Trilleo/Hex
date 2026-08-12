package net.trilleo.chat.gui

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.chat.ChatHighlightConfig
import net.trilleo.chat.model.ChatHighlight
import net.trilleo.skyblock.SkyblockLocation
import java.util.*

/**
 * The chat-highlight rule editor: a scrolling [ChatHighlightList] plus a footer for creating rules.
 *
 * Reachable from the **Chat Highlight** tab of `/hexa config`, from `/hexa chat edit`, and from its own keybind.
 *
 * There is no "add what you are looking at" button here, and there could not be: chat has no crosshair. The
 * discovery affordances are elsewhere — `/hexa chat test` runs a line of your own past the rules, and the
 * editor previews a rule as you type it — which is why this footer is three buttons rather than the entity
 * screen's four.
 *
 * As with entity highlights, rules are not inherently per island, so the list shows everything by default and the
 * island filter is the opt-in: a rule with no island is the normal case, not the exception.
 */
class ChatHighlightsScreen(private val parent: Screen?) :
    Screen(Component.translatable("hex.chat_highlights.title")) {

    private var list: ChatHighlightList? = null

    /** Whether to show only the rules that can fire where the player is standing. */
    private var hereOnly: Boolean = false

    private var filterButton: Button? = null

    override fun init() {
        val listHeight = height - TOP - FOOTER_HEIGHT
        list = addRenderableWidget(ChatHighlightList(minecraft, width, listHeight, TOP, this))

        addRenderableWidget(StringWidget(MARGIN, 12, width - MARGIN * 2, 12, title, font))

        val y = height - 28
        var x = width / 2 - (BUTTON_WIDTH * 3 + GAP * 2) / 2

        addRenderableWidget(
            Button.builder(Component.translatable("hex.chat_highlights.add")) { addEmpty() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.chat_highlights.add.tooltip")))
                .build(),
        )
        x += BUTTON_WIDTH + GAP

        filterButton = addRenderableWidget(
            Button.builder(filterLabel()) { toggleFilter() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.chat_highlights.done")) { onClose() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )

        refreshRows()
    }

    /** Re-reads the rules into the list. Called after every add, delete, and return from the sub-editor. */
    fun refreshRows() {
        val island = SkyblockLocation.current
        val all = ChatHighlightConfig.settings.highlights
        val shown = if (hereOnly) all.filter { it.matchesIsland(island) } else all
        val hint = if (all.isEmpty() || !hereOnly) {
            Component.translatable("hex.chat_highlights.empty")
        } else {
            Component.translatable("hex.chat_highlights.empty_here")
        }
        list?.show(shown, hint)
    }

    private fun addEmpty() {
        val rule = ChatHighlight().apply {
            id = UUID.randomUUID().toString()
            name = ChatHighlightConfig.uniqueName(
                Component.translatable("hex.chat_highlights.new_name").string,
            )
        }
        ChatHighlightConfig.settings.highlights.add(rule)
        ChatHighlightConfig.normalizeNow()
        ChatHighlightConfig.save()
        refreshRows()
        list?.scrollToBottom()
        // Straight into the editor — a rule with no text to look for matches nothing at all.
        minecraft.setScreen(ChatHighlightEditScreen(this, rule))
    }

    private fun toggleFilter() {
        hereOnly = !hereOnly
        filterButton?.message = filterLabel()
        refreshRows()
    }

    private fun filterLabel(): Component = Component.translatable(
        if (hereOnly) "hex.chat_highlights.filter.here" else "hex.chat_highlights.filter.all",
    )

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun removed() {
        // Edits mark the config dirty as they happen; this makes leaving the screen a definite save point rather
        // than waiting on the debounce.
        ChatHighlightConfig.save()
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
