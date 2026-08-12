package net.trilleo.chat

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.trilleo.chat.ChatHighlighter.MAX_SPANS
import net.trilleo.chat.ChatHighlighter.MAX_SUBJECT
import net.trilleo.chat.ChatHighlighter.restyle
import net.trilleo.chat.model.ChatHighlight
import net.trilleo.chat.model.ChatScope
import net.trilleo.skyblock.SkyblockLocation
import net.trilleo.util.Chroma
import net.trilleo.util.HexColor
import net.trilleo.util.TextClean
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Finds the rules that claim an incoming chat message, repaints the text they claim, and decides whether the
 * line is shown at all.
 *
 * ### One pass, two events
 *
 * Fabric offers two hooks and this feature needs both: `ALLOW_GAME` is the only one that can *swallow* a
 * message, and `MODIFY_GAME` is the only one that can *replace* it. Running the scan twice would be wasteful and
 * — worse — could disagree with itself if the island resolved in between. So the whole pass runs in
 * [onChatReceive], and the styled result is stashed for [onChatModify] to hand back a moment later. A one-slot
 * cache keyed on the message's **identity** is enough: Fabric fires the two events back to back, for the same
 * object, on the same thread, and a message that never reaches the second event simply leaves a stale slot that
 * the next identity check rejects.
 *
 * ### Why the subject is the raw flattened text
 *
 * Every other chat reader in this mod matches against [TextClean.strip]. This one cannot, and the reason is
 * offsets: `strip` removes Hypixel's padding and formatting characters, so an index into the stripped text points
 * at the wrong character in the real one, and the paint would land beside the word instead of on it. The subject
 * here is `Component.string` exactly as it arrives. The consequence is worth stating plainly — a rule whose text
 * would span one of those invisible characters does not match — and it is the right trade against highlighting
 * the wrong half of a sentence. Channel detection is free of this problem, because it never needs an offset, so
 * it gets a stripped copy.
 *
 * ### Why nothing here can hang the client
 *
 * Matching is `String.indexOf` and nothing else — no regex, no backtracking, no player-supplied pattern. The
 * hazards [net.trilleo.reminder.ChatMatcher] exists to contain do not arise. What is bounded instead is
 * *output*: [MAX_SUBJECT] caps how far into a line rules are looked for and [MAX_SPANS] caps how many pieces one
 * message may be cut into, so a rule matching a single common letter cannot turn one line into hundreds of
 * components.
 */
object ChatHighlighter {
    private val LOGGER = LoggerFactory.getLogger("hex/chat")

    /**
     * How far into a line rules are looked for.
     *
     * The same reasoning as [net.trilleo.reminder.ChatMatcher]'s own input cap: the interesting part of a
     * Hypixel message is at the front. Text beyond this is still shown, and still styled if a span reaches into
     * it — only the *search* stops here.
     */
    private const val MAX_SUBJECT = 512

    /** How many highlighted runs one message may carry. Past this the rest of the line is left alone. */
    private const val MAX_SPANS = 64

    /** A stretch of the subject one rule has claimed, as a half-open range. */
    private class Span(val start: Int, val end: Int, val rule: ChatHighlight)

    /**
     * What a message turned out to be.
     *
     * @property styled the message to show, which is the original object itself when nothing matched.
     * @property hide whether some matching rule asks for the line to be swallowed.
     * @property matched the rules that claimed it, in list order — what the notifications are fired from.
     */
    class Outcome(val styled: Component, val hide: Boolean, val matched: List<ChatHighlight>) {
        val isEmpty: Boolean get() = matched.isEmpty()

        companion object {
            fun none(message: Component) = Outcome(message, hide = false, matched = emptyList())
        }
    }

    private var candidateIsland: String? = null
    private var candidateRevision: Int = -1
    private var candidates: List<ChatHighlight> = emptyList()

    private var stashedFor: Component? = null
    private var stashed: Component? = null

