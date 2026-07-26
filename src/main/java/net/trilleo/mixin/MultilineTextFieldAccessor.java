package net.trilleo.mixin;

import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes where the selection starts and ends in a {@link MultilineTextField}.
 *
 * <p>The field already answers this publicly, through {@code getSelected()} — but that returns a
 * {@code StringView}, a nested record declared <em>protected</em>, so no caller outside the class can so much as
 * name the type of what it gets back. The two ints behind it are the whole content of that record, and reading
 * them directly is both exact and free of the accessibility trap.
 *
 * <p>{@code cursor} is the moving end of the selection and {@code selectCursor} the anchor, so either may be the
 * larger — callers want the pair sorted, which is what {@code net.trilleo.notebook.gui.NoteEdits} does with them.
 */
@Mixin(MultilineTextField.class)
public interface MultilineTextFieldAccessor {
    @Accessor("cursor")
    int hexCursor();

    @Accessor("selectCursor")
    int hexSelectCursor();
}
