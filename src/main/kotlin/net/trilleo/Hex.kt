package net.trilleo

import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier
import net.trilleo.attack.AttackModeFeature
import net.trilleo.feature.Features
import net.trilleo.freecam.FreecamFeature
import net.trilleo.hand.HandFeature
import net.trilleo.keybind.KeybindsFeature
import net.trilleo.region.RegionFeature
import net.trilleo.reminder.ReminderFeature
import net.trilleo.suggest.SuggestFeature
import net.trilleo.update.UpdateFeature
import org.slf4j.LoggerFactory

/**
 * Client entrypoint. Registers every [net.trilleo.feature.Feature] and hands off to [Features], which
 * wires all Fabric events and commands. Adding a feature is a single [Features.register] line here.
 */
object Hex : ClientModInitializer {
    const val MOD_ID: String = "hex"

    private val LOGGER = LoggerFactory.getLogger(MOD_ID)

    /**
     * The dedicated "Hex" keybind category shown under Options → Controls. Registered once here (the
     * factory throws on a duplicate id) and shared by every feature that owns a [KeyMapping], so all Hex
     * binds group together instead of scattering into Misc.
     */
    val KEY_CATEGORY: KeyMapping.Category = KeyMapping.Category.register(id("hex"))

    override fun onInitializeClient() {
        Features.register(KeybindsFeature)
        Features.register(FreecamFeature)
        Features.register(HandFeature)
        Features.register(AttackModeFeature)
        // Ahead of reminders: a region crossing detected on this tick is drained by ReminderTriggers during
        // the same tick's reminder dispatch, so a region-armed reminder starts counting without a tick's lag.
        Features.register(RegionFeature)
        Features.register(ReminderFeature)
        Features.register(SuggestFeature)
        Features.register(UpdateFeature)
        Features.bootstrap()

        LOGGER.info("Hex initialized")
    }

    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