    /** Per-rule quiet periods, in wall-clock millis. In memory only — a cooldown means nothing across a restart. */
    private val quietUntil = mutableMapOf<String, Long>()

    /**
     * Scans [message], fires whatever it triggers, and reports whether chat should still show it.
     *
     * Everything is wrapped, because this runs inside the chat event that
     * [net.trilleo.feature.Features] fans out to every feature: a throw here would break chat for the whole mod,
     * and no highlight rule is worth that. A failure shows the line untouched, which is exactly what the player
     * would have had without this feature.
     */
    fun onChatReceive(client: Minecraft, message: Component): Boolean {
        if (!ChatHighlightConfig.active) return true
        if (ChatHighlightConfig.settings.skyblockOnly && !SkyblockLocation.onSkyblock) return true

        val outcome = runCatching { scan(message) }
            .onFailure { LOGGER.warn("Chat highlight scan failed; showing the line untouched", it) }
            .getOrElse { Outcome.none(message) }

        stashedFor = message
        stashed = outcome.styled

        outcome.matched.forEach { rule ->
            if (rule.notify) ChatHighlightAlerts.found(client, rule)
        }
        // Deliberately after the notifications: hiding and announcing are not opposites, so a rule that drops a
        // line of spam from the chat can still be the one that plays the sound for it.
        return !outcome.hide
    }

    /** The styled replacement for [message], or [message] itself when this is not the line just scanned. */
    fun onChatModify(message: Component): Component {
        val styled = if (stashedFor === message) stashed else null
        // Cleared either way: the slot is good for exactly one message, and holding a component past its event
        // would pin a whole chat line's tree in memory for no reason.
        stashedFor = null
        stashed = null
        return styled ?: message
    }

    /** Forgets the cached candidates, the stash and every cooldown. Called when the world goes away. */
    fun reset() {
        candidateIsland = null
        candidateRevision = -1
        candidates = emptyList()
        stashedFor = null
        stashed = null
        quietUntil.clear()
    }

    /**
     * Whether [rule] may announce itself now, starting its quiet period if so.
     *
     * Keyed on the rule's id rather than the rule itself, so a rename or an edit in the editor does not hand a
     * busy rule a fresh licence to shout.
     */
    fun claimCooldown(rule: ChatHighlight, now: Long): Boolean {
        if (now < (quietUntil[rule.id] ?: 0L)) return false
        quietUntil[rule.id] = now + (rule.cooldownSeconds * 1000.0).toLong()
        return true
    }

    /**
     * Runs [line] through the rules as though it had just arrived, without firing anything or touching the
     * stash. Backs `/hexa chat test` and the editor's preview.
     */
    fun dryRun(line: String): Outcome = runCatching { scan(Component.literal(line)) }
        .getOrElse { Outcome.none(Component.literal(line)) }

    /**
     * [line] painted as though [rule] alone had claimed all of it — the editor's preview.
     *
     * Goes through the same [restyle] the real path does, rather than approximating it, so what the preview shows
     * is what chat will show: the same colour resolution, the same format flags, the same markers, and the same
     * chroma marking, which the preview screen then animates exactly as the chat log does.
     */
    fun preview(rule: ChatHighlight, line: String, at: Int): Component {
        val start = at.coerceIn(0, line.length)
        val end = (start + rule.text.length).coerceAtMost(line.length)
        if (rule.text.isEmpty() || start >= end) return Component.literal(line)
        val span = if (rule.scope == ChatScope.LINE) Span(0, line.length, rule) else Span(start, end, rule)
        return restyle(Component.literal(line), listOf(span), liveChroma = true)
    }

