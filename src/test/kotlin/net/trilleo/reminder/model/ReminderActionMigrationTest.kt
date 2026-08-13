package net.trilleo.reminder.model

import net.trilleo.config.JsonConfig
import net.trilleo.title.model.TitleFormat
import net.trilleo.title.model.TitleSpec
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
    fun `an old colour becomes a code that dresses the alert's message`() {
        // The old field could only tint the message, never replace it — which is exactly what a line of nothing
        // but codes means now, so the migration is the identity in meaning even though the storage changed.
        val action = read("""{"kind":"TITLE","titleColor":"#FF5555"}""")

        assertEquals("&#FF5555", action.title.title)
        assertFalse(TitleFormat.hasText(action.title.title))
        assertEquals("&#FF5555cookie ran out", TitleFormat.merge(action.title.title, "cookie ran out"))
    }

    @Test
    fun `an old title keeps its subtitle and duration`() {
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

        assertEquals("the smaller line", action.title.subtitle)
        assertEquals(6.5, action.title.staySeconds)
    }

    @Test
    fun `the old keys are dropped, so the next write is clean`() {
        val action = read("""{"kind":"TITLE","subtitle":"beneath","titleColor":"#00FF00","titleSeconds":2.0}""")
        val written = JsonConfig.GSON.toJson(action)

        assertFalse(written.contains("titleColor"), written)
        assertFalse(written.contains("titleSeconds"), written)
        // The subtitle moved into the new block; the top-level key it came from is gone.
        assertFalse(written.contains("\"subtitle\":\"beneath\""), written)
        assertTrue(written.contains("beneath"), written)
    }

    @Test
    fun `a blank old colour leaves the line empty rather than writing a black code`() {
        // The legacy empty value meant "leave it the game's white". Parsed rather than guarded, it would have
        // become `&#000000` — an invisible title, and one nothing in the editor would explain.
        val action = read("""{"kind":"TITLE","subtitle":"","titleColor":"","titleSeconds":3.5}""")

        assertEquals("", action.title.title)
    }

    @Test
    fun `an action with no title block at all still gets one`() {
        // What a sound action from any older file looks like: no `title` key, and no legacy keys either.
        val action = read("""{"kind":"SOUND","value":"minecraft:block.note_block.pling","pitch":1.5}""")

        assertEquals(ActionKind.SOUND, action.kind)
        assertEquals(1.5, action.pitch)
        assertEquals("", action.title.title)
        assertEquals("", action.title.sound)
    }

    @Test
    fun `an out-of-range old duration is bounded rather than rejected`() {
        val action = read("""{"kind":"TITLE","titleSeconds":9999.0}""")
        assertEquals(TitleSpec.STAY_MAX, action.title.staySeconds)
    }

    @Test
    fun `a file already written by this build is left alone`() {
        val action = read(
            """
            {
              "kind": "TITLE",
              "title": {
                "title": "&z&lBOSS &einbound",
                "subtitle": "&7get to the platform",
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

        assertEquals("&z&lBOSS &einbound", action.title.title)
        assertEquals("&7get to the platform", action.title.subtitle)
        assertEquals(0.0, action.title.fadeInSeconds)
        assertEquals(8.0, action.title.staySeconds)
        assertEquals("minecraft:block.anvil.land", action.title.sound)
    }

    @Test
    fun `an old colour on a line that already has codes goes in front of them`() {
        // Only reachable from a hand-edited file carrying both. The old key is the one such a file was written
        // to use, so it leads — and leading is also where a colour code has to be to apply to the whole line.
        val action = read("""{"kind":"TITLE","titleColor":"#FF0000","title":{"title":"&lBOSS"}}""")

        assertEquals("&#FF0000&lBOSS", action.title.title)
    }

    @Test
    fun `copying an action copies its title rather than sharing it`() {
        val original = read("""{"kind":"TITLE","subtitle":"first","titleColor":"#112233"}""")
        val duplicate = original.copy()

        duplicate.title.subtitle = "second"
        duplicate.title.title = "&a"

        assertEquals("first", original.title.subtitle)
        assertEquals("&#112233", original.title.title)
    }
}
