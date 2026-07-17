package net.trilleo

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier
import net.trilleo.keybind.KeybindConfig
import net.trilleo.keybind.KeybindManager
import net.trilleo.keybind.gui.KeybindScreen
import org.slf4j.LoggerFactory

object Hex : ClientModInitializer {
	const val MOD_ID: String = "hex"

	private val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitializeClient() {
		KeybindConfig.load()

		val openMenuKey = KeyMapping(
			"key.hex.open_menu",
			InputConstants.UNKNOWN.value,
			KeyMapping.Category.MISC,
		)
		KeyMappingHelper.registerKeyMapping(openMenuKey)

		ClientTickEvents.END_CLIENT_TICK.register { client ->
			while (openMenuKey.consumeClick()) {
				client.setScreen(KeybindScreen(client.screen))
			}
			KeybindManager.onEndTick(client)
		}

		LOGGER.info("Hex initialized")
	}

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)
}
