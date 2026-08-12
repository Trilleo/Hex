package net.trilleo.config.gui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.*
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.color.ColorValue
import net.trilleo.color.gui.ColorPickerScreen
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
     *
     * Set [preserveScroll] when the rows are being rebuilt underneath a player who is part-way through
     * editing them — a screen whose entry list changes shape in response to one of its own controls, such as
     * a trigger kind that swaps which fields apply. Switching tabs or running a search should leave it off:
     * there, the top of the new list is exactly where the player expects to land.
     */
    fun show(groups: List<Pair<Component, List<ConfigEntry>>>, preserveScroll: Boolean = false) {
        val scroll = scrollAmount()
        clearEntries()
        val showHeadings = groups.size > 1
        for ((title, entries) in groups) {
            if (showHeadings && entries.isNotEmpty()) addEntry(HeadingRow(title))
            entries.forEach { addEntry(rowFor(it)) }
        }
        visibleEntries = groups.sumOf { it.second.size }
        setScrollAmount(if (preserveScroll) scroll else 0.0)
    }

    // ---- completion popup ------------------------------------------------------------------------------

    /** What a completing [TextRow] wants shown, in screen coordinates. */
    class Popup(
        val x: Int,
        val anchorTop: Int,
        val anchorBottom: Int,
        val minWidth: Int,
        val entries: List<String>,
        val selected: Int,
    )

    /** Where the popup actually landed last frame, so a click can be tested against it. */
    private class Placed(
        val row: TextRow,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val first: Int,
        val shown: Int,
    )

    private var placed: Placed? = null

    /**
     * Draws the rows, then the completion popup on top of them.
     *
     * After `super` returns the list's scissor is off again, which is the whole reason the popup is drawn here
     * rather than by the row that owns it: inside [extractListItems] it would be clipped to the list and then
     * painted over by every row below. Drawing it last also means it covers those rows, which is exactly what
     * a popup should do.
     */
    override fun extractWidgetRenderState(
        extractor: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        super.extractWidgetRenderState(extractor, mouseX, mouseY, delta)

        val row = children().filterIsInstance<TextRow>().firstOrNull { it.popup() != null }
        val popup = row?.popup()
        if (row == null || popup == null) {
            placed = null
            return
        }
        placed = drawPopup(extractor, row, popup)
    }

    private fun drawPopup(extractor: GuiGraphicsExtractor, row: TextRow, popup: Popup): Placed {
        val font = minecraft.font

        // Window the list around the highlighted entry, so walking past the bottom scrolls rather than stops.
        val shown = minOf(POPUP_ROWS, popup.entries.size)
        val first = (popup.selected - shown + 1)
            .coerceAtLeast(0)
            .coerceAtMost((popup.entries.size - shown).coerceAtLeast(0))
        val window = popup.entries.subList(first, first + shown)

        // Never off the right edge of the list; a suggestion running under the scrollbar is unreadable. The
        // floor keeps the range valid when the field sits close to that edge, so the clamp cannot invert.
        val available = (right - popup.x - GAP).coerceAtLeast(popup.minWidth)
        val width = window.maxOf { font.width(it) + POPUP_PADDING * 2 }.coerceIn(popup.minWidth, available)
        val height = shown * POPUP_ROW_HEIGHT + 2

        // Below the field by preference, above it when there is no room — the bottom rows of a long list would
        // otherwise open their popup past the end of the screen.
        val y = if (popup.anchorBottom + height <= bottom) popup.anchorBottom else popup.anchorTop - height

        extractor.fill(popup.x, y, popup.x + width, y + height, POPUP_BACKGROUND)
        extractor.outline(popup.x, y, width, height, POPUP_BORDER)

        window.forEachIndexed { index, text ->
            val top = y + 1 + index * POPUP_ROW_HEIGHT
            val isSelected = first + index == popup.selected
            if (isSelected) {
                extractor.fill(popup.x + 1, top, popup.x + width - 1, top + POPUP_ROW_HEIGHT, POPUP_SELECTED)
            }
            val drawn = if (font.width(text) <= width - POPUP_PADDING * 2) {
                text
            } else {
                font.plainSubstrByWidth(text, width - POPUP_PADDING * 2 - font.width("…")) + "…"
            }
            extractor.text(
                font,
                drawn,
                popup.x + POPUP_PADDING,
                top + (POPUP_ROW_HEIGHT - font.lineHeight) / 2,
                if (isSelected) POPUP_SELECTED_TEXT else POPUP_TEXT,
            )
        }

        return Placed(row, popup.x, y, width, height, first, shown)
    }

    /**
     * Lets a click land on the popup before the rows underneath it get a chance.
     *
     * The popup is drawn over rows that still occupy that space as far as the list's hit-testing is concerned,
     * so without this a click on a suggestion would focus whatever row happens to be behind it.
     */
    override fun mouseClicked(event: net.minecraft.client.input.MouseButtonEvent, doubled: Boolean): Boolean {
        placed?.let { p ->
            val x = event.x
            val y = event.y
            if (x >= p.x && x < p.x + p.width && y >= p.y && y < p.y + p.height) {
                val index = p.first + (((y - p.y - 1) / POPUP_ROW_HEIGHT).toInt()).coerceIn(0, p.shown - 1)
                p.row.choose(index)
                return true
            }
        }
        return super.mouseClicked(event, doubled)
    }

    private fun rowFor(entry: ConfigEntry): Row = when (entry) {
        is BooleanEntry -> BooleanRow(entry)
        is SliderEntry -> SliderRow(entry)
        is CycleEntry -> CycleRow(entry)
        is EnumEntry<*> -> EnumRow(entry)
        is ActionEntry -> ActionRow(entry, screen)
        is TextEntry -> TextRow(entry)
        is ColorEntry -> ColorRow(entry, screen)
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

    /**
     * A text field, optionally completing.
     *
     * When its [TextEntry] offers a vocabulary, the row keeps a ranked shortlist of what the typed text could
     * become and claims Tab and the arrow keys while the field has focus — the same contract the chat box's
     * command suggestions have, so it needs no explaining. The popup itself is drawn by
     * [ConfigEntryList.extractWidgetRenderState] rather than here, because this row is inside a scissor and
     * anything it drew below itself would be clipped by the list and painted over by the next row.
     *
     * **Escape is deliberately not claimed.** [Screen.keyPressed] answers it before any child sees it, so a
     * row that tried to dismiss its popup with Escape would be writing code that never runs. The popup closes
     * by the field losing focus, which is the other thing a player reaches for.
     */
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

        /** The shortlist for what is typed now, best first. Empty means no popup. */
        private var matches: List<String> = emptyList()

        private var selected: Int = 0

        /** The text [matches] was computed for, so the ranking runs on a change rather than every frame. */
        private var rankedFor: String? = null

        override val widgets: List<AbstractWidget> = listOf(field, resetButton)

        /** Where the popup should hang, or null when there is nothing to show. */
        fun popup(): Popup? {
            if (!field.isFocused || matches.isEmpty()) return null
            return Popup(field.x, field.y, field.y + WIDGET_HEIGHT, field.width, matches, selected)
        }

        /** Applies the candidate at [index]; called when one is clicked. */
        fun choose(index: Int) {
            matches.getOrNull(index)?.let { apply(it) }
        }

        override fun keyPressed(event: net.minecraft.client.input.KeyEvent): Boolean {
            if (field.isFocused && matches.isNotEmpty()) {
                when (event.key) {
                    InputConstants.KEY_TAB -> {
                        accept()
                        return true
                    }

                    InputConstants.KEY_DOWN -> {
                        move(1)
                        return true
                    }

                    InputConstants.KEY_UP -> {
                        move(-1)
                        return true
                    }
                }
            }
            return super.keyPressed(event)
        }

        /**
         * Takes the highlighted candidate — or, when it is already in the field, moves on to the next.
         *
         * That second half is what makes holding Tab walk the list, the way it does in chat: applying a
         * candidate narrows the ranking to it and its longer siblings, so without it a second press would
         * re-apply the same value forever.
         */
        private fun accept() {
            val pick = matches.getOrNull(selected) ?: return
            if (pick == field.value) move(1) else apply(pick)
        }

        private fun apply(pick: String) {
            // Assigning the value fires the responder, which is what writes it through to the setting.
            field.value = pick
            field.moveCursorToEnd(false)
            // Force a re-rank next frame: the shortlist for the new text is a different one.
            rankedFor = null
        }

        private fun move(by: Int) {
            if (matches.isEmpty()) return
            selected = (selected + by).mod(matches.size)
        }

        /** Re-ranks when the typed text has changed. A row with no vocabulary settles on an empty list. */
        private fun refresh() {
            if (!field.isFocused) {
                matches = emptyList()
                rankedFor = null
                return
            }
            val typed = field.value
            if (typed == rankedFor) return
            rankedFor = typed
            matches = Suggestions.rank(entry.suggestions(), typed)
            selected = 0
        }

        override fun layout(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            if (!field.isFocused && field.value != entry.get()) field.value = entry.get()
            place(field, controlX(), CONTROL_WIDTH)
            draw(field, extractor, mouseX, mouseY, delta)
            refresh()
            // An invalid value is flagged in place rather than blocking the row, since edits apply live.
            entry.validate(field.value)?.let { error ->
                val font = Minecraft.getInstance().font
                extractor.text(font, error, contentX, contentYMiddle + font.lineHeight / 2 + 1, ERROR_COLOR)
            }
            drawReset(extractor, mouseX, mouseY, delta)
        }
    }

    /**
     * A colour: the value as text, plus a swatch that opens the mod's colour picker.
     *
     * The swatch is a real [Button] with the colour painted over it rather than a rectangle this row
     * hit-tests itself, so it inherits click routing, keyboard focus and the tooltip from the list for
     * nothing; hover is signalled by its border, since the colour covers vanilla's own highlight.
     *
     * The text field stays, and it is not redundant. Typing is how a colour arrives from a wiki page or a
     * teammate, and the field is also where an alpha byte is written — the picker deliberately has no alpha
     * slider, so a translucent value is carried through unchanged rather than edited by dragging.
     */
    private class ColorRow(private val entry: ColorEntry, private val screen: Screen) : ResettableRow(
        entry.label,
        entry.tooltip,
        isDefault = { entry.get() == entry.default },
        onReset = { entry.set(entry.default) },
    ) {
        private val field = EditBox(
            Minecraft.getInstance().font, 0, 0, CONTROL_WIDTH - SWATCH - GAP, WIDGET_HEIGHT, entry.label,
        ).apply {
            value = entry.get()
            setMaxLength(ColorValue.MAX_LENGTH)
            // Applied only once the text is a value this setting accepts, so a colour can be typed through
            // its half-finished states without the setting flickering on every keystroke.
            setResponder { text -> if (accepts(text)) entry.set(ColorValue.normalize(text, entry.alpha)) }
            this@ColorRow.rowTooltip?.let(::setTooltip)
        }

        private val swatch: Button = Button.builder(Component.empty()) { open() }
            .bounds(0, 0, SWATCH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.color.open.tooltip")))
            .build()

        override val widgets: List<AbstractWidget> = listOf(field, swatch, resetButton)

        private fun accepts(text: String): Boolean = when {
            ColorValue.isChroma(text) -> entry.chroma
            ColorValue.isNone(text) -> entry.optional
            else -> ColorValue.parse(text) != null
        }

        private fun open() {
            Minecraft.getInstance().setScreen(
                ColorPickerScreen(
                    parent = screen,
                    subject = entry.label,
                    initial = entry.get(),
                    alpha = entry.alpha,
                    allowChroma = entry.chroma,
                    allowNone = entry.optional,
                    apply = entry.set,
                ),
            )
        }

        override fun layout(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            if (!field.isFocused && field.value != entry.get()) field.value = entry.get()
            place(field, controlX(), CONTROL_WIDTH - SWATCH - GAP)
            draw(field, extractor, mouseX, mouseY, delta)

            val swatchX = controlX() + CONTROL_WIDTH - SWATCH
            val top = widgetY()
            place(swatch, swatchX, SWATCH)
            draw(swatch, extractor, mouseX, mouseY, delta)

            val spec = entry.get()
            // Resolved every frame rather than cached, which is the whole reason a chroma swatch flows here
            // exactly as the thing it is colouring will.
            val argb = when {
                ColorValue.isNone(spec) -> EMPTY_SWATCH
                else -> ColorValue.resolve(spec, EMPTY_SWATCH)
            }
            extractor.fill(swatchX, top, swatchX + SWATCH, top + WIDGET_HEIGHT, argb)
            extractor.outline(
                swatchX,
                top,
                SWATCH,
                WIDGET_HEIGHT,
                if (swatch.isHovered || swatch.isFocused) SWATCH_HOVER else SWATCH_BORDER,
            )
            drawReset(extractor, mouseX, mouseY, delta)
        }

        private companion object {
            const val SWATCH = 20
            const val SWATCH_BORDER = 0xFF000000.toInt()
            const val SWATCH_HOVER = 0xFFFFFFFF.toInt()

            /** What "no colour" and an unreadable value both draw as: a dark, obviously-empty square. */
            const val EMPTY_SWATCH = 0xFF2A2A2A.toInt()
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

        /** How many candidates the completion popup shows at once; the rest are reached with the arrows. */
        private const val POPUP_ROWS = 8
        private const val POPUP_ROW_HEIGHT = 12
        private const val POPUP_PADDING = 4

        private const val LABEL_COLOR = 0xFFE8E8E8.toInt()
        private const val HEADING_COLOR = 0xFFFFD25F.toInt()
        private const val SEPARATOR_COLOR = 0x40FFFFFF
        private const val ERROR_COLOR = 0xFFFF6B6B.toInt()

        // Near-opaque, because the popup sits over rows that are still drawn underneath it and a translucent
        // one would read as two overlapping lists rather than one on top.
        private const val POPUP_BACKGROUND = 0xF0100010.toInt()
        private const val POPUP_BORDER = 0xFF4A4A5A.toInt()
        private const val POPUP_SELECTED = 0xFF3A5A8A.toInt()
        private const val POPUP_TEXT = 0xFFB0B0B0.toInt()
        private const val POPUP_SELECTED_TEXT = 0xFFFFFFFF.toInt()

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
