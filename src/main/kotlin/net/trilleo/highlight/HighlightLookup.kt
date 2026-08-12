package net.trilleo.highlight

import net.minecraft.client.renderer.entity.state.EntityRenderState
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

        state.outlineColor = match.color

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
}
