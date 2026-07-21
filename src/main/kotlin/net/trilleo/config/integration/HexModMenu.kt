package net.trilleo.config.integration

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import net.trilleo.config.HexConfigScreens

/**
 * Adds the settings button next to Hex in Mod Menu's mod list, opening the same menu as `/hexa config`.
 *
 * Mod Menu is an optional dependency, and this class is what makes that work: Fabric Loader only instantiates
 * an entrypoint when some mod asks for its key, and Mod Menu itself is the only reader of the `"modmenu"` key.
 * With Mod Menu absent nothing ever reads it, so this class is never loaded and its [ModMenuApi] supertype is
 * never resolved — no `NoClassDefFoundError`.
 *
 * That only holds while this file stays a leaf: **nothing else in the mod may reference [HexModMenu] or any
 * `com.terraformersmc` type**, or the missing class gets pulled in through the back door on a normal launch.
 */
object HexModMenu : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> =
        ConfigScreenFactory { parent -> HexConfigScreens.create(parent) }
}
