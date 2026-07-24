package net.trilleo.region

import net.minecraft.client.Minecraft
import net.trilleo.reminder.ReminderActions
import net.trilleo.region.model.Region

/**
 * Fires a region's own title and sound.
 *
 * Thin on purpose: the actual work of turning an action into a title or a sound is
 * [ReminderActions.run], which regions and reminders share. What lives here is only the part that is
 * genuinely about regions — which message a crossing uses, and whether the cooldown allows it at all.
 */
object RegionAlerts {

    /** Fires [region]'s entry alert, unless its cooldown is still running. */
    fun enter(client: Minecraft, region: Region) {
        fire(client, region, region.text)
    }

    /**
     * Fires [region]'s exit alert, if it has one.
     *
     * Falls back to the entry message when no separate one was written, because a region worth announcing on
     * the way in is usually worth the same words on the way out — and a blank title shows as nothing at all.
     */
    fun leave(client: Minecraft, region: Region) {
        if (!region.notifyOnLeave) return
        fire(client, region, region.leaveText.ifBlank { region.text })
    }

    /**
     * Fires [region]'s actions with [text] now, ignoring the cooldown. The editor's **Test** button, where
     * being made to wait thirty seconds to hear the sound again would be absurd.
     */
    fun test(client: Minecraft, region: Region) {
        ReminderActions.run(client, region.actions, region.text, subtitleOf(region))
    }

    private fun fire(client: Minecraft, region: Region, text: String) {
        if (!RegionTracker.claimCooldown(region, System.currentTimeMillis())) return
        ReminderActions.run(client, region.actions, text, subtitleOf(region))
    }

    /**
     * The subtitle the region's title action carries, or `""`.
     *
     * Unlike a reminder's, this needs no capture substitution: a region is armed by a position, and there is
     * no chat line behind it to have captured anything from.
     */
    private fun subtitleOf(region: Region): String =
        region.actions.firstOrNull { it.kind == net.trilleo.reminder.model.ActionKind.TITLE }?.subtitle.orEmpty()
}
