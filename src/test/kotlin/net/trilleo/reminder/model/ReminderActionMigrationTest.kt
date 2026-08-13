package net.trilleo.reminder.model

import net.trilleo.config.JsonConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Reading a title written by a build that predates the title helper.
 *
 * This is the part of the change that can only fail on someone else's machine: the code is exercised exactly
 * once per config file per upgrade, against JSON this repository no longer produces. A title that came back
 * white, silent or three seconds too short would look like a setting that had simply been forgotten, and there
 * would be nothing left in the file to say otherwise.
 *
 * Goes through the same GSON instance the config files do, so what is tested is the deserialization the mod
 * actually performs and not a hand-built object that skipped it.
 */
class ReminderActionMigrationTest {

    private fun read(json: String): ReminderAction =
        JsonConfig.GSON.fromJson(json, ReminderAction::class.java).also { it.normalize() }

    @Test
    fun `an old title keeps its subtitle, colour and duration`() {
        val action = read(
            """
            {
              "kind": "TITLE",
              "subtitle": "the smaller line",
              "titleColor": "#FF5555",
              "titleSeconds": 6.5
            }
            """,
        )

        assertEquals("the smaller line", action.title.subtitle.text)
        assertEquals("#FF5555", action.title.title.color)
        assertEquals(6.5, action.title.staySeconds)
    }

    @Test
    fun `the old keys are dropped, so the next write is clean`() {
        @Suppress("DEPRECATION")
        val action = read("""{"kind":"TITLE","subtitle":"beneath","titleColor":"#00FF00","titleSeconds":2.0}""")

        @Suppress("DEPRECATION")
        val written = JsonConfig.GSON.toJson(action)
        assertFalse(written.contains("titleColor"), written)
        assertFalse(written.contains("titleSeconds"), written)
        // The subtitle moved into the new block; the top-level key it came from is gone.
        assertFalse(written.contains("\"subtitle\":\"beneath\""), written)
        assertTrue(written.contains("beneath"), written)
    }

    @Test
    fun `a blank old colour stays blank rather than becoming black`() {
        // The legacy field's empty value meant "leave it the game's white", which is what an empty colour still
        // means. Parsed rather than defaulted through, it would have come out as 0x000000 — an invisible title.
        val action = read("""{"kind":"TITLE","subtitle":"","titleColor":"","titleSeconds":3.5}""")

        assertEquals("", action.title.title.color)
    }

    @Test
    fun `an action with no title block at all still gets one`() {
        // What a sound action from any older file looks like: no `title` key, and no legacy keys either.
        val action = read("""{"kind":"SOUND","value":"minecraft:block.note_block.pling","pitch":1.5}""")

        assertEquals(ActionKind.SOUND, action.kind)
        assertEquals(1.5, action.pitch)
        assertEquals("", action.title.title.color)
        assertEquals("", action.title.sound)
    }

    @Test
    fun `an out-of-range old duration is bounded rather than rejected`() {
        val action = read("""{"kind":"TITLE","titleSeconds":9999.0}""")
        assertEquals(net.trilleo.title.model.TitleSpec.STAY_MAX, action.title.staySeconds)
    }

    @Test
    fun `a file already written by this build is left alone`() {
        val action = read(
            """
            {
              "kind": "TITLE",
              "title": {
                "title": {"text": "", "color": "chroma", "bold": true},
                "subtitle": {"text": "under", "color": "#AAAAAA"},
                "fadeInSeconds": 0.0,
                "staySeconds": 8.0,
                "fadeOutSeconds": 2.0,
                "sound": "minecraft:block.anvil.land",
                "pitch": 1.5,
                "volume": 0.6
              }
            }
            """,
        )

        assertEquals("chroma", action.title.title.color)
        assertTrue(action.title.title.bold)
        assertEquals("under", action.title.subtitle.text)
        assertEquals(0.0, action.title.fadeInSeconds)
        assertEquals(8.0, action.title.staySeconds)
        assertEquals("minecraft:block.anvil.land", action.title.sound)
    }

    @Test
    fun `a hand-edited file naming both wins with the old key`() {
        // The old key is the one such a file was written to use, and this build never leaves one behind — so
        // reaching this case at all means someone typed it deliberately.
        val action = read(
            """
            {
              "kind": "TITLE",
              "titleColor": "#FF0000",
              "title": { "title": {"color": "#0000FF"} }
            }
            """,
        )

        assertEquals("#FF0000", action.title.title.color)
    }

    @Test
    fun `copying an action copies its title rather than sharing it`() {
        val original = read("""{"kind":"TITLE","subtitle":"first","titleColor":"#112233"}""")
        val duplicate = original.copy()

        duplicate.title.subtitle.text = "second"
        duplicate.title.title.color = "#445566"

        assertEquals("first", original.title.subtitle.text)
        assertEquals("#112233", original.title.title.color)
    }
}
