package net.trilleo.reminder

import net.minecraft.client.Minecraft
import net.trilleo.reminder.model.ActionKind
import net.trilleo.reminder.model.Reminder
import net.trilleo.reminder.model.ReminderAction
import net.trilleo.util.HexColor
import net.trilleo.util.Titles

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
     * Never throws — one bad action must not strand the caller's phase transition, and the caller is the
     * engine's [ReminderEngine.fire], which still has state to write afterwards.
     */
    fun run(client: Minecraft, actions: List<ReminderAction>, text: String, subtitle: String = "") {
        actions.forEach { action ->
            runCatching {
                when (action.kind) {
                    // Nothing to do: the panel draws whatever is in the FIRING phase, so the flash falls out
                    // of the state change the engine has already made. A region has no phase and therefore no
                    // panel row, which is why it never offers this kind.
                    ActionKind.HUD -> Unit
                    ActionKind.SOUND -> playSound(client, action.value, action.pitch, action.volume)
                    ActionKind.TITLE -> showTitle(client, action, text, subtitle)
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
            state.resolvedSubtitle.ifEmpty { reminder.actions.firstOrNull { it.kind == ActionKind.TITLE }?.subtitle.orEmpty() },
        )
    }

    private fun showTitle(client: Minecraft, action: ReminderAction, text: String, subtitle: String) {
        Titles.show(
            client,
            text,
            subtitle,
            // A blank colour means "leave it vanilla white" rather than "black", so the absent case has to be
            // distinguished before parsing rather than defaulted through it.
            color = action.titleColor.takeIf { it.isNotBlank() }?.let { HexColor.parse(it) },
            stay = (action.titleSeconds * TICKS_PER_SECOND).toInt(),
        )
    }

    private const val TICKS_PER_SECOND = 20.0

    private fun playSound(client: Minecraft, id: String, pitch: Double, volume: Double) {
        // The master sound switch is checked here rather than at the call site so it covers every path that
        // fires a reminder — the tick, the catch-up on load, and the test button in the editor.
        if (!ReminderConfig.settings.soundEnabled) return
        net.trilleo.util.Notify.uiSound(client, id, pitch.toFloat(), volume.toFloat())
    }
}
