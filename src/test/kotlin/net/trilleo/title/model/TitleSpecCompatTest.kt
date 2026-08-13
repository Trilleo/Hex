package net.trilleo.title.model

import net.trilleo.config.JsonConfig
import net.trilleo.reminder.model.ReminderAction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Reading a title whose two lines were written as objects rather than strings.
 *
 * Pinned because of what it actually cost: a dev client refused the whole of `chathighlights.json` over one
 * nested field, fell back to defaults, and the next save wrote those defaults over the rules. The type
 * mismatch is invisible until GSON meets it, so the only way to know this keeps working is to hand it the
 * shape that broke.
 */
class TitleSpecCompatTest {

    private fun read(json: String): TitleSpec =
        JsonConfig.GSON.fromJson(json, TitleSpec::class.java).also { it.normalize() }

    @Test
    fun `the object shape that broke a config file now reads`() {
        // The exact shape the old build wrote, and the one the log named at
        // `$.highlights[0].actions[0].title.title`.
        val spec = read(
            """
            {
              "title": {
                "text": "",
                "color": "#FF5555",
                "bold": true,
                "italic": false,
                "underline": false,
                "strikethrough": false,
                "obfuscated": false
              },
              "subtitle": {
                "text": "get to the platform",
                "color": "#AAAAAA",
                "bold": false,
                "italic": true,
                "underline": false,
                "strikethrough": false,
                "obfuscated": false
              },
              "fadeInSeconds": 0.5,
              "staySeconds": 6.0,
              "fadeOutSeconds": 1.0,
              "sound": "minecraft:block.anvil.land",
              "pitch": 1.5,
              "volume": 0.6
            }
            """,
        )

        assertEquals("&#FF5555&l", spec.title)
        assertEquals("&#AAAAAA&oget to the platform", spec.subtitle)
        assertEquals(6.0, spec.staySeconds)
        assertEquals("minecraft:block.anvil.land", spec.sound)
    }

    @Test
    fun `the colour comes before the styles, because a colour code clears them`() {
        // `&l&#FF5555` would be red and *not* bold — the flags would be wiped by the colour that followed
        // them. This ordering is the whole reason the conversion is not a concatenation.
        val spec = read("""{"title":{"text":"BOSS","color":"#FF5555","bold":true,"italic":true}}""")
        assertEquals("&#FF5555&l&oBOSS", spec.title)
    }

    @Test
    fun `a chroma line stays chroma`() {
        val spec = read("""{"title":{"text":"","color":"chroma","bold":true}}""")
        assertEquals("&z&l", spec.title)
    }

    @Test
    fun `a line with no colour keeps only its styles`() {
        val spec = read("""{"title":{"text":"BOSS","color":"","bold":true}}""")
        assertEquals("&lBOSS", spec.title)
    }

    @Test
    fun `a plain line survives with nothing added`() {
        val spec = read("""{"title":{"text":"BOSS","color":""}}""")
        assertEquals("BOSS", spec.title)
    }

    @Test
    fun `the string shape this build writes is untouched`() {
        val spec = read("""{"title":"&c&lBOSS","subtitle":"&7beneath","staySeconds":4.0}""")

        assertEquals("&c&lBOSS", spec.title)
        assertEquals("&7beneath", spec.subtitle)
        assertEquals(4.0, spec.staySeconds)
    }

    @Test
    fun `writing never produces the old shape again`() {
        // A compatibility read that also changed the write would keep the old shape alive for ever.
        val written = JsonConfig.GSON.toJson(read("""{"title":{"text":"BOSS","color":"#FF5555"}}"""))
        assertTrue(written.contains("\"title\": \"&#FF5555BOSS\""), written)
        assertFalse(written.contains("\"color\""), written)
    }

    @Test
    fun `codes are written as codes, not as escapes`() {
        // GSON escapes `&` to a unicode sequence by default, which is valid JSON and unreadable to a person.
        // A title line is mostly ampersands, and this file is one people open.
        val written = JsonConfig.GSON.toJson(read("""{"title":"&c&lBOSS"}"""))
        assertFalse(written.contains("u0026"), written)
    }

    @Test
    fun `a whole action carrying the old shape loads rather than throwing`() {
        // The failure was never really about a title: it took the entire config file down with it.
        val action = JsonConfig.GSON.fromJson(
            """{"kind":"TITLE","title":{"title":{"text":"","color":"#FF5555","bold":true},"subtitle":{"text":"x"}}}""",
            ReminderAction::class.java,
        ).also { it.normalize() }

        assertEquals("&#FF5555&l", action.title.title)
        assertEquals("x", action.title.subtitle)
    }

    @Test
    fun `junk where a line should be does not throw`() {
        // A hand-edited file can hold anything at all, and this runs before the normalizer can repair it.
        assertEquals("", read("""{"title":{"color":12345,"bold":"yes"}}""").title)
        assertEquals("", read("""{"title":{}}""").title)
    }
}
