package net.trilleo.mixin;

import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.gui.components.CommandSuggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

/**
 * Exposes the in-flight completion request vanilla has asked the server for.
 *
 * Hex hides vanilla's own suggestion popup while it has something better to offer, and that would silently
 * throw away every completion the <em>server</em> knows about — on Hypixel, the only source that knows a
 * command added last week exists. Reading the pending future lets those be folded back in as candidates and
 * re-ranked by habit, so nothing is lost by taking the popup over.
 *
 * <p>An accessor rather than an injection because there is nothing to change here: the field is private, its
 * value is exactly what is wanted, and every decision about what to do with it belongs in
 * {@link net.trilleo.suggest.SuggestSession} rather than inside a mixin.
 *
 * <p>The future may be null (nothing asked yet), incomplete (asked, no reply yet), or completed
 * exceptionally — callers check all three, since none of them is unusual.
 */
@Mixin(CommandSuggestions.class)
public interface CommandSuggestionsAccessor {
    @Accessor("pendingSuggestions")
    CompletableFuture<Suggestions> hex$pendingSuggestions();
}
