package net.trilleo.config.cloth

import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.util.*
import java.util.function.Supplier

/**
 * A row that runs an action instead of holding a value — the Cloth counterpart of
 * [net.trilleo.config.ActionEntry].
 *
 * Cloth ships toggles, sliders, fields and selectors but has no button entry, so this is hand-written. It
 * mirrors `BooleanListEntry`: extend [TooltipListEntry] so the tooltip machinery comes for free, expose the
 * button through [children] so the list forwards clicks to it, and position and draw it from
 * [extractRenderState] — this Minecraft build has no `render(GuiGraphics)`, and Cloth 26.1 is already built
 * against the extractor pipeline.
 *
 * The button spans the whole row rather than sitting in Cloth's right-hand control column, because an action
 * label ("Check for updates now") *is* the control; there is no separate value to show beside it.
 *
 * The type parameter is [Unit] since there is nothing to store: [getValue] is meaningless here and
 * [getDefaultValue] is deliberately empty, which is also what keeps Cloth from drawing a reset arrow.
 */
class ActionListEntry(
    fieldName: Component,
    tooltipSupplier: Supplier<Optional<Array<Component>>>,
    private val onClick: (Screen) -> Unit,
) : TooltipListEntry<Unit>(fieldName, tooltipSupplier) {

    // configScreen is only set once the entry is attached, so it is nullable in principle even though a
    // click cannot reach an unattached row; fall back to whatever screen is open rather than risk an NPE
    // inside a GUI callback.
    private val button: Button = Button.builder(fieldName) {
        (configScreen ?: Minecraft.getInstance().screen)?.let(onClick)
    }
        .bounds(0, 0, 150, 20)
        .build()

    private val widgets: List<Button> = listOf(button)

    override fun getValue() {
        // Nothing to hold; the row exists for its side effect.
    }

    override fun getDefaultValue(): Optional<Unit> = Optional.empty()

    /** Never dirty — an action changes whatever it changes directly, not through this entry's value. */
    override fun isEdited(): Boolean = false

    override fun save() {
        // Nothing to persist.
    }

    override fun extractRenderState(
        extractor: GuiGraphicsExtractor,
        index: Int,
        y: Int,
        x: Int,
        entryWidth: Int,
        entryHeight: Int,
        mouseX: Int,
        mouseY: Int,
        isHovered: Boolean,
        delta: Float,
    ) {
        super.extractRenderState(
            extractor, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta,
        )
        // No field-name text: the label rides on the button, so the row is the button.
        button.active = isEditable
        button.setX(x)
        button.setY(y)
        button.width = entryWidth
        button.extractRenderState(extractor, mouseX, mouseY, delta)
    }

    override fun children(): List<GuiEventListener> = widgets

    override fun narratables(): List<NarratableEntry> = widgets
}
