package net.trilleo.notebook.model

import net.trilleo.config.JsonConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [NoteMeta.normalize], which is what stands between a hand-edited note file and a crash.
 *
 * Every case here is one GSON reflection gap: a field absent from the JSON arrives at its JVM default, which
 * for a non-nullable Kotlin `String` means `null` despite what the type says.
 */
class NoteMetaTest {

    private fun fromJson(json: String): NoteMeta =
        JsonConfig.GSON.fromJson(json, NoteMeta::class.java).also { it.normalize("fallback-id") }

    @Test
    fun `an empty object normalises to usable defaults`() {
        val meta = fromJson("{}")
        assertEquals("fallback-id", meta.title)
        assertEquals("", meta.folder)
        assertEquals("", meta.color)
        assertEquals("", meta.icon)
        assertTrue(meta.tags.isEmpty())
        assertFalse(meta.showsInHud)
        assertEquals(NoteMeta.FORMAT_VERSION, meta.v)
    }

    /** The whole reason normalize exists: these fields are declared non-null and arrive null anyway. */
    @Test
    fun `explicit nulls are repaired`() {
        val meta = fromJson("""{ "title": null, "folder": null, "color": null, "icon": null, "tags": null }""")
        assertEquals("fallback-id", meta.title)
        assertEquals("", meta.folder)
        assertTrue(meta.tags.isEmpty())
    }

    @Test
    fun `a null inside the tag list is dropped`() {
        val meta = fromJson("""{ "tags": ["mining", null, "  ", "PRICES"] }""")
        assertEquals(listOf("mining", "prices"), meta.tags)
    }

    @Test
    fun `tags are folded, trimmed and de-duplicated`() {
        val meta = NoteMeta().apply { setTagsFromText(" Mining , mining,, PRICES ,prices") }
        meta.normalize("id")
        assertEquals(listOf("mining", "prices"), meta.tags)
        assertEquals("mining, prices", meta.tagsAsText())
    }

    @Test
    fun `a folder is folded and stripped of leading and trailing slashes`() {
        val meta = fromJson("""{ "folder": "/Skyblock/Mining/" }""")
        assertEquals("skyblock/mining", meta.folder)
    }

    /**
     * A zero timestamp is left at zero rather than filled in with the current time. A fabricated date reads as
     * fact once it is on screen, and ConfigProfiles makes exactly this argument.
     */
    @Test
    fun `absent timestamps are left absent`() {
        val meta = fromJson("{}")
        assertEquals(0L, meta.createdAt)
        assertEquals(0L, meta.modifiedAt)
    }

    @Test
    fun `a note from a newer format is read-only`() {
        assertTrue(fromJson("""{ "v": ${NoteMeta.FORMAT_VERSION + 1} }""").readOnly)
        assertFalse(fromJson("""{ "v": ${NoteMeta.FORMAT_VERSION} }""").readOnly)
        // A hand-written file that omits the key, or writes nonsense into it, is this build's own format.
        assertFalse(fromJson("{}").readOnly)
        assertFalse(fromJson("""{ "v": 0 }""").readOnly)
    }

    /** Nullable so an absent key is distinguishable from an explicit "no" — see the field's own doc. */
    @Test
    fun `the hud flag reads through showsInHud`() {
        assertFalse(fromJson("{}").showsInHud)
        assertFalse(fromJson("""{ "hud": false }""").showsInHud)
        assertTrue(fromJson("""{ "hud": true }""").showsInHud)
    }

    @Test
    fun `copy is deep enough that editing one note cannot change another`() {
        val original = NoteMeta().apply { title = "A"; tags = mutableListOf("one") }
        val copy = original.copy()
        copy.title = "B"
        copy.tags.add("two")

        assertEquals("A", original.title)
        assertEquals(listOf("one"), original.tags)
        assertEquals(listOf("one", "two"), copy.tags)
    }

    /** Identity equality, deliberately — a list row deletes *this* note, not one that looks the same. */
    @Test
    fun `two identical metas are not equal`() {
        val a = NoteMeta().apply { title = "same" }
        val b = NoteMeta().apply { title = "same" }
        assertFalse(a == b)
        assertTrue(a == a)
    }
}
