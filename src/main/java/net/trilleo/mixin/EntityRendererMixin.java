package net.trilleo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import net.trilleo.highlight.HighlightLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a name tag this mod marked bigger than the game would have drawn it.
 *
 * <p>A mob Hypixel has not sent yet is nothing but its floating name — see {@code HighlightTracker.nameTagFor}
 * — and at the range that happens, that name is a few pixels tall. Growing it is the difference between a
 * marking that catches the eye and one you have to already be looking at.
 *
 * <p><b>Why here.</b> {@code submitNameDisplay} is the one place a name tag becomes a submit node, and the
 * five-argument overload is {@code final}, so every renderer and every override funnels through it. The
 * alternative — a field added to {@code EntityRenderState} — would mean writing to every entity in the world
 * every frame to keep a pooled state from carrying a stale size.
 *
 * <p><b>Why the scale is sandwiched between two translations.</b> The name is anchored by
 * {@code translate(attachment + 0.5)}, then turned to face the camera, then scaled down by {@code 0.025}. A
 * scale applied to the pose as it stands would multiply that anchoring translation too, and the tag would climb
 * away from the mob — a whole block at 3×. Moving to the anchor, scaling, and moving back leaves the anchor
 * exactly where vanilla put it and grows only what is drawn around it. A uniform scale commutes with the
 * camera rotation that comes after, so the text still faces the player.
 *
 * <p>Both halves ask {@link HighlightLookup#nameTagScale} rather than passing a flag between them: it is the
 * same object and the same setting a few instructions apart, so the two answers cannot disagree, and nothing
 * has to be stored on a renderer that every entity of a kind shares.
 *
 * <p>This targets a client class, and is registered in the {@code client} array of {@code hex.mixins.json}.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(
            method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
            at = @At("HEAD")
    )
    private void hex$growMarkedName(
            EntityRenderState state,
            PoseStack pose,
            SubmitNodeCollector collector,
            CameraRenderState camera,
            int lineOffset,
            CallbackInfo ci
    ) {
        float scale = HighlightLookup.INSTANCE.nameTagScale(state.nameTag);
        if (scale == 1.0F) {
            return;
        }
        // Vanilla draws nothing without an attachment, so there is nothing to grow either.
        Vec3 anchor = state.nameTagAttachment;
        if (anchor == null) {
            return;
        }

        pose.pushPose();
        // The 0.5 is vanilla's own lift, applied where the tag is anchored; it has to be inside the sandwich or
        // the tag would sit half a block low.
        pose.translate(anchor.x, anchor.y + 0.5, anchor.z);
        pose.scale(scale, scale, scale);
        pose.translate(-anchor.x, -(anchor.y + 0.5), -anchor.z);
    }

    @Inject(
            method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
            at = @At("RETURN")
    )
    private void hex$restorePose(
            EntityRenderState state,
            PoseStack pose,
            SubmitNodeCollector collector,
            CameraRenderState camera,
            int lineOffset,
            CallbackInfo ci
    ) {
        if (HighlightLookup.INSTANCE.nameTagScale(state.nameTag) == 1.0F || state.nameTagAttachment == null) {
            return;
        }
        pose.popPose();
    }
}
