package net.trilleo.config

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.trilleo.config.gui.HexConfigScreen

/**
 * The single way to open Hex's settings menu.
 *
 * Everything that opens the menu goes through here — the `/hexa config` command, the config keybind, Mod
 * Menu's settings button, and [rebuild] after config values are replaced wholesale. Keeping one entry point
 * means the choice of GUI backend lives in exactly one place.
 */
object HexConfigScreens {

    /**
     * Where the currently open menu should return to when it closes.
     *
     * Remembered so [rebuild] can put the user back on an equivalent screen without stranding them: a screen
     * has no way to hand back its own parent, so without this a rebuild would silently drop the caller's
     * place in the navigation stack.
     */
    private var lastParent: Screen? = null

    /** Builds the settings screen, returning to [parent] when it closes. */
    fun create(parent: Screen?): Screen {
        lastParent = parent
        return HexConfigScreen(parent)
    }

    /**
     * Opens the settings screen on the next tick.
     *
     * The deferral matters when opening from a command: the chat screen closes after the command runs and
     * would otherwise overwrite whatever was set here.
     */
    fun open(client: Minecraft, parent: Screen?) {
        client.execute { client.setScreen(create(parent)) }
    }

    /**
     * Rebuilds the open menu's widgets from the current config values.
     *
     * Required after anything that swaps config values underneath the GUI — switching profile, importing
     * from the clipboard. Rows capture their widget's value when they are built, so a stale screen would go
     * on showing (and writing back) the settings from before the swap.
     *
     * The open screen is *reused* rather than replaced when there is one. `rebuildWidgets` re-runs `init`,
     * which discards every row and builds fresh ones — all this needs — while the screen's own fields
     * survive, so the user keeps their selected tab and search text. Constructing a new screen would reset
     * both and dump them back on the first tab after every profile switch.
     */
    fun rebuild() {
        val client = Minecraft.getInstance()
        client.execute {
            val open = client.screen
            if (open is HexConfigScreen) open.refresh() else client.setScreen(create(lastParent))
        }
    }
}
