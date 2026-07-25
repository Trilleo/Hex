package net.trilleo.itemcustom.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.trilleo.itemcustom.ItemCustomization
import net.trilleo.itemcustom.ItemCustomizer
import net.trilleo.itemcustom.gui.ItemPreviewWidget.Companion.SCALE

/**
 * Shows what the item being edited looked like before, what it looks like now, and what it is now called.
 *
 * The two icons are the whole reason this widget exists: a model id or a texture hash is unreadable, and
 * without seeing the result there is no way to tell a hash that is wrong from one that is merely for a skin
 * you did not expect. Everything updates as you type, because the config generation bumps on every keystroke
 * and the render hooks re-resolve on the next frame — this widget does no invalidation of its own.
 *
 * ### Drawing the "before"
 *
 * Anything drawn through the normal item pipeline is customized on the way, including a draw from inside this
 * screen — [net.trilleo.mixin.ItemModelResolverMixin] does not care who asked. So the left-hand icon is a copy
 * of the stack with its custom-data component removed: no Skyblock uuid means no customization matches, and
 * removing that component changes nothing else about how the item draws, since it holds only Hypixel's own
 * metadata.
 *
 * ### When there is no item
 *
 * An entry reached from the management list has no stack behind it — the item is wherever the player left it.
 * The icons are then simply omitted and the name is rendered from the customization against its captured
 * label, which still previews the two fields that matter most without pretending to an item that is not here.
 */
class ItemPreviewWidget(
    x: Int,
    y: Int,
    width: Int,
    private val customization: ItemCustomization,
    stack: ItemStack,
) : AbstractWidget(x, y, width, HEIGHT, Component.empty()) {

    /** The item as it was when the screen opened, or empty when the entry was opened without one. */
    private val stack: ItemStack = if (stack.isEmpty) ItemStack.EMPTY else stack.copy()

    /**
     * The same stack with no customization possible. Built once: the customization can change while this
     * screen is open, but what the item started as cannot.
     */
    private val original: ItemStack =
        if (this.stack.isEmpty) ItemStack.EMPTY
        else this.stack.copy().apply { remove(DataComponents.CUSTOM_DATA) }

    override fun extractWidgetRenderState(
        extractor: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        val font = Minecraft.getInstance().font
        val centreX = x + width / 2

        if (!stack.isEmpty) {
            val arrow = Component.literal("→")
            val arrowWidth = font.width(arrow)
            var iconX = centreX - (ICON_SIZE * 2 + GAP * 2 + arrowWidth) / 2

            drawIcon(extractor, original, iconX)
            iconX += ICON_SIZE + GAP
            extractor.text(font, arrow, iconX, y + ICON_SIZE / 2 - font.lineHeight / 2, ARROW_COLOR)
            iconX += arrowWidth + GAP
            drawIcon(extractor, stack, iconX)
        }

        val name = nameText()
        extractor.text(font, name, centreX - font.width(name) / 2, y + ICON_SIZE + GAP, NAME_COLOR)
    }

    /**
     * The name to preview.
     *
     * With an item in hand this is simply the stack's own name: the `getHoverName` hook has already applied
     * the customization, so it is exactly the text a tooltip would show, colours and all. Without one, the
     * customization is applied directly to the captured label, which produces the same answer for every field
     * that does not depend on the item.
     */
    private fun nameText(): Component {
        if (!stack.isEmpty) return stack.hoverName
        val label = Component.literal(customization.label)
        return ItemCustomizer.nameFor(customization, label) ?: label
    }

    /** Draws one 16×16 item at [SCALE], by scaling the matrix rather than the item — there is no scaled draw. */
    private fun drawIcon(extractor: GuiGraphicsExtractor, item: ItemStack, atX: Int) {
        val pose = extractor.pose()
        pose.pushMatrix()
        pose.translate(atX.toFloat(), y.toFloat())
        pose.scale(SCALE)
        extractor.item(item, 0, 0)
        pose.popMatrix()
    }

    /** Nothing to narrate: the icons are decoration, and every value they reflect has its own row below. */
    override fun updateWidgetNarration(output: NarrationElementOutput) = Unit

    companion object {
        private const val SCALE = 2.0f

        /** A vanilla item sprite is 16×16; at [SCALE] it occupies this much of the screen. */
        private const val ICON_SIZE = 32
        private const val GAP = 6

        /** Fixed, and public, so the screen above can lay the rest of itself out beneath this widget. */
        const val HEIGHT: Int = ICON_SIZE + GAP + 10

        private const val ARROW_COLOR = 0xFFA0A0A0.toInt()
        private const val NAME_COLOR = 0xFFFFFFFF.toInt()
    }
}
