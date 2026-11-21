package net.trilleo.sync

import org.slf4j.LoggerFactory

/**
 * Shard names to Hypixel item IDs, from the site's own list at `GET /v1/skyblock/shards`.
 *
 * The menus only ever show names (see [ShardText]), and a few shards are named differently from their IDs,
 * so guessing `SHARD_<NAME>` alone would upload some shards under IDs the site does not know. The site's
 * list carries each shard's `name` and the `attribute` it grants, so both spellings map to the right ID.
 *
 * Fetched lazily on [SyncThread] the first time a sync needs it, and kept for the session. If the fetch fails
 * the sync goes ahead on [ShardText.fallbackId] — the site skips any ID it does not recognise and says so,
 * which is better than holding back everything the player read — and the list is tried again later.
 */
internal object ShardCatalog {
    private val LOGGER = LoggerFactory.getLogger("hex/sync")

    private var byName: Map<String, String> = emptyMap()
    private var loadedFrom: String? = null
    private var attemptedFrom: String? = null
    private var lastAttempt = 0L

    /** The item ID for a shard name. */
    fun resolve(name: String): String {
        ensureLoaded()
        return byName[ShardText.nameKey(name)] ?: ShardText.fallbackId(name)
    }

    private fun ensureLoaded() {
        val base = TrilleoApi.base()
        if (loadedFrom == base) return
        val now = System.currentTimeMillis()
        // A failed fetch is not retried on every sync, only after a while — unless the address changed.
        if (attemptedFrom == base && now - lastAttempt < RETRY_MILLIS) return
        attemptedFrom = base
        lastAttempt = now
        when (val result = TrilleoApi.call("GET", "/v1/skyblock/shards")) {
            is ApiResult.Ok -> {
                val map = HashMap<String, String>()
                result.body.getAsJsonArray("items")?.forEach { element ->
                    val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                    val id = item.stringOrNull("id") ?: return@forEach
                    item.stringOrNull("name")?.let { map[ShardText.nameKey(it)] = id }
                    // The attribute name is a second spelling; a shard's own name wins over it on a clash.
                    item.stringOrNull("attribute")?.let { map.putIfAbsent(ShardText.nameKey(it), id) }
                }
                if (map.isNotEmpty()) {
                    byName = map
                    loadedFrom = base
                }
            }

            is ApiResult.Error -> LOGGER.warn("Could not read the shard list: {}", result.error.code)
            is ApiResult.Network -> LOGGER.info("Could not read the shard list: {}", result.reason)
        }
    }

    private const val RETRY_MILLIS = 10 * 60 * 1000L
}