    private fun scan(message: Component): Outcome {
        val subject = message.string
        if (subject.isEmpty()) return Outcome.none(message)

        val rules = candidatesFor(SkyblockLocation.current)
        if (rules.isEmpty()) return Outcome.none(message)

        // Stripped, because the channel tag sits behind Hypixel's padding — and free of the offset problem the
        // subject has, because a channel is a property of the whole line rather than of a position in it.
        val channel = ChatChannels.of(TextClean.strip(subject))

        val spans = ArrayList<Span>()
        val matched = ArrayList<ChatHighlight>()
        var hide = false
        val limit = minOf(subject.length, MAX_SUBJECT)

        for (rule in rules) {
            if (!rule.matchesChannel(channel)) continue
            var found = false
            var from = 0
            while (from < limit && spans.size < MAX_SPANS) {
                val at = rule.indexIn(subject, from)
                if (at < 0 || at >= limit) break
                found = true
                if (rule.scope == ChatScope.LINE) {
                    spans.add(Span(0, subject.length, rule))
                    break
                }
                spans.add(Span(at, at + rule.text.length, rule))
                from = at + rule.text.length
            }
            if (!found) continue
            matched.add(rule)
            if (rule.hide) hide = true
        }

        if (matched.isEmpty()) return Outcome.none(message)
        return Outcome(restyle(message, merge(spans)), hide, matched)
    }

    /**
     * The rules that could apply on [island], cached until the island or the config changes.
     *
     * The same shape [net.trilleo.highlight.HighlightTracker] uses, and for the same reason: this runs on every
     * chat line, and the common case has to be one comparison rather than a walk of the whole list.
     */
    private fun candidatesFor(island: String?): List<ChatHighlight> {
        if (island == candidateIsland && ChatHighlightConfig.revision == candidateRevision) return candidates
        candidateIsland = island
        candidateRevision = ChatHighlightConfig.revision
        candidates = ChatHighlightConfig.settings.highlights.filter { rule ->
            rule.enabled && rule.text.isNotEmpty() && rule.matchesIsland(island)
        }
        return candidates
    }

    /**
     * Drops every span that overlaps one an earlier rule already claimed, and sorts what survives.
     *
     * **First rule in the list wins**, which makes the list screen's order meaningful: a specific rule placed
     * above a broad one keeps its colour on the words they both match. Trimming the loser instead of dropping it
     * was the alternative, and it is worse — half a word in one colour and half in another is not what either
     * rule was asking for.
     *
     * Quadratic, over at most [MAX_SPANS] entries. That ceiling is what makes it the right algorithm rather than
     * an interval tree nobody would be able to read.
     */
    private fun merge(spans: List<Span>): List<Span> {
        val kept = ArrayList<Span>(spans.size)
        for (span in spans) {
            if (kept.none { it.start < span.end && span.start < it.end }) kept.add(span)
        }
        kept.sortBy { it.start }
        return kept
    }

