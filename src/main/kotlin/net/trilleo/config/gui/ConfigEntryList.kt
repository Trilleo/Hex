package net.trilleo.config.gui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.*
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.config.*
import java.util.*
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The scrolling list of settings rows in [HexConfigScreen].
 *
 * Built on vanilla's [ContainerObjectSelectionList], which supplies scrolling, the scrollbar, keyboard
 * navigation and mouse routing to each row's widgets — everything the old hand-rolled screen faked with page
 * arrows. Each row is one [Row] subclass drawing itself through `extractContent`, matching this Minecraft
 * build's extractor-based render pipeline.
 *
 * Rows apply immediately: flipping a toggle or dragging a slider takes effect as you do it, so a settings
 * screen is something you tune while watching the result rather than a form you submit. Persistence is
 * separate — setters mark the config dirty and [net.trilleo.config.ConfigRegistry] batches the write.
 */
class ConfigEntryList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val screen: Screen,
) : ContainerObjectSelectionList<ConfigEntryList.Row>(minecraft, width, height, top, ROW_HEIGHT) {

    /**
     * How many setting rows are currently shown, ignoring category captions. Exposed because
     * `itemCount` is protected, and the screen needs it to know when a search matched nothing.
     */
    var visibleEntries: Int = 0
        private set

    /** Rows are as wide as the panel allows, minus room for the scrollbar. */
    override fun getRowWidth(): Int = width - 24

    override fun scrollBarX(): Int = x + width - 8

    /**
     * Replaces the visible rows.
     *
     * [groups] is a category-title to entries mapping; a title is only drawn when there is more than one
     * group, which is what makes search results readable across tabs without adding noise to a single tab.
     */
    fun show(groups: List<Pair<Component, List<ConfigEntry>>>) {
        clearEntries()
        val showHeadings = groups.size > 1
        for ((title, entries) in groups) {
            if (showHeadings && entries.isNotEmpty()) addEntry(HeadingRow(title))
            entries.forEach { addEntry(rowFor(it)) }
        }
        visibleEntries = groups.sumOf { it.second.size }
        setScrollAmount(0.0)
    }

    private fun rowFor(entry: ConfigEntry): Row = when (entry) {
        is BooleanEntry -> BooleanRow(entry)
        is SliderEntry -> SliderRow(entry)
        is CycleEntry -> CycleRow(entry)
        is EnumEntry<*> -> EnumRow(entry)
        is ActionEntry -> ActionRow(entry, screen)
        is TextEntry -> TextRow(entry)
        is ColorEntry -> ColorRow(entry)
        is KeybindEntry -> KeybindRow(entry)
    }

    // ---- rows ----------------------------------------------------------------------------------------

    /** Base row: draws the label on the left and leaves the right-hand column to the subclass. */
    abstract class Row : ContainerObjectSelectionList.Entry<Row>() {
        /** Widgets in this row, in tab order. */
        protected abstract val widgets: List<AbstractWidget>

        override fun children(): List<AbstractWidget> = widgets

        override fun narratables(): List<NarratableEntry> = widgets

        /** Lays out and draws the row's controls; the label is already drawn. */
        protected abstract fun layout(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float)

        /** The row's label, or null for a row that fills the full width itself. */
        protected open val label: Component? = null

        override fun extractContent(
            extractor: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            delta: Float,
        ) {
            val font = Minecraft.getInstance().font
            label?.let { text ->
                // Truncate rather than overlap the controls: a long translated label must never run under
                // the widget column.
                val available = contentWidth - CONTROL_WIDTH - RESET_WIDTH - GAP * 3
                val drawn = if (font.width(text) <= available) {
                    text
                } else {
                    Component.literal(font.plainSubstrByWidth(text.string, available - font.width("…")) + "…")
                }
                extractor.text(font, drawn, contentX, contentYMiddle - font.lineHeight / 2, LABEL_COLOR)
            }
            layout(extractor, mouseX, mouseY, delta)
        }

        /** Left edge of the fixed-width control column. */
        protected fun controlX(): Int = contentRight - RESET_WIDTH - GAP - CONTROL_WIDTH

        /** Vertically centres a standard 20px widget in the row. */
        protected fun widgetY(): Int = contentYMiddle - WIDGET_HEIGHT / 2

        protected fun place(widget: AbstractWidget, x: Int, width: Int) {
            widget.x = x
            widget.y = widgetY()
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

    /** A category caption, shown only when the list mixes several categories (i.e. while searching). */
    private class HeadingRow(private val title: Component) : Row() {
        override val widgets: List<AbstractWidget> = emptyList()

        override fun layout(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            val font = Minecraft.getInstance().font
            val y = contentYMiddle - font.lineHeight / 2
            extractor.text(font, title.copy().withStyle(ChatFormatting.BOLD), contentX, y, HEADING_COLOR)
            // A rule running to the right edge separates groups without needing extra vertical space.
            val lineX = contentX + font.width(title) + GAP
            extractor.horizontalLine(lineX, contentRight, contentYMiddle, SEPARATOR_COLOR)
        }
    }

    /**
     * A row with a reset button that returns the setting to its default.
     *
     * The button is only enabled while the value differs from the default, so the row doubles as an
     * at-a-glance indicator of what has been changed from stock.
     */
    private abstract class ResettableRow(
        entryLabel: Component,
        rowTooltip: Component?,
        private val isDefault: () -> Boolean,
        onReset: () -> Unit,
    ) : Row() {
        override val label: Component = entryLabel

        protected val resetButton: Button = Button.builder(Component.literal("⟲")) { onReset() }
            .bounds(0, 0, RESET_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.config.reset_entry")))
            .build()

        /**
         * The row's hover text, for the subclass to hang on whichever control the user will point at.
         *
         * Applied by each subclass rather than from an `init` block here: a base-class initialiser runs
         * before the subclass's properties exist, so reaching into the subclass from one hands it a widget
         * that is still null.
         */
        protected val rowTooltip: Tooltip? = rowTooltip?.let(Tooltip::create)

        protected fun drawReset(
            extractor: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            delta: Float,
        ) {
            resetButton.active = !isDefault()
            place(resetButton, contentRight - RESET_WIDTH, RESET_WIDTH)
            draw(resetButton, extractor, mouseX, mouseY, delta)
        }
    }

    private class BooleanRow(private val entry: BooleanEntry) : ResettableRow(
        entry.label,
        entry.tooltip,
        isDefault = { entry.get() == entry.default },
        onReset = { entry.set(entry.default) },
    ) {
        private val toggle: Button = Button.builder(onOff(entry.get())) { button ->
            entry.set(!entry.get())
            button.message = onOff(entry.get())
        }.bounds(0, 0, CONTROL_WIDTH, WIDGET_HEIGHT).build()
            .apply { this@BooleanRow.rowTooltip?.let(::setTooltip) }

        override val widgets: List<AbstractWidget> = listOf(toggle, resetButton)

        override fun layout(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            // Re-read every frame: a profile switch or reset can change the value from outside this row.
            toggle.message = onOff(entry.get())
            place(toggle, controlX(), CONTROL_WIDTH)
            draw(toggle, extractor, mouseX, mouseY, delta)
            drawReset(extractor, mouseX, mouseY, delta)
        }

        private companion object {
            fun onOff(on: Boolean): Component = Component.translatable(
                if (on) "hex.config.on" else "hex.config.off",
            ).withStyle(if (on) ChatFormatting.GREEN else ChatFormatting.RED)
        }
    }

    private class SliderRow(private val entry: SliderEntry) : ResettableRow(
        entry.label,
        entry.tooltip,
        isDefault = { entry.get() == entry.default },
        onReset = { entry.set(entry.default) },
    ) {
        private val slider = object : AbstractSliderButton(
            0, 0, CONTROL_WIDTH, WIDGET_HEIGHT,
            entry.format(entry.get()),
            toFraction(entry, entry.get()),
        ) {
            override fun updateMessage() {
                message = entry.format(fromFraction(entry, value))
            }

            override fun applyValue() {
                entry.set(fromFraction(entry, value))
            }

            /** Lets the row push an externally changed value back onto the handle. */
            fun syncFrom(v: Double) {
                if (fromFraction(entry, value) != v) {
                    value = toFraction(entry, v)
                    updateMessage()
                }
            }
        }.apply { this@SliderRow.rowTooltip?.let(::setTooltip) }

        override val widgets: List<AbstractWidget> = listOf(slider, resetButton)

        override fun layout(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            // Only sync while the handle is idle; doing it mid-drag would fight the user's mouse.
            if (!slider.isFocused && !slider.isHovered) slider.syncFrom(entry.get())
            place(slider, controlX(), CONTROL_WIDTH)
            draw(slider, extractor, mouseX, mouseY, delta)
            drawReset(extractor, mouseX, mouseY, delta)
        }

        private companion object {
            /**
             * Sliders work in 0..1 internally, so values are projected onto the step grid and rounded back
             * on the way out; without the rounding a 0.01-step slider drifts into values like
             * `0.30000000000000004` and writes them to the config file.
             */
            fun toFraction(entry: SliderEntry, value: Double): Double {
                val span = entry.max - entry.min
                return if (span <= 0.0) 0.0 else ((value - entry.min) / span).coerceIn(0.0, 1.0)
            }

            fun fromFraction(entry: SliderEntry, fraction: Double): Double {
                val steps = ((entry.max - entry.min) / entry.step).roundToLong().coerceAtLeast(1)
                val notch = (fraction * steps).roundToInt().toLong().coerceIn(0, steps)
                val decimals = when {
                    entry.step >= 1.0 -> 0
                    entry.step >= 0.1 -> 1
                    entry.step >= 0.01 -> 2
                    else -> 3
                }
                var factor = 1.0
                repeat(decimals) { factor *= 10.0 }
                val raw = entry.min + notch * entry.step
                return ((raw * factor).roundToLong() / factor).coerceIn(entry.min, entry.max)
            }
        }
    }

    private class CycleRow(private val entry: CycleEntry) : ResettableRow(
        entry.label,
        entry.tooltip,
        isDefault = { entry.get() == entry.default },
        onReset = { entry.set(entry.default) },
    ) {
        private val button: Button = Button.builder(current()) {
            if (entry.options.isNotEmpty()) entry.set((entry.get() + 1) % entry.options.size)
        }.bounds(0, 0, CONTROL_WIDTH, WIDGET_HEIGHT).build()
            .apply { this@CycleRow.rowTooltip?.let(::setTooltip) }

        override val widgets: List<AbstractWidget> = listOf(button, resetButton)

        override fun layout(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            button.message = current()
            place(button, controlX(), CONTROL_WIDTH)
            draw(button, extractor, mouseX, mouseY, delta)
            drawReset(extractor, mouseX, mouseY, delta)
        }

        private fun current(): Component =
            entry.options.getOrElse(entry.get()) { Component.empty() }
    }

    private class EnumRow<T : Enum<T>>(private val entry: EnumEntry<T>) : ResettableRow(
        entry.label,
        entry.tooltip,
        isDefault = { entry.get() == entry.default },
        onReset = { entry.set(entry.default) },
    ) {
        private val constants: List<T> = entry.type.enumConstants.toList()

        private val button: Button = Button.builder(entry.nameOf(entry.get())) {
            if (constants.isNotEmpty()) {
                val next = (constants.indexOf(entry.get()) + 1).mod(constants.size)
                entry.set(constants[next])
            }
        }.bounds(0, 0, CONTROL_WIDTH, WIDGET_HEIGHT).build()
            .apply { this@EnumRow.rowTooltip?.let(::setTooltip) }

        override val widgets: List<AbstractWidget> = listOf(button, resetButton)

        override fun layout(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            button.message = entry.nameOf(entry.get())
            place(button, controlX(), CONTROL_WIDTH)
            draw(button, extractor, mouseX, mouseY, delta)
            drawReset(extractor, mouseX, mouseY, delta)
        }
    }

    /** An action fills the row: the label is the button, because there is no value to show beside it. */
    private class ActionRow(entry: ActionEntry, screen: Screen) : Row() {
        private val button: Button = Button.builder(entry.label) { entry.onClick(screen) }
            .bounds(0, 0, CONTROL_WIDTH, WIDGET_HEIGHT)
            .also { builder -> entry.tooltip?.let { builder.tooltip(Tooltip.create(it)) } }
            .build()

        override val widgets: List<AbstractWidget> = listOf(button)

        override fun layout(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            place(button, contentX, contentWidth)
            draw(button, extractor, mouseX, mouseY, delta)
        }
    }

    private class TextRow(private val entry: TextEntry) : ResettableRow(
        entry.label,
        entry.tooltip,
        isDefault = { entry.get() == entry.default },
        onReset = { entry.set(entry.default) },
    ) {
        private val field = EditBox(
            Minecraft.getInstance().font, 0, 0, CONTROL_WIDTH, WIDGET_HEIGHT, entry.label,
        ).apply {
            value = entry.get()
            setMaxLength(256)
            setResponder { text -> if (entry.validate(text) == null) entry.set(text) }
            this@TextRow.rowTooltip?.let(::setTooltip)
        }

        override val widgets: List<AbstractWidget> = listOf(field, resetButton)

        override fun layout(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            if (!field.isFocused && field.value != entry.get()) field.value = entry.get()
            place(field, controlX(), CONTROL_WIDTH)
            draw(field, extractor, mouseX, mouseY, delta)
            // An invalid value is flagged in place rather than blocking the row, since edits apply live.
            entry.validate(field.value)?.let { error ->
                val font = Minecraft.getInstance().font
                extractor.text(font, error, contentX, contentYMiddle + font.lineHeight / 2 + 1, ERROR_COLOR)
            }
            drawReset(extractor, mouseX, mouseY, delta)
        }
    }

    /** A hex colour field with a swatch, so the value is legible without decoding the digits. */
    private class ColorRow(private val entry: ColorEntry) : ResettableRow(
        entry.label,
        entry.tooltip,
        isDefault = { entry.get() == entry.default },
        onReset = { entry.set(entry.default) },
    ) {
        private val field = EditBox(
            Minecraft.getInstance().font, 0, 0, CONTROL_WIDTH - SWATCH - GAP, WIDGET_HEIGHT, entry.label,
        ).apply {
            value = entry.get()
            setMaxLength(9)
            setResponder { text -> if (parse(text) != null) entry.set(text) }
            this@ColorRow.rowTooltip?.let(::setTooltip)
        }

        override val widgets: List<AbstractWidget> = listOf(field, resetButton)

        override fun layout(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            if (!field.isFocused && field.value != entry.get()) field.value = entry.get()
            place(field, controlX(), CONTROL_WIDTH - SWATCH - GAP)
            draw(field, extractor, mouseX, mouseY, delta)

            val swatchX = controlX() + CONTROL_WIDTH - SWATCH
            val top = widgetY()
            val argb = parse(field.value)?.let { if (entry.alpha) it else it or OPAQUE } ?: 0
            extractor.fill(swatchX, top, swatchX + SWATCH, top + WIDGET_HEIGHT, argb)
            extractor.outline(swatchX, top, SWATCH, WIDGET_HEIGHT, SWATCH_BORDER)
            drawReset(extractor, mouseX, mouseY, delta)
        }

        private companion object {
            const val SWATCH = 20
            const val OPAQUE = 0xFF000000.toInt()
            const val SWATCH_BORDER = 0xFF000000.toInt()

            fun parse(text: String): Int? = text.removePrefix("#").toLongOrNull(16)?.toInt()
        }
    }

    /**
     * Captures a key combination in place: click to arm, then the next key press is taken, with Escape
     * cancelling. Modifiers held at the time are recorded alongside the key.
     */
    private class KeybindRow(private val entry: KeybindEntry) : ResettableRow(
        entry.label,
        entry.tooltip,
        isDefault = { entry.get() == entry.default },
        onReset = { entry.set(entry.default) },
    ) {
        private var listening = false

        private val button: Button = Button.builder(describe(entry.get())) {
            listening = true
        }.bounds(0, 0, CONTROL_WIDTH, WIDGET_HEIGHT).build()
            .apply { this@KeybindRow.rowTooltip?.let(::setTooltip) }

        override val widgets: List<AbstractWidget> = listOf(button, resetButton)

        override fun keyPressed(event: net.minecraft.client.input.KeyEvent): Boolean {
            if (!listening) return super.keyPressed(event)
            listening = false
            if (event.key != InputConstants.KEY_ESCAPE) {
                entry.set(
                    KeyCombo(
                        keyCode = event.key,
                        ctrl = (event.modifiers and InputConstants.MOD_CONTROL) != 0,
                        shift = (event.modifiers and InputConstants.MOD_SHIFT) != 0,
                        alt = (event.modifiers and InputConstants.MOD_ALT) != 0,
                    ),
                )
            }
            return true
        }

        override fun layout(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            button.message = if (listening) {
                Component.translatable("hex.config.press_a_key").withStyle(ChatFormatting.YELLOW)
            } else {
                describe(entry.get())
            }
            place(button, controlX(), CONTROL_WIDTH)
            draw(button, extractor, mouseX, mouseY, delta)
            drawReset(extractor, mouseX, mouseY, delta)
        }

        private companion object {
            fun describe(combo: KeyCombo): Component {
                if (combo.keyCode == InputConstants.UNKNOWN.value) {
                    return Component.translatable("hex.config.unbound")
                }
                val parts = buildList {
                    if (combo.ctrl) add("Ctrl")
                    if (combo.shift) add("Shift")
                    if (combo.alt) add("Alt")
                    add(InputConstants.Type.KEYSYM.getOrCreate(combo.keyCode).displayName.string)
                }
                return Component.literal(parts.joinToString(" + "))
            }
        }
    }

    companion object {
        const val ROW_HEIGHT = 24
        private const val WIDGET_HEIGHT = 20
        private const val CONTROL_WIDTH = 100
        private const val RESET_WIDTH = 20
        private const val GAP = 6

        private const val LABEL_COLOR = 0xFFE8E8E8.toInt()
        private const val HEADING_COLOR = 0xFFFFD25F.toInt()
        private const val SEPARATOR_COLOR = 0x40FFFFFF
        private const val ERROR_COLOR = 0xFFFF6B6B.toInt()

        /** Whether an entry matches a search query, tested against its label and tooltip. */
        fun matches(entry: ConfigEntry, query: String): Boolean {
            if (query.isBlank()) return true
            val needle = query.lowercase(Locale.ROOT)
            return entry.label.string.lowercase(Locale.ROOT).contains(needle) ||
                    entry.tooltip?.string?.lowercase(Locale.ROOT)?.contains(needle) == true
        }

        /** Categories reduced to just the entries matching [query], dropping any left empty. */
        fun filter(categories: List<ConfigCategory>, query: String): List<Pair<Component, List<ConfigEntry>>> =
            categories.map { it.title to it.entries.filter { entry -> matches(entry, query) } }
                .filter { it.second.isNotEmpty() }
    }
}
