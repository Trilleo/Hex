package net.trilleo.config

import com.google.gson.reflect.TypeToken
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * What happens to a config file this build cannot read.
 *
 * The answer used to be "it is silently replaced by the defaults that overwrite it", and that is how a set of
 * chat highlight rules was lost to one nested field changing type. A failed load is recoverable; a failed load
 * followed by a save is not, and the save comes seconds later without anyone touching anything.
 */
class JsonConfigTest {

    /** A config shaped like the real ones: a list that a normalizer has to un-null. */
    data class Settings(var rules: MutableList<String> = mutableListOf(), var count: Int = 0)

    private fun config() = JsonConfig(
        name = "sample",
        type = object : TypeToken<Settings>() {}.type,
        default = { Settings() },
        normalizer = { @Suppress("SENSELESS_COMPARISON") if (it.rules == null) it.rules = mutableListOf() },
    )

    private fun write(dir: Path, text: String): Path =
        dir.resolve("sample.json").also { Files.writeString(it, text) }

    @Test
    fun `a good file round-trips`(@TempDir dir: Path) {
        val config = config()
        config.saveTo(dir, Settings(mutableListOf("a", "b"), 2))

        val loaded = config.loadFrom(dir)
        assertEquals(listOf("a", "b"), loaded.rules)
        assertEquals(2, loaded.count)
    }

    @Test
    fun `an unreadable file is kept, not left to be overwritten`(@TempDir dir: Path) {
        // A field of the wrong type — exactly the shape of the failure this test exists for.
        val original = """{"rules": {"not": "a list"}, "count": 7}"""
        val path = write(dir, original)

        val config = config()
        val loaded = config.loadFrom(dir)

        // The caller gets defaults, so the client keeps running...
        assertTrue(loaded.rules.isEmpty())
        // ...and the very next save, which is what destroyed the data before, cannot reach the original.
        config.saveTo(dir, loaded)

        val backup = dir.resolve("sample.json${JsonConfig.BROKEN_SUFFIX}")
        assertTrue(Files.exists(backup), "the unreadable file should have been kept")
        assertEquals(original, Files.readString(backup))
        assertTrue(Files.exists(path), "a fresh file should have been written in its place")
    }

    @Test
    fun `malformed json is kept too`(@TempDir dir: Path) {
        // Not a type mismatch but a truncated file — what a crash mid-write leaves behind.
        write(dir, """{"rules": ["a", """)
        config().loadFrom(dir)

        assertTrue(Files.exists(dir.resolve("sample.json${JsonConfig.BROKEN_SUFFIX}")))
    }

    @Test
    fun `a second failure does not replace the first backup`(@TempDir dir: Path) {
        // The backup holds the player's data. Anything failing afterwards is a file this build wrote itself,
        // so replacing the backup with it would trade the only copy that mattered for one that never did.
        val real = """{"rules": {"the": "original"}}"""
        write(dir, real)
        config().loadFrom(dir)

        write(dir, """{"rules": {"written": "later"}}""")
        config().loadFrom(dir)

        val backup = dir.resolve("sample.json${JsonConfig.BROKEN_SUFFIX}")
        assertEquals(real, Files.readString(backup))
    }

    @Test
    fun `a missing file is not an error and leaves nothing behind`(@TempDir dir: Path) {
        val loaded = config().loadFrom(dir)

        assertTrue(loaded.rules.isEmpty())
        assertFalse(Files.exists(dir.resolve("sample.json${JsonConfig.BROKEN_SUFFIX}")))
    }
}
