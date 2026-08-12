package net.trilleo.highlight

import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityAttachment

/**
 * The render path's entry point into entity highlighting: everything
 * [net.trilleo.mixin.EntityRenderDispatcherMixin] calls, and the only place a render state is written.
 *
 * Split out from [HighlightTracker] on purpose, the same way [net.trilleo.itemcustom.ItemCustomLookup] is split
 * from the customizer: the tracker decides *what* matches, on a tick; this decides *how* that reaches the
 * screen, and is built to do almost nothing.
 *
 * **[apply] runs for every visible entity, every frame.** The uncustomized case — which is every entity on a
 * client that has highlighted nothing — costs one volatile boolean read. There is deliberately no matching, no
 * string work and no distance maths here; all of it happened at the last scan.
 *
 * ## How a glow is produced
 *
 * [EntityRenderState.outlineColor] is what vanilla writes when an entity has the glowing effect, and
 * `LevelRenderer` reads it twice: once during extraction, through `appearsGlowing()`, to decide whether the
 * outline pass runs at all, and again when submitting, to colour it. Writing the field at the end of
 * extraction therefore both switches the pass on and picks its colour, and costs this mod no rendering code of
 * its own. Alpha is meaningless to that pass, which is why [HighlightTracker] forces the colour opaque.
 *
 * ## When there is no body to glow
 *
 * A mob Hypixel has not sent yet exists on the client only as the floating name above where it will be, and no
 * outline can be drawn on that — so [HighlightTracker.Match.nameTag] carries a marked-up copy of the name and
 * this swaps it in. Which entities get one, and why walking closer puts the ordinary glow back, is
 * `HighlightTracker.nameTagFor`.
 */
object HighlightLookup {

    /**
     * Applies whatever [entity]'s rule asked for to [state]. Called at the end of every entity extraction.
     *
     * @param partialTick the frame's fraction of a tick, needed to place a label on an entity that is mid-turn.
     */
    fun apply(entity: Entity, state: EntityRenderState, partialTick: Float) {
        if (!HighlightTracker.anyMatched) return
        val match = HighlightTracker.matchFor(entity.id) ?: return

        // Skipped for an entity that cannot draw one — vanilla has already written NO_OUTLINE, and writing a
        // colour there would switch the outline pass on for a frame in which it can produce nothing.
        if (match.outline) state.outlineColor = match.color

        match.nameTag?.let { marked ->
            // Only ever a replacement for a name the game is already drawing, never a new one: the attachment
            // the renderer hangs it from is written by vanilla in the same branch that set `nameTag`, and is
            // stale otherwise — see below. A stand whose name is hidden is left alone.
            if (state.nameTag != null) state.nameTag = marked
            return
        }

        val label = match.label ?: return
        // Never over the top of a name the game was already showing: a rule's label is additional information,
        // and replacing an entity's real name with it would take away more than it gives.
        if (state.nameTag != null) return

        // Vanilla only writes the attachment inside the branch where it decided to show a name, and leaves it
        // stale otherwise — render states are pooled and reused per entity — while the renderer dereferences it
        // without a null check. So both fields are written together here, and a label is skipped outright for an
        // entity that has nowhere to hang one.
        val attachment = entity.attachments.getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTick))
            ?: return
        state.nameTag = label
        state.nameTagAttachment = attachment
    }

    /**
     * How much bigger than the game intended [tag] should be drawn — 1 for every name tag but a marked one.
     *
     * Called by [net.trilleo.mixin.EntityRendererMixin] for each name tag about to be submitted, which is a
     * handful a frame rather than one per entity, and answers on a single volatile read in the normal case
     * where nothing is marked at all.
     *
     * The size lives in the config rather than in the [HighlightTracker.Match] so that dragging the slider
     * shows its effect on the mobs already marked, instead of on the ones found after the next scan.
     */
    fun nameTagScale(tag: Component?): Float {
        if (tag == null || !HighlightTracker.anyMarked) return 1.0f
        if (!HighlightTracker.isMarkedTag(tag)) return 1.0f
        return HighlightConfig.settings.nameTagScale.toFloat()
    }
}
