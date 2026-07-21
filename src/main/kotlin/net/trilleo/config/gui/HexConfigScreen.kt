package net.trilleo.config.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.config.ConfigCategory
import net.trilleo.config.ConfigProfiles
import net.trilleo.config.ConfigRegistry
import net.trilleo.feature.Features

/**
 * Hex's settings menu, opened with `/hexa config` or the config keybind.
 *
 * Laid out as a header (title and search), a sidebar of category tabs, a scrolling list of settings, and a
 * footer. Categories come from [Features.categories] plus the Profiles tab, so a feature adds a whole tab
 * just by overriding [net.trilleo.feature.Feature.settingsCategory] with no changes here.
 *
 * Settings apply as you change them — there is no Save button, and closing the screen keeps what you did.
 * Writes are still batched: setters mark their config dirty and [ConfigRegistry] flushes about a second
 * later, with a hard flush here on close so nothing is lost.
 *
 * Typing in the search box switches the list from "the selected tab" to "matching settings from every tab",
 * grouped under category captions. The sidebar dims while a search is active to make that mode obvious.
 */
class HexConfigScreen(private val parent: Screen?) : Screen(Component.translatable("hex.config.title")) {

    private var categories: List<ConfigCategory> = emptyList()
    private var selected = 0
    private var query = ""

    private lateinit var list: ConfigEntryList
    private lateinit var search: EditBox
    private val tabButtons = mutableListOf<Button>()

    override fun init() {
        categories = Features.categories() + ConfigProfiles.category()
        selected = selected.coerceIn(0, (categories.size - 1).coerceAtLeast(0))
        tabButtons.clear()

        val listX = SIDEBAR_WIDTH
        list = ConfigEntryList(minecraft!!, width - listX, height - HEADER_HEIGHT - FOOTER_HEIGHT, HEADER_HEIGHT, this)
        list.setX(listX)
        addWidget(list)

        search = EditBox(font, width - SEARCH_WIDTH - MARGIN, MARGIN + 4, SEARCH_WIDTH, 18, SEARCH_LABEL).apply {
            setHint(SEARCH_LABEL)
            setMaxLength(64)
            value = query
            setResponder { text ->
                // Re-filter as you type; the list rebuilds rather than hiding rows so the scrollbar stays honest.
                query = text
                refreshList()
            }
        }
        addRenderableWidget(search)

        categories.forEachIndexed { index, category ->
            val button = Button.builder(category.title) {
                selected = index
                // Selecting a tab is an explicit "show me this", so it clears any active search.
                if (query.isNotEmpty()) {
                    query = ""
                    search.value = ""
                }
                refreshTabs()
                refreshList()
            }.bounds(
                MARGIN,
                HEADER_HEIGHT + MARGIN + index * (TAB_HEIGHT + TAB_GAP),
                SIDEBAR_WIDTH - MARGIN * 2,
                TAB_HEIGHT,
            ).build()
            tabButtons += button
            addRenderableWidget(button)
        }

        addRenderableWidget(
            Button.builder(Component.translatable("gui.done")) { onClose() }
                .bounds(width / 2 - 100, height - FOOTER_HEIGHT + (FOOTER_HEIGHT - 20) / 2, 200, 20)
                .build(),
        )

        refreshTabs()
        refreshList()
    }

    /** The active tab reads as selected by being non-interactive, matching the rest of the game's menus. */
    private fun refreshTabs() {
        tabButtons.forEachIndexed { index, button ->
            button.active = query.isNotEmpty() || index != selected
        }
    }

    private fun refreshList() {
        val groups = if (query.isBlank()) {
            categories.getOrNull(selected)?.let { listOf(it.title to it.entries) }.orEmpty()
        } else {
            ConfigEntryList.filter(categories, query)
        }
        list.show(groups)
    }

    /**
     * The chrome: panels, dividers and the title.
     *
     * This has to be the background pass rather than [extractRenderState], because that one only iterates
     * the registered renderables — drawing the panels there would paint straight over the tab buttons and
     * the search box.
     */
    override fun extractBackground(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(extractor, mouseX, mouseY, delta)

        extractor.fill(0, 0, width, HEADER_HEIGHT, PANEL_COLOR)
        extractor.fill(0, HEADER_HEIGHT, SIDEBAR_WIDTH, height - FOOTER_HEIGHT, SIDEBAR_COLOR)
        extractor.fill(0, height - FOOTER_HEIGHT, width, height, PANEL_COLOR)
        extractor.horizontalLine(0, width, HEADER_HEIGHT - 1, DIVIDER_COLOR)
        extractor.horizontalLine(0, width, height - FOOTER_HEIGHT, DIVIDER_COLOR)
        extractor.verticalLine(SIDEBAR_WIDTH - 1, HEADER_HEIGHT, height - FOOTER_HEIGHT, DIVIDER_COLOR)

        extractor.text(font, title, MARGIN, MARGIN + 8, TITLE_COLOR)
    }

    override fun extractRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        // The list is registered with addWidget rather than addRenderableWidget — it handles its own
        // scissoring and scrollbar — so it is drawn here explicitly, beneath the chrome widgets.
        list.extractWidgetRenderState(extractor, mouseX, mouseY, delta)
        super.extractRenderState(extractor, mouseX, mouseY, delta)

        if (query.isNotBlank() && list.visibleEntries == 0) {
            extractor.centeredText(
                font,
                Component.translatable("hex.config.no_results", query),
                SIDEBAR_WIDTH + (width - SIDEBAR_WIDTH) / 2,
                height / 2,
                MUTED_COLOR,
            )
        }
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    override fun removed() {
        // Edits applied as they were made; this only forces the batched write out early.
        ConfigRegistry.flushAll()
    }

    private companion object {
        const val SIDEBAR_WIDTH = 110
        const val HEADER_HEIGHT = 32
        const val FOOTER_HEIGHT = 32
        const val MARGIN = 6
        const val TAB_HEIGHT = 20
        const val TAB_GAP = 2
        const val SEARCH_WIDTH = 140

        val SEARCH_LABEL: Component = Component.translatable("hex.config.search")

        const val PANEL_COLOR = 0xC0101010.toInt()
        const val SIDEBAR_COLOR = 0x80000000.toInt()
        const val DIVIDER_COLOR = 0x60FFFFFF
        const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        const val MUTED_COLOR = 0xFF9A9A9A.toInt()
    }
}
