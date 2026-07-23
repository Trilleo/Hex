package net.trilleo.suggest.ui.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.suggest.SuggestConfig
import net.trilleo.suggest.SuggestFeature
import net.trilleo.suggest.model.CommandCatalog
import net.trilleo.suggest.model.ModelStore
import net.trilleo.suggest.model.SuggestModel
import net.trilleo.suggest.model.Weights
import java.util.*

/**
 * What the model has learned, and the controls for disagreeing with it.
 *
 * A learned ranker that cannot be inspected is a black box the player is asked to trust, and there is no
 * reason they should. This screen exists so that every suggestion has an answer to "why", every association
 * the model has made is visible as text, and anything wrong can be pinned, blocked or forgotten on the spot
 * rather than waited out. That is also the honest place to put the wipe button: someone who can see exactly
 * what has been recorded about them is in a position to decide they would rather it were not.
 *
 * Reachable from the **Command Suggestions** tab of `/hexa config` and from `/hexa suggest dashboard`.
 */
class SuggestScreen(private val parent: Screen?) :
    Screen(Component.translatable("hex.suggest.title")) {

    private var list: SuggestList? = null
    private var search: EditBox? = null

    /**
     * The footer line, formatted once per rebuild.
     *
     * [SuggestModel.summary] sorts every learned key to find the top few, and [Weights.current] copies the
     * weight vector — neither of which belongs on a path that runs sixty times a second to draw a line of text
     * that changes only when a row is pinned, blocked or forgotten.
     */
    private var footer: Component = Component.empty()

    override fun init() {
        val listHeight = height - TOP - FOOTER_HEIGHT
        list = addRenderableWidget(SuggestList(minecraft, width, listHeight, TOP, this))

        addRenderableWidget(StringWidget(MARGIN, 8, width - MARGIN * 2, 12, title, font))

        val box = EditBox(font, MARGIN, 22, width - MARGIN * 2, 16, Component.translatable("hex.suggest.search"))
        box.setHint(Component.translatable("hex.suggest.search"))
        box.setResponder { refreshRows() }
        addRenderableWidget(box)
        search = box

        val y = height - 28
        var x = width / 2 - (BUTTON_WIDTH * 3 + GAP * 2) / 2

        addRenderableWidget(
            Button.builder(learningLabel()) { button ->
                SuggestConfig.settings.learning = !SuggestConfig.learning
                SuggestConfig.save()
                button.message = learningLabel()
            }.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.suggest.learning.tooltip")))
                .build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.suggest.forget_all")) { confirmWipe() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.suggest.forget_all.tooltip")))
                .build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.suggest.done")) { onClose() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )

        refreshRows()
    }

    /** Re-reads the model into the list. Called after every pin, block and forget. */
    fun refreshRows() {
        list?.show(SuggestList.entries(search?.value.orEmpty()))
        footer = buildFooter()
    }

    /**
     * How much has been learned, and what the ranker currently believes.
     *
     * The two weights named are the ones that actually differ between players — whether habit or the here and
     * now carries more — so a glance says which way this install has been trained without anyone opening the
     * "why" view for a specific command.
     */
    private fun buildFooter(): Component = Component.literal(
        run {
            val summary = SuggestModel.summary(System.currentTimeMillis())
            val weights = Weights.current()
            String.format(
                Locale.ROOT,
                "%d learned · %d trained · habit %.1f · context %.1f · catalogue %d",
                summary.keys,
                summary.trainingSteps,
                weights[Weights.PRIOR],
                weights[Weights.CONTEXT],
                CommandCatalog.size,
            )
        },
    )

    override fun extractBackground(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(extractor, mouseX, mouseY, delta)
        extractor.text(font, footer, MARGIN, height - FOOTER_TEXT_Y, FOOTER_COLOR)
    }

    private fun learningLabel(): Component = Component.translatable(
        if (SuggestConfig.learning) "hex.suggest.learning.on" else "hex.suggest.learning.off",
    )

    /** Shares the settings tab's prompt, so "forget everything" cannot mean two subtly different things. */
    private fun confirmWipe() {
        minecraft.setScreen(SuggestFeature.wipePrompt(this))
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun removed() {
        // Pins and blocks already save immediately; this makes leaving the screen a definite save point too.
        ModelStore.flush()
    }

    private companion object {
        const val MARGIN = 24
        const val TOP = 44
        const val FOOTER_HEIGHT = 52
        const val FOOTER_TEXT_Y = 42
        const val BUTTON_WIDTH = 96
        const val BUTTON_HEIGHT = 20
        const val GAP = 6

        const val FOOTER_COLOR = 0xFF9A9A9A.toInt()
    }
}
