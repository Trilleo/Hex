package net.trilleo.hand.gui

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.hand.SwingItems
import net.trilleo.hand.SwingItemsConfig
import net.trilleo.skyblock.item.HeldItem
import net.trilleo.skyblock.item.ItemRule

/**
 * The editor for the per-item swing list: a scrolling [SwingItemList] plus a footer for adding rules.
 *
 * Reachable from the **Hand** tab of `/hexa config`, from `/hexa hand swing`, and — for adding alone — from
 * the **Toggle Swing For Held Item** keybind, which needs no screen at all.
 */
class SwingItemsScreen(private val parent: Screen?) :
    Screen(Component.translatable("hex.swing_items.title")) {

    private var list: SwingItemList? = null

    /** Kept so the footer's enabled state can be recomputed without rebuilding every widget. */
    private var addHeldButton: Button? = null

    override fun init() {
        val listTop = TOP
        val listHeight = height - TOP - FOOTER_HEIGHT
        val created = SwingItemList(minecraft, width, listHeight, listTop, this)
        list = addRenderableWidget(created)

        addRenderableWidget(StringWidget(MARGIN, 12, width - MARGIN * 2, 12, title, font))

        val bottomY = height - 28
        var x = width / 2 - (BUTTON_WIDTH * 3 + BUTTON_GAP * 2) / 2

        addHeldButton = addRenderableWidget(
            Button.builder(Component.translatable("hex.swing_items.add_held")) {
                if (SwingItems.addHeld(minecraft)) refreshRows()
            }.bounds(x, bottomY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.swing_items.add_held.tooltip")))
                .build(),
        )
        x += BUTTON_WIDTH + BUTTON_GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.swing_items.add_empty")) {
                SwingItemsConfig.settings.rules.add(ItemRule())
                SwingItemsConfig.markDirty()
                refreshRows()
                // Scroll to it: a row appended out of sight looks like the button did nothing.
                list?.scrollToBottom()
            }.bounds(x, bottomY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.swing_items.add_empty.tooltip")))
                .build(),
        )
        x += BUTTON_WIDTH + BUTTON_GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.swing_items.done")) { onClose() }
                .bounds(x, bottomY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.swing_items.done.tooltip")))
                .build(),
        )

        refreshRows()
    }

    /** Re-reads the rules into the list and re-checks whether anything can be added from the hand. */
    fun refreshRows() {
        list?.show(SwingItemsConfig.settings.rules)
        updateAddHeld()
    }

    override fun tick() {
        // HeldItem keeps ticking while a screen is open, so what is in hand can change under an open editor —
        // switching slots with the scroll wheel still works here.
        updateAddHeld()
    }

    private fun updateAddHeld() {
        val button = addHeldButton ?: return
        val addable = HeldItem.present && (HeldItem.id != null || HeldItem.uuid != null)
        button.active = addable
        button.setTooltip(
            Tooltip.create(
                Component.translatable(
                    if (addable) "hex.swing_items.add_held.tooltip" else "hex.swing_items.add_held.unavailable",
                ),
            ),
        )
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun removed() {
        // Edits mark the config dirty as they happen; this makes leaving the screen a definite save point
        // rather than waiting on the debounce.
        SwingItemsConfig.save()
    }

    private companion object {
        const val MARGIN = 24
        const val TOP = 32
        const val FOOTER_HEIGHT = 40
        const val BUTTON_WIDTH = 90
        const val BUTTON_HEIGHT = 20
        const val BUTTON_GAP = 6
    }
}
