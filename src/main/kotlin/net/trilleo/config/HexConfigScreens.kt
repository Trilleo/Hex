package net.trilleo.config

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.trilleo.config.cloth.ClothConfigFactory

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
        return ClothConfigFactory.create(parent)
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
     * Replaces the open menu with a freshly built one.
     *
     * Required after anything that swaps config values underneath the GUI — switching profile, importing
     * from the clipboard. Cloth reads each row's value when the row is constructed and writes it back on
     * Save, so continuing to use the old screen would push the pre-switch values straight back over the
     * ones just loaded.
     */
    fun rebuild() {
        val client = Minecraft.getInstance()
        client.execute { client.setScreen(create(lastParent)) }
    }
}
