package net.trilleo.reminder

import net.minecraft.client.Minecraft
import net.trilleo.reminder.model.ActionKind
import net.trilleo.reminder.model.Reminder

/**
 * Runs what a reminder does when it fires.
 *
 * Adding a delivery route is an appended [ActionKind] constant and one branch here — the HUD flash is not
 * special-cased anywhere else, because a firing reminder is simply one whose phase the panel reads.
 */
object ReminderActions {

    /** Runs every action on [reminder]. Never throws — one bad action must not strand the phase transition. */
    fun run(client: Minecraft, reminder: Reminder) {
        reminder.actions.forEach { action ->
            runCatching {
                when (action.kind) {
                    // Nothing to do: the panel draws whatever is in the FIRING phase, so the flash falls out
                    // of the state change the engine has already made.
                    ActionKind.HUD -> Unit
                    ActionKind.SOUND -> playSound(client, action.value, action.pitch, action.volume)
                }
            }
        }
    }

    private fun playSound(client: Minecraft, id: String, pitch: Double, volume: Double) {
        // The master sound switch is checked here rather than at the call site so it covers every path that
        // fires a reminder — the tick, the catch-up on load, and the test button in the editor.
        if (!ReminderConfig.settings.soundEnabled) return
        net.trilleo.util.Notify.uiSound(client, id, pitch.toFloat(), volume.toFloat())
    }
}
