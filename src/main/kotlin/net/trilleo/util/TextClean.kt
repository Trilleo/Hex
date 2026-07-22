package net.trilleo.util

/**
 * Strips the decoration Hypixel puts on the text a client can read, leaving what a human sees.
 *
 * Removes `§` colour codes, control characters, and the invisible padding Hypixel appends — a non-breaking
 * space, a zero-width space, or a zero-width non-breaking space, varying per line and per tick so that
 * otherwise-identical scoreboard entries stay unique. Leaving those in would mean no two reads of the same
 * line ever compared equal.
 *
 * Ordinary spaces are deliberately kept. They are meaningful in both callers: island names contain them
 * (`"private island"`), and a chat pattern keys on word boundaries, so a reminder pattern with a space in it
 * would never match against text that had them removed.
 */
object TextClean {

    private const val NBSP = ' '
    private const val ZERO_WIDTH = '​'
    private const val ZERO_WIDTH_NBSP = '﻿'

    /** Strips formatting and invisible padding, preserving ordinary spacing, and trims the result. */
    fun strip(raw: String): String {
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            when {
                // Skip the code character that follows as well, hence the extra step.
                ch == '§' -> i++
                ch == NBSP || ch == ZERO_WIDTH || ch == ZERO_WIDTH_NBSP -> Unit
                ch.isISOControl() -> Unit
                else -> out.append(ch)
            }
            i++
        }
        return out.toString().trim()
    }
}
