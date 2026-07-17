package net.trilleo.keybind.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CommandSuggestions
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.trilleo.keybind.Keybind
import net.trilleo.keybind.KeybindAction
import net.trilleo.keybind.KeybindFormat

/**
 * Per-keybind editor: one row per [KeybindAction] (command input + delay + remove), reached from the
 * "Edit" button on [KeybindScreen]. Edits the binding's live action list in place; the parent screen
 * persists everything in its `removed()`.
 *
 * Each command input offers the same tab-completion as the vanilla chat box. `CommandSuggestions` binds
 * to a single [EditBox], so a lone active instance is (re)wired to whichever command box is focused —
 * tracked via [getFocused] in [tick] since widgets are rebuilt on every change.
 */
class KeybindActionScreen(private val parent: Screen, private val kb: Keybind) :
	Screen(Component.literal("Edit Keybind")) {

	private val rowsPerPage = 6
	private var page = 0

	/** Command boxes on the current page (only these host suggestions), repopulated each [rebuild]. */
	private val cmdBoxes = ArrayList<EditBox>()

	/** The command box that currently has focus, or null. */
	private var focusedCmdBox: EditBox? = null

	/** Suggestion popup bound to [focusedCmdBox]; null when nothing is focused or offline. */
	private var activeSuggestions: CommandSuggestions? = null

	override fun init() {
		rebuild()
	}

	private fun actions() = kb.actions

	private fun pageCount(): Int {
		val n = actions().size
		return maxOf(1, (n + rowsPerPage - 1) / rowsPerPage)
	}

	private fun rebuild() {
		clearWidgets()
		cmdBoxes.clear()
		// Widgets are recreated, so any previously focused box/suggestion is stale.
		focusedCmdBox = null
		activeSuggestions = null

		page = page.coerceIn(0, pageCount() - 1)

		val margin = 24
		addRenderableWidget(
			StringWidget(margin, 12, width - margin * 2, 12,
				Component.literal("Actions for ${KeybindFormat.comboLabel(kb)}"), font)
		)

		val rowH = 26
		val top = 40
		val contentW = width - margin * 2
		val gap = 6
		val delayW = 44
		val delW = 22
		val cmdW = (contentW - delayW - delW - gap * 2).coerceAtLeast(60)

		val start = page * rowsPerPage
		val end = minOf(start + rowsPerPage, actions().size)
		for (i in start until end) {
			val action = actions()[i]
			val y = top + (i - start) * rowH
			var x = margin

			val cmd = EditBox(font, x, y, cmdW, 20, Component.literal("Command"))
			cmd.setMaxLength(2000)
			cmd.value = action.command
			cmd.setHint(Component.literal("/warp hub"))
			cmd.setResponder { text ->
				action.command = text
				if (cmd === focusedCmdBox) activeSuggestions?.updateCommandInfo()
			}
			addRenderableWidget(cmd)
			cmdBoxes.add(cmd)
			x += cmdW + gap

			val delay = EditBox(font, x, y, delayW, 20, Component.literal("Delay"))
			delay.setMaxLength(4)
			delay.value = action.delayTicks.toString()
			delay.setHint(Component.literal("t"))
			delay.setTooltip(TIP_DELAY)
			delay.setResponder { text -> action.delayTicks = text.toIntOrNull()?.coerceAtLeast(0) ?: 0 }
			addRenderableWidget(delay)
			x += delayW + gap

			addRenderableWidget(Button.builder(Component.literal("X")) { _ ->
				actions().remove(action)
				rebuild()
			}.bounds(x, y, delW, 20).tooltip(TIP_REMOVE).build())
		}

		val bottomY = height - 28

		if (pageCount() > 1) {
			addRenderableWidget(Button.builder(Component.literal("<")) { _ ->
				if (page > 0) { page--; rebuild() }
			}.bounds(margin, bottomY, 20, 20).tooltip(TIP_PREV).build())
			addRenderableWidget(
				StringWidget(margin + 26, bottomY + 6, 90, 12,
					Component.literal("Page ${page + 1} / ${pageCount()}"), font)
			)
			addRenderableWidget(Button.builder(Component.literal(">")) { _ ->
				if (page < pageCount() - 1) { page++; rebuild() }
			}.bounds(margin + 122, bottomY, 20, 20).tooltip(TIP_NEXT).build())
		}

		addRenderableWidget(Button.builder(Component.literal("Add Action")) { _ ->
			actions().add(KeybindAction())
			page = pageCount() - 1
			rebuild()
		}.bounds(width / 2 - 105, bottomY, 100, 20).tooltip(TIP_ADD).build())

		addRenderableWidget(Button.builder(Component.literal("Done")) { _ ->
			onClose()
		}.bounds(width / 2 + 5, bottomY, 100, 20).tooltip(TIP_DONE).build())
	}

	/** Rebuild the suggestion popup for whichever command box is focused (none when offline). */
	private fun rebuildSuggestions() {
		val box = focusedCmdBox
		if (box == null || minecraft.player == null) {
			activeSuggestions = null
			return
		}
		val cs = CommandSuggestions(
			minecraft, this, box, font,
			false,               // commandsOnly: false → only '/'-lines complete, mirroring chat
			false,               // onlyShowCommands: mirror ChatScreen
			1,                   // lineStartOffset (ChatScreen literal)
			10,                  // suggestionLineLimit (ChatScreen literal)
			false,               // anchorToBottom: false → popup renders below a mid-screen box
			0xD0000000.toInt(),  // fillColor (ChatScreen literal)
		)
		cs.setAllowSuggestions(true)
		cs.setAllowHiding(false)
		cs.updateCommandInfo()
		activeSuggestions = cs
	}

	override fun tick() {
		super.tick()
		val focused = focused as? EditBox
		val cmdFocused = if (focused != null && cmdBoxes.any { it === focused }) focused else null
		if (cmdFocused !== focusedCmdBox) {
			focusedCmdBox = cmdFocused
			rebuildSuggestions()
		}
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		if (focusedCmdBox != null && activeSuggestions?.keyPressed(event) == true) return true
		return super.keyPressed(event)
	}

	override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
		if (activeSuggestions?.mouseClicked(event) == true) return true
		val handled = super.mouseClicked(event, doubleClick)
		// A click may have moved focus to a different command box; refresh the popup this frame.
		val focused = focused as? EditBox
		val cmdFocused = if (focused != null && cmdBoxes.any { it === focused }) focused else null
		if (cmdFocused !== focusedCmdBox) {
			focusedCmdBox = cmdFocused
			rebuildSuggestions()
		}
		return handled
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
		if (activeSuggestions?.mouseScrolled(scrollY) == true) return true
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
	}

	override fun extractRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
		super.extractRenderState(extractor, mouseX, mouseY, partialTick)
		activeSuggestions?.extractRenderState(extractor, mouseX, mouseY)
	}

	override fun onClose() {
		minecraft.setScreen(parent)
	}

	private companion object {
		val TIP_DELAY: Tooltip = Tooltip.create(
			Component.literal("Ticks to wait before this action runs, after the previous one (20 ticks = 1 second).")
		)
		val TIP_REMOVE: Tooltip = Tooltip.create(Component.literal("Remove this action."))
		val TIP_ADD: Tooltip = Tooltip.create(Component.literal("Add an action."))
		val TIP_DONE: Tooltip = Tooltip.create(Component.literal("Back to the keybind list."))
		val TIP_PREV: Tooltip = Tooltip.create(Component.literal("Previous page."))
		val TIP_NEXT: Tooltip = Tooltip.create(Component.literal("Next page."))
	}
}
