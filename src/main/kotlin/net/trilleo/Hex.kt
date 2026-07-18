package net.trilleo

import net.fabricmc.api.ClientModInitializer
import net.minecraft.resources.Identifier
import net.trilleo.feature.Features
import net.trilleo.keybind.KeybindsFeature
import net.trilleo.update.UpdateFeature
import org.slf4j.LoggerFactory

/**
 * Client entrypoint. Registers every [net.trilleo.feature.Feature] and hands off to [Features], which
 * wires all Fabric events and commands. Adding a feature is a single [Features.register] line here.
 */
object Hex : ClientModInitializer {
	const val MOD_ID: String = "hex"

	private val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitializeClient() {
		Features.register(KeybindsFeature)
		Features.register(UpdateFeature)
		Features.bootstrap()

		LOGGER.info("Hex initialized")
	}

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)
}
