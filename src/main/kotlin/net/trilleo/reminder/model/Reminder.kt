package net.trilleo.reminder.model

/**
 * One user-defined reminder: what arms it, what has to be true for it to speak up, and what it does.
 *
 * A deliberately plain class rather than a data class, so equality is identity — the editor screens hold rows
 * by reference to delete them, exactly as [net.trilleo.skyblock.item.ItemRule] and
 * [net.trilleo.keybind.Keybind] do. `var`-only with a no-arg constructor because GSON instantiates it
 * reflectively and never runs Kotlin's defaults.
 *
 * Note that nothing about the reminder's *current* state lives here. Whether it is counting down, and until
 * when, is [net.trilleo.reminder.ReminderRuntimeState] in a separate unregistered file — see
 * [net.trilleo.reminder.ReminderState] for why that split matters.
 */
class Reminder {
    /**
     * Stable identity, a UUID string. This is what ties a definition to its runtime state across a restart,
     * a rename, or an edit, so it must survive everything except deletion. The normalizer mints one for a
     * blank id and re-mints duplicates, so a hand-copied entry cannot end up sharing another's countdown.
     */
    var id: String = ""

    /** The name shown in the editor list and on the HUD row. */
    var name: String = ""

    var enabled: Boolean = true

    /**
     * The message shown on the HUD.
     *
     * For a chat-armed reminder this may contain `$0`–`$9`, which are replaced with the corresponding capture
     * group of the matching line — `$0` being the whole match. `$$` is a literal `$`, and any other `$x` is
     * left alone so a message mentioning a price survives unharmed. Substitution happens once, when the
     * reminder is armed; see [net.trilleo.reminder.ChatMatcher].
     */
    var text: String = ""

    var trigger: Trigger = Trigger()

    /** All must hold when the reminder would fire, or it is suppressed. Empty means "always". */
    var conditions: MutableList<Condition> = mutableListOf()

    /** Run in order when the reminder fires. Never empty — the normalizer adds a HUD action if it is. */
    var actions: MutableList<ReminderAction> = mutableListOf()

    /** The preset this came from, or `""` when the player wrote it. See `net.trilleo.reminder.preset`. */
    var presetId: String = ""

    /** The preset revision this copy was taken from, used to offer updates. */
    var presetRevision: Int = 0

    /**
     * Whether the player has edited this since it was copied from a preset. Set by the edit screens, and
     * deliberately *not* by the list screen's enable toggle — turning a preset off is not customising it.
     * While false, a newer shipped revision may overwrite the definition in place.
     */
    var customized: Boolean = false

    /** Created by `/hexa remind in`; deleted once it has fired rather than lingering in the editor. */
    var ephemeral: Boolean = false

    /** A short description of what arms this, for the editor list and `/hexa remind list`. */
    fun triggerSummary(): String {
        val kind = trigger.kind.name.lowercase().replace('_', ' ')
        return if (trigger.usesValue() && trigger.value.isNotEmpty()) "$kind: ${trigger.value}" else kind
    }

    /** A copy carrying a fresh [id] and no preset provenance — used when duplicating a reminder. */
    fun copyDefinition(into: Reminder) {
        into.name = name
        into.text = text
        into.trigger = Trigger().also {
            it.kind = trigger.kind
            it.value = trigger.value
            it.seconds = trigger.seconds
            it.repeat = trigger.repeat
            it.literal = trigger.literal
        }
        into.conditions = conditions.mapTo(mutableListOf()) { source ->
            Condition().also { it.kind = source.kind; it.value = source.value }
        }
        into.actions = actions.mapTo(mutableListOf()) { source ->
            ReminderAction().also {
                it.kind = source.kind
                it.value = source.value
                it.pitch = source.pitch
                it.volume = source.volume
            }
        }
    }
}
