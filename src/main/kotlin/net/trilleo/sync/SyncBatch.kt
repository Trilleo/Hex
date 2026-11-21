package net.trilleo.sync

/** How the site applies a list: `replace` sets everything left out to 0, `merge` changes only what is sent. */
enum class SyncMode(val apiName: String) {
    REPLACE("replace"),
    MERGE("merge"),
}

/** One list waiting to be sent: shard name to level or count, and how the site should apply it. */
data class ListPart(val mode: SyncMode, val entries: Map<String, Int>) {
    companion object {
        fun of(snapshot: MenuSnapshot): ListPart =
            ListPart(if (snapshot.complete) SyncMode.REPLACE else SyncMode.MERGE, snapshot.entries)
    }
}

/**
 * The pure rules for combining reads that pile up before a sync goes out.
 *
 * Both rules keep the meaning of what the player saw. A newer complete read is the whole truth, so it
 * replaces whatever was waiting. A newer partial read only knows the shards it saw, so it is laid over what
 * was waiting — and a waiting complete list stays complete, its seen shards simply updated.
 */
object SyncBatch {

    /** [next] combined into what was already waiting for the same list. */
    fun coalesce(waiting: ListPart?, next: ListPart): ListPart =
        if (waiting == null || next.mode == SyncMode.REPLACE) next
        else ListPart(waiting.mode, waiting.entries + next.entries)

    /**
     * Splits the two waiting lists into requests. One request carries one `mode` for both lists, so two lists
     * with different modes go as two requests; a list left out of a request is untouched by it.
     */
    fun requests(levels: ListPart?, held: ListPart?): List<Pair<ListPart?, ListPart?>> = when {
        levels == null && held == null -> emptyList()
        levels == null || held == null || levels.mode == held.mode -> listOf(levels to held)
        else -> listOf(levels to null, null to held)
    }
}
