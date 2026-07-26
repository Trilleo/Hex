package net.trilleo.mixin;

import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the text model behind a {@link MultiLineEditBox}, so a formatting toolbar can act on the selection.
 *
 * <p>{@link MultilineTextField} is public and already offers everything a toolbar needs — {@code getSelected()},
 * {@code getSelectedText()}, {@code insertText(String)} (which replaces the selection), {@code cursor()} and
 * {@code seekCursor(...)}. The widget simply keeps it private and re-exports only {@code getValue}/{@code setValue},
 * which is a whole-document swap: using it for a toolbar would move the caret to the end of the note on every
 * button press and throw the selection away.
 *
 * <p>An accessor rather than an injection, for the same reason as {@link BossHealthOverlayAccessor}: the field is
 * already exactly what is wanted, and every decision about what a button <em>does</em> belongs in Kotlin beside
 * the editor rather than inside a mixin. See {@code net.trilleo.notebook.gui.NoteEdits}.
 */
@Mixin(MultiLineEditBox.class)
public interface MultiLineEditBoxAccessor {
    @Accessor("textField")
    MultilineTextField hexTextField();
}
