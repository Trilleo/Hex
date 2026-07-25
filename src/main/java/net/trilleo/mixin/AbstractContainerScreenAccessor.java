package net.trilleo.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes which slot the mouse is over and where the container's panel sits, so the item customization
 * keybind knows what it was pressed on and the slot marker knows where to draw.
 *
 * <p>All three members are {@code protected}, which puts them out of reach of Kotlin code that does not
 * extend the class — and no mod screen does, since these are Hypixel's own menus.
 *
 * <p>An accessor rather than an injection, for the same reason as {@link BossHealthOverlayAccessor}: the
 * fields already hold exactly what is wanted, and every decision about what to <em>do</em> with a hovered slot
 * belongs in Kotlin beside the rest of the feature rather than inside a mixin. Nothing here changes vanilla
 * behaviour, so a Hypixel GUI behaves identically whether or not the feature is switched on.
 *
 * <p>Keyboard input is taken through Fabric's {@code ScreenKeyboardEvents} instead of a second injection here,
 * which is why this file has no hook on {@code keyPressed}.
 *
 * <p>These methods drop the {@code hex$} prefix the mixins use for their own members, for the same reason
 * {@link net.trilleo.duck.HexItemStack} does: they are called from Kotlin, and the {@code hex} prefix alone
 * already keeps them clear of anything vanilla declares.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    /**
     * The slot under the cursor, or null when the cursor is not over one.
     */
    @Accessor("hoveredSlot")
    Slot hexHoveredSlot();

    /**
     * The x of the container panel's top-left corner; slot coordinates are relative to it.
     */
    @Accessor("leftPos")
    int hexLeftPos();

    /**
     * The y of the container panel's top-left corner.
     */
    @Accessor("topPos")
    int hexTopPos();
}
