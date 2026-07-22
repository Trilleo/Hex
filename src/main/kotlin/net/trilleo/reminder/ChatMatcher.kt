package net.trilleo.reminder

import net.trilleo.reminder.ChatMatcher.BUDGET
import net.trilleo.reminder.ChatMatcher.MAX_INPUT
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Matches chat lines against user-written patterns, and substitutes their capture groups into reminder text.
 *
 * This is the most dangerous code in the mod, and the reason is worth stating plainly: patterns come from the
 * player, and this runs inside `ClientReceiveMessageEvents.ALLOW_GAME`, which
 * [net.trilleo.feature.Features] fans out to *every* feature. A throw here breaks chat for the whole mod. A
 * hang here locks the client with no crash log and nothing to point at. Neither is acceptable for a mistyped
 * bracket, so the defences below are load-bearing rather than belt-and-braces.
 *
 * **Catastrophic backtracking** is the hang case. A pattern like `(a+)+b` against a long run of `a`s takes
 * time exponential in the input length — seconds, then minutes, inside a chat callback. Three layers stop it:
 *
 *  1. [MAX_INPUT] caps the subject length. The blowup is exponential *in that length*, so capping it cuts the
 *     worst case by orders of magnitude before anything else has to work.
 *  2. [BudgetedSequence] caps the total work. A `Matcher` reads its subject through `CharSequence.charAt` on
 *     every backtracking step, so counting reads is a hard ceiling on the number of steps — with no watchdog
 *     thread, no timeout, and no interruption machinery to get wrong.
 *  3. [matches] catches everything regardless, so nothing at all escapes into the chat event.
 *
 * A pattern that trips the budget is marked bad and never retried, and its reminder is switched off, so the
 * player gets one clear message naming the culprit instead of a recurring stutter.
 */
object ChatMatcher {
    private val LOGGER = LoggerFactory.getLogger("hex/reminder")

    /**
     * The longest subject matched against. Chat lines longer than this are truncated rather than skipped —
     * the interesting part of a Hypixel message is at the front, and a pattern that needed character 300 is
     * far less likely than a pattern that would blow up on one.
     */
    private const val MAX_INPUT = 256

    /**
     * How many subject reads one match may cost.
     *
     * A well-behaved pattern on a 256-character line costs on the order of hundreds of reads, so this is
     * thousands of times more headroom than any sane pattern needs, and still completes in microseconds when
     * a pathological one burns through it.
     */
    private const val BUDGET = 20_000

    /**
     * Compiled patterns, keyed on the raw pattern string.
     *
     * An empty [Optional] marks a pattern known to be bad — either it would not compile or it exhausted the
     * budget — so it is diagnosed once and thereafter costs a map lookup. Keying on the raw string means an
     * edit in the editor naturally produces a new key rather than needing explicit invalidation.
     *
     * Concurrent because chat can arrive off the client thread.
     */
    private val cache = ConcurrentHashMap<String, Optional<Pattern>>()

    /** Thrown when a match exceeds [BUDGET]. Stackless — it is control flow, and it must be cheap. */
    private object BudgetExceeded : RuntimeException(null, null, false, false)

    /**
     * A view of the subject that aborts once it has been read [budget] times.
     *
     * This is the whole backtracking defence, and it works because `Matcher` has no way to make progress
     * without reading characters: every step of every alternative goes through [get].
     */
    private class BudgetedSequence(private val text: CharSequence, private var budget: Int) : CharSequence {
        override val length: Int get() = text.length

