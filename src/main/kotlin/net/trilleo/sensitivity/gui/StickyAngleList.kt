package net.trilleo.sensitivity.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.*
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.trilleo.sensitivity.SensitivityConfig
import net.trilleo.sensitivity.model.StickyAngle
import net.trilleo.sensitivity.model.StickyAxis
import java.util.*

/**
 * The scrolling list of custom angles in [StickyAnglesScreen].
 *
 * Built on [ContainerObjectSelectionList] the same way [net.trilleo.region.gui.RegionList] is, and holding
 * rows by reference identity for the same reason — [StickyAngle] is deliberately not a data class, so deleting
 * a row is an identity remove that cannot take an equal-looking sibling with it. Two entries reading "Yaw 45°"
 * is an entirely ordinary thing to end up with while typing, which makes that the realistic case rather than
 * the exotic one.
 */
class StickyAngleList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val screen: StickyAnglesScreen,
) : ContainerObjectSelectionList<StickyAngleList.Row>(minecraft, width, height, top, ROW_HEIGHT) {

    override fun getRowWidth(): Int = width - 24

    override fun scrollBarX(): Int = x + width - 8

    /** Replaces the visible rows, or shows the empty-list hint. */
    fun show(angles: List<StickyAngle>, emptyHint: Component) {
        val scroll = scrollAmount()
        clearEntries()
        if (angles.isEmpty()) {
            addEntry(HintRow(emptyHint))
        } else {
            angles.forEach { addEntry(AngleRow(it, screen)) }
        }
        // Preserve the scroll position: this is called after every add and delete, and snapping to the top
        // each time would throw away the player's place in a long list.
        setScrollAmount(scroll)
    }

    fun scrollToBottom() {
        setScrollAmount(maxScrollAmount().toDouble())
    }

    // ---- rows ------------------------------------------------------------------------------------------

    abstract class Row : ContainerObjectSelectionList.Entry<Row>() {
        protected abstract val widgets: List<AbstractWidget>

        override fun children(): List<AbstractWidget> = widgets

        override fun narratables(): List<NarratableEntry> = widgets

        protected fun place(widget: AbstractWidget, x: Int, width: Int) {
            widget.x = x
            widget.y = contentYMiddle - WIDGET_HEIGHT / 2
            widget.width = width
        }

        protected fun draw(
            widget: AbstractWidget,
            extractor: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            delta: Float,
        ) = widget.extractRenderState(extractor, mouseX, mouseY, delta)
    }

    /** One angle: an on/off button, the axis it applies to, the number itself, and delete. */
    private class AngleRow(
        private val angle: StickyAngle,
        private val screen: StickyAnglesScreen,
    ) : Row() {

        private val toggleButton: Button = Button.builder(toggleLabel(angle.enabled)) {
            angle.enabled = !angle.enabled
            SensitivityConfig.save()
        }.bounds(0, 0, TOGGLE_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.sticky.toggle.tooltip")))
            .build()

        private val degreesField: EditBox = EditBox(
            Minecraft.getInstance().font, 0, 0, FIELD_WIDTH, WIDGET_HEIGHT,
            Component.translatable("hex.sticky.degrees"),
        ).apply {
            value = format(angle.degrees)
            setMaxLength(10)
            // Written through only once the text is a number, so "-" and "1." on the way to "-1.5" leave the
            // setting alone instead of snapping it to something the player is still in the middle of typing.
            // Not folded or clamped here either: that happens when the screen closes, so typing "180" does
            // not become "-180" under the cursor at the moment the third digit lands.
            setResponder { text ->
                text.trim().toDoubleOrNull()?.takeIf { it.isFinite() }?.let {
                    angle.degrees = it
                    SensitivityConfig.markDirty()
                }
            }
            setTooltip(Tooltip.create(Component.translatable("hex.sticky.degrees.tooltip")))
        }

        private val axisButton: Button = Button.builder(axisLabel(angle.axis)) {
            angle.axis = if (angle.axis == StickyAxis.YAW) StickyAxis.PITCH else StickyAxis.YAW
            // Straight back through the normaliser: a yaw of 170 is a perfectly good yaw and an impossible
            // pitch, so flipping the axis has to bring the number with it rather than leave one out of range.
            SensitivityConfig.normalizeNow()
            degreesField.value = format(angle.degrees)
            SensitivityConfig.save()
        }.bounds(0, 0, AXIS_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.sticky.axis.tooltip")))
            .build()

        private val deleteButton: Button = Button.builder(Component.literal("✕")) {
            SensitivityConfig.settings.stickyAngles.remove(angle)
            SensitivityConfig.save()
            screen.refreshRows()
        }.bounds(0, 0, DELETE_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.sticky.delete.tooltip")))
            .build()

        override val widgets: List<AbstractWidget> = listOf(toggleButton, axisButton, degreesField, deleteButton)

        override fun extractContent(
            extractor: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            delta: Float,
        ) {
            toggleButton.message = toggleLabel(angle.enabled)
            axisButton.message = axisLabel(angle.axis)

            var x = contentX
            place(toggleButton, x, TOGGLE_WIDTH)
            draw(toggleButton, extractor, mouseX, mouseY, delta)
            x += TOGGLE_WIDTH + GAP

            place(axisButton, x, AXIS_WIDTH)
            draw(axisButton, extractor, mouseX, mouseY, delta)
            x += AXIS_WIDTH + GAP

            place(degreesField, x, FIELD_WIDTH)
            draw(degreesField, extractor, mouseX, mouseY, delta)
            x += FIELD_WIDTH + GAP

            // What the angle actually points at, so a number is a direction rather than arithmetic. Dimmed
            // while the entry is off, which is the only other thing the row has to say.
            val font = Minecraft.getInstance().font
            val color = if (angle.enabled) FACING_COLOR else DISABLED_COLOR
            val facing = facing(angle)
            val available = contentRight - DELETE_WIDTH - GAP - x
            if (font.width(facing) <= available) {
                extractor.text(font, facing, x, contentYMiddle - font.lineHeight / 2, color)
            }

            place(deleteButton, contentRight - DELETE_WIDTH, DELETE_WIDTH)
            draw(deleteButton, extractor, mouseX, mouseY, delta)
        }

        private companion object {
            fun toggleLabel(enabled: Boolean): Component = Component.literal(if (enabled) "✔" else "✖")

            fun axisLabel(axis: StickyAxis): Component = Component.translatable(
                if (axis == StickyAxis.YAW) "hex.sticky.axis.yaw" else "hex.sticky.axis.pitch",
            )

            /** Trailing zeroes trimmed, so a captured 45.0 reads as "45" and a typed 22.5 keeps its half. */
            fun format(degrees: Double): String {
                val text = String.format(Locale.ROOT, "%.2f", degrees)
                return text.trimEnd('0').trimEnd('.').ifEmpty { "0" }
            }

            /**
             * The direction in words — "south", "up", "level" — for the angles that have one, and nothing at
             * all for the ones in between.
             *
             * Yaw 0 being south and −90 being east is Minecraft's own convention and one nobody holds in their
             * head, so a bare number in this list would be a number you have to go and check by looking.
             */
            fun facing(angle: StickyAngle): Component {
                val key = when (angle.axis) {
                    StickyAxis.YAW -> when (Mth.wrapDegrees(angle.degrees)) {
                        0.0 -> "south"
                        90.0 -> "west"
                        180.0, -180.0 -> "north"
                        -90.0 -> "east"
                        else -> return Component.empty()
                    }

                    StickyAxis.PITCH -> when (angle.degrees) {
                        0.0 -> "level"
                        -90.0 -> "up"
                        90.0 -> "down"
                        else -> return Component.empty()
                    }
                }
                return Component.translatable("hex.sticky.facing.$key")
            }
        }
    }

    /** Shown in place of rows when the list is empty, so the screen explains itself rather than sitting bare. */
    private class HintRow(private val text: Component) : Row() {
        override val widgets: List<AbstractWidget> = emptyList()

        override fun extractContent(
            extractor: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            delta: Float,
        ) {
            val font = Minecraft.getInstance().font
            val x = contentX + (contentWidth - font.width(text)) / 2
            extractor.text(font, text, x, contentYMiddle - font.lineHeight / 2, HINT_COLOR)
        }
    }

    companion object {
        const val ROW_HEIGHT = 26
        private const val WIDGET_HEIGHT = 20
        private const val TOGGLE_WIDTH = 22
        private const val AXIS_WIDTH = 54
        private const val FIELD_WIDTH = 60
        private const val DELETE_WIDTH = 22
        private const val GAP = 6

        private const val FACING_COLOR = 0xFF909090.toInt()
        private const val DISABLED_COLOR = 0xFF606060.toInt()
        private const val HINT_COLOR = 0xFFA0A0A0.toInt()
    }
}
