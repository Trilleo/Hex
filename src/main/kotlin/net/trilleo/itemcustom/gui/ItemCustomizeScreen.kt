package net.trilleo.itemcustom.gui

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.trilleo.config.ConfigCategory
import net.trilleo.config.gui.ConfigEntryList
import net.trilleo.config.gui.ConfirmActionScreen
import net.trilleo.itemcustom.GlintOverride
import net.trilleo.itemcustom.ItemCustomization
import net.trilleo.itemcustom.ItemCustomizeConfig
import net.trilleo.itemcustom.ItemCustomizer

/**
 * Edits one item's customization: a live preview over a settings list, and nothing to submit.
 *
 * Reuses [ConfigEntryList] by building a throwaway [ConfigCategory] whose entries close over this one
 * customization, exactly as [net.trilleo.region.gui.RegionEditScreen] and
 * [net.trilleo.reminder.gui.ReminderEditScreen] do — inheriting scrolling, keyboard navigation, per-row reset,
 * inline validation and tooltips, and keeping the editor looking like the rest of the mod. Every field this
 * screen offers is already expressible as a [net.trilleo.config.ConfigEntry], so it adds no widget types.
 *
 * **The stack is a snapshot.** Opening this screen from a Hypixel GUI closes that GUI — vanilla's
 * `AbstractContainerScreen.removed` tells the server the container is closed — so the item that was under the
 * cursor is gone by the time the first frame draws. [ItemPreviewWidget] copies it, which is all the preview
 * needs; the customization itself is keyed by uuid and never reads the stack at all.
 */
class ItemCustomizeScreen(
    private val parent: Screen?,
    private val customization: ItemCustomization,
    /**
     * The item that was captured, or [ItemStack.EMPTY] when the entry was reached from the management list
     * with the item nowhere in reach. The preview degrades to a name-only one rather than refusing to open —
     * the fields it edits are the same either way.
     */
    private val stack: ItemStack = ItemStack.EMPTY,
) : Screen(Component.translatable("hex.item_custom.edit.title")) {

    private var list: ConfigEntryList? = null

    override fun init() {
        addRenderableWidget(StringWidget(MARGIN, 12, width - MARGIN * 2, 12, title, font))
        addRenderableWidget(ItemPreviewWidget(MARGIN, PREVIEW_TOP, width - MARGIN * 2, customization, stack))

        val listTop = PREVIEW_TOP + ItemPreviewWidget.HEIGHT + GAP
        val listHeight = height - listTop - FOOTER_HEIGHT
        list = addRenderableWidget(ConfigEntryList(minecraft, width, listHeight, listTop, this))

        val y = height - 28
        var x = width / 2 - (BUTTON_WIDTH * 2 + GAP) / 2

        addRenderableWidget(
            Button.builder(Component.translatable("hex.item_custom.edit.delete")) { confirmDelete() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.item_custom.edit.done")) { onClose() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )

        val category = buildCategory()
        list?.show(listOf(category.title to category.entries))
    }

    /**
     * The rows, in the order they are usually reached for: what the item is called, then what it looks like.
     *
     * Every setter goes through [ItemCustomizeConfig.markDirty] rather than `save`, because a text field's
     * responder fires on each keystroke. That still re-indexes immediately — so the preview updates as you
     * type — and only the file write is batched.
     */
    private fun buildCategory(): ConfigCategory = ConfigCategory.build("item_custom_edit") {
        toggle(
            "enabled",
            default = true,
            get = { customization.active },
            set = { customization.enabled = it; ItemCustomizeConfig.save() },
        )
        text(
            "name",
            default = "",
            get = { customization.name },
            set = { customization.name = it; ItemCustomizeConfig.markDirty() },
        )
        // Chroma is one of the values this row can hold rather than a switch beside it — see
        // net.trilleo.color.ColorValue.
        color(
            "color",
            default = "",
            chroma = true,
            get = { customization.color },
            set = { customization.color = it.trim(); ItemCustomizeConfig.markDirty() },
        )
        enum(
            "glint",
            default = GlintOverride.DEFAULT,
            get = { customization.glintOrDefault },
            set = { customization.glint = it; ItemCustomizeConfig.save() },
        )
        text(
            "model",
            default = "",
            get = { customization.model },
            set = { customization.model = it.trim(); ItemCustomizeConfig.markDirty() },
            // Reported rather than refused: an id that names nothing draws the missing-model cube, which is a
            // clear enough signal on its own, and blocking the value would make a typo mid-word impossible to
            // type through.
            validate = {
                if (ItemCustomizer.modelKnown(it)) null
                else Component.translatable("hex.config.item_custom_edit.model.invalid")
            },
        )
        text(
            "texture",
            default = "",
            get = { customization.texture },
            // Stored normalized while the box keeps showing exactly what was pasted — the same split
            // net.trilleo.hand.gui.SwingItemList uses for its case folding.
            set = { customization.texture = ItemCustomizer.normalizeTexture(it); ItemCustomizeConfig.markDirty() },
            validate = {
                if (it.isBlank() || ItemCustomizer.normalizeTexture(it).isNotEmpty()) null
                else Component.translatable("hex.config.item_custom_edit.texture.invalid")
            },
        )
        color(
            "dye",
            default = "",
            get = { customization.dye },
            set = { customization.dye = it.trim(); ItemCustomizeConfig.markDirty() },
        )
    }

    private fun confirmDelete() {
        minecraft.setScreen(
            ConfirmActionScreen(
                this,
                Component.translatable("hex.item_custom.delete.title"),
                Component.translatable("hex.item_custom.delete.message"),
                Component.literal(customization.label),
                listOf(
                    ConfirmActionScreen.Choice(Component.translatable("hex.item_custom.delete.confirm")) {
                        ItemCustomizeConfig.remove(customization)
                        minecraft.setScreen(parent)
                    },
                    ConfirmActionScreen.Choice(Component.translatable("hex.item_custom.delete.cancel"), null),
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
        const val PREVIEW_TOP = 30
        const val FOOTER_HEIGHT = 40
        const val BUTTON_WIDTH = 100
        const val BUTTON_HEIGHT = 20
        const val GAP = 6
    }
}
