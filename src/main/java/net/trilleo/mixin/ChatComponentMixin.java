package net.trilleo.mixin;

import net.minecraft.util.FormattedCharSequence;
import net.trilleo.chat.ChatChroma;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Recolours the characters a chat highlight marked for chroma, once per frame, as chat is drawn.
 *
 * <p><b>Why a mixin is needed at all.</b> A chat message is styled exactly once, when it arrives:
 * {@code ChatComponent} wraps it into {@link FormattedCharSequence} lines and keeps those for as long as the
 * message is on screen. Chroma is a colour that changes every frame, so there is no colour that could have been
 * baked in at arrival — a rainbow painted then would simply sit still. See {@code ChatChroma} for the marking
 * scheme this reads.
 *
 * <p><b>Why here.</b> {@code ChatComponent.ChatGraphicsAccess#handleMessage} is the single method every chat line
 * passes through on its way to being drawn, so one injection covers the whole chat log — scrolled, focused,
 * unfocused, wrapped, faded — with no knowledge of how any of that works. Rewriting the argument is enough
 * because a {@link FormattedCharSequence} is lazy: the wrapper does nothing until the game accepts it.
 *
 * <p><b>Why two targets, and not the third.</b> The interface has three implementations. The two named here are
 * the ones that draw. The third, {@code ClickableTextOnlyGraphicsAccess}, is the click hit-testing pass, and it
 * is deliberately left alone: recolouring is meaningless to it, and a mod reaching into the path that decides
 * what a click does is a mod that can break clicking.
 *
 * <p>Only the colour of a marked character changes. Its click event, hover event, insertion and every format flag
 * are passed through untouched, and the marker font names the same glyph providers vanilla's default does, so
 * nothing about the line's width or layout moves.
 *
 * <p>This targets client classes, and is registered in the {@code client} array of {@code hex.mixins.json}.
 */
@Mixin(targets = {
        "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess",
        "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess"
})
public class ChatComponentMixin {

    @ModifyVariable(
            method = "handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z",
            at = @At("HEAD"),
            argsOnly = true
    )
    private FormattedCharSequence hex$animateChroma(FormattedCharSequence content) {
        return ChatChroma.INSTANCE.recolor(content);
    }
}
