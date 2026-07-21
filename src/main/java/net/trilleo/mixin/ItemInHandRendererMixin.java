package net.trilleo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import com.mojang.math.Axis;
import net.trilleo.hand.HandState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the user's first-person hand settings.
 *
 * <p>{@code renderHandsWithItems} calls {@code renderArmWithItem} once per hand, so hooking the latter is
 * the single point that covers both. The pose is pushed at {@code HEAD} and popped at {@code RETURN};
 * {@code RETURN} injects at every return site, so the stack stays balanced on the method's early-outs.
 *
 * <p>Transforming at {@code HEAD} means the offsets compose <em>before</em> vanilla's own positioning, so
 * the rotation sliders orbit the hand around hand-space origin rather than spinning the item in place —
 * the usual behaviour for this class of tweak, and what the slider ranges are tuned around.
 *
 * <p>Both hooks are first-person only. Nothing here touches the swing packet or the player's own model.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    /**
     * Whether {@link #hex$popHandTransform} must pop. Recomputed per call rather than re-reading the
     * config at {@code RETURN}, so a settings change landing mid-frame can never unbalance the stack.
     */
    private boolean hex$transformPushed;

    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void hex$pushHandTransform(
            AbstractClientPlayer player,
            float partialTick,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack stack,
            float equippedProgress,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int light,
            CallbackInfo ci
    ) {
        // Offsets are main-hand only; the off hand keeps its vanilla position.
        hex$transformPushed = hand == InteractionHand.MAIN_HAND && HandState.INSTANCE.shouldTransform();
        if (!hex$transformPushed) {
            return;
        }

        HandState state = HandState.INSTANCE;
        poseStack.pushPose();
        poseStack.translate(state.getOffsetX(), state.getOffsetY(), state.getOffsetZ());
        poseStack.mulPose(Axis.XP.rotationDegrees(state.getRotationX()));
        poseStack.mulPose(Axis.YP.rotationDegrees(state.getRotationY()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.getRotationZ()));
        poseStack.scale(state.getScale(), state.getScale(), state.getScale());
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void hex$popHandTransform(
            AbstractClientPlayer player,
            float partialTick,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack stack,
            float equippedProgress,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int light,
            CallbackInfo ci
    ) {
        if (hex$transformPushed) {
            hex$transformPushed = false;
            poseStack.popPose();
        }
    }

    /**
     * Holds the swing animation at rest. Index 5 is the {@code swingProgress} parameter's LVT slot — slot
     * 0 is {@code this}, so the fifth argument lands at 5.
     */
    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, index = 5)
    private float hex$suppressSwing(float swingProgress) {
        return HandState.INSTANCE.shouldSuppressSwing() ? 0.0F : swingProgress;
    }
}
