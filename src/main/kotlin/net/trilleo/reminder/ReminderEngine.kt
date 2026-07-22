package net.trilleo.reminder

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.trilleo.reminder.ReminderEngine.NO_CATCH_UP
import net.trilleo.reminder.ReminderEngine.settle
import net.trilleo.reminder.hud.ReminderHudModel
import net.trilleo.reminder.model.Reminder
import net.trilleo.reminder.model.TriggerKind
import net.trilleo.util.Notify
import org.slf4j.LoggerFactory

/**
 * Drives every reminder's countdown: arms them, fires them, and settles them afterwards.
 *
 * The tick is cheap by construction — one clock read plus a long comparison per reminder, over a list of a few
 * dozen at most. Nothing here is throttled because nothing needs to be: the two genuinely expensive inputs are
 * already throttled upstream, [net.trilleo.skyblock.SkyblockLocation] polling the sidebar every twenty ticks
 * and [net.trilleo.skyblock.item.HeldItem] re-reading only when the held stack changes identity.
 *
 * State is written on phase transitions only, never per tick, and [ReminderState] batches a burst of them into
 * one file write.
 */
object ReminderEngine {
    private val LOGGER = LoggerFactory.getLogger("hex/reminder")

    /** Prefix on a reminder that came due while the game was shut. */
    private const val OVERDUE = "(overdue) "

    /**
     * How long after joining a world to run the overdue catch-up.
     *
     * [net.trilleo.skyblock.SkyblockLocation] reads the scoreboard sidebar, and Hypixel leaves that empty for
     * the first seconds after a join — so a catch-up run immediately would evaluate every condition against
     * "not on Skyblock, island unknown" and silently suppress the very reminders it exists to deliver. Waiting
     * a few seconds costs nothing on a deadline that is already hours old.
     */
    private const val CATCH_UP_DELAY_TICKS = 100

    /** Ticks until the pending catch-up runs, or [NO_CATCH_UP] when none is due. */
    private var catchUpIn = NO_CATCH_UP

    private const val NO_CATCH_UP = -1

    /** Reads persisted countdowns and drops any belonging to a reminder that no longer exists. */
    fun onLoad() {
        ReminderState.load()
        ReminderState.pruneTo(ReminderConfig.settings.reminders.mapTo(HashSet()) { it.id })

        // A flash that was still on screen when the game closed has long since served its purpose.
        val now = System.currentTimeMillis()
        ReminderConfig.settings.reminders.forEach { reminder ->
            if (ReminderState.of(reminder.id).phase == Phase.FIRING) settle(reminder, now)
        }
        ReminderState.markDirty()
    }

    /**
     * Fires anything that came due while the game was closed.
     *
     * **A missed deadline fires once, never repeatedly.** A repeating twenty-minute reminder left for two days
     * has technically missed a hundred and forty-four firings; replaying them would mean a hundred and
     * forty-four sounds and a panel full of duplicates. One firing, marked overdue, is the only useful reading.
     */
    private fun catchUp(client: Minecraft) {
        val now = System.currentTimeMillis()
        ReminderConfig.settings.reminders.forEach { reminder ->
            if (!reminder.enabled) return@forEach
            val state = ReminderState.of(reminder.id)
            val due = state.phase == Phase.ARMED || state.phase == Phase.SNOOZED
            if (due && now >= state.firesAtEpochMs) {
                if (!state.resolvedText.startsWith(OVERDUE)) state.resolvedText = OVERDUE + state.resolvedText
                fire(client, reminder, now)
            }
        }
        ReminderState.markDirty()
    }

    /** Called every client tick from [ReminderFeature]. */
    fun tick(client: Minecraft) {
        val now = System.currentTimeMillis()

        if (catchUpIn > 0 && --catchUpIn == 0) {
            catchUpIn = NO_CATCH_UP
            catchUp(client)
        }

        ReminderTriggers.tick { kind, value -> armMatching(kind, value, now) }

        ReminderConfig.settings.reminders.forEach { reminder ->
            if (!reminder.enabled) return@forEach
            val state = ReminderState.of(reminder.id)
            when (state.phase) {
                Phase.ARMED, Phase.SNOOZED -> if (now >= state.firesAtEpochMs) fire(client, reminder, now)
                Phase.FIRING -> if (now >= state.firesAtEpochMs) settle(reminder, now)
                Phase.IDLE, Phase.PAUSED -> Unit
            }
        }

        drainSpent()

        ReminderHudModel.refresh(now)
        ReminderState.flush()
    }

