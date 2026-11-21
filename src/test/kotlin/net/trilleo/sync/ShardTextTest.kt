package net.trilleo.sync

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Reading the two shard menus' titles, item names and lore. */
class ShardTextTest {

    @Test
    fun `menu titles give the menu and the page`() {
        assertEquals(MenuPage(ShardMenu.ATTRIBUTES, 1, 1), ShardText.menuPage("Attribute Menu"))
        assertEquals(MenuPage(ShardMenu.ATTRIBUTES, 11, 13), ShardText.menuPage("(11/13) Attribute Menu"))
        assertEquals(MenuPage(ShardMenu.HUNTING_BOX, 2, 3), ShardText.menuPage("(2/3) Hunting Box"))
        assertNull(ShardText.menuPage("Fusion Box"))
        assertNull(ShardText.menuPage("(4/3) Hunting Box"))
    }

    @Test
    fun `an attribute item gives its shard and level`() {
        val lore = listOf("Some description", "Source: Drowned Shard (R18)", "Enabled: Yes")
        assertEquals("Drowned" to 9, ShardText.attributeEntry("Berry Eater IX", lore))
        assertEquals("Barbarian Duke X" to 4, ShardText.attributeEntry("Some Power IV", listOf("Source: Barbarian Duke X Shard (E27)")))
    }

    @Test
    fun `an attribute without a numeral is level 0`() {
        assertEquals("Galaxy Fish" to 0, ShardText.attributeEntry("Nature Elemental", listOf("Source: Galaxy Fish Shard (L41)")))
    }

    @Test
    fun `an item with no source line is not an attribute`() {
        assertNull(ShardText.attributeEntry("Advanced Mode", listOf("Advanced Mode unlocked at 30")))
        assertNull(ShardText.attributeEntry("Close", emptyList()))
    }

    @Test
    fun `a Hunting Box item gives its shard and count`() {
        assertEquals("Voracious Spider" to 1729, ShardText.heldEntry("Voracious Spider", listOf("Owned: 1,729 Shards")))
        assertEquals("Bal" to 1, ShardText.heldEntry("Bal Shard", listOf("Owned: 1 Shard")))
        assertNull(ShardText.heldEntry("Next Page", listOf("Click to go forward")))
    }

    @Test
    fun `a search query is spotted`() {
        assertTrue(ShardText.isSearchActive(listOf("Search", "Query: spider")))
        assertFalse(ShardText.isSearchActive(listOf("Search", "Click to search")))
    }

    @Test
    fun `only the middle of the box holds shards`() {
        assertTrue(ShardText.isContentSlot(10))
        assertTrue(ShardText.isContentSlot(43))
        assertFalse(ShardText.isContentSlot(9))
        assertFalse(ShardText.isContentSlot(17))
        assertFalse(ShardText.isContentSlot(4))
        assertFalse(ShardText.isContentSlot(49))
    }

    @Test
    fun `roman numerals read from I to X and beyond`() {
        val expected = listOf("I" to 1, "II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6, "IX" to 9, "X" to 10)
        expected.forEach { (numeral, value) -> assertEquals(value, ShardText.roman(numeral), numeral) }
        assertNull(ShardText.roman("Q"))
        assertNull(ShardText.roman(""))
    }

    @Test
    fun `the fallback ID follows Hypixel's naming`() {
        assertEquals("SHARD_VORACIOUS_SPIDER", ShardText.fallbackId("Voracious Spider"))
        assertEquals("SHARD_BAL", ShardText.fallbackId(" bal "))
        assertEquals("SHARD_KING_COBRA", ShardText.fallbackId("King-Cobra"))
    }
}
