package net.trilleo.config

import java.nio.file.Path

/**
 * The set of user-facing config files, so they can be flushed, reloaded, snapshotted and exported as a
 * group without any caller knowing which features exist.
 *
 * A config joins by constructing its [ConfigHandle] through [register] — see
 * [net.trilleo.hand.HandConfig] for the usual shape. Registration is deliberately opt-in rather than
 * automatic: only *settings* belong here. Transient machine state that happens to use [JsonConfig], such as
 * [net.trilleo.update.UpdateStaging]'s record of a downloaded-but-not-yet-applied jar, must stay out — it is
 * not something a user would want captured in a profile or pasted to a friend.
 *
 * [tick] drives the debounced writes and is called once per client tick from
 * [net.trilleo.feature.Features], ahead of feature dispatch so it runs regardless of any feature's enabled
 * flag.
 */
object ConfigRegistry {
    private val handles = mutableListOf<ConfigHandle<*>>()

    /**
     * How many config writes have happened, bumped by [ConfigHandle.saveNow].
     *
     * [ProfileDirtyTracker] watches this so it only re-compares settings against the active profile when
     * something was actually written, rather than serializing every config on every tick.
     */
    @Volatile
    internal var flushCount: Int = 0
        private set

    internal fun noteFlush() {
        flushCount++
    }

    /** Registers and returns [handle], so a config can write `private val handle = ConfigRegistry.register(...)`. */
    fun <T : Any> register(handle: ConfigHandle<T>): ConfigHandle<T> {
        handles += handle
        return handle
    }

    /** Every registered config, in registration order. */
    fun all(): List<ConfigHandle<*>> = handles

    /** The config with this file base name, or null. Used to route an entry of an imported blob. */
    fun byName(name: String): ConfigHandle<*>? = handles.firstOrNull { it.name == name }

    /** Advances the debounce timers; any config whose changes have settled is written now. */
    fun tick() {
        handles.forEach { it.tickDown() }
        ProfileDirtyTracker.refreshIfChanged()
    }

    /** Writes every config with unsaved changes immediately. */
    fun flushAll() {
        handles.forEach { it.flush() }
    }

    /** Re-reads every config from disk, discarding unsaved changes. */
    fun reloadAll() {
        handles.forEach { it.reload() }
    }

    /**
     * Restores every config to its stock values.
     *
     * This touches live state only — no profile snapshot is rewritten, so the active profile simply reads as
     * modified afterwards and discarding brings the saved settings back. That recoverability is the point:
     * a mis-clicked "reset everything" must not be able to destroy a saved profile.
     */
    fun resetAll() {
        handles.forEach { it.resetToDefault() }
    }

    /** Copies every config's live value into [dir], capturing the current setup as a profile snapshot. */
    fun snapshotTo(dir: Path) {
        handles.forEach { it.snapshotTo(dir) }
    }

    /**
     * Adopts every config that [dir] has a file for, and returns how many were restored. Configs absent from
     * the snapshot keep their current value.
     */
    fun restoreFrom(dir: Path): Int = handles.count { it.restoreFrom(dir) }
}
