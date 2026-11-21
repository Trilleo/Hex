package net.trilleo.sync

/**
 * What one visit to a shard menu read, across however many of its pages the player looked at.
 *
 * @property entries shard name to level (Attribute Menu) or count (Hunting Box).
 * @property complete every page was seen and no search narrowed any of them, so [entries] is the whole list.
 *   Only a complete read may be sent as `replace`, which sets everything left out to 0.
 */
data class MenuSnapshot(val menu: ShardMenu, val entries: Map<String, Int>, val complete: Boolean)

/**
 * Accumulates the pages of one menu visit until the player closes it or leaves for another screen.
 *
 * Paging through a Hypixel menu opens a new container each time, titled `(2/3) …`, so a visit is a run of
 * screens of the same menu. Each page keeps only its latest read: items arrive over several ticks and can
 * change while open, and the last read is the one closest to what the player left the menu showing.
 */
class MenuSession(val menu: ShardMenu) {
    private val pages = HashMap<Int, Map<String, Int>>()
    private var totalPages = 1
    private var searched = false

    /**
     * Records a read of [page]. [loaded] says whether the container had any items in it at all — a read taken
     * before the server has filled the menu is not evidence that a page is empty.
     */
    fun accept(page: MenuPage, entries: Map<String, Int>, searchActive: Boolean, loaded: Boolean) {
        if (page.menu != menu || !loaded) return
        totalPages = maxOf(totalPages, page.pages)
        if (searchActive) searched = true
        pages[page.page] = entries
    }

    /** The visit so far, or null when no page was ever read. */
    fun snapshot(): MenuSnapshot? {
        if (pages.isEmpty()) return null
        val merged = LinkedHashMap<String, Int>()
        pages.keys.sorted().forEach { merged.putAll(pages.getValue(it)) }
        val complete = !searched && (1..totalPages).all { it in pages }
        return MenuSnapshot(menu, merged, complete)
    }
}
