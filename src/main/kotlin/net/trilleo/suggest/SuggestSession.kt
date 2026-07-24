package net.trilleo.suggest

import com.mojang.brigadier.suggestion.Suggestions
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.CommandSuggestions
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.trilleo.mixin.CommandSuggestionsAccessor
import net.trilleo.suggest.SuggestSession.impression
import net.trilleo.suggest.SuggestSession.panicked
import net.trilleo.suggest.SuggestSession.refresh
import net.trilleo.suggest.context.ContextSnapshot
import net.trilleo.suggest.context.ContextSources
import net.trilleo.suggest.model.Candidate
import net.trilleo.suggest.model.ModelStore
import net.trilleo.suggest.model.Ranker
import net.trilleo.suggest.model.Weights
import net.trilleo.suggest.ui.GhostText
import net.trilleo.suggest.ui.OverlayRow
import net.trilleo.suggest.ui.SuggestOverlay
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

/**
 * Everything the chat screen does, in one place.
 *
 * `ChatScreenMixin` is a set of one-line delegations to this object and holds no logic of its own, so the
 * whole feature's interaction with vanilla is auditable by reading one file rather than by reading injections.
 *
 * **The panic guard is the most important thing here.** These methods run inside `ChatScreen.keyPressed`,
 * `onEdited` and `extractRenderState` — the paths for typing in chat and drawing it. An exception escaping
 * any of them does not degrade suggestions, it breaks chat. So every entry point catches, and the first one
 * that fires sets [panicked] for the rest of the session: the feature switches itself off, logs once, and the
 * chat screen carries on as vanilla. This is the same call [net.trilleo.reminder.ChatMatcher] makes about a
 * bad pattern — diagnose once, disable, never retry — and for the same reason. `Exception` and not
 * `Throwable`, also for the same reason: continuing after an `Error` means continuing on a damaged stack.
 *
 * **Handing the box back and forth.** While Hex has something to offer it calls
 * [CommandSuggestions.setAllowSuggestions]`(false)`, which both blanks vanilla's list and makes vanilla's own
 * async callback decline to re-show it when the server replies. When Hex has nothing, the flag goes back and
 * `updateCommandInfo()` re-asks — once, on the transition, never per keystroke, since vanilla already asks on
 * every edit of its own accord. The `suppressing` flag exists purely so that handing back happens once.
 *
 * **Suppression has to be re-asserted, restoring does not.** `ChatScreen.onEdited` sets the flag back to true
 * and re-asks on *every* keystroke, before this class is given the edit, and a completion that resolves
 * without asking the server — a command name, which is most of what gets typed — resolves inside that call,
 * so vanilla has rebuilt its popup by the time Hex is asked to refresh. Suppressing only on the transition
 * therefore suppresses the first keystroke and leaves both popups drawn over each other for every keystroke
 * after it. Vanilla's `showSuggestions` does not consult the flag either, so a Tab that reaches vanilla
 * rebuilds the list whatever Hex has said. Hence [suppressVanilla] runs on every refresh — it is two field
 * writes — while [restoreVanilla], which costs a suggestion request, keeps its guard.
 */
object SuggestSession {
    private val LOGGER = LoggerFactory.getLogger("hex/suggest")

    private var input: EditBox? = null
    private var vanilla: CommandSuggestions? = null

    /** Taken once when chat opens; see [ContextSources] for why once is enough. */
    private var context: ContextSnapshot = ContextSnapshot.EMPTY

    private var ranked: List<Candidate> = emptyList()
    private var rows: List<OverlayRow> = emptyList()
    private var selected: Int = 0
    private var typed: String = ""

    /** Set by Escape. Suppresses the popup until the next edit, without closing chat. */
    private var dismissed: Boolean = false

    /** Whether vanilla's popup is currently switched off by us. */
    private var suppressing: Boolean = false

    /**
     * The candidate list the player is currently looking at, kept until something is chosen from it.
     *
     * This is the training example. A list that was shown and then acted on is one labelled observation —
     * *these* were the options and *that* was the answer — and it is the only honest one available, because
     * the player can only have rejected what they could actually see. Cleared the moment it is used, so no
     * single showing can train the ranker twice.
     */
    private var impression: List<Candidate> = emptyList()

    /**
     * Set when a suggestion was just taken, so sending it does not train on the same choice twice.
     *
     * Accepting writes the chosen line into the box, which cascades straight back into [refresh] and builds a
     * *new* impression — one the accepted command now heads as an exact match. Pressing Enter would then
     * train on that too. The second step is close to harmless (the list it trains over is saturated, so the
     * gradient is nearly zero) but it is not principled, and one flag is cheaper than the paragraph it would
     * otherwise take to explain why the double count does not matter.
     */
    private var justAccepted: Boolean = false

