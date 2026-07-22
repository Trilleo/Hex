package net.trilleo.config

import com.google.gson.JsonElement
import net.trilleo.config.ConfigHandle.Companion.CLEAN
import java.nio.file.Files
import java.nio.file.Path

/**
 * Wraps a [JsonConfig] with the live value it persists, so [ConfigRegistry] can flush, reload, snapshot and
 * export every config generically without knowing any feature's types.
 *
 * The live value is reached through [adopt] / [current] rather than stored here, because the two config
 * shapes in this mod differ: most features swap in a whole new settings object, while
 * [net.trilleo.keybind.KeybindConfig] hands out a mutable list that the GUI and the manager hold by
 * reference — that one must be refilled in place or those references go stale. Expressing both as a pair of
 * lambdas keeps the registry uniform and leaves each config in charge of its own identity rules.
 *
 * Saving is debounced. Callers signal a change with [markDirty] and the write lands [DEBOUNCE_TICKS] ticks
 * later, so dragging a slider — which fires its setter every frame — costs one file write once the drag
 * settles instead of one per frame. Anything that must not be lost ([flush], screen close, profile switch,
 * shutdown) writes immediately.
 *
 * @param json the underlying file.
 * @param adopt installs a freshly read value into live state.
 * @param current reads the live value to be written.
 * @param afterReload side effects needed when the live value is replaced wholesale rather than edited —
 *   e.g. leaving freecam if a profile switch turned it off mid-flight. Not called on the initial load.
 */
class ConfigHandle<T : Any>(
    val json: JsonConfig<T>,
    private val adopt: (T) -> Unit,
    private val current: () -> T,
    private val afterReload: () -> Unit = {},
) {
    /** Ticks remaining before an automatic flush, or [CLEAN] when there is nothing to write. */
    @Volatile
    private var ticksUntilFlush: Int = CLEAN

    /** This config's stable key — the file base name, e.g. `"hand"`. */
    val name: String get() = json.name

    /** Reads the file into live state. Call once at feature init. */
    fun loadInitial() {
        adopt(json.load())
    }

    /** Note that live state changed; the write lands once [DEBOUNCE_TICKS] ticks pass without another change. */
    fun markDirty() {
        ticksUntilFlush = DEBOUNCE_TICKS
    }

    /** Writes immediately if there are unsaved changes, otherwise does nothing. */
    fun flush() {
        if (ticksUntilFlush == CLEAN) return
        saveNow()
    }

    /**
     * Writes immediately whether or not anything was marked dirty. This is what a config's `save()` maps to,
     * keeping the pre-debounce contract for callers that mutate live state and then persist in one breath.
     */
    fun saveNow() {
        ticksUntilFlush = CLEAN
        json.save(current())
        ConfigRegistry.noteFlush()
    }

    /** Re-reads the file into live state, discarding unsaved changes, and fires [afterReload]. */
    fun reload() {
        ticksUntilFlush = CLEAN
        adopt(json.load())
        afterReload()
    }

    /**
     * Installs [value] as the live value — normalizing it first, since a value that did not come through
     * [JsonConfig.loadFrom] (a clipboard import, say) has not been repaired yet — then marks it for saving.
     */
    fun replace(value: T) {
        json.normalize(value)
        adopt(value)
        markDirty()
        afterReload()
    }

    /**
     * Restores this config to its stock values, exactly as if the file had never existed.
     *
     * Deliberately routed through [replace] rather than deleting the file: that normalizes, adopts and fires
     * [afterReload], so a reset behaves like any other wholesale swap. The file itself is rewritten with the
     * defaults on the next flush rather than removed, so nothing else has to cope with a missing file.
     */
    fun resetToDefault() {
        replace(json.defaultValue())
    }

    /** Writes the live value into [dir] instead of the live config directory. Used to capture a profile. */
    fun snapshotTo(dir: Path) {
        json.saveTo(dir, current())
    }

    /**
     * Adopts this config's file from [dir] if it exists, and reports whether it did. A profile that has no
     * file for this config leaves the live value alone, so a partial profile inherits rather than resetting.
     */
    fun restoreFrom(dir: Path): Boolean {
        if (!Files.exists(json.fileIn(dir))) return false
        ticksUntilFlush = CLEAN
        adopt(json.loadFrom(dir))
        afterReload()
        return true
    }

    /** The live value as a JSON tree, for the combined clipboard blob. */
    fun exportTree(): JsonElement = JsonConfig.GSON.toJsonTree(current(), json.typeToken())

    /**
     * Adopts a value parsed out of a clipboard blob. Returns false if the element does not fit this config's
     * shape, leaving live state untouched — a hand-edited blob must not be able to throw out of a GUI click.
     */
    fun importTree(element: JsonElement): Boolean = runCatching {
        val value: T? = JsonConfig.GSON.fromJson(element, json.typeToken())
        if (value == null) false else {
            replace(value); true
        }
    }.getOrDefault(false)

    /** Called once per client tick by [ConfigRegistry]; performs the debounced write when it comes due. */
    internal fun tickDown() {
        if (ticksUntilFlush > 0 && --ticksUntilFlush == 0) flush()
    }

    companion object {
        private const val CLEAN = -1

        /** One second at 20 tps — long enough to swallow a slider drag, short enough to survive a crash. */
        const val DEBOUNCE_TICKS: Int = 20
    }
}
