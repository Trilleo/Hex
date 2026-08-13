package net.trilleo.chat

import net.minecraft.client.Minecraft
import net.trilleo.chat.model.ChatHighlight
import net.trilleo.reminder.ReminderActions

/**
 * Fires a chat highlight's title and sound.
 *
 * Thin on purpose. The work of turning an action into an effect belongs to [ReminderActions], which reminders,
 * regions and entity highlights all share; what is specific to this feature is only *when* to fire — which is
 * what [found] adds, and what [test] deliberately skips.
 */
object ChatHighlightAlerts {

    /**
     * Announces [rule], unless it is still in its quiet period.
     *
     * The gate matters more here than it does for an entity highlight. A mob is announced once because the
     * tracker remembers which entities it has already seen; a chat line has no such identity, so a rule watching
     * a busy channel would fire on every single message that mentions its word. The cooldown is the only thing
     * standing between "tell me when someone says my name" and a title that never leaves the screen.
     */
    fun found(client: Minecraft, rule: ChatHighlight) {
        if (!ChatHighlighter.claimCooldown(rule, System.currentTimeMillis())) return
        ReminderActions.run(client, rule.actions, messageOf(rule), subtitleOf(rule))
    }

    /**
     * Fires [rule] now, ignoring the cooldown — the editor's Test button.
     *
     * Ignoring it is the point: someone tuning a sound presses Test repeatedly, and a Test that silently did
     * nothing for the next thirty seconds would read as a broken rule rather than as a working cooldown.
     */
    fun test(client: Minecraft, rule: ChatHighlight) {
        ReminderActions.run(client, rule.actions, messageOf(rule), subtitleOf(rule))
    }

    /** What the notification says. Falls back to the rule's name — a blank title draws as nothing at all. */
    private fun messageOf(rule: ChatHighlight): String = rule.notifyText.ifBlank { rule.name }

    private fun subtitleOf(rule: ChatHighlight): String = ReminderActions.subtitleOf(rule.actions)
}
