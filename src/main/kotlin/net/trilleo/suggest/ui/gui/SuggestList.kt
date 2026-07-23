package net.trilleo.suggest.ui.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component
import net.trilleo.suggest.SuggestConfig
import net.trilleo.suggest.context.ContextSnapshot
import net.trilleo.suggest.model.KeyStat
import net.trilleo.suggest.model.ModelStore
import net.trilleo.suggest.model.SuggestModel
import net.trilleo.util.Duration
import java.util.*

/** One learned command line, as the dashboard shows it. */
class LearnedEntry(
    val key: String,
    val stat: KeyStat,
    val weight: Double,
    /** The context feature most associated with this line, already rendered, or null. */
    val tie: String?,
)

/**
 * The scrolling list of learned command lines in [SuggestScreen].
 *
 * Built on [ContainerObjectSelectionList] the same way [net.trilleo.reminder.gui.ReminderList] is. Rows are
 * held by key rather than by identity, because unlike a reminder a learned line *is* its key — two rows can
 * never be equal-looking siblings.
 */
class SuggestList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val screen: SuggestScreen,
) : ContainerObjectSelectionList<SuggestList.Row>(minecraft, width, height, top, ROW_HEIGHT) {

    override fun getRowWidth(): Int = width - 24

    override fun scrollBarX(): Int = x + width - 8

    /** Replaces the visible rows, or shows the empty-list hint. Preserves the scroll position. */
    fun show(entries: List<LearnedEntry>) {
        val scroll = scrollAmount()
        clearEntries()
        if (entries.isEmpty()) {
            addEntry(HintRow(Component.translatable("hex.suggest.empty")))
        } else {
            entries.forEach { addEntry(LearnedRow(it, screen)) }
        }
        setScrollAmount(scroll)
    }

    // ---- rows ----------------------------------------------------------------------------------------

    abstract class Row : ContainerObjectSelectionList.Entry<Row>() {
        protected abstract val widgets: List<AbstractWidget>

        override fun children(): List<AbstractWidget> = widgets

        override fun narratables(): List<NarratableEntry> = widgets

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

    /**
     * One learned line: what it is, how strongly it is held and what it is tied to, plus the three controls
     * that make the model something the player owns rather than something that happens to them — pin, block,
     * forget — and a way to see the arithmetic.
     */
    private class LearnedRow(
        private val entry: LearnedEntry,
        private val screen: SuggestScreen,
    ) : Row() {

        private val whyButton: Button = Button.builder(Component.literal("?")) {
            Minecraft.getInstance().setScreen(WhyScreen(screen, entry.key))
        }.bounds(0, 0, SMALL_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.suggest.why.tooltip")))
            .build()

        private val pinButton: Button = Button.builder(pinLabel(entry.stat.pinned)) {
            SuggestModel.setPinned(entry.key, !entry.stat.pinned)
            ModelStore.saveNow()
            screen.refreshRows()
        }.bounds(0, 0, SMALL_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.suggest.pin.tooltip")))
            .build()

        private val blockButton: Button = Button.builder(blockLabel(entry.stat.blocked)) {
            SuggestModel.setBlocked(entry.key, !entry.stat.blocked)
            ModelStore.saveNow()
            screen.refreshRows()
        }.bounds(0, 0, SMALL_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.suggest.block.tooltip")))
            .build()

        private val forgetButton: Button = Button.builder(Component.literal("✕")) {
            SuggestModel.forget(entry.key)
            ModelStore.saveNow()
            screen.refreshRows()
        }.bounds(0, 0, SMALL_WIDTH, WIDGET_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("hex.suggest.forget.tooltip")))
            .build()

        override val widgets: List<AbstractWidget> = listOf(whyButton, pinButton, blockButton, forgetButton)

        override fun extractContent(
            extractor: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            delta: Float,
        ) {
            val font = Minecraft.getInstance().font
            pinButton.message = pinLabel(entry.stat.pinned)
            blockButton.message = blockLabel(entry.stat.blocked)

            val buttons = SMALL_WIDTH * 4 + GAP * 3
            val textRight = contentRight - buttons - GAP
            val available = (textRight - contentX).coerceAtLeast(40)

            val nameColor = if (entry.stat.blocked) BLOCKED_COLOR else NAME_COLOR
            extractor.text(
                font,
                truncate("/${entry.key}", available),
                contentX,
                contentYMiddle - font.lineHeight - 1,
                nameColor,
            )
            extractor.text(font, truncate(subtitle(), available), contentX, contentYMiddle + 1, SUB_COLOR)

            var x = contentRight - buttons
            listOf(whyButton, pinButton, blockButton, forgetButton).forEach { button ->
                place(button, x, SMALL_WIDTH)
                draw(button, extractor, mouseX, mouseY, delta)
                x += SMALL_WIDTH + GAP
            }
        }

        /**
         * The second line: how often, how recently, and what the model has tied it to.
         *
         * The tie is the part worth showing. A count tells the player the model noticed something; naming the
         * island or the item it associated the command with tells them *what* it noticed, which is the only
         * form in which a wrong association can be spotted and corrected.
         */
        private fun subtitle(): String {
            val parts = mutableListOf<String>()
            parts += "${entry.stat.uses}×"
            parts += String.format(Locale.ROOT, "weight %.1f", entry.weight)
            if (entry.stat.last > 0L) {
                val ago = (System.currentTimeMillis() - entry.stat.last).coerceAtLeast(0L)
                parts += "${Duration.format(ago)} ago"
            }
            entry.tie?.let { parts += it }
            if (entry.stat.pinned) parts += "pinned"
            if (entry.stat.blocked) parts += "blocked"
            return parts.joinToString(" · ")
        }

        private fun truncate(text: String, available: Int): String {
            val font = Minecraft.getInstance().font
            if (font.width(text) <= available) return text
            return font.plainSubstrByWidth(text, available - font.width("…")) + "…"
        }

        private companion object {
            fun pinLabel(pinned: Boolean): Component = Component.literal(if (pinned) "★" else "☆")

            fun blockLabel(blocked: Boolean): Component = Component.literal(if (blocked) "⊘" else "○")
        }
    }

    /** Shown instead of rows when nothing matches, so the screen explains itself rather than sitting bare. */
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
        private const val SMALL_WIDTH = 22
        private const val GAP = 4

        private const val NAME_COLOR = 0xFFFFFFFF.toInt()
        private const val BLOCKED_COLOR = 0xFF808080.toInt()
        private const val SUB_COLOR = 0xFF909090.toInt()
        private const val HINT_COLOR = 0xFFA0A0A0.toInt()

        /**
         * Every learned line, heaviest first, optionally filtered by [search].
         *
         * The "tie" is computed here rather than in the row so it is done once per rebuild instead of once per
         * frame: it walks each line's context counters looking for the value that is most over-represented
         * against how common that value is overall — the same comparison naive Bayes makes, reduced to the
         * single strongest answer.
         */
        fun entries(search: String): List<LearnedEntry> {
            val now = System.currentTimeMillis()
            val halfLife = SuggestConfig.halfLifeMs
            val needle = search.trim().removePrefix("/").lowercase(Locale.ROOT)

            return SuggestModel.data.keys.entries
                .asSequence()
                .filter { needle.isEmpty() || it.key.lowercase(Locale.ROOT).contains(needle) }
                .map { (key, stat) ->
                    LearnedEntry(key, stat, stat.count.value(now, halfLife), tieOf(key, stat, now, halfLife))
                }
                .sortedByDescending { it.weight }
                .take(MAX_ROWS)
                .toList()
        }

        private fun tieOf(key: String, stat: KeyStat, now: Long, halfLife: Long): String? {
            var bestLabel: String? = null
            var bestRatio = MIN_TIE_RATIO

            stat.ctx.forEach { (feature, values) ->
                // Previous-command features describe a routine rather than a place, and reading "last
                // command: warp hub" as this line's defining association is more confusing than helpful.
                if (feature == ContextSnapshot.PREV1 || feature == ContextSnapshot.PREV2) return@forEach
                values.forEach inner@{ (value, counter) ->
                    if (value == ContextSnapshot.UNKNOWN) return@inner
                    val here = counter.value(now, halfLife)
                    if (here < MIN_TIE_OBSERVATIONS) return@inner
                    val overall = SuggestModel.marginalCount(feature, value, now)
                    if (overall <= 0.0) return@inner
                    val total = SuggestModel.keyCount(key, now)
                    if (total <= 0.0) return@inner
                    // How much of this line happens here, against how much of everything happens here.
                    val ratio = (here / total) / (overall / SuggestModel.total(now).coerceAtLeast(1.0))
                    if (ratio > bestRatio) {
                        bestRatio = ratio
                        bestLabel = "${ContextSnapshot.LABELS[feature] ?: feature}: $value"
                    }
                }
            }
            return bestLabel
        }

        /** A tie has to be at least this much stronger than chance before it is worth naming. */
        private const val MIN_TIE_RATIO = 1.5

        /** …and rest on more than a single coincidence. */
        private const val MIN_TIE_OBSERVATIONS = 2.0

        /** The list is scrollable, but a model at its cap would still be a thousand widgets to build. */
        private const val MAX_ROWS = 300
    }
}
