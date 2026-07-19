package net.trilleo.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import net.trilleo.freecam.FreecamState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppresses the player's walk movement while the freecam is active by forcing the move vector to zero.
 * {@code getMoveVector} is declared on {@code ClientInput} (and not overridden by {@code KeyboardInput}), so
 * injecting here covers the local player too; {@code LocalPlayer.applyInput} reads it for WASD movement.
 * Jump/sneak/sprint and the outgoing input packet are handled in {@link KeyboardInputMixin}.
 */
@Mixin(ClientInput.class)
public abstract class ClientInputMixin {
    @Inject(method = "getMoveVector", at = @At("RETURN"), cancellable = true)
    private void hex$freezeMoveWhileFreecam(CallbackInfoReturnable<Vec2> cir) {
        if (FreecamState.INSTANCE.getActive()) {
            cir.setReturnValue(Vec2.ZERO);
        }
    }
}