    /**
     * The last server suggestion future folded into a ranking.
     *
     * Vanilla asks the server for completions asynchronously, so on the keystroke that triggers the request
     * the answer is not there yet. Comparing the future by identity each frame is how the ranking gets redone
     * exactly once when the reply lands, rather than being permanently one keystroke stale or being recomputed
     * every frame on the off chance.
     */
    private var lastServerFuture: CompletableFuture<Suggestions>? = null

    /** Set by the first exception out of any entry point. Disables the feature for the rest of the session. */
    private var panicked: Boolean = false

    // ---- lifecycle -------------------------------------------------------------------------------------

    /** The chat screen opened. */
    fun open(box: EditBox, suggestions: CommandSuggestions) {
        if (panicked) return
        try {
            input = box
            vanilla = suggestions
            context = ContextSources.snapshot(Minecraft.getInstance())
            selected = 0
            dismissed = false
            suppressing = false
            impression = emptyList()
            justAccepted = false
            lastServerFuture = null
            refresh()
        } catch (e: Exception) {
            panic(e)
        }
    }

    /** The chat screen closed. */
    fun close() {
        try {
            input?.let { GhostText.clear(it) }
            // Deliberately *not* restoreVanilla(): that would re-ask the server for completions for a screen
            // that is going away, and the widget it would hand them to is about to be discarded. Dropping the
            // flag is enough — the next chat screen builds a fresh CommandSuggestions with its own.
            suppressing = false
            // The natural save point: the player has stopped typing and a hitch cannot be noticed.
            ModelStore.flush()
        } catch (e: Exception) {
            LOGGER.error("Command suggestions failed while closing the chat screen", e)
        } finally {
            input = null
            vanilla = null
            ranked = emptyList()
            rows = emptyList()
            impression = emptyList()
            typed = ""
        }
    }

    /** The text in the box changed. */
    fun onEdited() {
        if (panicked) return
        try {
            dismissed = false
            selected = 0
            // A genuine edit after accepting means the player changed their mind, so whatever they send now
            // is a fresh choice rather than the one already learned from. The edit the accept itself causes
            // runs before the flag is set, so it does not clear it.
            justAccepted = false
            refresh()
        } catch (e: Exception) {
            panic(e)
        }
    }

    // ---- input -----------------------------------------------------------------------------------------

    /** Returns true when Hex consumed the key and the chat screen should not act on it. */
    fun keyPressed(event: KeyEvent): Boolean {
        if (panicked) return false
        return try {
            handleKey(event)
        } catch (e: Exception) {
            panic(e)
            false
        }
    }

    private fun handleKey(event: KeyEvent): Boolean {
        val box = input ?: return false

        if (visible()) {
            when (event.key()) {
                GLFW.GLFW_KEY_UP -> {
                    move(-1); return true
                }

                GLFW.GLFW_KEY_DOWN -> {
                    move(1); return true
                }

                // Dismiss the list rather than the whole screen — the same thing Escape does to vanilla's own
                // popup, so the key means one consistent thing whichever list happens to be up.
                GLFW.GLFW_KEY_ESCAPE -> {
                    dismiss(); return true
                }

                // Consumed whether or not it is the accept key. Vanilla's own Tab handler rebuilds its
                // suggestion list without consulting the flag Hex switched off, so letting Tab through while
                // Hex's popup is up puts a second list on screen underneath this one.
                GLFW.GLFW_KEY_TAB -> {
                    if (tabAccepts()) accept(selected)
                    return true
                }

                // The other half of the accept-key setting, which is documented as taking "the highlighted
                // suggestion or the inline completion" — without this the arrow only ever reached the ghost
                // text below, so a player who set it to the arrow could not take a row at all unless the
                // ranker happened to be confident enough to be showing one.
                GLFW.GLFW_KEY_RIGHT -> if (arrowAccepts() && box.cursorPosition == box.value.length) {
                    accept(selected); return true
                }
            }
        }

        // Reachable with the popup hidden as well: a completion can be showing on its own once the field has
        // narrowed to a single confident answer.
        if (GhostText.showing() != null) {
            val tab = event.key() == GLFW.GLFW_KEY_TAB && tabAccepts()
            val arrow = event.key() == GLFW.GLFW_KEY_RIGHT && arrowAccepts() &&
                    box.cursorPosition == box.value.length
            if (tab || arrow) {
                acceptGhost()
                return true
            }
        }

        return false
    }

