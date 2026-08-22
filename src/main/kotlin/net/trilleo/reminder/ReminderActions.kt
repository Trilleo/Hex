package net.trilleo.reminder

import net.minecraft.client.Minecraft
import net.trilleo.reminder.model.ActionKind
import net.trilleo.reminder.model.Reminder
import net.trilleo.reminder.model.ReminderAction
import net.trilleo.sound.SoundPlayer
import net.trilleo.title.Titles
import net.trilleo.title.model.TitleFormat

/**
 * Runs what a reminder does when it fires.
 *
 * Adding a delivery route is an appended [ActionKind] constant and one branch here — the HUD flash is not
 * special-cased anywhere else, because a firing reminder is simply one whose phase the panel reads.
 *
 * **This is the only place an action becomes a sound or a title, and it deliberately does not take a
 * [Reminder].** [net.trilleo.region.model.Region] fires the very same [ReminderAction] list, so the primitive
 * here is "run these actions with this text" and a reminder is one caller of it. Keeping a single runner is
 * what stops regions growing a parallel alert pipeline that could drift from this one.
 */
object ReminderActions {

    /**
     * Runs every action in [actions], using [text] and [subtitle] for anything that shows a message.
     *
     * @param text the alert's own message. A title line made of nothing but `&` codes styles *this* rather
     *   than replacing it — see [net.trilleo.title.model.TitleFormat.merge].
     * @param titleText the title's own big line with capture groups already substituted, for the one caller
     *   that has any: [run]'s reminder overload. Blank takes the line straight off the action, which is right
     *   everywhere else — a region and a highlight are armed by a place or a mob, and have nothing to capture.
     *
     * Never throws — one bad action must not strand the caller's phase transition, and the caller is the
     * engine's [ReminderEngine.fire], which still has state to write afterwards.
     */
    fun run(
        client: Minecraft,
        actions: List<ReminderAction>,
        text: String,
        subtitle: String = "",
        titleText: String = "",
    ) {
        actions.forEach { action ->
            runCatching {
                when (action.kind) {
                    // Nothing to do: the panel draws whatever is in the FIRING phase, so the flash falls out
                    // of the state change the engine has already made. A region has no phase and therefore no
                    // panel row, which is why it never offers this kind.
                    ActionKind.HUD -> Unit
                    ActionKind.SOUND -> playSound(client, action.value, action.pitch, action.volume)
                    ActionKind.TITLE -> showTitle(client, action, text, subtitle, titleText)
                }
            }
        }
    }

    /**
     * Runs [reminder]'s actions, taking the message the engine already resolved when it armed.
     *
     * Falls back to the raw text for a reminder that has never been armed — which is the editor's **Test**
     * button, where showing the message as typed is exactly right.
     */
    fun run(client: Minecraft, reminder: Reminder) {
        val state = ReminderState.of(reminder.id)
        run(
            client,
            reminder.actions,
            state.resolvedText.ifEmpty { reminder.text },
            state.resolvedSubtitle.ifEmpty { subtitleOf(reminder.actions) },
            state.resolvedTitle.ifEmpty { titleOf(reminder.actions) },
        )
    }

    /**
     * The big line the title action in [actions] carries, or `""`.
     *
     * Usually nothing but `&` codes, which is the point: the words come from the alert's message, and this is
     * how it is dressed. See [net.trilleo.title.model.TitleFormat].
     */
    fun titleOf(actions: List<ReminderAction>): String =
        actions.firstOrNull { it.kind == ActionKind.TITLE }?.title?.title.orEmpty()

    /**
     * The subtitle the title action in [actions] carries, or `""`.
     *
     * Here rather than repeated at each caller: reminders, regions, entity highlights and chat highlights all
     * need the same answer, and three of them were spelling it out inline.
     */
    fun subtitleOf(actions: List<ReminderAction>): String =
        actions.firstOrNull { it.kind == ActionKind.TITLE }?.title?.subtitle.orEmpty()

    /**
     * Shows the title.
     *
     * **The one place a title line is merged with the alert's message**, which is why it is here rather than in
     * [Titles]: an alert's message is this object's business, and a renderer that had to know about one would
     * have to be told by every caller anyway.
     */
    private fun showTitle(
        client: Minecraft,
        action: ReminderAction,
        text: String,
        subtitle: String,
        titleText: String,
    ) {
        Titles.show(
            client,
            action.title,
            title = TitleFormat.merge(titleText.ifEmpty { action.title.title }, text),
            subtitle = subtitle.ifEmpty { action.title.subtitle },
        )
    }

    /**
     * @param spec a [net.trilleo.sound.SoundValue] — a sound id, or `"@a-sequence"`, so an alert can be a
     *   whole phrase rather than one note. Pitch and volume multiply every step of a sequence.
     */
    private fun playSound(client: Minecraft, spec: String, pitch: Double, volume: Double) {
        // The master sound switch is checked here rather than inside SoundPlayer so it covers every path that
        // fires a reminder — the tick, the catch-up on load, and the test button in the editor — without also
        // silencing the rest of the mod. It is the *Reminders* switch, and it says so.
        if (!ReminderConfig.settings.soundEnabled) return
        SoundPlayer.play(client, spec, pitch, volume)
    }
}