    /** Called on world join — arms the reminders that wait for one, and reseeds the edge detectors. */
    fun onWorldJoin() {
        ReminderTriggers.reset()
        // Scheduled rather than run now: the sidebar this depends on is not populated yet. See the constant.
        catchUpIn = CATCH_UP_DELAY_TICKS
        val now = System.currentTimeMillis()
        ReminderConfig.settings.reminders.forEach { reminder ->
            if (!reminder.enabled) return@forEach
            when (reminder.trigger.kind) {
                TriggerKind.WORLD_JOIN -> arm(reminder, now, null)
                // A plain timer with a repeat is a heartbeat — it should be running whenever you are in a
                // world, without anyone having to start it by hand.
                TriggerKind.TIMER -> if (reminder.trigger.repeat && ReminderState.of(reminder.id).phase == Phase.IDLE) {
                    arm(reminder, now, null)
                }

                else -> Unit
            }
        }
        ReminderState.markDirty()
    }

    fun onWorldLeave() {
        ReminderTriggers.reset()
        // A catch-up owed to a world we have already left would evaluate its conditions against nothing.
        catchUpIn = NO_CATCH_UP
        ReminderState.flush()
    }

    /**
     * Offers a chat line to every chat-armed reminder.
     *
     * Wrapped whole, and always returning normally, because this runs inside the chat event that
     * [net.trilleo.feature.Features] shares with every other feature — see [ChatMatcher] for the full
     * reasoning. Reminders observe chat; they never swallow it.
     */
    fun onChat(client: Minecraft, line: String) {
        runCatching {
            val now = System.currentTimeMillis()
            ReminderConfig.settings.reminders.forEach { reminder ->
                if (!reminder.enabled) return@forEach
                if (reminder.trigger.kind != TriggerKind.CHAT_MATCH) return@forEach
                // Already counting down: a second matching line should not restart the clock, or a chatty
                // trigger would push its own deadline out for ever.
                if (ReminderState.of(reminder.id).phase != Phase.IDLE) return@forEach

                val groups = ChatMatcher.match(reminder.trigger.value, reminder.trigger.literal, line)
                if (groups == null) {
                    // No match is the ordinary case; a pattern the matcher has given up on is not, and the
                    // player has to be told which reminder is at fault rather than left with a silent one.
                    if (!reminder.trigger.literal && ChatMatcher.isKnownBad(reminder.trigger.value)) {
                        disableForBadPattern(client, reminder)
                    }
                    return@forEach
                }
                arm(reminder, now, groups)
                ReminderState.markDirty()
            }
        }.onFailure { LOGGER.error("Reminder chat handling failed", it) }
    }

    /** Arms every enabled reminder whose trigger is [kind] and whose payload equals [value]. */
    private fun armMatching(kind: TriggerKind, value: String, now: Long) {
        ReminderConfig.settings.reminders.forEach { reminder ->
            if (!reminder.enabled) return@forEach
            if (reminder.trigger.kind != kind || reminder.trigger.value != value) return@forEach
            if (ReminderState.of(reminder.id).phase != Phase.IDLE) return@forEach
            arm(reminder, now, null)
        }
        ReminderState.markDirty()
    }

    /** Starts [reminder]'s countdown, resolving its text against [groups] once and for all. */
    fun arm(reminder: Reminder, now: Long, groups: List<String?>?) {
        val state = ReminderState.of(reminder.id)
        state.phase = Phase.ARMED
        state.firesAtEpochMs = now + (reminder.trigger.seconds * 1000.0).toLong()
        state.resolvedText = ChatMatcher.substitute(reminder.text, groups)
        ReminderState.markDirty()
    }

    /** Stops [reminder] counting down, without firing it. */
    fun disarm(reminder: Reminder) {
        val state = ReminderState.of(reminder.id)
        state.phase = Phase.IDLE
        state.firesAtEpochMs = 0L
        state.remainingMs = 0L
        ReminderState.markDirty()
    }

