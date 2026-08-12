package net.trilleo.highlight

import net.minecraft.client.Minecraft
import net.trilleo.highlight.model.Highlight
import net.trilleo.reminder.ReminderActions
import net.trilleo.reminder.model.ActionKind

/**
 * Announces a newly found entity with the rule's title and sound.
 *
 * Thin on purpose, exactly as [net.trilleo.region.RegionAlerts] is: turning an action into a title or a sound
 * is [ReminderActions.run], which reminders, regions and now highlights all share. What lives here is only the
 * part that is genuinely about highlights — which message a find uses, and whether the cooldown allows it.
 */
object HighlightAlerts {

    /**
     * Announces that [highlight] has found something, unless its cooldown is still running.
     *
     * A pack that spawns together produces one announcement rather than one each. Every member is still
     * recorded as seen by [HighlightTracker], so the ones the cooldown swallowed stay quiet afterwards instead
     * of trickling out one per cooldown window — "six zombies appeared" is news once, not six times.
     */
    fun found(client: Minecraft, highlight: Highlight) {
        if (!HighlightTracker.claimCooldown(highlight, System.currentTimeMillis())) return
        ReminderActions.run(client, highlight.actions, messageOf(highlight), subtitleOf(highlight))
    }

    /**
     * Fires [highlight]'s actions now, ignoring the cooldown. The editor's **Test** button, where being made to
     * wait for a mob to spawn to hear the sound would be absurd.
     */
    fun test(client: Minecraft, highlight: Highlight) {
        ReminderActions.run(client, highlight.actions, messageOf(highlight), subtitleOf(highlight))
    }

    /** A blank message renders as an empty title, which vanilla draws as a silent flash of nothing. */
    private fun messageOf(highlight: Highlight): String = highlight.notifyText.ifBlank { highlight.name }

    /**
     * The subtitle the title action carries, or `""`.
     *
     * Unlike a reminder's, this needs no capture substitution: a highlight is armed by an entity appearing, and
     * there is no chat line behind it to have captured anything from.
     */
    private fun subtitleOf(highlight: Highlight): String =
        highlight.actions.firstOrNull { it.kind == ActionKind.TITLE }?.subtitle.orEmpty()
}
