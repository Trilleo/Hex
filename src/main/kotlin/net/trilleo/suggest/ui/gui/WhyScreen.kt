package net.trilleo.suggest.ui.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.suggest.context.ContextSnapshot
import net.trilleo.suggest.context.ContextSources
import net.trilleo.suggest.model.Candidate
import net.trilleo.suggest.model.MarkovChain
import net.trilleo.suggest.model.NaiveBayes
import net.trilleo.suggest.model.Ranker
import net.trilleo.suggest.model.Weights
import java.util.*

/**
 * The arithmetic behind one suggestion, in full.
 *
 * Every number the ranker uses is here, with its name, its value, the weight applied to it and the product —
 * which is to say, the entire model, for this one line, at this one moment. Nothing is summarised away.
 *
 * This exists because a ranker that learns is only trustworthy if it is checkable. "Habit +1.4, context
 * +0.9 (island: dwarven mines)" is something a player can agree or disagree with; a suggestion that simply
 * appears is something they can only tolerate. It is also the debugging tool — the same view
 * `/hexa suggest why` prints, and the reason that command exists at all.
 *
 * The context shown is read live rather than remembered, so opening this from the hub and opening it from the
 * dungeon give genuinely different answers. That is the point: the question is not "why was this suggested
 * once" but "why would this be suggested here".
 */
class WhyScreen(private val parent: Screen?, private val key: String) :
    Screen(Component.translatable("hex.suggest.why.title")) {

    private var lines: List<Pair<String, Int>> = emptyList()

    override fun init() {
        addRenderableWidget(StringWidget(MARGIN, 10, width - MARGIN * 2, 12, Component.literal("/$key"), font))
        addRenderableWidget(
            Button.builder(Component.translatable("hex.suggest.done")) { onClose() }
                .bounds(width / 2 - 50, height - 28, 100, 20).build(),
        )
        lines = build()
    }

    /**
     * Assembles the whole explanation once, on open.
     *
     * Once rather than per frame because this walks the model — a naive-Bayes pass over seventeen features and
     * a fresh context snapshot with its dozen tag reads. None of it changes while the screen is open, and
     * doing it per frame would make a static page the most expensive thing in the mod.
     */
    private fun build(): List<Pair<String, Int>> {
        val now = System.currentTimeMillis()
        val context = ContextSources.snapshot(Minecraft.getInstance())

        // Ranked against the field it would actually compete in, so the confidence figure means what it
        // means everywhere else: how far ahead of the alternatives this is, not how large its score is.
        val ranked = Ranker.rank(key, context, limit = RANK_FIELD, now = now)
        val candidate = ranked.firstOrNull { it.key == key } ?: standalone(context, now)
        val explanation = Ranker.explain(candidate, ranked.ifEmpty { listOf(candidate) }, context, now)

        val out = mutableListOf<Pair<String, Int>>()
        out += String.format(
            Locale.ROOT,
            "score %.2f · %.0f%% confident here",
            explanation.score,
            explanation.probability * 100,
        ) to HEADING

        out += "" to BODY
        out += "What the score is made of" to HEADING
        explanation.terms.forEach { term ->
            val row = String.format(
                Locale.ROOT,
                "  %-16s %+6.2f    (%.2f × %.2f)",
                term.name, term.contribution, term.value, term.weight,
            )
            out += row to if (kotlin.math.abs(term.contribution) < NEGLIGIBLE) MUTED else BODY
        }

        explanation.routine?.let {
            out += "" to BODY
            out += "Routine: $it" to BODY
        }

        out += "" to BODY
        out += "What the context says" to HEADING
        if (explanation.context.isEmpty()) {
            out += "  nothing yet — this line has too little history here" to MUTED
        } else {
            explanation.context.take(MAX_CONTEXT).forEach { (feature, value) ->
                val label = ContextSnapshot.LABELS[feature] ?: feature
                val seen = context[feature].takeIf { it != ContextSnapshot.UNKNOWN } ?: "unknown"
                out += String.format(Locale.ROOT, "  %-14s %+5.2f    now: %s", label, value, seen) to
                    if (value > 0) BODY else MUTED
            }
        }
        return out
    }

    /**
     * A feature vector for a line that did not make its own shortlist.
     *
     * Reachable from the dashboard, where any learned line can be opened regardless of whether it would be
     * suggested — and "this would not currently be offered" is exactly the case someone opens this screen to
     * understand, so it has to produce a full breakdown rather than an empty page.
     */
    private fun standalone(context: ContextSnapshot, now: Long): Candidate {
        val features = DoubleArray(Weights.COUNT)
        features[Weights.CONTEXT] = NaiveBayes.logOdds(key, context, now)
        features[Weights.MARKOV] = MarkovChain.logOdds(key, context, now)
        features[Weights.MATCH] = 1.0
        return Candidate(key, features, Weights.score(features))
    }

    override fun extractBackground(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(extractor, mouseX, mouseY, delta)
        var y = TOP
        lines.forEach { (text, color) ->
            if (text.isNotEmpty()) extractor.text(font, text, MARGIN, y, color)
            y += LINE_HEIGHT
        }
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    private companion object {
        const val MARGIN = 24
        const val TOP = 30
        const val LINE_HEIGHT = 11

        const val HEADING = 0xFFFFFFFF.toInt()
        const val BODY = 0xFFC0C0C0.toInt()
        const val MUTED = 0xFF7A7A7A.toInt()

        /** Wide enough that the confidence figure is meaningful, small enough to stay cheap. */
        const val RANK_FIELD = 12

        const val MAX_CONTEXT = 8
        const val NEGLIGIBLE = 0.01
    }
}