    /**
     * Rebuilds [message] with [spans] repainted, preserving everything else about it.
     *
     * The naive version of this — flatten to a string, rebuild from scratch — is what
     * [net.trilleo.itemcustom.ItemCustomizer] does to an item name, and it would be wrong here: Hypixel's chat
     * lines carry click and hover events, and flattening throws them away. A party invite that stops being
     * clickable because a rule painted a word in it is a worse outcome than not painting the word.
     *
     * So the original is *visited* instead, which hands over each run of text together with the style it
     * resolved to, and each run is cut at the span boundaries that fall inside it. The paint is applied as
     * `overlay.applyTo(original)` — the receiver wins field by field — so the rule's colour and format flags take
     * effect while the original's click event, hover event and insertion survive untouched.
     *
     * @param liveChroma resolve chroma into real per-character colours here and now, instead of marking the run
     *   for [ChatChroma] to repaint later. The chat log wants the marking, because it is redrawn every frame and
     *   the mixin is what animates it; a preview drawn straight onto a screen has no such pass behind it, so it
     *   has to do the same arithmetic itself. Both routes compute the identical colour, which is what makes the
     *   preview trustworthy rather than merely suggestive.
     */
    private fun restyle(message: Component, spans: List<Span>, liveChroma: Boolean = false): Component {
        if (spans.isEmpty()) return message

        val out = Component.empty()
        var index = 0
        var next = 0

        message.visit({ style, text ->
            var pos = 0
            while (pos < text.length) {
                val absolute = index + pos
                // Spans are sorted and consumed in order, so this walks forward once across the whole message
                // rather than searching for each run's span.
                while (next < spans.size && spans[next].end <= absolute) next++
                val span = spans.getOrNull(next)

                if (span == null || span.start >= index + text.length) {
                    out.append(Component.literal(text.substring(pos)).setStyle(style))
                    pos = text.length
                } else if (absolute < span.start) {
                    val cut = span.start - index
                    out.append(Component.literal(text.substring(pos, cut)).setStyle(style))
                    pos = cut
                } else {
                    val cut = minOf(text.length, span.end - index)
                    val overlay = styleFor(span.rule)
                    // The markers are emitted at the span's true edges, which may fall in different runs — hence
                    // the exact-boundary tests rather than "the first piece" and "the last piece".
                    if (absolute == span.start && span.rule.prefix.isNotEmpty()) {
                        out.append(Component.literal(span.rule.prefix).setStyle(overlay))
                    }
                    val painted = overlay.applyTo(style)
                    if (liveChroma && span.rule.chroma) {
                        // One component per character — the whole cost of chroma, and the reason the chat log
                        // marks a single run for the render pass instead of doing this to every message.
                        val phase = Chroma.phase(ChatHighlightConfig.chromaSeconds)
                        val chromaWidth = ChatHighlightConfig.chromaWidth
                        for (i in pos until cut) {
                            val color = Chroma.colorAt(phase, index + i, chromaWidth)
                            out.append(Component.literal(text[i].toString()).setStyle(painted.withColor(color)))
                        }
                    } else {
                        out.append(Component.literal(text.substring(pos, cut)).setStyle(painted))
                    }
                    if (index + cut == span.end && span.rule.suffix.isNotEmpty()) {
                        out.append(Component.literal(span.rule.suffix).setStyle(overlay))
                    }
                    pos = cut
                }
            }
            index += text.length
            Optional.empty<Unit>()
        }, Style.EMPTY)

        return out
    }

    /**
     * The style a rule paints with.
     *
     * Only the flags that are *on* are set, so everything untouched stays null and is inherited from the original
     * run — the same null-means-inherit convention [net.trilleo.util.Chroma] follows, and what makes
     * `applyTo` above preserve the parts of Hypixel's own styling the rule has no opinion about.
     *
     * A chroma rule is given [ChatChroma.FONT] *and* a real colour. The font is what
     * [ChatChroma.recolor] repaints every frame; the colour is what shows if that never runs — a resource pack
     * quarrel, a mixin that failed to apply — and a chroma highlight that degrades to a plain coloured one is a
     * far better failure than one that degrades to invisible.
     */
    fun styleFor(rule: ChatHighlight): Style {
        var style = Style.EMPTY
        style = if (rule.chroma) {
            style
                .withFont(ChatChroma.FONT)
                .withColor(Chroma.color(0, ChatHighlightConfig.chromaSeconds, ChatHighlightConfig.chromaWidth))
        } else {
            val raw = rule.color.ifBlank { ChatHighlightConfig.settings.defaultColor }
            // Masked to 24 bits: a config colour may carry an alpha byte, and Style.withColor takes plain RGB —
            // an unmasked value would arrive as a nonsense colour rather than as a translucent one.
            style.withColor(HexColor.parseOrDefault(raw, DEFAULT_RGB) and RGB_MASK)
        }
        if (rule.bold) style = style.withBold(true)
        if (rule.italic) style = style.withItalic(true)
        if (rule.underlined) style = style.withUnderlined(true)
        if (rule.strikethrough) style = style.withStrikethrough(true)
        if (rule.obfuscated) style = style.withObfuscated(true)
        return style
    }

    private const val RGB_MASK = 0xFFFFFF

    /** What an unparseable colour falls back to. Plain white, so the text is at least legible. */
    private const val DEFAULT_RGB = 0xFFFFFF
}
