package net.trilleo.mixin;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/**
 * Exposes the boss bars the server has sent, so their text can be read without drawing anything.
 * <p>
 * Hypixel announces a Skyblock mining event — {@code 2X POWDER}, {@code GOBLIN RAID}, {@code RAFFLE} — on a boss
 * bar and nowhere else a client can see: it is on neither the scoreboard sidebar nor the player list, so without
 * this the mod is blind to the events that matter most in the Dwarven Mines and the Crystal Hollows. The bar text
 * carries the event and its countdown, which is exactly what
 * {@link net.trilleo.skyblock.SkyblockEvents} wants.
 *
 * <p>An accessor rather than an injection, for the same reason as {@link CommandSuggestionsAccessor}: the map is
 * private, its contents are already what is wanted, and every decision about what a bar <em>means</em> belongs in
 * Kotlin next to the other event sources rather than inside a mixin. Vanilla's own renderer reads this field the
 * same way, so nothing here depends on the bars being on screen — the map is filled from the packet.
 *
 * <p>The map is the live one, not a copy: callers read it and get out, and never hold it past the call.
 */
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccessor {
    @Accessor("events")
    Map<UUID, LerpingBossEvent> hex$events();
}