        override fun get(index: Int): Char {
            if (--budget < 0) throw BudgetExceeded
            return text[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            text.subSequence(startIndex, endIndex)

        override fun toString(): String = text.toString()
    }

    /**
     * Forgets every compiled pattern. Called when the whole settings object is replaced — a profile switch or
     * a clipboard import — so a pattern cached from the outgoing config cannot outlive it.
     */
    fun invalidate() {
        cache.clear()
    }

    /**
     * Whether [pattern] matches [line], and what it captured.
     *
     * Returns null for no match, or a list whose entries are groups `0..n` with `null` for a group that did
     * not participate. A [literal] pattern is compared as a plain case-insensitive substring and captures only
     * group 0, which is the whole line — no regex engine is involved at all, which is why it is the safe
     * choice for someone who just wants to match some words.
     *
     * Never throws. A bad pattern yields null and is remembered as bad.
     */
    fun match(pattern: String, literal: Boolean, line: String): List<String?>? {
        if (pattern.isEmpty()) return null
        val subject = if (line.length > MAX_INPUT) line.substring(0, MAX_INPUT) else line

        if (literal) {
            return if (subject.contains(pattern, ignoreCase = true)) listOf(subject) else null
        }

        val compiled = patternFor(pattern) ?: return null
        return try {
            val matcher = compiled.matcher(BudgetedSequence(subject, BUDGET))
            if (!matcher.find()) return null
            (0..matcher.groupCount()).map { runCatching { matcher.group(it) }.getOrNull() }
        } catch (e: BudgetExceeded) {
            // Remember it as bad so this cannot recur on the next chat line, and tell the caller nothing
            // matched. ReminderEngine turns this into a one-off message naming the reminder.
            cache[pattern] = Optional.empty()
            LOGGER.warn("Reminder pattern exceeded its matching budget and was disabled: {}", pattern)
            null
        } catch (e: Exception) {
            // A StackOverflowError from a deeply nested pattern is an Error, not an Exception, and is left to
            // the caller's own guard — catching it here would risk continuing on a damaged stack.
            LOGGER.warn("Reminder pattern failed to match: {}", pattern, e)
            null
        }
    }

    /**
     * Compiles and caches [raw], or returns null when it will not compile. Compilation itself is bounded —
     * `Pattern.compile` is linear in the pattern's length — so only matching needs the budget.
     */
    private fun patternFor(raw: String): Pattern? =
        cache.getOrPut(raw) {
            try {
                Optional.of(Pattern.compile(raw))
            } catch (e: Exception) {
                LOGGER.warn("Reminder pattern will not compile: {}", raw, e)
                Optional.empty()
            }
        }.orElse(null)

    /**
     * Whether [pattern] has been diagnosed as unusable — it would not compile, or it exhausted the matching
     * budget. Lets the engine tell "this line did not match" apart from "this pattern can never be used", and
     * switch the offending reminder off rather than retrying it on every chat line for the rest of the session.
     */
    fun isKnownBad(pattern: String): Boolean = cache[pattern]?.isPresent == false

    /** Whether [raw] compiles, for the editor's inline validation. Null when fine, else the reason. */
    fun compileError(raw: String): String? =
        try {
            Pattern.compile(raw)
            null
        } catch (e: Exception) {
            e.message?.lineSequence()?.firstOrNull() ?: "Invalid pattern"
        }

    /**
     * Substitutes capture groups into [text]: `$0`–`$9` become the corresponding group, and `$$` a literal
     * `$`. A group that did not participate substitutes as empty rather than as `null`.
     *
     * **A `$n` the pattern has no group `n` for is left exactly as written**, as is any other `$x`. That is
     * what keeps a message mentioning `$5m` intact, which matters on Skyblock where prices in reminder text
     * are entirely ordinary — blanking them would quietly turn "sell at $5m" into "sell at m".
     */
    fun substitute(text: String, groups: List<String?>?): String {
        if (groups == null || !text.contains('$')) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch != '$' || i + 1 >= text.length) {
                out.append(ch)
                i++
                continue
            }
            val next = text[i + 1]
            when {
                next == '$' -> {
                    out.append('$')
                    i += 2
                }
                // Only a digit the pattern actually captured is a reference; anything else is just text.
                next.isDigit() && next - '0' < groups.size -> {
                    out.append(groups[next - '0'].orEmpty())
                    i += 2
                }

                else -> {
                    out.append(ch)
                    i++
                }
            }
        }
        return out.toString()
    }
}
