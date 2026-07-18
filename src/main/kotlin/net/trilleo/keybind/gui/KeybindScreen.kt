package net.trilleo.keybind.gui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.trilleo.keybind.Keybind
import net.trilleo.keybind.KeybindConfig
import net.trilleo.keybind.KeybindFormat

/**
 * Config screen for managing keybind shortcuts. Built entirely from standard widgets (buttons and
 * edit boxes) so it needs no custom rendering — this build renders via `extractRenderState`, so there
 * is no `render(GuiGraphics)` to override.
 *
 * Bindings that overflow one page are paged rather than scrolled, again to avoid a custom list widget.
 */
class KeybindScreen(private val parent: Screen?) : Screen(Component.literal("Hex Keybinds")) {

    private val rowsPerPage = 6
    private var page = 0

    /** The binding currently awaiting a key press (capture mode), or null. */
    private var capturing: Keybind? = null

    override fun init() {
        rebuild()
    }

    private fun binds() = KeybindConfig.keybinds

    private fun pageCount(): Int {
        val n = binds().size
        return maxOf(1, (n + rowsPerPage - 1) / rowsPerPage)
    }

    private fun rebuild() {
        clearWidgets()

        // Drop capture state if the target binding was deleted.
        capturing?.let { cap -> if (binds().none { it === cap }) capturing = null }

        page = page.coerceIn(0, pageCount() - 1)

        // Horizontal inset so nothing sits flush against the screen edges.
        val margin = 24

        addRenderableWidget(StringWidget(margin, 12, width - margin * 2, 12, title, font))

        val rowH = 26
        val top = 40
        val contentW = width - margin * 2
        val gap = 6
        val keyW = 120
        val editW = 50
        val toggleW = 40
        val delW = 22
        val sumW = (contentW - keyW - editW - toggleW - delW - gap * 4).coerceAtLeast(60)

        val start = page * rowsPerPage
        val end = minOf(start + rowsPerPage, binds().size)
        for (i in start until end) {
            val kb = binds()[i]
            val y = top + (i - start) * rowH
            var x = margin

            val keyLabel = if (capturing === kb) "> Press a key <" else KeybindFormat.comboLabel(kb)
            addRenderableWidget(Button.builder(Component.literal(keyLabel)) { _ ->
                capturing = if (capturing === kb) null else kb
                rebuild()
            }.bounds(x, y, keyW, 20).tooltip(TIP_SET_KEY).build())
            x += keyW + gap

            addRenderableWidget(
                StringWidget(x, y, sumW, 20, Component.literal(KeybindFormat.summary(kb)), font)
            )
            x += sumW + gap

            addRenderableWidget(Button.builder(Component.literal("Edit")) { _ ->
                minecraft.setScreen(KeybindActionScreen(this, kb))
            }.bounds(x, y, editW, 20).tooltip(TIP_EDIT).build())
            x += editW + gap

            addRenderableWidget(Button.builder(Component.literal(if (kb.enabled) "On" else "Off")) { _ ->
                kb.enabled = !kb.enabled
                rebuild()
            }.bounds(x, y, toggleW, 20).tooltip(TIP_TOGGLE).build())
            x += toggleW + gap

            addRenderableWidget(Button.builder(Component.literal("X")) { _ ->
                binds().remove(kb)
                if (capturing === kb) capturing = null
                rebuild()
            }.bounds(x, y, delW, 20).tooltip(TIP_DELETE).build())
        }

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

        addRenderableWidget(Button.builder(Component.literal("Add Binding")) { _ ->
            binds().add(Keybind())
            page = pageCount() - 1
            rebuild()
        }.bounds(width / 2 - 105, bottomY, 100, 20).tooltip(TIP_ADD).build())

        addRenderableWidget(Button.builder(Component.literal("Done")) { _ ->
            onClose()
        }.bounds(width / 2 + 5, bottomY, 100, 20).tooltip(TIP_DONE).build())
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val cap = capturing
        if (cap != null) {
            val key = event.key()
            if (key == InputConstants.KEY_ESCAPE) {
                capturing = null
                rebuild()
                return true
            }
            // Wait for a non-modifier key so the combo can include held modifiers.
            if (KeybindFormat.isModifierKey(key)) return true

            cap.keyCode = key
            val mods = event.modifiers()
            cap.ctrl = (mods and InputConstants.MOD_CONTROL) != 0
            cap.shift = (mods and InputConstants.MOD_SHIFT) != 0
            cap.alt = (mods and InputConstants.MOD_ALT) != 0
            capturing = null
            rebuild()
            return true
        }
        return super.keyPressed(event)
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun removed() {
        KeybindConfig.save()
    }

    private companion object {
        val TIP_SET_KEY: Tooltip = Tooltip.create(
            Component.literal("Click, then press a key to bind it.\nHold Ctrl/Shift/Alt for a combo. Esc cancels.")
        )
        val TIP_EDIT: Tooltip = Tooltip.create(
            Component.literal("Edit this shortcut's actions and their delays.")
        )
        val TIP_TOGGLE: Tooltip = Tooltip.create(Component.literal("Enable or disable this shortcut."))
        val TIP_DELETE: Tooltip = Tooltip.create(Component.literal("Delete this shortcut."))
        val TIP_ADD: Tooltip = Tooltip.create(Component.literal("Add a new shortcut."))
        val TIP_DONE: Tooltip = Tooltip.create(Component.literal("Save and close."))
        val TIP_PREV: Tooltip = Tooltip.create(Component.literal("Previous page."))
        val TIP_NEXT: Tooltip = Tooltip.create(Component.literal("Next page."))
    }
}
