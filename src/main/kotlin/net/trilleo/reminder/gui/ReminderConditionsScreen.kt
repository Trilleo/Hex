package net.trilleo.reminder.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.*
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.reminder.ReminderConfig
import net.trilleo.reminder.model.Condition
import net.trilleo.reminder.model.ConditionKind
import java.util.*

/**
 * Edits a reminder's conditions — the tests that must all pass at the moment it would fire.
 *
 * A separate screen rather than rows in [ReminderEditScreen] because a variable-length list of
 * `(kind, value)` pairs has no [net.trilleo.config.ConfigEntry] shape. Inventing a list entry type would mean
 * a new member on a sealed interface, a new branch in `ConfigEntryList.rowFor`, a row class, and a generic
 * nested-editor mechanism — a great deal of machinery for one use, which every future exhaustive `when` over
 * `ConfigEntry` would then have to carry. The codebase already settles this: a curated list lives behind an
 * action button that opens its own screen, exactly as the per-item swing list does.
 *
 * @param onChange marks the owning reminder as hand-edited, so preset updates stop overwriting it.
 */
class ReminderConditionsScreen(
    private val parent: Screen?,
    /** Read by the row classes to add and remove conditions. */
    val reminder: net.trilleo.reminder.model.Reminder,
    private val onChange: () -> Unit,
) : Screen(Component.translatable("hex.reminders.conditions.title")) {

    private var list: ConditionList? = null

    override fun init() {
        val listHeight = height - TOP - FOOTER_HEIGHT
        list = addRenderableWidget(ConditionList(minecraft, width, listHeight, TOP, this))

        addRenderableWidget(StringWidget(MARGIN, 12, width - MARGIN * 2, 12, title, font))

        val y = height - 28
        var x = width / 2 - (BUTTON_WIDTH * 2 + GAP) / 2

        addRenderableWidget(
            Button.builder(Component.translatable("hex.reminders.conditions.add")) {
                reminder.conditions.add(Condition())
                changed()
                list?.scrollToBottom()
            }.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.reminders.conditions.add.tooltip")))
                .build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.reminders.conditions.done")) { onClose() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )

        refreshRows()
    }

    fun refreshRows() {
        list?.show(reminder.conditions)
    }

    /** Records an edit and re-reads the rows. */
    fun changed() {
        onChange()
        refreshRows()
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun removed() {
        ReminderConfig.save()
    }

    private companion object {
        const val MARGIN = 24
        const val TOP = 32
        const val FOOTER_HEIGHT = 40
        const val BUTTON_WIDTH = 100
        const val BUTTON_HEIGHT = 20
        const val GAP = 6
    }

    /** The scrolling list of conditions, one row each. */
    class ConditionList(
        minecraft: Minecraft,
        width: Int,
        height: Int,
        top: Int,
        private val screen: ReminderConditionsScreen,
    ) : ContainerObjectSelectionList<ConditionList.Row>(minecraft, width, height, top, ROW_HEIGHT) {

        override fun getRowWidth(): Int = width - 24

        override fun scrollBarX(): Int = x + width - 8

        fun show(conditions: List<Condition>) {
            val scroll = scrollAmount()
            clearEntries()
            if (conditions.isEmpty()) {
                addEntry(HintRow(Component.translatable("hex.reminders.conditions.empty")))
            } else {
                conditions.forEach { addEntry(ConditionRow(it, screen)) }
            }
            setScrollAmount(scroll)
        }

        fun scrollToBottom() {
            setScrollAmount(maxScrollAmount().toDouble())
        }

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

        /** One condition: a kind cycler, its value, and a delete button. */
        private class ConditionRow(
            private val condition: Condition,
            private val screen: ReminderConditionsScreen,
        ) : Row() {

            private val kindButton: Button = Button.builder(kindLabel(condition.kind)) {
                val values = ConditionKind.entries
                condition.kind = values[(condition.kind.ordinal + 1) % values.size]
                // The kinds fold case differently, so the stored value has to follow the kind.
                foldValue()
                field.value = condition.value
                screen.changed()
            }.bounds(0, 0, KIND_WIDTH, WIDGET_HEIGHT).build()

            private val field: EditBox = EditBox(
                Minecraft.getInstance().font, 0, 0, 100, WIDGET_HEIGHT,
                Component.translatable("hex.reminders.conditions.value.hint"),
            ).apply {
                value = condition.value
                setMaxLength(VALUE_MAX_LENGTH)
                setResponder { text ->
                    condition.value = text
                    foldValue()
                    ReminderConfig.markDirty()
                }
            }

            private val deleteButton: Button = Button.builder(Component.literal("✕")) {
                screen.reminder.conditions.remove(condition)
                screen.changed()
            }.bounds(0, 0, DELETE_WIDTH, WIDGET_HEIGHT).build()

            override val widgets: List<AbstractWidget> = listOf(kindButton, field, deleteButton)

            /** Matches the normalizer's folding, so a value typed here works without waiting for a reload. */
            private fun foldValue() {
                condition.value = when (condition.kind) {
                    ConditionKind.ON_ISLAND, ConditionKind.NOT_ON_ISLAND,
                    ConditionKind.IN_REGION, ConditionKind.NOT_IN_REGION,
                        -> condition.value.trim().lowercase(Locale.ROOT)

                    ConditionKind.HOLDING_ITEM -> condition.value.trim().uppercase(Locale.ROOT)
                    ConditionKind.ON_SKYBLOCK -> condition.value
                }
            }

            override fun extractContent(
                extractor: GuiGraphicsExtractor,
                mouseX: Int,
                mouseY: Int,
                hovered: Boolean,
                delta: Float,
            ) {
                kindButton.message = kindLabel(condition.kind)

                var x = contentX
                place(kindButton, x, KIND_WIDTH)
                draw(kindButton, extractor, mouseX, mouseY, delta)
                x += KIND_WIDTH + GAP

                // ON_SKYBLOCK takes no value, so the box is hidden rather than shown doing nothing.
                if (condition.usesValue()) {
                    val fieldWidth = (contentRight - DELETE_WIDTH - GAP - x).coerceAtLeast(60)
                    place(field, x, fieldWidth)
                    draw(field, extractor, mouseX, mouseY, delta)
                }

                place(deleteButton, contentRight - DELETE_WIDTH, DELETE_WIDTH)
                draw(deleteButton, extractor, mouseX, mouseY, delta)
            }

            private companion object {
                fun kindLabel(kind: ConditionKind): Component =
                    Component.translatable("hex.reminders.conditions.kind.${kind.name.lowercase(Locale.ROOT)}")
            }
        }

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
            const val ROW_HEIGHT = 24
            private const val WIDGET_HEIGHT = 20
            private const val KIND_WIDTH = 96
            private const val DELETE_WIDTH = 22
            private const val GAP = 6
            private const val VALUE_MAX_LENGTH = 64
            private const val HINT_COLOR = 0xFFA0A0A0.toInt()
        }
    }
}
