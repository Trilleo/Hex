package net.trilleo.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.trilleo.hand.HandState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rescales how long the local player's swing animation lasts.
 *
 * <p>{@code getCurrentSwingDuration} is the tick budget {@code updateSwingTime} counts {@code swingTime}
 * against, and it already has Haste / Mining Fatigue folded in — so scaling its result composes with those
 * rather than overriding them.
 *
 * <p>This is purely a local animation timer. {@code LivingEntity.swing} sends its
 * {@code ServerboundSwingPacket} the moment the swing starts regardless of duration, attack cooldown lives
 * on {@code Player.getCurrentItemAttackStrengthDelay}, and other players' clients time your animation
 * themselves — so nothing here is visible to the server or to anyone else.
 *
 * <p>Guarded to the local player: the mixin applies to every {@link LivingEntity}, and mobs must keep
 * vanilla timing.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true)
    private void hex$scaleSwingDuration(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        cir.setReturnValue(HandState.INSTANCE.swingDuration(cir.getReturnValueI()));
    }
}
