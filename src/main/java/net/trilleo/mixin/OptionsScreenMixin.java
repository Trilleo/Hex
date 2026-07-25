package net.trilleo.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.trilleo.config.gui.OptionsMenuShortcut;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts Hex's shortcut button in the footer of the vanilla Options screen.
 *
 * <p>The geometry and the button itself belong to {@link OptionsMenuShortcut}; this only performs the three
 * vanilla calls that cannot be made from outside a {@code Screen}.
 *
 * <p>Injecting at {@code TAIL} means vanilla has already handed its own widgets to {@code addRenderableWidget}
 * and arranged the layout, so the new child has to be registered and the layout re-arranged by hand — hence
 * the trailing {@code repositionElements}, which on this screen is just another {@code arrangeElements} pass.
 * Adding to the footer rather than placing the button at fixed coordinates is what keeps it beside Done when
 * the window is resized: {@code Screen.resize} re-arranges the layout without re-running {@code init}.
 */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {
    @Shadow
    @Final
    private HeaderAndFooterLayout layout;

    private OptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void hex$addConfigShortcut(CallbackInfo ci) {
        Button button = OptionsMenuShortcut.INSTANCE.create(this);
        this.layout.addToFooter(button, OptionsMenuShortcut.INSTANCE::place);
        this.addRenderableWidget(button);
        this.repositionElements();
    }
}
