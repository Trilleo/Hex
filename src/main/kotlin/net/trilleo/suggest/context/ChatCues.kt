package net.trilleo.suggest.context

import net.trilleo.suggest.context.ChatCues.BUILTIN
import net.trilleo.suggest.context.ChatCues.WINDOW_MS
import java.util.*

/**
 * What chat said in the last few seconds, reduced to a single tag.
 *
 * This is the context source that makes the difference between a model that knows your routine and one that
 * knows your *situation*. Where you are standing cannot explain why `/party accept` is the right suggestion;
 * the invite that arrived eight seconds ago explains it completely. Every other feature describes the world;
 * this one describes what the world just asked you.
 *
 * **Substrings, not regular expressions.** These patterns are built in rather than player-written, so none of
 * [net.trilleo.reminder.ChatMatcher]'s defences against catastrophic backtracking are needed — and a plain
 * `contains` is both cheaper and impossible to get wrong on a hook that sees every line Hypixel sends. The
 * catalogue extends the set through [install]; it never gets to supply a pattern language.
 *
 * A cue expires by wall clock rather than by being consumed, because the point is recency: an invite from two
 * minutes ago is not why you are typing now, whether or not anything read it in the meantime.
 */
object ChatCues {

    /** A tag and the phrases that raise it. Phrases are matched lower-cased against the stripped line. */
    class Cue(val tag: String, val phrases: List<String>)

    /**
     * The built-in cues.
     *
     * Chosen for one property: each is a message that has an obvious command as its answer. Anything that
     * merely reports what happened, however common, is left out — it would raise a tag on almost every line
     * and dilute the feature into noise.
     */
    private val BUILTIN: List<Cue> = listOf(
        Cue("party-invite", listOf("has invited you to join their party", "invited you to join their party")),
        Cue("guild-invite", listOf("has invited you to join their guild", "invited you to join the guild")),
        Cue("friend-request", listOf("has sent you a friend request", "sent you a friend request")),
        Cue("visit-request", listOf("has requested to visit your island", "wants to visit your island")),
        Cue("trade-request", listOf("has sent you a trade request", "wants to trade with you")),
        Cue("party-joined", listOf("joined the party", "you have joined")),
        Cue("party-left", listOf("has left the party", "the party was disbanded")),
        Cue("coop-invite", listOf("has invited you to join their co-op", "invited you to their co-op")),
        Cue("auction-sold", listOf("your auction", "was bought by", "sold for")),
        Cue("boss-spawn", listOf("is spawning", "has spawned", "boss has appeared")),
        Cue("slayer-done", listOf("slayer quest complete", "you have slain")),
        Cue("dungeon-ready", listOf("starting in", "dungeon has started", "party finder")),
        Cue("island-full", listOf("your inventory is full", "inventory is full")),
    )

    /** The active set: [BUILTIN] plus whatever the catalogue added. Replaced wholesale, never mutated. */
    @Volatile
    private var cues: List<Cue> = BUILTIN

    @Volatile
    private var tag: String? = null

    @Volatile
    private var raisedAtMs: Long = 0L

    /** Adds the catalogue's cues on top of the built-ins. Idempotent — always rebuilds from [BUILTIN]. */
    fun install(extra: List<Cue>) {
        cues = if (extra.isEmpty()) BUILTIN else BUILTIN + extra
    }

    /** Forgets the current cue. Called on world join and leave. */
    fun reset() {
        tag = null
        raisedAtMs = 0L
    }

    /**
     * Offers a chat line to the cue set, raising a tag if one matches.
     *
     * Called from `Feature.onChatReceive`, which every feature shares — so like everything else on that path
     * it must be cheap and it must not throw. Both hold here: the work is a lower-case of a bounded prefix
     * and a handful of substring searches, and there is nothing in it that can fail.
     */
    fun offer(line: String) {
        if (line.isEmpty()) return
        val subject = (if (line.length > MAX_INPUT) line.substring(0, MAX_INPUT) else line)
            .lowercase(Locale.ROOT)
        val matched = cues.firstOrNull { cue -> cue.phrases.any { subject.contains(it) } } ?: return
        tag = matched.tag
        raisedAtMs = System.currentTimeMillis()
    }

    /** The cue raised within the last [WINDOW_MS], or null. This is [ContextSnapshot.CUE]'s value. */
    fun current(): String? {
        val raised = raisedAtMs
        if (raised == 0L) return null
        val age = System.currentTimeMillis() - raised
        return if (age in 0..WINDOW_MS) tag else null
    }

    /** Same cap and same reasoning as [net.trilleo.reminder.ChatMatcher]: the interesting part is at the front. */
    private const val MAX_INPUT = 256

    /**
     * Fifteen seconds — about as long as it takes to read a message, decide, and open chat. Longer would let
     * a stale invite keep colouring predictions well after you had ignored it.
     */
    private const val WINDOW_MS = 15_000L
}
