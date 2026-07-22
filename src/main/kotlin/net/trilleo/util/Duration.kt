package net.trilleo.util

import java.util.*

/**
 * Human durations, in both directions: `"2h30m"` in, `"2h 30m"` out.
 *
 * A text field with a parser rather than a slider, because the useful range spans seconds (an ability
 * cooldown) to days (a booster cookie), and no single slider step is usable across five orders of magnitude.
 *
 * Both directions are deliberately lenient about what they accept and strict about what they produce, so a
 * value typed by hand round-trips through [format] into something the parser would accept again.
 */
object Duration {

    private const val SECOND = 1000L
    private const val MINUTE = 60 * SECOND
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    /** The longest duration accepted, matching the clamp the reminder normalizer applies. */
    const val MAX_MS: Long = 7 * DAY

    /**
     * Parses `"90"`, `"90s"`, `"5m"`, `"2h30m"`, `"1d 12h"` into milliseconds, or null if it is not a
     * duration. A bare number is read as seconds, which is what someone typing `30` into a delay field means.
     *
     * Returns null rather than throwing, and rather than silently clamping: the caller is a `validate` hook
     * that needs to tell the difference between "unparseable" and "out of range" to say which is wrong.
     */
    fun parse(raw: String): Long? {
        val text = raw.trim().lowercase(Locale.ROOT).replace(" ", "")
        if (text.isEmpty()) return null

        // A bare number is seconds. Fractional so "1.5m" works; the unit loop below handles that too.
        text.toDoubleOrNull()?.let { seconds ->
            if (seconds < 0 || !seconds.isFinite()) return null
            return (seconds * SECOND).toLong()
        }

        var total = 0L
        var digits = StringBuilder()
        var sawUnit = false
        for (ch in text) {
            when {
                ch.isDigit() || ch == '.' -> digits.append(ch)
                else -> {
                    val amount = digits.toString().toDoubleOrNull() ?: return null
                    if (!amount.isFinite() || amount < 0) return null
                    val unit = when (ch) {
                        'd' -> DAY
                        'h' -> HOUR
                        'm' -> MINUTE
                        's' -> SECOND
                        else -> return null
                    }
                    total += (amount * unit).toLong()
                    digits = StringBuilder()
                    sawUnit = true
                }
            }
        }
        // Trailing digits with no unit ("2h30") would be ambiguous, and a lone unit ("m") carries no amount.
        if (digits.isNotEmpty() || !sawUnit) return null
        return total
    }

    /**
     * Renders a countdown at the coarsest useful precision: `"4d 03h"`, `"2h 05m"`, `"12:34"`, `"47s"`.
     *
     * Two units at most, because this is read at a glance from a HUD panel — knowing a cookie has `3d 07h`
     * left is the whole answer, and the seconds digit changing twenty times a second is noise that would
     * also defeat the render model's change detection.
     */
    fun format(ms: Long): String {
        if (ms <= 0) return "0s"
        val days = ms / DAY
        val hours = (ms % DAY) / HOUR
        val minutes = (ms % HOUR) / MINUTE
        val seconds = (ms % MINUTE) / SECOND
        return when {
            days > 0 -> String.format(Locale.ROOT, "%dd %02dh", days, hours)
            hours > 0 -> String.format(Locale.ROOT, "%dh %02dm", hours, minutes)
            minutes > 0 -> String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
            else -> "${seconds}s"
        }
    }
}
