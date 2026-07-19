package net.trilleo.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import net.trilleo.freecam.FreecamState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Redirects the camera to the freecam position/rotation while it is active.
 *
 * <p>{@code Camera.update} first calls {@code alignWithEntity} (which sets {@code position}/rotation from
 * the player), then builds the cull frustum from {@code this.position}. Injecting right <em>after</em>
 * {@code alignWithEntity} — before the frustum is prepared — means the frustum is built at the freecam
 * location too, so chunk culling stays correct wherever the camera flies. Writing the position directly
 * gives noclip for free (no block collision is ever run against it).
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V",
                    shift = At.Shift.AFTER
            )
    )
    private void hex$applyFreecam(DeltaTracker deltaTracker, CallbackInfo ci) {
        FreecamState state = FreecamState.INSTANCE;
        if (!state.getActive()) {
            return;
        }
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        setPosition(state.interpolatedPos(partialTick));
        setRotation(state.getYaw(), state.getPitch());
    }
}
