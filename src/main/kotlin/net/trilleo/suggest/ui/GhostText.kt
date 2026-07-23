package net.trilleo.suggest.ui

import net.minecraft.client.gui.components.EditBox
import net.trilleo.suggest.SuggestConfig
import net.trilleo.suggest.model.Candidate
import net.trilleo.suggest.model.Fuzzy

/**
 * The greyed-out completion drawn inline, after the cursor.
 *
 * Rendered by vanilla rather than by Hex: [EditBox.setSuggestion] is the same mechanism the multiplayer
 * screen uses to hint a server address, and borrowing it means the ghost text sits at exactly the right
 * pixel, in the right colour, clipped correctly when the line scrolls — with no drawing code here to keep in
 * step with the edit box's own layout.
 *
 * **This is the one surface that asserts rather than offers**, which is why it is the most conservative. A
 * popup listing five things is visibly a list of guesses; a completion appearing in the line you are typing
 * reads as the mod finishing your sentence, and being wrong at that is worse than saying nothing. Three
 * conditions all have to hold:
 *
 *  1. the candidate must *literally* continue what has been typed — anything else could not be drawn as a
 *     suffix, however well it scored;
 *  2. the cursor must be at the end of the line, since text after the cursor makes the suffix a lie;
 *  3. the ranker must clear the player's confidence threshold, so it stays quiet when the field is close.
 */
object GhostText {

    /**
     * What is currently drawn, or null.
     *
     * Tracked here because [EditBox] exposes a setter for its suggestion and no getter, so this is the only
     * record of what the player is being shown. Every write goes through [apply] or [clear], which keeps it
     * in step by construction — and the accept path reads *this* rather than recomputing, so it can only ever
     * insert the text that was actually on screen.
     */
    private var current: String? = null

    /**
     * Shows the completion for [ranked]'s leader, or clears it when any condition fails.
     *
     * @param typed the command text without its leading slash.
     * @param confidence the ranker's softmax confidence in the leader.
     */
    fun apply(input: EditBox, typed: String, ranked: List<Candidate>, confidence: Double) {
        if (!SuggestConfig.ghostText) return clear(input)
        val best = ranked.firstOrNull() ?: return clear(input)
        if (confidence < SuggestConfig.settings.confidence) return clear(input)
        if (input.cursorPosition != input.value.length) return clear(input)

        val completion = Fuzzy.completionOf(typed, best.key) ?: return clear(input)
        current = completion
        input.setSuggestion(completion)
    }

    /** The completion currently showing, or null. */
    fun showing(): String? = current

    /** Removes the completion. Safe to call when there is none. */
    fun clear(input: EditBox) {
        current = null
        input.setSuggestion(null)
    }
}
