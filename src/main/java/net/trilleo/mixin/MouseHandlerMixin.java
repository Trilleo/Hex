package net.trilleo.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.trilleo.freecam.FreecamState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Redirects mouse look and the scroll wheel to the freecam while it is active, and cancels the vanilla
 * handling so the real player never rotates (no rotation packets) and the hotbar slot never changes.
 *
 * <p>The look delta replicates vanilla's normal (non-smoothed, non-scoped) sensitivity
 * ({@code (sens * 0.6 + 0.2)^3 * 8}); the {@code * 0.15} turn factor is applied inside
 * {@link FreecamState#applyLook}, so the feel matches the vanilla camera exactly.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void hex$freecamTurn(double d, CallbackInfo ci) {
        FreecamState state = FreecamState.INSTANCE;
        if (!state.getActive()) {
            return;
        }
        double sensitivity = Minecraft.getInstance().options.sensitivity().get() * 0.6 + 0.2;
        // sens^3 * 8 is vanilla's normal-look multiplier (the sens^3-only path is the scoped/zoom branch).
        double factor = sensitivity * sensitivity * sensitivity * 8.0;
        state.applyLook(this.accumulatedDX * factor, this.accumulatedDY * factor);
        this.accumulatedDX = 0.0;
        this.accumulatedDY = 0.0;
        ci.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void hex$freecamScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        FreecamState state = FreecamState.INSTANCE;
        if (!state.getActive()) {
            return;
        }
        state.adjustSpeed(yOffset != 0.0 ? yOffset : xOffset);
        ci.cancel();
    }
}
