package net.trilleo.sync

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * The rules that decide between `replace` and `merge`. Getting these wrong is the one way this feature can
 * destroy data on the site: a partial read sent as `replace` sets every shard it did not see to 0.
 */
class SyncRulesTest {

    private val attrs = { page: Int, pages: Int -> MenuPage(ShardMenu.ATTRIBUTES, page, pages) }

    @Test
    fun `every page seen is a complete read`() {
        val session = MenuSession(ShardMenu.ATTRIBUTES)
        session.accept(attrs(1, 2), mapOf("A" to 1), searchActive = false, loaded = true)
        session.accept(attrs(2, 2), mapOf("B" to 2), searchActive = false, loaded = true)
        assertEquals(MenuSnapshot(ShardMenu.ATTRIBUTES, mapOf("A" to 1, "B" to 2), complete = true), session.snapshot())
    }

    @Test
    fun `a page not visited makes the read partial`() {
        val session = MenuSession(ShardMenu.ATTRIBUTES)
        session.accept(attrs(1, 3), mapOf("A" to 1), searchActive = false, loaded = true)
        assertFalse(session.snapshot()!!.complete)
    }

    @Test
    fun `a search makes the read partial even with every page seen`() {
        val session = MenuSession(ShardMenu.HUNTING_BOX)
        session.accept(MenuPage(ShardMenu.HUNTING_BOX, 1, 1), mapOf("A" to 5), searchActive = true, loaded = true)
        assertFalse(session.snapshot()!!.complete)
    }

    @Test
    fun `a read before the menu filled in counts for nothing`() {
        val session = MenuSession(ShardMenu.HUNTING_BOX)
        session.accept(MenuPage(ShardMenu.HUNTING_BOX, 1, 1), emptyMap(), searchActive = false, loaded = false)
        assertNull(session.snapshot())
    }

    @Test
    fun `an empty Hunting Box that did load is a complete empty read`() {
        val session = MenuSession(ShardMenu.HUNTING_BOX)
        session.accept(MenuPage(ShardMenu.HUNTING_BOX, 1, 1), emptyMap(), searchActive = false, loaded = true)
        assertEquals(MenuSnapshot(ShardMenu.HUNTING_BOX, emptyMap(), complete = true), session.snapshot())
    }

    @Test
    fun `a newer complete read replaces what was waiting`() {
        val waiting = ListPart(SyncMode.MERGE, mapOf("A" to 1))
        val next = ListPart(SyncMode.REPLACE, mapOf("B" to 2))
        assertEquals(next, SyncBatch.coalesce(waiting, next))
    }

    @Test
    fun `a newer partial read updates what was waiting and keeps its mode`() {
        val waiting = ListPart(SyncMode.REPLACE, mapOf("A" to 1, "B" to 1))
        val next = ListPart(SyncMode.MERGE, mapOf("B" to 3))
        assertEquals(ListPart(SyncMode.REPLACE, mapOf("A" to 1, "B" to 3)), SyncBatch.coalesce(waiting, next))
    }

    @Test
    fun `lists with different modes go as separate requests`() {
        val levels = ListPart(SyncMode.REPLACE, mapOf("A" to 1))
        val held = ListPart(SyncMode.MERGE, mapOf("B" to 2))
        assertEquals(listOf(levels to null, null to held), SyncBatch.requests(levels, held))
        assertEquals(listOf(levels to levels), SyncBatch.requests(levels, levels))
        assertEquals(listOf(null to held), SyncBatch.requests(null, held))
        assertTrue(SyncBatch.requests(null, null).isEmpty())
    }
}
