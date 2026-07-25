package net.trilleo.config

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi

/**
 * Puts a working settings button next to Hex's entry in Mod Menu's mod list.
 *
 * A **soft** integration, in both directions. Hex does not depend on Mod Menu: the API is a `compileOnly`
 * dependency, nothing here is referenced from mod code, and Fabric only loads an entrypoint class when
 * something asks for that entrypoint — so with Mod Menu absent this class is never loaded and the types it
 * names are never resolved. And Mod Menu does not own the menu: it gets the same screen as every other way in
 * ([HexConfigScreens]), so it is a shortcut rather than a second config backend. Hex's settings menu stays
 * in-house, which is the whole reason it is not built on a config library.
 *
 * Registered under the `modmenu` entrypoint in `fabric.mod.json`.
 */
object ModMenuIntegration : ModMenuApi {

    /**
     * Mod Menu hands over its own mod list as the parent, which is where closing the menu returns to — the
     * same contract as the Options screen shortcut and the `/hexa config` command.
     */
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> =
        ConfigScreenFactory { parent -> HexConfigScreens.create(parent) }
}
