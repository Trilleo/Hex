package net.trilleo

import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier
import net.trilleo.attack.AttackModeFeature
import net.trilleo.chat.ChatHighlightFeature
import net.trilleo.feature.Features
import net.trilleo.freecam.FreecamFeature
import net.trilleo.hand.HandFeature
import net.trilleo.highlight.HighlightFeature
import net.trilleo.itemcustom.ItemCustomizeFeature
import net.trilleo.keybind.KeybindsFeature
import net.trilleo.notebook.NotebookFeature
import net.trilleo.region.RegionFeature
import net.trilleo.reminder.ReminderFeature
import net.trilleo.sensitivity.SensitivityFeature
import net.trilleo.sound.SoundFeature
import net.trilleo.suggest.SuggestFeature
import net.trilleo.title.TitleFeature
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
        // Beside the freecam: the two share the scroll wheel, and the mouse mixin gives it to whichever of
        // them is engaged.
        Features.register(SensitivityFeature)
        Features.register(HandFeature)
        Features.register(AttackModeFeature)
        Features.register(ItemCustomizeFeature)
        // Ahead of everything that makes a noise, which by now is most of the mod: a feedback slot read
        // before sounds.json has loaded would fall back to stock rather than to what the player chose.
        Features.register(SoundFeature)
        // Ahead of every feature that pops a title — regions, entity highlights, reminders and chat highlights
        // are all below. Each of them normalizes its own config during onInit, and that is where a title
        // action is migrated and seeded from the shared settings, so `titles.json` has to be loaded first.
        Features.register(TitleFeature)
        // Ahead of reminders: a region crossing detected on this tick is drained by ReminderTriggers during
        // the same tick's reminder dispatch, so a region-armed reminder starts counting without a tick's lag.
        Features.register(RegionFeature)
        Features.register(HighlightFeature)
        Features.register(ReminderFeature)
        Features.register(NotebookFeature)
        Features.register(SuggestFeature)
        // Last of the chat readers, and that is the whole reason it is here rather than beside HighlightFeature.
        // A chat highlight can hide a line, and Features fans chat out with `all { … }`, which stops at the first
        // feature that refuses one — so registering it any earlier would mean a hidden line never reaching
        // reminders or command suggestions. Hiding a message from the chat window should not hide it from the
        // rest of the mod.
        Features.register(ChatHighlightFeature)
        Features.register(UpdateFeature)
        Features.bootstrap()

        LOGGER.info("Hex initialized")
    }

    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
