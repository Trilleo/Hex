package net.trilleo.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.trilleo.freecam.FreecamState;
import net.trilleo.sensitivity.SensitivityState;
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
 *
 * <p>Also lends the scroll wheel to the {@linkplain SensitivityState sensitivity hold} while its key is
 * down, cancelling vanilla's handling so the hotbar slot does not change underneath it.
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

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void hex$sensitivityScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        // The freecam already binds the wheel to its fly speed, and it is the more specific mode of the two,
        // so it keeps the wheel while flying. Checked here rather than left to injector order, so which one
        // wins is stated rather than inherited.
        if (FreecamState.INSTANCE.getActive()) {
            return;
        }
        SensitivityState state = SensitivityState.INSTANCE;
        if (!state.getActive()) {
            return;
        }
        state.adjust(yOffset != 0.0 ? yOffset : xOffset);
        ci.cancel();
    }
}
