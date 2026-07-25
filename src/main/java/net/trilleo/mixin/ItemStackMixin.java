package net.trilleo.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.trilleo.duck.HexItemStack;
import net.trilleo.itemcustom.ItemCustomLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies a customized display name, and gives every stack the scratch space {@link HexItemStack} describes.
 *
 * <p><b>Why {@code getHoverName}.</b> It is the single choke point for an item's name: {@code getTooltipLines}
 * builds its first line from {@code getStyledHoverName}, which calls this, and the hotbar popup, container
 * slot labels and chat item links all read it directly. Hooking here covers every one of them, where hooking
 * the tooltip would cover only the tooltip.
 *
 * <p><b>Why {@code RETURN} rather than {@code HEAD}.</b> The name Hypixel gave the item is needed for the
 * colour-only case — recolouring an item you did not rename — and at {@code RETURN} it is simply the return
 * value. Reading it at {@code HEAD} would mean asking the stack for its name from inside the method that
 * answers that question. Vanilla's own work here is two component lookups, so nothing is wasted by letting it
 * finish first.
 *
 * <p>This targets a common class rather than a client one. That is fine — the mixin is registered in the
 * {@code client} array, so it is only ever applied on the client, which is the only side that draws anything.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin implements HexItemStack {

    /**
     * The Skyblock uuid latch: null until read, {@code ""} for a stack that has none. Not stamped with a
     * generation because it cannot go stale — see {@link HexItemStack}.
     */
    private String hex$skyblockUuid;

    private int hex$nameGeneration;
    private int hex$nameFrame;
    private Component hex$customName;

    private int hex$renderGeneration;
    private ItemStack hex$renderStack;

    @Override
    public String hexSkyblockUuid() {
        return hex$skyblockUuid;
    }

    @Override
    public void hexSetSkyblockUuid(String uuid) {
        hex$skyblockUuid = uuid;
    }

    @Override
    public int hexNameGeneration() {
        return hex$nameGeneration;
    }

    @Override
    public int hexNameFrame() {
        return hex$nameFrame;
    }

    @Override
    public Component hexCustomName() {
        return hex$customName;
    }

    @Override
    public void hexSetCustomName(int generation, int frame, Component name) {
        hex$customName = name;
        hex$nameFrame = frame;
        // Written last: a reader that sees the current generation must already be able to see the payload.
        hex$nameGeneration = generation;
    }

    @Override
    public int hexRenderGeneration() {
        return hex$renderGeneration;
    }

    @Override
    public ItemStack hexRenderStack() {
        return hex$renderStack;
    }

    @Override
    public void hexSetRenderStack(int generation, ItemStack renderStack) {
        hex$renderStack = renderStack;
        hex$renderGeneration = generation;
    }

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void hex$customizeName(CallbackInfoReturnable<Component> cir) {
        Component custom = ItemCustomLookup.INSTANCE.nameFor((ItemStack) (Object) this, cir.getReturnValue());
        if (custom != null) {
            cir.setReturnValue(custom);
        }
    }
}
