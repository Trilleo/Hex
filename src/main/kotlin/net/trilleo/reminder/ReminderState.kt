package net.trilleo.reminder

import com.google.gson.reflect.TypeToken
import net.trilleo.config.JsonConfig

/** Where a reminder is in its lifecycle. Persisted by name; an unknown constant repairs to [IDLE]. */
enum class Phase {
    /** Not counting down. Waiting for its trigger to arm it. */
    IDLE,

    /** Counting down to [ReminderRuntimeState.firesAtEpochMs]. */
    ARMED,

    /** Firing — flashing on the panel until the flash time is up. */
    FIRING,

    /** Counting down, but stopped, with [ReminderRuntimeState.remainingMs] left to go. */
    PAUSED,

    /** Fired and pushed back; counting down again to a new deadline. */
    SNOOZED,
}

/**
 * One reminder's live countdown, keyed to [net.trilleo.reminder.model.Reminder.id].
 *
 * **Deadlines are absolute epoch milliseconds, not tick counts.** Three things point the same way: every
 * Skyblock timer this feature exists to track is wall-clock (a cookie expires in four real days, a potion in
 * forty-eight real minutes), a tick countdown drifts under server lag and stops dead when a singleplayer world
 * pauses, and surviving a relog *requires* an absolute instant because there is nothing else that compares
 * across sessions. It also makes offline behaviour correct for free: a cookie keeps expiring while you are
 * logged out, so the countdown should too.
 *
 * The cost is a dependence on the system clock, which an NTP step or a manual change can move. The failure
 * mode is a reminder firing early or late, never a crash, and [ReminderEngine]'s catch-up rule bounds how
 * badly a backwards jump can behave.
 */
class ReminderRuntimeState {
    var id: String = ""

    var phase: Phase = Phase.IDLE

    /** When this fires, for [Phase.ARMED] and [Phase.SNOOZED]; when the flash ends, for [Phase.FIRING]. */
    var firesAtEpochMs: Long = 0L

    /** What is left to run, for [Phase.PAUSED]. */
    var remainingMs: Long = 0L

    /**
     * The reminder's message with any capture groups already substituted.
     *
     * Resolved once, when the reminder is armed, rather than per frame — so the render path never touches a
     * `Matcher` or builds a string, and so the text a chat line produced survives into this file and is still
     * right after a reconnect.
     */
    var resolvedText: String = ""

    /**
     * The reminder's title subtitle with its capture groups substituted, resolved alongside [resolvedText].
     *
     * Separate rather than derived at fire time because the groups themselves are not kept: they belong to a
     * chat line that scrolled away when the reminder was armed, possibly sessions ago.
     */
    var resolvedSubtitle: String = ""

    var lastFiredEpochMs: Long = 0L
}

data class ReminderStateFile(
    var entries: MutableList<ReminderRuntimeState> = mutableListOf(),
)

/**
 * Every reminder's live countdown, persisted at `config/hex/reminder_state.json`.
 *
 * **Deliberately not registered with [net.trilleo.config.ConfigRegistry]**, which is the same call
 * [net.trilleo.update.UpdateStaging] makes and for the same reason: this is machine state, not a setting. A
 * half-elapsed booster-cookie countdown is not something anyone would want captured in a config profile or
 * pasted to a friend. Registering it would mean switching profiles silently reset every running timer, and
 * that sharing your setup handed someone else your countdowns.
 *
 * The flip side is that this file has to be driven by hand — [save] is called on phase transitions and at
 * shutdown, not by the registry's tick.
 */
object ReminderState {
    private val config = JsonConfig(
        name = "reminder_state",
        type = object : TypeToken<ReminderStateFile>() {}.type,
        default = { ReminderStateFile() },
        normalizer = ::normalize,
    )

    private var file: ReminderStateFile = ReminderStateFile()

    /** Indexed on load and kept in step by [of], so the tick loop is not a linear scan per reminder. */
    private val byId: MutableMap<String, ReminderRuntimeState> = HashMap()

    /** Set by a phase transition, cleared by [flush]. Batches a burst of transitions into one write. */
    private var dirty = false

    fun load() {
        file = config.load()
        reindex()
    }

    /**
     * This reminder's state, creating an idle one the first time it is asked for. Never returns null, so the
     * engine has no "has it got state yet" case to handle.
     */
    fun of(id: String): ReminderRuntimeState = byId.getOrPut(id) {
        ReminderRuntimeState().also {
            it.id = id
            file.entries.add(it)
        }
    }

    /** Forgets a deleted reminder's state, so the file does not accumulate orphans. */
    fun forget(id: String) {
        if (byId.remove(id) != null) {
            file.entries.removeAll { it.id == id }
            dirty = true
        }
    }

    /**
     * Drops state belonging to no current reminder. Called after the definitions load, since a reminder can
     * be deleted while a profile is switched or a file is edited outside the game.
     */
    fun pruneTo(ids: Set<String>) {
        val removed = file.entries.removeAll { it.id !in ids }
        if (removed) {
            reindex()
            dirty = true
        }
    }

    /** Records that something changed. Cheap — the write happens in [flush]. */
    fun markDirty() {
        dirty = true
    }

    /** Writes if anything changed since the last write. Called from the tick and at shutdown. */
    fun flush() {
        if (!dirty) return
        dirty = false
        config.save(file)
    }

    private fun reindex() {
        byId.clear()
        file.entries.forEach { byId[it.id] = it }
    }

    private fun normalize(state: ReminderStateFile) {
        @Suppress("SENSELESS_COMPARISON")
        if (state.entries == null) state.entries = mutableListOf()

        state.entries.forEach { entry ->
            @Suppress("SENSELESS_COMPARISON")
            if (entry.id == null) entry.id = ""
            @Suppress("SENSELESS_COMPARISON")
            if (entry.phase == null) entry.phase = Phase.IDLE
            @Suppress("SENSELESS_COMPARISON")
            if (entry.resolvedText == null) entry.resolvedText = ""
            @Suppress("SENSELESS_COMPARISON")
            if (entry.resolvedSubtitle == null) entry.resolvedSubtitle = ""
            if (entry.firesAtEpochMs < 0L) entry.firesAtEpochMs = 0L
            if (entry.remainingMs < 0L) entry.remainingMs = 0L
        }
        // An entry with no id can never be matched to a reminder, and two with the same id would fight.
        val seen = HashSet<String>()
        state.entries.removeAll { it.id.isBlank() || !seen.add(it.id) }
    }
}
