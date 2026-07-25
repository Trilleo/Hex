package net.trilleo.itemcustom

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.trilleo.Hex
import net.trilleo.command.Commands
import net.trilleo.config.ConfigCategory
import net.trilleo.feature.Feature
import net.trilleo.itemcustom.gui.ItemCustomizeListScreen
import net.trilleo.mixin.AbstractContainerScreenAccessor
import net.trilleo.util.Chroma
import java.util.*

/**
 * Lets a Skyblock item be renamed, recoloured, reskinned and de-glinted on this client alone.
 *
 * The feature itself is only the doors in — the settings tab, the command, the keybind that fires over a slot,
 * and the marker drawn on a customized one. The rendering happens in [net.trilleo.mixin.ItemStackMixin] and
 * [net.trilleo.mixin.ItemModelResolverMixin], driven by [ItemCustomLookup].
 *
 * Note this feature deliberately leaves [enabled] at its default `true` and gates behaviour on
 * [ItemCustomizeConfig.active] instead. [net.trilleo.feature.Features.categories] hides the tab of a disabled
 * feature, so wiring the master switch to [enabled] would make it impossible to switch back on from the menu —
 * the same reasoning as [net.trilleo.hand.HandFeature].
 */
object ItemCustomizeFeature : Feature {
    override val id: String = "item_custom"

    /** Opens the editor for the item under the cursor in a container GUI. Unbound by default. */
    private lateinit var customizeKey: KeyMapping

    override fun onInit() {
        ItemCustomizeConfig.load()

        // Unbound by default on purpose: this key fires inside Hypixel's own menus, where every unclaimed
        // letter already means something to somebody. Picking one for the player would be picking a fight.
        customizeKey = KeyMapping(
            "key.hex.item_custom.customize",
            InputConstants.UNKNOWN.value,
            Hex.KEY_CATEGORY,
        )
        KeyMappingHelper.registerKeyMapping(customizeKey)

        // Fabric's screen API rather than a mixin on AbstractContainerScreen: it gives both the key hook and
        // the draw hook for any screen, and leaves vanilla's own input handling alone. The accessor mixin is
        // still needed for the hovered slot itself, which is a protected field.
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*>) return@register
            // Returning false swallows the key, so vanilla never sees the press that opened the editor.
            ScreenKeyboardEvents.allowKeyPress(screen).register { target, event -> !handleKey(target, event) }
            ScreenEvents.afterExtract(screen).register { target, extractor, _, _, _ -> markSlots(target, extractor) }
        }
    }

    override fun registerCommands(hex: LiteralArgumentBuilder<FabricClientCommandSource>) {
        hex.then(
            Commands.literal("item")
                // A bare `/hexa item` states its subcommands rather than guessing at one of them.
                .executes { ctx ->
                    Commands.feedback(ctx.source, Component.translatable("hex.item_custom.command.list"))
                    1
                }
                .then(
                    Commands.literal("list").executes { ctx ->
                        // Deferred: opening a screen mid-command is undone when the chat screen that ran the
                        // command closes.
                        val client = ctx.source.client
                        client.execute { client.setScreen(ItemCustomizeListScreen(null)) }
                        1
                    },
                ),
        )
    }

    override fun settingsCategory(): ConfigCategory = ConfigCategory.build("item_custom") {
        toggle(
            "enabled",
            default = true,
            get = { ItemCustomizeConfig.active },
            set = { ItemCustomizeConfig.settings.enabled = it; ItemCustomizeConfig.save() },
        )
        action("manage") { screen -> Minecraft.getInstance().setScreen(ItemCustomizeListScreen(screen)) }
        toggle(
            "mark_slots",
            default = true,
            get = { ItemCustomizeConfig.markSlots },
            set = { ItemCustomizeConfig.settings.markSlots = it; ItemCustomizeConfig.save() },
        )

        // Shared by every chroma name rather than set per item — see ItemCustomizeSettings. markDirty, not
        // save: a slider setter fires every frame of a drag, and re-indexing there is what makes a name
        // already on screen change speed as the handle moves.
        slider(
            "chroma_speed",
            min = Chroma.SECONDS_MIN,
            max = Chroma.SECONDS_MAX,
            step = Chroma.SECONDS_STEP,
            default = Chroma.SECONDS_DEFAULT,
            get = { ItemCustomizeConfig.chromaSeconds },
            set = { ItemCustomizeConfig.settings.chromaSeconds = it; ItemCustomizeConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.1fs", it) },
        )
        slider(
            "chroma_width",
            min = Chroma.WIDTH_MIN,
            max = Chroma.WIDTH_MAX,
            step = Chroma.WIDTH_STEP,
            default = Chroma.WIDTH_DEFAULT,
            get = { ItemCustomizeConfig.chromaWidth },
            set = { ItemCustomizeConfig.settings.chromaWidth = it; ItemCustomizeConfig.markDirty() },
            format = { "${it.toInt()}" },
        )

        resetsTo(ItemCustomizeConfig.handle)
    }

    /**
     * Handles the customize key inside a container screen, and reports whether it was this feature's press.
     *
     * Gated on the key being bound at all: an unbound [KeyMapping] holds `InputConstants.UNKNOWN`, and
     * `matches` on an unknown key would answer true for every key the player pressed.
     *
     * **Opening the editor closes the container**, because vanilla's `AbstractContainerScreen.removed` tells
     * the server so. That is unavoidable without drawing the editor inside the GUI, and it costs nothing but
     * the player's place in a chest page: the customization is keyed on the item's uuid, and the editor keeps
     * its own copy of the stack for the preview.
     *
     * The editor is therefore opened with no parent. Handing it the container screen would send the player
     * back to a menu the server has already forgotten — one that draws but no longer does anything.
     */
    private fun handleKey(screen: Screen, event: KeyEvent): Boolean {
        if (!ItemCustomizeConfig.active || customizeKey.isUnbound || !customizeKey.matches(event)) return false
        if (screen !is AbstractContainerScreen<*>) return false

        val slot = (screen as AbstractContainerScreenAccessor).hexHoveredSlot()
        // Report the press over an empty slot rather than ignoring it: a key that does nothing is
        // indistinguishable from one that is bound wrongly.
        ItemCapture.open(Minecraft.getInstance(), null, slot?.item ?: ItemStack.EMPTY)
        return true
    }

    /**
     * Draws a `✎` over every slot holding a customized item.
     *
     * Without it there is no way to tell a customized item from one that simply looks like that — which
     * matters most when a customization is doing something subtle, or when you are hunting for the item behind
     * an entry in the list screen.
     */
    private fun markSlots(screen: Screen, extractor: GuiGraphicsExtractor) {
        if (!ItemCustomizeConfig.active || !ItemCustomizeConfig.markSlots) return
        if (screen !is AbstractContainerScreen<*>) return

        val accessor = screen as AbstractContainerScreenAccessor
        val left = accessor.hexLeftPos()
        val top = accessor.hexTopPos()
        val font = Minecraft.getInstance().font
        val marker = Component.literal("✎")

        screen.menu.slots.forEach { slot ->
            val stack = slot.item
            if (stack.isEmpty) return@forEach
            val customization = ItemCustomLookup.customizationFor(stack) ?: return@forEach
            if (!customization.hasEffect()) return@forEach
            extractor.text(font, marker, left + slot.x + MARKER_X, top + slot.y + MARKER_Y, MARKER_COLOR)
        }
    }

    /** Top-left of the slot, where nothing else is drawn — the stack count and durability bar sit low right. */
    private const val MARKER_X = 0
    private const val MARKER_Y = -1

    private const val MARKER_COLOR = 0xFF55FFFF.toInt()
}
