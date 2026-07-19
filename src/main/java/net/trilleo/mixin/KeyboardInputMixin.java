package net.trilleo.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.trilleo.freecam.FreecamState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freezes the player's key-press state while the freecam is active. {@code KeyboardInput.tick} (which
 * overrides {@code ClientInput.tick} without calling super) rebuilds {@code keyPresses} from the keyboard
 * each tick; this blanks it right after, so the real player never jumps, sneaks or sprints, and — because
 * {@code keyPresses} is what {@code LocalPlayer} sends in {@code ServerboundPlayerInputPacket} — no input
 * reaches the server. WASD movement is handled in {@link ClientInputMixin}; mouse look in
 * {@link MouseHandlerMixin}.
 *
 * <p>{@code keyPresses} is a <em>public</em> field declared on {@code ClientInput}, so it is written through
 * a cast rather than an {@code @Shadow} — Mixin resolves shadowed fields only in the direct target class,
 * and this mixin targets the {@code KeyboardInput} subclass (the concrete type the local player uses).
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void hex$freezeKeysWhileFreecam(CallbackInfo ci) {
        if (!FreecamState.INSTANCE.getActive()) {
            return;
        }
        ((ClientInput) (Object) this).keyPresses = Input.EMPTY;
    }
}