    /** Returns true when Hex consumed the click. */
    fun mouseClicked(event: MouseButtonEvent): Boolean {
        if (panicked || !visible()) return false
        return try {
            val box = input ?: return false
            if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
            val row = SuggestOverlay.rowAt(box, rows, event.x(), event.y())
            if (row < 0) return false
            accept(row)
            true
        } catch (e: Exception) {
            panic(e)
            false
        }
    }

    /** Returns true when Hex consumed the scroll. */
    fun mouseScrolled(delta: Double): Boolean {
        if (panicked || !visible() || delta == 0.0) return false
        return try {
            move(if (delta > 0) -1 else 1)
            true
        } catch (e: Exception) {
            panic(e)
            false
        }
    }

    // ---- drawing ---------------------------------------------------------------------------------------

    /** Draws the popup, after vanilla has drawn everything else. */
    fun draw(extractor: GuiGraphicsExtractor) {
        if (panicked) return
        try {
            adoptServerSuggestions()
            val box = input ?: return
            if (!visible()) return
            SuggestOverlay.draw(extractor, box, rows, selected)
        } catch (e: Exception) {
            panic(e)
        }
    }

    // ---- the model behind the list ---------------------------------------------------------------------

    private fun visible(): Boolean = !dismissed && rows.isNotEmpty()

    /**
     * Recomputes the candidate list for whatever is in the box.
     *
     * Selection is preserved by *key* rather than by index, because this also runs when the server's
     * completions arrive and reorder the list — moving what is under the player's cursor out from under it
     * would be the one way this feature could actively cost someone a keystroke.
     */
    private fun refresh() {
        val box = input ?: return
        if (!SuggestConfig.active) {
            clearSuggestions()
            return
        }

        val value = box.value
        if (!value.startsWith("/")) {
            clearSuggestions()
            return
        }
        typed = value.substring(1)

        val wanted = if (typed.isEmpty()) SuggestConfig.nextCommand else SuggestConfig.popup
        if (!wanted || dismissed) {
            clearSuggestions()
            return
        }

        val previous = ranked.getOrNull(selected)?.key
        val now = System.currentTimeMillis()
        ranked = Ranker.rank(typed, context, serverSuggestions(), SuggestConfig.settings.rows, now)
        if (ranked.isEmpty()) {
            clearSuggestions()
            return
        }

        selected = previous
            ?.let { key -> ranked.indexOfFirst { it.key == key } }
            ?.takeIf { it >= 0 }
            ?: 0
        rows = SuggestOverlay.rows(ranked, typed, context, now, box.innerWidth)
        impression = ranked

        suppressVanilla()
        GhostText.apply(box, typed, ranked, Ranker.confidence(ranked))
    }

    private fun clearSuggestions() {
        ranked = emptyList()
        rows = emptyList()
        input?.let { GhostText.clear(it) }
        restoreVanilla()
    }

    /**
     * Blanks vanilla's popup for as long as Hex has one of its own. Runs on every refresh rather than once on
     * the transition — see this object's documentation for what puts vanilla's list back.
     */
    private fun suppressVanilla() {
        vanilla?.setAllowSuggestions(false)
        suppressing = true
    }

    private fun restoreVanilla() {
        if (!suppressing) return
        suppressing = false
        val cs = vanilla ?: return
        cs.setAllowSuggestions(true)
        // Once, on the transition. Vanilla re-asks on every edit of its own accord, so calling this on each
        // keystroke would double the suggestion requests sent to the server for no benefit at all.
        cs.updateCommandInfo()
    }

    /**
     * Re-ranks once when the server's completions arrive.
     *
     * Called per frame but does nothing on almost all of them — the guard is a reference comparison against
     * the future already folded in.
     */
    private fun adoptServerSuggestions() {
        val cs = vanilla ?: return
        val future = pendingOf(cs) ?: return
        if (future === lastServerFuture || !future.isDone) return
        lastServerFuture = future
        if (input?.value?.startsWith("/") == true) refresh()
    }

