package net.trilleo.mixin;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.ItemStack;
import net.trilleo.itemcustom.ItemCustomLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Draws a customized item using a substituted stack, which is what changes its model, skin, dye and glint.
 *
 * <p><b>Why this method.</b> {@code appendItemLayers} is the single funnel every drawn item passes through:
 * {@code updateForLiving} and {@code updateForNonLiving} both delegate to {@code updateForTopItem}, and that
 * calls this. Inventory slots, the item in hand, dropped entities and items held by other entities therefore
 * all arrive here, so one hook covers every place an item appears.
 *
 * <p><b>Why swapping the argument covers all four fields at once.</b> The method's first act is to read
 * {@code DataComponents.ITEM_MODEL} off the stack to decide which model to resolve, and it then passes that
 * same stack into {@code ItemModel.update}, where the model reads whatever else it needs from it — the profile
 * for a head, the dyed colour for leather, {@code hasFoil()} for the glint. Handing it a copy carrying
 * different components therefore redirects the model lookup <em>and</em> everything the model reads, without a
 * second injection anywhere.
 *
 * <p>Index 2 is the {@code ItemStack} parameter's LVT slot: slot 0 is {@code this} and slot 1 the render
 * state, so the second argument lands at 2 — the same counting as
 * {@link ItemInHandRendererMixin#hex$suppressSwing}.
 *
 * <p>The substitute is resolved and cached per stack by {@code ItemCustomLookup}; nothing is allocated here
 * for an item that is not customized, which is every item on a stock client.
 */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {

    @ModifyVariable(method = "appendItemLayers", at = @At("HEAD"), argsOnly = true, index = 2)
    private ItemStack hex$customizeAppearance(ItemStack stack) {
        return ItemCustomLookup.INSTANCE.renderStack(stack);
    }
}
