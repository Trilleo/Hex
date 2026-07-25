package net.trilleo.notebook.gui

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.config.ConfigCategory
import net.trilleo.config.gui.ConfigEntryList
import net.trilleo.notebook.NoteIcon
import net.trilleo.notebook.Notebook
import net.trilleo.notebook.model.NoteDocument
import java.util.*

/**
 * Everything about a note except its text: where it is filed, what it is tagged, and how its row looks.
 *
 * Reuses [ConfigEntryList] by building a throwaway [ConfigCategory] whose entries close over this one note,
 * exactly as [net.trilleo.itemcustom.gui.ItemCustomizeScreen] and
 * [net.trilleo.region.gui.RegionEditScreen] do — inheriting scrolling, keyboard navigation, per-row reset,
 * inline validation and tooltips, and adding **no new widget types at all**. Every field here is already
 * expressible as a [net.trilleo.config.ConfigEntry].
 *
 * Kept off the editor screen deliberately. Filing a note is something you do once; writing it is what the
 * editor is for, and five metadata rows above the text would cost the writing surface far more than they are
 * worth.
 */
class NoteMetaScreen(
    private val parent: Screen?,
    private val document: NoteDocument,
) : Screen(Component.translatable("hex.notebook.meta.title")) {

    private var list: ConfigEntryList? = null

    override fun init() {
        addRenderableWidget(StringWidget(MARGIN, 12, width - MARGIN * 2, 12, title, font))

        val listHeight = height - TOP - FOOTER_HEIGHT
        list = addRenderableWidget(ConfigEntryList(minecraft, width, listHeight, TOP, this))

        addRenderableWidget(
            Button.builder(Component.translatable("hex.notebook.meta.done")) { onClose() }
                .bounds(width / 2 - BUTTON_WIDTH / 2, height - 28, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build(),
        )

        val category = buildCategory()
        list?.show(listOf(category.title to category.entries))
    }

    /**
     * The rows, in the order they are usually reached for: where it goes, then what it looks like.
     *
     * Every setter goes through [Notebook.markDirty] rather than a direct save, because a text field's
     * responder fires on each keystroke and writing the file per character typed would be one write per
     * letter. Leaving the screen flushes.
     */
    private fun buildCategory(): ConfigCategory = ConfigCategory.build("notebook_meta") {
        text(
            "folder",
            default = "",
            get = { document.meta.folder },
            // Folded here rather than left as typed, because the sidebar groups on an exact match and
            // "Mining" sitting beside "mining" would read as a bug rather than as two folders.
            set = {
                document.meta.folder = it.trim().trim('/').lowercase(Locale.ROOT)
                Notebook.markDirty(document)
            },
        )
        text(
            "tags",
            default = "",
            get = { document.meta.tagsAsText() },
            set = { document.meta.setTagsFromText(it); Notebook.markDirty(document) },
        )
        color(
            "color",
            default = "",
            get = { document.meta.color },
            set = { document.meta.color = it.trim(); Notebook.markDirty(document) },
        )
        text(
            "icon",
            default = "",
            get = { document.meta.icon },
            set = { document.meta.icon = it.trim().lowercase(Locale.ROOT); Notebook.markDirty(document) },
            // Reported rather than refused, for the reason ItemCustomizeScreen's model row documents: an id
            // that names nothing falls back to the default book, which is signal enough, and blocking the
            // value would make a typo mid-word impossible to type through.
            validate = {
                if (NoteIcon.known(it)) null else Component.translatable("hex.config.notebook_meta.icon.invalid")
            },
        )
        toggle(
            "pinned",
            default = false,
            get = { document.meta.pinned },
            set = { document.meta.pinned = it; Notebook.markDirty(document) },
        )
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun removed() {
        // Edits mark the note dirty as they happen; this makes leaving the screen a definite save point
        // rather than waiting on the debounce.
        Notebook.saveNow(document)
    }

    private companion object {
        const val MARGIN = 24
        const val TOP = 32
        const val FOOTER_HEIGHT = 40
        const val BUTTON_WIDTH = 100
        const val BUTTON_HEIGHT = 20
    }
}
