package net.trilleo.reminder.hud

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.trilleo.reminder.Phase
import net.trilleo.reminder.ReminderConfig
import net.trilleo.reminder.ReminderState
import net.trilleo.reminder.model.Reminder
import net.trilleo.util.Duration

/** One prepared line of the panel: the text to draw, whether it is flashing, and how wide it measured. */
class HudRow(
    val text: Component,
    val flashing: Boolean,
    val width: Int,
)

/**
 * The panel's contents, prepared on the tick so the render path has only to draw them.
 *
 * The split matters because [ReminderHudRenderer] runs once per *frame* — several times per tick at a normal
 * frame rate, and far more on a fast machine — while what it shows changes at most once a second. So every
 * cost that scales with content lives here: formatting the countdown, building components, sorting, and
 * measuring text with `Font.width`, which is the expensive one.
 *
 * Even on the tick the work is skipped unless something visible actually changed, detected with a signature
 * over each row's phase and its countdown *in whole seconds*. A countdown that reads `2:31` produces the same
 * signature for twenty consecutive ticks, so a panel of eight reminders rebuilds about once a second.
 */
object ReminderHudModel {

    @Volatile
    var rows: List<HudRow> = emptyList()
        private set

    /** The widest row, so the renderer sizes the panel without measuring anything itself. */
    @Volatile
    var contentWidth: Int = 0
        private set

    private var lastSignature: Long = 0L

    /** Forces the next [refresh] to rebuild — for a settings change the signature would not notice. */
    fun invalidate() {
        lastSignature = Long.MIN_VALUE
    }

    /** Rebuilds the rows if what they would show has changed. Called once per client tick. */
    fun refresh(now: Long) {
        val visible = visibleReminders(now)
        val signature = signatureOf(visible, now)
        if (signature == lastSignature) return
        lastSignature = signature
        build(visible, now)
    }

    /** The reminders the panel shows, already ordered, with anything firing pinned to the top. */
    private fun visibleReminders(now: Long): List<Reminder> {
        val hud = ReminderConfig.hud
        val candidates = ReminderConfig.settings.reminders.filter { reminder ->
            if (!reminder.enabled) return@filter false
            when (ReminderState.of(reminder.id).phase) {
                Phase.ARMED, Phase.FIRING, Phase.PAUSED, Phase.SNOOZED -> true
                Phase.IDLE -> false
            }
        }

        val ordered = when (hud.sort) {
            HudSort.LIST_ORDER -> candidates
            HudSort.SOONEST_FIRST -> candidates.sortedBy { remainingOf(it, now) }
            HudSort.NEWEST_FIRST -> candidates.sortedByDescending { ReminderState.of(it.id).lastFiredEpochMs }
        }

        // Firing first regardless of the chosen order: the whole point of a flash is that it is the thing
        // being asked for attention, and burying it eight rows down would defeat that.
        return ordered.sortedBy { if (ReminderState.of(it.id).phase == Phase.FIRING) 0 else 1 }
    }

    private fun build(visible: List<Reminder>, now: Long) {
        val font = Minecraft.getInstance().font
        val hud = ReminderConfig.hud
        val shown = visible.take(hud.maxRows)

        val built = ArrayList<HudRow>(shown.size + 1)
        shown.forEach { reminder ->
            val state = ReminderState.of(reminder.id)
            val flashing = state.phase == Phase.FIRING
            val label = state.resolvedText.ifEmpty { reminder.text }.ifEmpty { reminder.name }
            val suffix = when (state.phase) {
                Phase.FIRING -> ""
                Phase.PAUSED -> "  paused"
                else -> "  " + Duration.format(remainingOf(reminder, now))
            }
            val text = Component.literal(label + suffix)
            built += HudRow(text, flashing, font.width(text))
        }

        val hidden = visible.size - shown.size
        if (hidden > 0) {
            val more = Component.literal("+$hidden more").withStyle(ChatFormatting.DARK_GRAY)
            built += HudRow(more, false, font.width(more))
        }

        rows = built
        contentWidth = built.maxOfOrNull { it.width } ?: 0
    }

    /** Milliseconds left on a reminder, or 0 for one that is firing. */
    private fun remainingOf(reminder: Reminder, now: Long): Long {
        val state = ReminderState.of(reminder.id)
        return when (state.phase) {
            Phase.PAUSED -> state.remainingMs
            Phase.FIRING -> 0L
            else -> (state.firesAtEpochMs - now).coerceAtLeast(0L)
        }
    }

    /**
     * A cheap hash of everything the rows would show. Countdowns are folded to whole seconds, which is the
     * precision the panel renders at — so a change that would not be visible does not trigger a rebuild.
     */
    private fun signatureOf(visible: List<Reminder>, now: Long): Long {
        var hash = 1125899906842597L
        visible.forEach { reminder ->
            val state = ReminderState.of(reminder.id)
            hash = hash * 31 + reminder.id.hashCode()
            hash = hash * 31 + state.phase.ordinal
            hash = hash * 31 + remainingOf(reminder, now) / 1000L
            hash = hash * 31 + state.resolvedText.hashCode()
        }
        return hash
    }
}