    /** Freezes a running countdown with its remaining time intact. */
    fun pause(reminder: Reminder) {
        val state = ReminderState.of(reminder.id)
        if (state.phase != Phase.ARMED && state.phase != Phase.SNOOZED) return
        state.remainingMs = (state.firesAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
        state.firesAtEpochMs = 0L
        state.phase = Phase.PAUSED
        ReminderState.markDirty()
    }

    /** Resumes a paused countdown from where it stopped. */
    fun resume(reminder: Reminder) {
        val state = ReminderState.of(reminder.id)
        if (state.phase != Phase.PAUSED) return
        state.firesAtEpochMs = System.currentTimeMillis() + state.remainingMs
        state.remainingMs = 0L
        state.phase = Phase.ARMED
        ReminderState.markDirty()
    }

    /** Ends a reminder's flash early. */
    fun dismiss(reminder: Reminder) {
        val state = ReminderState.of(reminder.id)
        if (state.phase != Phase.FIRING) return
        settle(reminder, System.currentTimeMillis())
    }

    /** Pushes a firing reminder back by [minutes], keeping the text it already resolved. */
    fun snooze(reminder: Reminder, minutes: Double) {
        val state = ReminderState.of(reminder.id)
        state.phase = Phase.SNOOZED
        state.firesAtEpochMs = System.currentTimeMillis() + (minutes * 60_000.0).toLong()
        ReminderState.markDirty()
    }

    /** The reminder currently flashing, closest to the top of the panel — what a dismiss key acts on. */
    fun topmostFiring(): Reminder? =
        ReminderConfig.settings.reminders.firstOrNull { ReminderState.of(it.id).phase == Phase.FIRING }

    private fun fire(client: Minecraft, reminder: Reminder, now: Long) {
        val state = ReminderState.of(reminder.id)

        // Conditions are asked now rather than when the reminder was armed, so a countdown started somewhere
        // else still respects where you actually are when it comes due.
        if (!reminder.conditions.all { it.holds() }) {
            settle(reminder, now)
            return
        }

        if (state.resolvedText.isEmpty()) state.resolvedText = reminder.text

        ReminderActions.run(client, reminder)

        state.phase = Phase.FIRING
        state.lastFiredEpochMs = now
        state.firesAtEpochMs = now + (ReminderConfig.settings.flashSeconds * 1000.0).toLong()
        ReminderState.markDirty()
    }

    /**
     * Ids of one-shot `/hexa remind in` reminders that have run their course.
     *
     * Collected rather than deleted on the spot because [settle] is reached from inside a walk over the
     * reminder list, and removing an element there would break the iteration.
     */
    private val spent = mutableListOf<String>()

    /** Deletes the ephemeral reminders that finished this tick. Called once the walk is over. */
    private fun drainSpent() {
        if (spent.isEmpty()) return
        ReminderConfig.settings.reminders.removeAll { it.id in spent }
        spent.forEach { ReminderState.forget(it) }
        spent.clear()
        ReminderConfig.markDirty()
    }

    private fun settle(reminder: Reminder, now: Long) {
        val state = ReminderState.of(reminder.id)
        if (reminder.ephemeral && !reminder.trigger.repeat) {
            // A `/hexa remind in` one-shot has said its piece; leaving it in the editor would turn a throwaway
            // timer into clutter the player has to clean up by hand.
            state.phase = Phase.IDLE
            spent += reminder.id
            return
        }
        if (reminder.trigger.repeat) {
            state.phase = Phase.ARMED
            // Rearmed from now, not from the deadline that just passed. Rearming from the old deadline after
            // a long absence would queue up every interval missed in between and fire them in a burst.
            state.firesAtEpochMs = now + (reminder.trigger.seconds * 1000.0).toLong()
        } else {
            state.phase = Phase.IDLE
            state.firesAtEpochMs = 0L
        }
        ReminderState.markDirty()
    }

    /** Switches a reminder off after its pattern proved unusable, and says so once. */
    fun disableForBadPattern(client: Minecraft, reminder: Reminder) {
        reminder.enabled = false
        ReminderConfig.markDirty()
        Notify.chat(
            client,
            "Disabled reminder \"${reminder.name}\": its pattern is too costly to match.",
            ChatFormatting.RED
        )
    }
}
