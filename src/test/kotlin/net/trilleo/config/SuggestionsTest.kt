package net.trilleo.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The ranking behind a completing settings field.
 *
 * Testable for the same reason [net.trilleo.notebook.md.NoteBlocksTest] is: [Suggestions] turns a list of
 * strings into a shorter list of strings and touches nothing of Minecraft. What it covers is the part with the
 * interesting edges — the three tiers, and which of them a candidate lands in when it would qualify for
 * several.
 */
class SuggestionsTest {

    private val entities = listOf(
        "minecraft:cave_spider",
        "minecraft:spider",
        "minecraft:zombie",
        "minecraft:zombie_horse",
        "othermod:zombie_king",
    )

    @Test
    fun `a path prefix outranks a match in the middle`() {
        val ranked = Suggestions.rank(entities, "spider")

        // "minecraft:spider" matches on its path; "minecraft:cave_spider" only somewhere inside. Both are
        // offered — dropping the second is what would make searching by a word feel broken.
        assertEquals(listOf("minecraft:spider", "minecraft:cave_spider"), ranked)
    }

    @Test
    fun `a full id prefix matches when the path does not`() {
        assertEquals(
            listOf("minecraft:zombie", "minecraft:zombie_horse"),
            Suggestions.rank(entities, "minecraft:zomb"),
        )
    }

    @Test
    fun `a path prefix outranks another namespace`() {
        assertEquals(
            listOf("minecraft:zombie", "minecraft:zombie_horse", "othermod:zombie_king"),
            Suggestions.rank(entities, "zombie"),
        )
    }

    @Test
    fun `an empty query offers everything, so the vocabulary is browsable`() {
        assertEquals(entities, Suggestions.rank(entities, ""))
        assertEquals(entities, Suggestions.rank(entities, "   "))
    }

    @Test
    fun `case and surrounding space do not matter`() {
        assertEquals(Suggestions.rank(entities, "zombie"), Suggestions.rank(entities, " ZOMBIE "))
    }

    @Test
    fun `a candidate is offered once even when it qualifies twice`() {
        // "minecraft:zombie" starts with its own path prefix and also contains it; landing in two tiers would
        // show it twice and make the arrow keys stop on it twice.
        val ranked = Suggestions.rank(entities, "zombie")

        assertEquals(ranked.distinct(), ranked)
    }

    @Test
    fun `nothing matching yields nothing`() {
        assertTrue(Suggestions.rank(entities, "dragon").isEmpty())
        assertTrue(Suggestions.rank(emptyList(), "zombie").isEmpty())
    }

    @Test
    fun `the limit caps the shortlist`() {
        val many = (1..100).map { "minecraft:mob_$it" }

        assertEquals(3, Suggestions.rank(many, "mob", limit = 3).size)
        assertEquals(Suggestions.LIMIT, Suggestions.rank(many, "mob").size)
    }

    @Test
    fun `a candidate with no namespace is still matched`() {
        // Nothing says a vocabulary has to be namespaced ids; substringAfter leaves such a value whole.
        assertEquals(listOf("tab"), Suggestions.rank(listOf("tab", "escape"), "ta"))
    }
}
