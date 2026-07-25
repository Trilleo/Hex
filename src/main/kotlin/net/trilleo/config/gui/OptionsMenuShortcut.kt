package net.trilleo.config.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.layouts.LayoutSettings
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.config.HexConfigScreens

/**
 * The small square button Hex adds beside **Done** on the vanilla Options screen.
 *
 * Until now the settings menu could only be reached with `/hexa config` (in a world, through the chat box) or
 * with a keybind that ships unbound — so a fresh install had no discoverable way in. Options is where a
 * player already goes looking for settings, and it is reachable from the title screen too, which the other
 * two routes are not.
 *
 * Deliberately an icon rather than a labelled button: vanilla's footer is one centred 200-wide Done, and a
 * second full-width button there would read as a vanilla action. The tooltip carries the name, and vanilla
 * narrates a widget's tooltip alongside its message, so the bare `□` is not all a screen reader gets.
 *
 * [net.trilleo.mixin.OptionsScreenMixin] is what puts it on the screen; all the geometry lives here.
 */
object OptionsMenuShortcut {

    /** Square, at vanilla's standard button height. */
    const val SIZE = 20

    /** Vanilla's Done button, which this one sits beside. */
    private const val DONE_WIDTH = 200

    /** Space between Done's right edge and this button. */
    private const val GAP = 4

    /**
     * Left padding that lands the button just past Done.
     *
     * The footer is a [net.minecraft.client.gui.layouts.FrameLayout] stretched to the screen width that
     * centres each child — a child ends up at `lerp(0.5, paddingLeft, frameWidth - childWidth)` — so padding
     * on one side shifts the child by *half* of it. Done is centred, putting its right edge at
     * `width / 2 + DONE_WIDTH / 2`; padding of `DONE_WIDTH + 2 * GAP + SIZE` moves this button's left edge
     * exactly [GAP] past that. Expressing it as padding rather than an absolute x is what makes it follow
     * Done when the window is resized: vanilla's `resize` only re-arranges the layout, it does not re-run
     * `init`.
     */
    private const val FOOTER_PADDING_LEFT = DONE_WIDTH + GAP * 2 + SIZE

    /**
     * Not language, so not translated — see the translation rules. `□` is in the default font's unifont
     * provider, so it renders without Hex shipping a glyph for it.
     */
    private val LABEL: Component = Component.literal("□")

    private val TOOLTIP: Tooltip = Tooltip.create(Component.translatable("hex.config.options_shortcut.tooltip"))

    /** Builds the button, returning to [parent] — the Options screen it was pressed on — when the menu closes. */
    fun create(parent: Screen): Button =
        Button.builder(LABEL) { Minecraft.getInstance().setScreen(HexConfigScreens.create(parent)) }
            .size(SIZE, SIZE)
            .tooltip(TOOLTIP)
            .build()

    /** Positions the button within the footer frame. Handed to `addToFooter` as the child's layout settings. */
    fun place(settings: LayoutSettings) {
        settings.paddingLeft(FOOTER_PADDING_LEFT)
    }
}
