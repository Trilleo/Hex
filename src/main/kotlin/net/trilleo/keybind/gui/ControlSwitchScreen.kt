package net.trilleo.keybind.gui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.trilleo.keybind.ControlSwitch
import net.trilleo.keybind.Keybind
import net.trilleo.keybind.KeybindFormat

/**
 * Editor for a control-switch binding, reached from the "Edit" button on [KeybindScreen]: picks the target
 * Minecraft control and the list of keys the binding cycles it through.
 *
 * Edits the live [Keybind] in place; the parent screen persists everything in its `removed()`.
 *
 * Key slots accept mouse buttons as well as keyboard keys — a control bound to Left Click is the whole
 * point of the feature — but never modifiers, since a vanilla `KeyMapping` holds a single key. That makes
 * capture here deliberately different from the trigger-combo capture on [KeybindScreen].
 */
class ControlSwitchScreen(private val parent: Screen, private val kb: Keybind) :
    Screen(Component.literal("Control Switch")) {

    private val margin = 24
    private val listTop = 96
    private val rowH = 22

    private var page = 0

    /** Index into [Keybind.switchKeys] awaiting a key press, or null. Index, not identity — slots are Strings. */
    private var capturing: Int? = null

    override fun init() {
        rebuild()
    }

    private fun keys() = kb.switchKeys

    private fun rowsPerPage(): Int = maxOf(1, ((height - 28) - listTop) / rowH)

    private fun pageCount(): Int = maxOf(1, (keys().size + rowsPerPage() - 1) / rowsPerPage())

    private fun rebuild() {
        clearWidgets()

        // Drop capture state if the slot was removed or the list shrank under it.
        capturing?.let { if (it !in keys().indices) capturing = null }
        page = page.coerceIn(0, pageCount() - 1)

        val contentW = width - margin * 2
        val gap = 6

        addRenderableWidget(
            StringWidget(
                margin, 12, contentW, 12,
                Component.literal("Control switch for ${KeybindFormat.comboLabel(kb)}"), font
            )
        )

        buildTargetRow(contentW)
        buildHint(contentW)
        buildList(contentW, gap)
        buildFooter()
    }

    /** The target control: a label plus a full-width button opening the picker. */
    private fun buildTargetRow(contentW: Int) {
        addRenderableWidget(
            StringWidget(margin, 34, contentW, 12, Component.literal("Control to switch:"), font)
        )
        addRenderableWidget(Button.builder(Component.literal(ControlSwitch.targetLabel(kb))) { _ ->
            minecraft.setScreen(ControlPickerScreen(this, kb))
        }.bounds(margin, 48, contentW, 20).tooltip(TIP_TARGET).build())
    }

    /** Either how the cycle works, or why it currently wouldn't run. */
    private fun buildHint(contentW: Int) {
        val hint = when {
            kb.switchTarget.isEmpty() -> "Pick a control above first."
            ControlSwitch.resolve(kb) == null -> "That control is no longer registered — pick another."
            keys().size < 2 -> "Add at least two keys to cycle between."
            else -> "Pressing the shortcut cycles the control through these keys, in order."
        }
        addRenderableWidget(StringWidget(margin, 76, contentW, 12, Component.literal(hint), font))
    }

    /** One row per key slot: the key itself (click to re-capture) plus a remove button. */
    private fun buildList(contentW: Int, gap: Int) {
        val delW = 22
        val keyW = (contentW - delW - gap).coerceAtLeast(60)
        val perPage = rowsPerPage()
        val start = page * perPage
        val end = minOf(start + perPage, keys().size)

        for (i in start until end) {
            val y = listTop + (i - start) * rowH
            val label = if (capturing == i) "> Press a key <" else "${i + 1}. ${ControlSwitch.keyLabel(keys()[i])}"

            addRenderableWidget(Button.builder(Component.literal(label)) { _ ->
                capturing = if (capturing == i) null else i
                rebuild()
            }.bounds(margin, y, keyW, 20).tooltip(TIP_SLOT).build())

            addRenderableWidget(Button.builder(Component.literal("X")) { _ ->
                keys().removeAt(i)
                capturing = null
                rebuild()
            }.bounds(margin + keyW + gap, y, delW, 20).tooltip(TIP_REMOVE).build())
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

        addRenderableWidget(Button.builder(Component.literal("Add Key")) { _ ->
            // New slots start unbound and immediately in capture mode, so adding one is a single click.
            keys().add(InputConstants.UNKNOWN.name)
            page = pageCount() - 1
            capturing = keys().lastIndex
            rebuild()
        }.bounds(width / 2 - 105, bottomY, 100, 20).tooltip(TIP_ADD).build())

        addRenderableWidget(Button.builder(Component.literal("Done")) { _ ->
            onClose()
        }.bounds(width / 2 + 5, bottomY, 100, 20).tooltip(TIP_DONE).build())
    }

    // --- Capture --------------------------------------------------------------------------------------

    override fun keyPressed(event: KeyEvent): Boolean {
        val slot = capturing ?: return super.keyPressed(event)
        if (event.key() == InputConstants.KEY_ESCAPE) {
            capturing = null
            rebuild()
            return true
        }
        // Modifiers are not accepted: a KeyMapping holds one key, so binding Ctrl alone would be a trap.
        if (KeybindFormat.isModifierKey(event.key())) return true

        assign(slot, InputConstants.getKey(event).name)
        return true
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        // Checked before super so the click that *starts* capture still reaches the slot button; only the
        // next click lands here as the captured key.
        val slot = capturing ?: return super.mouseClicked(event, doubleClick)
        assign(slot, InputConstants.Type.MOUSE.getOrCreate(event.button()).name)
        return true
    }

    private fun assign(slot: Int, keyName: String) {
        if (slot in keys().indices) keys()[slot] = keyName
        capturing = null
        rebuild()
    }

    override fun onClose() {
        // An abandoned unbound slot would just be dropped at runtime; clear it so the list stays honest.
        keys().removeAll { it == InputConstants.UNKNOWN.name }
        minecraft.setScreen(parent)
    }

    private companion object {
        val TIP_TARGET: Tooltip = Tooltip.create(
            Component.literal("The Minecraft control this shortcut rebinds.")
        )
        val TIP_SLOT: Tooltip = Tooltip.create(
            Component.literal("Click, then press a key or mouse button. Esc cancels.")
        )
        val TIP_REMOVE: Tooltip = Tooltip.create(Component.literal("Remove this key from the cycle."))
        val TIP_ADD: Tooltip = Tooltip.create(Component.literal("Add a key to the cycle."))
        val TIP_DONE: Tooltip = Tooltip.create(Component.literal("Back to the keybind list."))
        val TIP_PREV: Tooltip = Tooltip.create(Component.literal("Previous page."))
        val TIP_NEXT: Tooltip = Tooltip.create(Component.literal("Next page."))
    }
}