    /**
     * Vanilla's own completions for the current line, spliced back into whole command lines.
     *
     * Brigadier hands back the *token* to insert plus the range it replaces, so a raw suggestion is `hub`,
     * not `warp hub`. Splicing it onto the text before that range is what turns it into a candidate this
     * ranker can score against everything else — and folding it in rather than discarding it is the whole
     * reason hiding vanilla's popup is not a regression: a command Hypixel added last week, that no player
     * has typed yet and no bundled catalogue knows about, still appears, just ordered by habit instead of
     * alphabetically.
     */
    private fun serverSuggestions(): List<String> {
        val cs = vanilla ?: return emptyList()
        val future = pendingOf(cs) ?: return emptyList()
        if (!future.isDone || future.isCompletedExceptionally) return emptyList()

        val suggestions = runCatching { future.join() }.getOrNull() ?: return emptyList()
        if (suggestions.isEmpty) return emptyList()

        val value = input?.value ?: return emptyList()
        val start = suggestions.range.start.coerceIn(0, value.length)
        val head = value.substring(0, start)

        return suggestions.list.asSequence()
            .take(MAX_SERVER_SUGGESTIONS)
            .map { (head + it.text).removePrefix("/").trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }

    private fun pendingOf(cs: CommandSuggestions): CompletableFuture<Suggestions>? =
        (cs as CommandSuggestionsAccessor).`hex$pendingSuggestions`()

    // ---- acting on a suggestion ------------------------------------------------------------------------

    private fun move(delta: Int) {
        if (rows.isEmpty()) return
        // Wraps, so holding Down cycles rather than sticking at the bottom.
        selected = ((selected + delta) % rows.size + rows.size) % rows.size
        // The inline completion follows the highlight. Leaving it on the leader while the highlight is three
        // rows down puts two different answers on screen at once, and makes the accept key take the one that
        // is not highlighted.
        input?.let { GhostText.show(it, typed, ranked.getOrNull(selected)) }
    }

    private fun dismiss() {
        dismissed = true
        rows = emptyList()
        ranked = emptyList()
        input?.let { GhostText.clear(it) }
    }

    /**
     * Puts candidate [index] into the box and learns from the choice.
     *
     * The impression is captured *before* the box is written, because writing it fires the edit box's
     * responder, which reaches `ChatScreen.onEdited` and back into [refresh] — synchronously, replacing
     * [impression] with the list for the new text. Training after that would be training on the wrong
     * example.
     */
    private fun accept(index: Int) {
        val box = input ?: return
        val candidate = ranked.getOrNull(index) ?: return
        val shown = impression
        impression = emptyList()

        box.value = "/${candidate.key}"
        box.moveCursorToEnd(false)

        learn(shown, candidate)
        // After the write, because the write cascades through onEdited, which clears this.
        justAccepted = true
    }

    /** Accepts the inline completion. */
    private fun acceptGhost() {
        val box = input ?: return
        val completion = GhostText.showing() ?: return
        val candidate = ranked.firstOrNull()
        val shown = impression
        impression = emptyList()

        GhostText.clear(box)
        box.value = box.value + completion
        box.moveCursorToEnd(false)

        if (candidate != null) learn(shown, candidate)
        justAccepted = true
    }

    /**
     * A command was sent. Trains on it if it was one of the things being offered at the time.
     *
     * This is the other half of the training signal, and the less obvious one: a player who reads the list,
     * ignores it and types the command out in full has still told the ranker which of those options was
     * right. Only counted when the sent command was actually *in* the list — otherwise there is nothing to
     * have chosen it over.
     */
    fun onCommandSent(key: String) {
        if (panicked) return
        try {
            val shown = impression
            impression = emptyList()
            if (justAccepted) {
                justAccepted = false
                return
            }
            if (shown.isEmpty()) return
            val candidate = shown.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: return
            learn(shown, candidate)
        } catch (e: Exception) {
            panic(e)
        }
    }

    private fun learn(shown: List<Candidate>, chosen: Candidate) {
        if (!SuggestConfig.learning || shown.isEmpty()) return
        Weights.train(shown, chosen)
        ModelStore.markDirty()
    }

    private fun tabAccepts(): Boolean =
        SuggestConfig.settings.acceptKey == AcceptKey.TAB || SuggestConfig.settings.acceptKey == AcceptKey.BOTH

    private fun arrowAccepts(): Boolean =
        SuggestConfig.settings.acceptKey == AcceptKey.ARROW || SuggestConfig.settings.acceptKey == AcceptKey.BOTH

    private fun panic(e: Exception) {
        panicked = true
        ranked = emptyList()
        rows = emptyList()
        impression = emptyList()
        runCatching {
            input?.let { GhostText.clear(it) }
            restoreVanilla()
        }
        LOGGER.error("Command suggestions hit an error and are off for this session; chat is unaffected", e)
    }

    /** Bounds a pathological server reply, which would otherwise dominate the shortlist. */
    private const val MAX_SERVER_SUGGESTIONS = 50
}
