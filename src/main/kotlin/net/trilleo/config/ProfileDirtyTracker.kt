package net.trilleo.config

import com.google.gson.JsonParser
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Whether the live settings differ from the active profile's saved snapshot — the `*` the manager screen
 * shows, and the guard that stops an automatic switch from throwing away unsaved work.
 *
 * This compares *content* rather than tracking a "something was edited" flag, and that distinction is the
 * whole design. A flag would be wrong here three separate ways:
 *
 *  - [ConfigProfiles.switchTo], [ConfigProfiles.delete] and [ConfigProfiles.importFromString] all call
 *    `markDirty` on every config as part of *restoring* one, so a freshly switched profile would immediately
 *    read as modified.
 *  - Features write outside the settings menu (a keybind firing a control switch, freecam toggling), and a
 *    flag cannot tell "changed" from "changed and then changed back".
 *  - At startup the live files may already differ from the snapshot — hand-edited, or a crash between an edit
 *    and the snapshot — where a flag starts clean and simply lies.
 *
 * Comparing parsed trees rather than raw file text matters: [JsonConfig] writes pretty-printed JSON, and GSON
 * gives no ordering guarantee across versions, so a textual comparison would report differences that are not
 * differences. [com.google.gson.JsonElement] compares by value.
 */
object ProfileDirtyTracker {
    private val LOGGER = LoggerFactory.getLogger("hex/config")

    /** Result of the last [refresh]; what the manager screen reads while drawing. */
    @Volatile
    var isDirty: Boolean = false
        private set

    /** The [ConfigRegistry.flushCount] the last check was made against, so a quiet tick costs nothing. */
    private var checkedAtFlush: Int = -1

    /**
     * Recomputes [isDirty] against the active profile's snapshot.
     *
     * Serializing every config is cheap but not free, so callers that run per tick should go through
     * [refreshIfChanged] instead.
     */
    fun refresh() {
        isDirty = runCatching { differs(ConfigProfiles.profileDir(ConfigProfiles.settings.active)) }
            .onFailure { LOGGER.error("Failed to compare settings against the active profile", it) }
            // A comparison that could not be made must not report unsaved changes, or a broken snapshot
            // directory would permanently block auto-switching with no way for the user to clear it.
            .getOrDefault(false)
        checkedAtFlush = ConfigRegistry.flushCount
    }

    /**
     * Recomputes only when a config has actually been written since the last check.
     *
     * This is a flag used the way a flag works well: as a cheap "something *might* have changed" gate in
     * front of the authoritative content comparison, never as the answer itself.
     */
    fun refreshIfChanged() {
        if (ConfigRegistry.flushCount != checkedAtFlush) refresh()
    }

    /** Declares the live settings to be the saved state, after a save, switch or discard. */
    fun markClean() {
        isDirty = false
        checkedAtFlush = ConfigRegistry.flushCount
    }

    /**
     * Whether any config in [dir]'s snapshot holds something other than the live value.
     *
     * Only configs the snapshot actually has a file for are compared. A snapshot written before some config
     * existed would otherwise read as permanently modified, and it also mirrors what
     * [ConfigHandle.restoreFrom] does on the way back in — a partial profile inherits the rest rather than
     * conflicting with it.
     */
    private fun differs(dir: Path): Boolean {
        if (!Files.isDirectory(dir)) return false
        // Profiled only: a global config is never captured, so comparing it could only ever report a
        // difference the user has no way to resolve.
        return ConfigRegistry.profiled().any { handle ->
            val file = handle.json.fileIn(dir)
            if (!Files.exists(file)) return@any false
            val saved = runCatching { JsonParser.parseString(Files.readString(file)) }.getOrNull()
            // An unparseable snapshot is not evidence of an edit; restoring it would fall back to
            // defaults anyway, so treat it as nothing to compare against.
                ?: return@any false
            saved != handle.exportTree()
        }
    }
}
