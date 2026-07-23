package net.trilleo.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.trilleo.suggest.SuggestSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wires Hex's learned command suggestions into the chat box.
 *
 * <p>Every method here is a one-line delegation to {@link SuggestSession}, deliberately: this file sits on
 * the paths for typing in chat and drawing it, where a mistake does not degrade a feature but breaks the
 * game's primary means of communication. Keeping the logic on the other side of the call means the whole
 * feature can be read, reviewed and switched off in one place — {@code SuggestSession} catches everything and
 * disables itself for the session on the first exception, so these injections cannot propagate a failure into
 * vanilla.
 *
 * <p><b>Ordering.</b> The input hooks go at {@code HEAD} so Hex's popup gets the arrow keys and Tab before
 * vanilla's does; {@code ChatScreen.keyPressed} otherwise gives them to {@code commandSuggestions} first and
 * then to the sent-message history. The draw hook goes at {@code TAIL} for the opposite reason — vanilla
 * draws its own popup inside the method body, so drawing after it is what puts Hex's on top.
 *
 * <p>{@code InBedChatScreen} extends {@code ChatScreen}, so it is covered by the same injections without
 * being named.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow
    protected EditBox input;

    @Shadow
    private CommandSuggestions commandSuggestions;

    @Inject(method = "init", at = @At("TAIL"))
    private void hex$openSuggestions(CallbackInfo ci) {
        SuggestSession.INSTANCE.open(this.input, this.commandSuggestions);
    }

    /**
     * Runs after vanilla's own handling, which has already re-asked the server for completions — so the
     * pending future Hex folds in is as fresh as it can be at this point in the edit.
     */
    @Inject(method = "onEdited", at = @At("TAIL"))
    private void hex$onEdited(String text, CallbackInfo ci) {
        SuggestSession.INSTANCE.onEdited();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void hex$keyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (SuggestSession.INSTANCE.keyPressed(event)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void hex$mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (SuggestSession.INSTANCE.mouseClicked(event)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void hex$mouseScrolled(
            double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        if (SuggestSession.INSTANCE.mouseScrolled(scrollY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void hex$draw(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        SuggestSession.INSTANCE.draw(extractor);
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void hex$closeSuggestions(CallbackInfo ci) {
        SuggestSession.INSTANCE.close();
    }
}
