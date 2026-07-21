package net.trilleo.config.cloth

import com.google.gson.JsonElement
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.trilleo.config.ConfigRegistry
import org.slf4j.LoggerFactory

/**
 * Makes the settings menu apply as you drag rather than only on Save.
 *
 * Cloth is transactional: it buffers edits and hands them over through save consumers when the user presses
 * Save. That is the right model for most settings, but not for the ones you are meant to *look* at while
 * adjusting — a hand-position slider is useless if the hand only moves after you close the menu.
 *
 * So while the menu is open, each tick pushes the widgets' current values straight into live state. To keep
 * Cancel honest, [arm] first snapshots every config as JSON, and closing the menu without saving restores
 * that snapshot. The snapshot is taken in memory rather than read back from disk precisely because previewing
 * marks configs dirty, so the file on disk may already have moved.
 */
object LivePreview {
    private val LOGGER = LoggerFactory.getLogger("hex/config")

    /** The menu being previewed, or null when none is open. */
    private var screen: Screen? = null

    /** Pushes one widget's current value into live state. */
    private var appliers: List<() -> Unit> = emptyList()

    /** Config values as they were before any preview, for restoring on Cancel. */
    private var snapshot: List<Pair<String, JsonElement>> = emptyList()

    /** Set by the save runnable; tells [tick] the edits were accepted rather than abandoned. */
    private var saved = false

    /** Starts previewing [screen], whose widgets are read by [appliers]. */
    fun arm(screen: Screen, appliers: List<() -> Unit>) {
        this.screen = screen
        this.appliers = appliers
        this.saved = false
        this.snapshot = ConfigRegistry.all().map { it.name to it.exportTree() }
    }

    /** Records that the open menu was saved, so [tick] keeps the edits instead of rolling them back. */
    fun markSaved() {
        saved = true
    }

    /**
     * Applies the open menu's widget values to live state, or tidies up once it closes.
     *
     * Called every client tick from [net.trilleo.feature.Features]; a no-op when no menu is open.
     */
    fun tick(client: Minecraft) {
        val active = screen ?: return

        if (client.screen === active) {
            // Previewing deliberately goes through the model's ordinary setters, so a value that is out of
            // range is clamped exactly as it would be coming from the config file.
            runCatching { appliers.forEach { it() } }
                .onFailure {
                    LOGGER.error("Live preview failed; disarming", it)
                    disarm()
                }
            return
        }

        // The menu is gone. Saved edits already went through Cloth's save consumers and were flushed, so
        // only the abandoned case needs undoing.
        if (!saved) restoreSnapshot()
        disarm()
    }

    private fun restoreSnapshot() {
        runCatching {
            snapshot.forEach { (name, tree) -> ConfigRegistry.byName(name)?.importTree(tree) }
            ConfigRegistry.flushAll()
        }.onFailure { LOGGER.error("Failed to roll back previewed settings", it) }
    }

    private fun disarm() {
        screen = null
        appliers = emptyList()
        snapshot = emptyList()
        saved = false
    }
}
