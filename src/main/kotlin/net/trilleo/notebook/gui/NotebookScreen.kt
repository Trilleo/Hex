package net.trilleo.notebook.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.config.gui.ConfirmActionScreen
import net.trilleo.notebook.NoteShare
import net.trilleo.notebook.Notebook
import net.trilleo.notebook.NotebookConfig
import net.trilleo.notebook.NotebookStore
import net.trilleo.notebook.md.NoteSearch
import net.trilleo.notebook.model.NoteDocument
import net.trilleo.util.Notify

/**
 * The notebook browser: every note, with a sidebar for narrowing and a search box for finding.
 *
 * Laid out like [net.trilleo.config.gui.HexConfigScreen] on purpose — header with a search box, sidebar of
 * filters, scrolling list, footer of actions — so the notebook feels like part of the same mod rather than a
 * screen borrowed from somewhere else. Typing in the search box switches the list from "the selected filter"
 * to "matching notes from anywhere", and dims the sidebar to make that mode obvious, exactly as the settings
 * menu does.
 *
 * The sidebar is built from the notes themselves: folders and tags are not stored anywhere, they are simply
 * the set of values notes carry, so a folder exists precisely as long as something is in it and never has to
 * be created or tidied up.
 */
class NotebookScreen(private val parent: Screen?) : Screen(Component.translatable("hex.notebook.title")) {

    /** What the sidebar is narrowing the list to. */
    private sealed interface Filter {
        data object All : Filter
        data object Pinned : Filter
        data class Folder(val name: String) : Filter
        data class Tag(val name: String) : Filter
    }

    private var filter: Filter = Filter.All
    private var query = ""

    private lateinit var list: NoteList
    private lateinit var search: EditBox
    private val filterButtons = mutableListOf<Pair<Button, Filter>>()

