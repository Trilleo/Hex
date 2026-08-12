package net.trilleo.mixin;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.trilleo.highlight.HighlightLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Colours the glow outline, and hangs a label, on the entities the highlight rules claim.
 *
 * <p><b>Why {@code extractEntity}.</b> It is the single point where an entity becomes a render state, called
 * once per visible entity per frame by {@code LevelRenderer.extractVisibleEntities}. Every kind of entity goes
 * through it, so hooking here covers all of them, where hooking a particular {@code EntityRenderer} would cover
 * one.
 *
 * <p><b>Why {@code RETURN} rather than {@code HEAD}.</b> Two reasons, and the second one is not optional.
 * Vanilla's own {@code extractRenderState} sets {@code outlineColor} from the entity's team colour partway
 * through, so anything written at {@code HEAD} would be overwritten. And the caller checks
 * {@code appearsGlowing()} on the returned state <i>immediately after this method returns</i>, to decide
 * whether the outline pass runs for this frame at all — so the colour has to be in place by then. Later than
 * {@code RETURN} is too late; the submit phase clears {@code outlineColor} on every state when that check found
 * nothing glowing.
 *
 * <p>The injection itself does no work worth naming: {@link HighlightLookup} early-returns on a single volatile
 * read when nothing is highlighted, which is the case on essentially every frame of a normal session.
 *
 * <p>This targets a client class, and is registered in the {@code client} array of {@code hex.mixins.json} —
 * this mod has no common or server side.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void hex$highlight(
            Entity entity,
            float partialTick,
            CallbackInfoReturnable<EntityRenderState> cir
    ) {
        HighlightLookup.INSTANCE.apply(entity, cir.getReturnValue(), partialTick);
    }
}
