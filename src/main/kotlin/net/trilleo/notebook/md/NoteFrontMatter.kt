package net.trilleo.notebook.md

import net.trilleo.config.JsonConfig
import net.trilleo.notebook.model.NoteMeta

/**
 * The metadata header at the top of a note file, and the reason a `.md` file *is* a note rather than half of
 * one.
 *
 * ```
 * ---hex
 * { "title": "Mining routes", "folder": "skyblock/mining", "tags": ["mining"], … }
 * ---
 * # Dwarven Mines
 * ```
 *
 * The payload is a [NoteMeta] through [JsonConfig.GSON] — the same serialiser and the same model the index
 * uses, so there is no second schema to keep in step. The header makes a note file self-describing, which is
 * what lets `index.json` be a disposable cache, lets a note dropped into the notes directory be adopted whole,
 * and lets sharing a note be nothing more than sharing its text.
 *
 * ### On the `---hex` marker
 *
 * Plain `---` would be ambiguous with a horizontal rule, which is a real construct in the body. Tagging the
 * opening fence removes the ambiguity at the only point it could arise, and a file that does not open with it
 * is simply a note with no metadata — which is exactly what a `.md` file written by something else is.
 */
object NoteFrontMatter {

    private const val OPEN = "---hex"
    private const val CLOSE = "---"

    /**
     * Splits [text] into its metadata and its body.
     *
     * Returns a null [NoteMeta] when there is no header, or when the header does not parse — in both cases the
     * whole of [text] is the body. Failing open rather than throwing is deliberate: a note whose header a hand
     * edit broke should lose its colour, not its contents.
     */
    fun read(text: String): Pair<NoteMeta?, String> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        if (!normalized.startsWith("$OPEN\n")) return null to normalized

        val body = normalized.substring(OPEN.length + 1)
        val end = findClose(body) ?: return null to normalized

        val json = body.substring(0, end.first)
        val meta = runCatching { JsonConfig.GSON.fromJson(json, NoteMeta::class.java) }.getOrNull()
        return meta to body.substring(end.second)
    }

    /** [text] with a fresh header for [meta] in front of it. The body is written exactly as given. */
    fun write(meta: NoteMeta, body: String): String =
        buildString {
            append(OPEN).append('\n')
            append(JsonConfig.GSON.toJson(meta)).append('\n')
            append(CLOSE).append('\n')
            append(body)
        }

    /**
     * Where the closing fence starts and where the body after it begins, or null if there is no closing fence.
     *
     * Matched on a line that is exactly `---`, so a `---` inside the JSON — which can only appear inside a
     * string, and then not at the start of a line on its own — cannot end the header early.
     */
    private fun findClose(body: String): Pair<Int, Int>? {
        var lineStart = 0
        while (lineStart <= body.length) {
            val newline = body.indexOf('\n', lineStart)
            val lineEnd = if (newline < 0) body.length else newline
            if (body.substring(lineStart, lineEnd).trimEnd() == CLOSE) {
                return lineStart to (if (newline < 0) body.length else newline + 1)
            }
            if (newline < 0) return null
            lineStart = newline + 1
        }
        return null
    }
}
