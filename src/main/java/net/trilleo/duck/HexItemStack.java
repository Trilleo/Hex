package net.trilleo.duck;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Per-{@link ItemStack} scratch space for the item customization feature, implemented by
 * {@code net.trilleo.mixin.ItemStackMixin} so that every stack in the game carries its own cache.
 *
 * <p><b>Why this package exists.</b> {@code hex.mixins.json} declares {@code net.trilleo.mixin} as a mixin
 * package, and Mixin treats <em>everything</em> under a declared package as a mixin: referencing a plain class
 * that lives there throws {@code IllegalClassLoadError: … is in a defined mixin package … and cannot be
 * referenced directly}, which happens at bootstrap and takes the game down with it. A duck interface has to be
 * referenced directly — that is the whole point of it — so it lives outside, and this package holds the duck
 * interfaces alone.
 *
 * <p><b>Why a cache is not optional here.</b> The two consumers sit on the hottest paths the client has:
 * {@code ItemStack.getHoverName} runs for every tooltip and every GUI label, and
 * {@code ItemModelResolver.appendItemLayers} runs once per drawn item per frame — a full inventory screen is
 * dozens of calls a frame. Answering "is this a customized item" from NBT would mean
 * {@code CustomData.copyTag()} each time, which deep-copies a Skyblock item's whole tag (its enchantment list
 * alone can hold dozens of entries). Latching the answer on the stack turns that into one field read.
 *
 * <p><b>Invalidation.</b> Two different rules, because the two things being cached go stale for two different
 * reasons:
 * <ul>
 *   <li>The Skyblock uuid is read <em>once per stack instance, ever</em>. It cannot change under a live stack:
 *       everything that would change it — switching hotbar slot, a server inventory update — hands back a
 *       different {@link ItemStack} object, exactly as {@code net.trilleo.skyblock.item.HeldItem} documents for
 *       the main hand. {@code null} means "not read yet"; the empty string means "read, and this is not a
 *       Skyblock item instance", which is the answer for every vanilla stack and the reason the fast path is
 *       one null check.</li>
 *   <li>The resolved name and render stack are stamped with the config generation they were resolved under.
 *       Any edit in the GUI bumps that counter, so the next frame re-resolves everything affected without the
 *       config having to work out <em>which</em> stacks an edit touched. The counter starts at 1, so a freshly
 *       allocated stack — whose int fields are zero — always reads as a miss.</li>
 *   <li>The name carries a <em>second</em> stamp, because a chroma name is a different colour every frame and
 *       the config has not changed at all. It holds the {@code net.trilleo.util.Chroma} frame it was built
 *       for, or {@code Chroma.STATIC} for a name that does not animate and so never expires on time alone.
 *       Nothing equivalent is needed for the render stack: chroma colours text, not models.</li>
 * </ul>
 *
 * <p>A cached value of {@code null} paired with a current generation means "resolved, and there is nothing to
 * apply"; that is why the generation is stored separately rather than inferred from the payload being null.
 *
 * <p>Note these methods drop the {@code hex$} prefix the mixins use for their own members. They are called
 * from Kotlin, which cannot name a method containing {@code $} without an escaped identifier; the {@code hex}
 * prefix alone is already enough to keep them clear of anything vanilla declares.
 */
public interface HexItemStack {

    /** The Skyblock instance uuid, {@code ""} for none, or {@code null} when it has not been read yet. */
    String hexSkyblockUuid();

    /** Latches the result of reading the uuid. Pass {@code ""} for a stack that has none. */
    void hexSetSkyblockUuid(String uuid);

    /** The config generation {@link #hexCustomName()} was resolved under, or 0 if never. */
    int hexNameGeneration();

    /**
     * The chroma frame {@link #hexCustomName()} was built for, or {@code Chroma.STATIC} when it does not
     * animate and stays good until the config changes.
     */
    int hexNameFrame();

    /** The customized display name, or null when this stack's customization does not change the name. */
    Component hexCustomName();

    /** Stores a resolved name against the generation, and the chroma frame, it was resolved under. */
    void hexSetCustomName(int generation, int frame, Component name);

    /** The config generation {@link #hexRenderStack()} was resolved under, or 0 if never. */
    int hexRenderGeneration();

    /** The stack to draw in place of this one, or null when the appearance is unchanged. */
    ItemStack hexRenderStack();

    /** Stores a resolved render stack against the generation it was resolved under. */
    void hexSetRenderStack(int generation, ItemStack renderStack);
}
