package net.trilleo.keybind.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.*
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.trilleo.keybind.Keybind
import net.trilleo.keybind.KeybindAction
import net.trilleo.keybind.KeybindFormat

/**
 * Per-keybind editor, reached from the "Edit" button on [KeybindScreen]. Edits the binding's live action
 * list in place; the parent screen persists everything in its `removed()`.
 *
 * Laid out as a single command editor at the top plus a selectable list of the binding's actions below.
 * This shape is dictated by [CommandSuggestions]: its popup Y is hardcoded (just under the top of the
 * screen, or the very bottom) and does not follow an arbitrary widget, so the one editable command box
 * lives near the top and its suggestion popup drops into the reserved gap above the list — never covering
 * an action row.
 */
class KeybindActionScreen(private val parent: Screen, private val kb: Keybind) :
    Screen(Component.literal("Edit Keybind")) {

    /** Max suggestion rows shown at once; also fixes the height of the reserved popup gap (LINE_HEIGHT each). */
    private val suggestionLines = 5

    /** The action currently loaded into the top editor, or null when the binding has no actions. */
    private var selected: KeybindAction? = null

    /** The list of actions is paged; this is the current page. */
    private var page = 0

    /** The top command editor box (present only when [selected] is non-null). */
    private var commandBox: EditBox? = null

    /** Suggestion popup bound to [commandBox]; null when there is no editor or no player (offline). */
    private var suggestions: CommandSuggestions? = null

    override fun init() {
        if (selected == null) selected = kb.actions.firstOrNull()
        rebuild()
    }

    private fun actions() = kb.actions

    // --- List paging ------------------------------------------------------------------------------------

    private val margin = 24
    private val listTop = 136
    private val listRowH = 22

    private fun rowsPerPage(): Int = maxOf(1, ((height - 28) - listTop) / listRowH)

    private fun pageCount(): Int {
        val n = actions().size
        return maxOf(1, (n + rowsPerPage() - 1) / rowsPerPage())
    }

    // --- Build ------------------------------------------------------------------------------------------

    private fun rebuild() {
        clearWidgets()
        commandBox = null
        suggestions = null

        // Drop a stale selection (e.g. the selected action was removed).
        selected?.let { sel -> if (actions().none { it === sel }) selected = actions().firstOrNull() }
        page = page.coerceIn(0, pageCount() - 1)

        val contentW = width - margin * 2
        val gap = 6

        addRenderableWidget(
            StringWidget(
                margin, 12, contentW, 12,
                Component.literal("Actions for ${KeybindFormat.comboLabel(kb)}"), font
            )
        )

        buildEditor(contentW, gap)
        buildList(contentW, gap)
        buildFooter()

        commandBox?.let { setFocused(it) }
    }

    /** The top editor: command box (with autocomplete) plus its delay, for the [selected] action. */
    private fun buildEditor(contentW: Int, gap: Int) {
        val action = selected ?: return
        val y = 40
        val delayW = 44
        val cmdW = (contentW - delayW - gap).coerceAtLeast(60)

        val cmd = EditBox(font, margin, y, cmdW, 20, Component.literal("Command"))
        cmd.setMaxLength(2000)
        cmd.value = action.command
        cmd.setHint(Component.literal("/warp hub"))
        cmd.setResponder { text ->
            action.command = text
            suggestions?.updateCommandInfo()
        }
        addRenderableWidget(cmd)
        commandBox = cmd

        val delay = EditBox(font, margin + cmdW + gap, y, delayW, 20, Component.literal("Delay"))
        delay.setMaxLength(4)
        delay.value = action.delayTicks.toString()
        delay.setHint(Component.literal("t"))
        delay.setTooltip(TIP_DELAY)
        delay.setResponder { text -> action.delayTicks = text.toIntOrNull()?.coerceAtLeast(0) ?: 0 }
        addRenderableWidget(delay)

        // Hint fills the reserved popup gap; the suggestion popup draws over it when active.
        addRenderableWidget(
            StringWidget(
                margin, 70, contentW, 12,
                Component.literal("Start with / for a command (Tab to complete); anything else is chat."), font
            )
        )

        buildSuggestions(cmd)
    }

    /** Wire a single [CommandSuggestions] to the editor box; no-op offline (no command dispatcher). */
    private fun buildSuggestions(box: EditBox) {
        if (minecraft.player == null) return
        val cs = CommandSuggestions(
            minecraft, this, box, font,
            false,               // commandsOnly: false → only '/'-lines complete, mirroring chat
            false,               // onlyShowCommands: mirror ChatScreen
            1,                   // lineStartOffset (ChatScreen literal)
            suggestionLines,     // suggestionLineLimit (caps the reserved gap height)
            false,               // anchorToBottom: false → popup drops just below the top editor box
            0xD0000000.toInt(),  // fillColor (ChatScreen literal)
        )
        cs.setAllowSuggestions(true)
        cs.setAllowHiding(false)
        cs.updateCommandInfo()
        suggestions = cs
    }

    /** The action list: one selectable row per action (with a remove button), paged. */
    private fun buildList(contentW: Int, gap: Int) {
        val delW = 22
        val selW = (contentW - delW - gap).coerceAtLeast(60)
        val perPage = rowsPerPage()
        val start = page * perPage
        val end = minOf(start + perPage, actions().size)
        for (i in start until end) {
            val action = actions()[i]
            val y = listTop + (i - start) * listRowH
            val marker = if (action === selected) "> " else ""
            addRenderableWidget(Button.builder(Component.literal(marker + rowLabel(i, action))) { _ ->
                selected = action
                rebuild()
            }.bounds(margin, y, selW, 20).tooltip(TIP_SELECT).build())

            addRenderableWidget(Button.builder(Component.literal("X")) { _ ->
                actions().remove(action)
                if (action === selected) selected = null
                rebuild()
            }.bounds(margin + selW + gap, y, delW, 20).tooltip(TIP_REMOVE).build())
        }
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

        addRenderableWidget(Button.builder(Component.literal("Add Action")) { _ ->
            val action = KeybindAction()
            actions().add(action)
            selected = action
            page = pageCount() - 1
            rebuild()
        }.bounds(width / 2 - 105, bottomY, 100, 20).tooltip(TIP_ADD).build())

        addRenderableWidget(Button.builder(Component.literal("Done")) { _ ->
            onClose()
        }.bounds(width / 2 + 5, bottomY, 100, 20).tooltip(TIP_DONE).build())
    }

    private fun rowLabel(i: Int, action: KeybindAction): String {
        val cmd = action.command.trim().ifEmpty { "(empty)" }
        val head = if (cmd.length > 30) cmd.take(29) + "…" else cmd
        return "${i + 1}. $head   ${action.delayTicks}t"
    }

    // --- Suggestion event routing ----------------------------------------------------------------------

    override fun keyPressed(event: KeyEvent): Boolean {
        if (suggestions?.keyPressed(event) == true) return true
        return super.keyPressed(event)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (suggestions?.mouseClicked(event) == true) return true
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (suggestions?.mouseScrolled(scrollY) == true) return true
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun extractRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(extractor, mouseX, mouseY, partialTick)
        suggestions?.extractRenderState(extractor, mouseX, mouseY)
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    private companion object {
        val TIP_DELAY: Tooltip = Tooltip.create(
            Component.literal("Ticks to wait before this action runs, after the previous one (20 ticks = 1 second).")
        )
        val TIP_SELECT: Tooltip = Tooltip.create(Component.literal("Edit this action above."))
        val TIP_REMOVE: Tooltip = Tooltip.create(Component.literal("Remove this action."))
        val TIP_ADD: Tooltip = Tooltip.create(Component.literal("Add an action."))
        val TIP_DONE: Tooltip = Tooltip.create(Component.literal("Back to the keybind list."))
        val TIP_PREV: Tooltip = Tooltip.create(Component.literal("Previous page."))
        val TIP_NEXT: Tooltip = Tooltip.create(Component.literal("Next page."))
    }
}
