package net.trilleo.itemcustom.gui

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.trilleo.itemcustom.ItemCapture
import net.trilleo.itemcustom.ItemCustomization
import net.trilleo.itemcustom.ItemCustomizeConfig

/**
 * Manages every customized item: a scrolling [ItemCustomizeList] plus a footer for adding the held one.
 *
 * Reachable from the **Item Customization** tab of `/hexa config` and from `/hexa item list`. This is the way
 * back to an entry once the item is no longer in front of you — the keybind needs a slot under the cursor,
 * and an item put away in a backpack has neither.
 */
class ItemCustomizeListScreen(private val parent: Screen?) :
    Screen(Component.translatable("hex.item_custom.title")) {

    private var list: ItemCustomizeList? = null

    /** Kept so the footer's enabled state can be recomputed without rebuilding every widget. */
    private var addHeldButton: Button? = null

    override fun init() {
        val listHeight = height - TOP - FOOTER_HEIGHT
        list = addRenderableWidget(ItemCustomizeList(minecraft, width, listHeight, TOP, this))

        addRenderableWidget(StringWidget(MARGIN, 12, width - MARGIN * 2, 12, title, font))

        val y = height - 28
        var x = width / 2 - (BUTTON_WIDTH * 2 + GAP) / 2

        addHeldButton = addRenderableWidget(
            Button.builder(Component.translatable("hex.item_custom.add_held")) { addHeld() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.item_custom.done")) { onClose() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.item_custom.done.tooltip")))
                .build(),
        )

        refreshRows()
    }

    /** Re-reads the customizations into the list and re-checks whether the held item can be added. */
    fun refreshRows() {
        list?.show(ItemCustomizeConfig.settings.items)
        updateAddHeld()
    }

    /**
     * Opens the editor for one entry.
     *
     * The item behind a stored entry is usually nowhere in reach — that is the whole point of this screen — so
     * the editor normally gets no stack and previews the name alone. The one case worth catching is the item
     * being in hand right now, where the full preview costs nothing.
     */
    fun edit(customization: ItemCustomization) {
        val held = ItemCapture.heldStack(minecraft)
        val stack = if (ItemCapture.uuidOf(held) == customization.uuid) held else ItemStack.EMPTY
        minecraft.setScreen(ItemCustomizeScreen(this, customization, stack))
    }

    override fun tick() {
        // What is in hand can change under an open screen — switching slots with the scroll wheel still works
        // here — so the footer is re-checked rather than fixed at init.
        updateAddHeld()
    }

    /**
     * Adds the held item and goes straight into its editor — an entry created and left unopened would be a
     * blank row and nothing else. Coming back lands on a rebuilt list, so nothing has to be refreshed here.
     */
    private fun addHeld() {
        ItemCapture.open(minecraft, this, ItemCapture.heldStack(minecraft))
    }

    private fun updateAddHeld() {
        val button = addHeldButton ?: return
        val addable = ItemCapture.customizable(ItemCapture.heldStack(minecraft))
        button.active = addable
        button.setTooltip(
            Tooltip.create(
                Component.translatable(
                    if (addable) "hex.item_custom.add_held.tooltip" else "hex.item_custom.add_held.unavailable",
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
        ItemCustomizeConfig.save()
    }

    private companion object {
        const val MARGIN = 24
        const val TOP = 32
        const val FOOTER_HEIGHT = 40
        const val BUTTON_WIDTH = 100
        const val BUTTON_HEIGHT = 20
        const val GAP = 6
    }
}