    override fun init() {
        filterButtons.clear()

        val listX = SIDEBAR_WIDTH
        list = NoteList(minecraft, width - listX, height - HEADER_HEIGHT - FOOTER_HEIGHT, HEADER_HEIGHT, this)
        list.setX(listX)
        addWidget(list)

        search = EditBox(font, width - SEARCH_WIDTH - MARGIN, MARGIN + 4, SEARCH_WIDTH, 18, SEARCH_LABEL).apply {
            setHint(SEARCH_LABEL)
            setMaxLength(64)
            value = query
            setResponder { text ->
                query = text
                refreshRows()
            }
        }
        addRenderableWidget(search)

        buildSidebar()

        val footerY = height - FOOTER_HEIGHT + (FOOTER_HEIGHT - BUTTON_HEIGHT) / 2
        var x = MARGIN

        addRenderableWidget(
            Button.builder(Component.translatable("hex.notebook.new")) { createNote() }
                .bounds(x, footerY, ACTION_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.notebook.new.tooltip")))
                .build(),
        )
        x += ACTION_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.notebook.import")) { importNote() }
                .bounds(x, footerY, ACTION_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.notebook.import.tooltip")))
                .build(),
        )

        addRenderableWidget(
            Button.builder(Component.translatable("gui.done")) { onClose() }
                .bounds(width / 2 - DONE_WIDTH / 2, footerY, DONE_WIDTH, BUTTON_HEIGHT)
                .build(),
        )

        refreshRows()
    }

    /**
     * The sidebar's filters: the two fixed ones, then a row per folder and per tag in use.
     *
     * Rebuilt from scratch on every [init] rather than kept in step, because the set of folders changes
     * whenever any note's metadata does and there is no cheaper way to be right than to ask.
     */
    private fun buildSidebar() {
        var y = HEADER_HEIGHT + MARGIN

        fun addFilter(label: Component, value: Filter) {
            // A sidebar longer than the screen is possible with enough folders. It is clipped rather than
            // scrolled: the search box already covers "I have too many to look through", and a second
            // scrolling region on this screen would be more chrome than the case deserves.
            if (y + TAB_HEIGHT > height - FOOTER_HEIGHT) return
            val button = Button.builder(label) {
                filter = value
                if (query.isNotEmpty()) {
                    query = ""
                    search.value = ""
                }
                refreshFilters()
                refreshRows()
            }.bounds(MARGIN, y, SIDEBAR_WIDTH - MARGIN * 2, TAB_HEIGHT).build()
            filterButtons += button to value
            addRenderableWidget(button)
            y += TAB_HEIGHT + TAB_GAP
        }

        addFilter(Component.translatable("hex.notebook.filter.all"), Filter.All)
        addFilter(Component.translatable("hex.notebook.filter.pinned"), Filter.Pinned)

        Notebook.folders().forEach { folder ->
            addFilter(Component.literal(folder), Filter.Folder(folder))
        }
        Notebook.tags().forEach { tag ->
            addFilter(Component.literal("#$tag"), Filter.Tag(tag))
        }

        refreshFilters()
    }

    /** The active filter reads as selected by being non-interactive, matching the rest of the mod's menus. */
    private fun refreshFilters() {
        filterButtons.forEach { (button, value) ->
            button.active = query.isNotEmpty() || value != filter
        }
    }

    /**
     * Re-reads the notebook into the list.
     *
     * Called after every rename and pin, and on return from the editor. Use [refreshAll] instead when the
     * sidebar could have changed — a new folder or tag means new buttons — so the common case of refilling
     * rows does not rebuild the whole screen.
     */
    fun refreshRows() {
        val entries = if (query.isBlank()) {
            Notebook.sorted(NotebookConfig.sort)
                .filter { matchesFilter(it) }
                .map { NoteList.Entry(it, it.snippet(SNIPPET_LENGTH)) }
        } else {
            NoteSearch.search(Notebook.all(), query).map { NoteList.Entry(it.document, it.snippet) }
        }
        list.show(entries, emptyHint())
    }

    /** Rebuilds the sidebar as well as the rows, for a change that could have added or emptied a folder. */
    fun refreshAll() {
        rebuildWidgets()
    }

    private fun matchesFilter(document: NoteDocument): Boolean = when (val f = filter) {
        Filter.All -> true
        Filter.Pinned -> document.meta.pinned
        is Filter.Folder -> document.meta.folder == f.name
        is Filter.Tag -> f.name in document.meta.tags
    }

    private fun emptyHint(): Component = when {
        query.isNotBlank() -> Component.translatable("hex.notebook.no_results", query)
        Notebook.isEmpty() -> Component.translatable("hex.notebook.empty")
        else -> Component.translatable("hex.notebook.empty_filter")
    }

    // ---- actions ---------------------------------------------------------------------------------------

    /**
     * Creates a note and opens it straight away.
     *
     * Into the editor rather than back to the list, because an empty note in a list is not something anyone
     * wanted for its own sake — the reason to press New is to write something.
     */
    private fun createNote() {
        val folder = (filter as? Filter.Folder)?.name.orEmpty()
        val document = Notebook.create(Component.translatable("hex.notebook.new_title").string, folder = folder)
        minecraft.setScreen(NoteEditorScreen(this, document))
    }

    /** Takes whatever is on the clipboard as a new note, and says what happened either way. */
    private fun importNote() {
        val text = minecraft.keyboardHandler.clipboard
        when (val result = NoteShare.import(text)) {
            is NoteShare.ImportResult.Added -> {
                refreshAll()
                Notify.chat(minecraft, Component.translatable("hex.notebook.import.done", result.document.meta.title))
            }

            is NoteShare.ImportResult.TooNew -> Notify.chat(
                minecraft,
                Component.translatable("hex.notebook.import.too_new", result.version),
            )

            NoteShare.ImportResult.NotANote -> Notify.chat(
                minecraft,
                Component.translatable("hex.notebook.import.empty"),
            )
        }
    }

    /** Asks before deleting: a note is text the player wrote, and there is no undo once the file is gone. */
    fun confirmDelete(document: NoteDocument) {
        minecraft.setScreen(
            ConfirmActionScreen(
                parent = this,
                title = Component.translatable("hex.notebook.delete.title"),
                message = Component.translatable("hex.notebook.delete.message"),
                detail = Component.literal(document.meta.title),
                choices = listOf(
                    ConfirmActionScreen.Choice(Component.translatable("hex.notebook.delete.confirm")) {
                        Notebook.delete(document)
                        refreshAll()
                    },
                    ConfirmActionScreen.Choice(Component.translatable("hex.notebook.delete.cancel"), null),
                ),
            ),
        )
    }

    // ---- chrome ----------------------------------------------------------------------------------------

    /**
     * The chrome: panels, dividers and the title.
     *
     * The background pass rather than [extractRenderState], for the reason
     * [net.trilleo.config.gui.HexConfigScreen] documents — that one only iterates the registered renderables,
     * so drawing the panels there would paint straight over the filter buttons and the search box.
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
        // Registered with addWidget rather than addRenderableWidget — it handles its own scissoring and
        // scrollbar — so it is drawn here explicitly, beneath the chrome widgets.
        list.extractWidgetRenderState(extractor, mouseX, mouseY, delta)
        super.extractRenderState(extractor, mouseX, mouseY, delta)
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun removed() {
        // Every edit already marked its note dirty; this makes leaving the browser a definite save point
        // rather than waiting on the debounce.
        NotebookStore.flush()
    }

    private companion object {
        const val SIDEBAR_WIDTH = 110
        const val HEADER_HEIGHT = 32
        const val FOOTER_HEIGHT = 32
        const val MARGIN = 6
        const val GAP = 6
        const val TAB_HEIGHT = 20
        const val TAB_GAP = 2
        const val SEARCH_WIDTH = 140
        const val ACTION_WIDTH = 60
        const val DONE_WIDTH = 100
        const val BUTTON_HEIGHT = 20
        const val SNIPPET_LENGTH = 80

        val SEARCH_LABEL: Component = Component.translatable("hex.notebook.search")

        const val PANEL_COLOR = 0xC0101010.toInt()
        const val SIDEBAR_COLOR = 0x80000000.toInt()
        const val DIVIDER_COLOR = 0x60FFFFFF
        const val TITLE_COLOR = 0xFFFFFFFF.toInt()
    }
}
