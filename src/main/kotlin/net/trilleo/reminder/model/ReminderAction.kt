package net.trilleo.reminder.model

/**
 * What a reminder does when it fires.
 *
 * Only the delivery routes the mod actually implements are declared. A chat line is a plausible addition, and
 * adding it is an appended constant plus one branch in [net.trilleo.reminder.ReminderActions] — exactly the
 * cheap operation this model is shaped for. Declaring it ahead of implementing it would be strictly worse than
 * not declaring it: the constant would survive the normalizer (it is a name this build knows), so a
 * hand-edited file naming it would produce a reminder that fires and does nothing at all, which is
 * indistinguishable from a broken one.
 *
 * Note that not every owner accepts every kind. [net.trilleo.region.model.Region] holds the same action list
 * but has no panel row of its own, so it offers [TITLE] and [SOUND] only — see
 * [net.trilleo.reminder.ReminderActions.run], where [HUD] is a no-op for exactly that reason.
 */
enum class ActionKind {
    /** Show the reminder on the HUD panel and flash it. */
    HUD,

    /** Play a sound. */
    SOUND,

    /** Show the message as the big centre-of-screen title. */
    TITLE,
}

/**
 * One thing a reminder does when it fires. A reminder holds a list of these, so it can flash and beep at once.
 *
 * Plain, `var`-only and no-arg constructible for the same GSON reasons as [Trigger].
 *
 * Several fields belong to one kind only, in the way [pitch] and [volume] always have: the editor shows a
 * field exactly when its kind is present, and the normalizer bounds it whether or not that kind is in use, so
 * switching a kind on never surfaces a value a hand-edited file left out of range.
 */
class ReminderAction {
    var kind: ActionKind = ActionKind.HUD

    /**
     * For [ActionKind.SOUND], the sound event id, e.g. `"minecraft:block.note_block.pling"`. Unused by
     * [ActionKind.HUD] and [ActionKind.TITLE].
     *
     * Deliberately not validated by the normalizer: the sound registry is not necessarily populated when
     * configs load at feature init, so an id is resolved when it is played (falling back to the standard UI
     * click) and checked inline by the editor, which can actually report the problem.
     */
    var value: String = DEFAULT_SOUND

    var pitch: Double = 1.0
    var volume: Double = 1.0

    /**
     * [ActionKind.TITLE] only: the smaller line beneath the title, or `""` for none.
     *
     * Carries the same `$0`–`$9` capture substitution the message does, so a chat-armed reminder can put the
     * number it matched on either line.
     */
    var subtitle: String = ""

    /** [ActionKind.TITLE] only: `"#RRGGBB"` for the title's colour, or `""` to leave it vanilla white. */
    var titleColor: String = ""

    /**
     * [ActionKind.TITLE] only: how long the title holds at full opacity, in seconds.
     *
     * Per action rather than a shared setting, for the same reason [pitch] and [volume] are: a region warning
     * you off a boss and a reminder noting your potion ran out want different dwell times, and one global
     * value would force the two features that fire titles to agree. The fade in and out either side stay at
     * vanilla's own lengths — see [net.trilleo.util.Titles].
     */
    var titleSeconds: Double = DEFAULT_TITLE_SECONDS

    /**
     * Repairs this action in place, covering GSON's reflection gaps and bounding every number.
     *
     * A member rather than a step inside one config's normalizer — the same shape
     * [net.trilleo.skyblock.item.ItemRule.normalizeValue] takes — because two configs now hold action lists
     * (`reminders.json` and `regions.json`) and a repair that lived in one of them would silently not apply to
     * the other.
     */
    fun normalize() {
        @Suppress("SENSELESS_COMPARISON")
        if (kind == null) kind = ActionKind.HUD
        @Suppress("SENSELESS_COMPARISON")
        if (value == null) value = DEFAULT_SOUND
        @Suppress("SENSELESS_COMPARISON")
        if (subtitle == null) subtitle = ""
        @Suppress("SENSELESS_COMPARISON")
        if (titleColor == null) titleColor = ""

        if (kind == ActionKind.SOUND && value.isBlank()) value = DEFAULT_SOUND

        pitch = pitch.sane(1.0).coerceIn(PITCH_MIN, PITCH_MAX)
        volume = volume.sane(1.0).coerceIn(VOLUME_MIN, VOLUME_MAX)
        titleSeconds = titleSeconds.sane(DEFAULT_TITLE_SECONDS)
            .coerceIn(TITLE_SECONDS_MIN, TITLE_SECONDS_MAX)
    }

    /** A field-for-field copy of this action, for duplicating whatever owns it. */
    fun copy(): ReminderAction = ReminderAction().also {
        it.kind = kind
        it.value = value
        it.pitch = pitch
        it.volume = volume
        it.subtitle = subtitle
        it.titleColor = titleColor
        it.titleSeconds = titleSeconds
    }

    /** Replaces a NaN or infinite value — which no slider can produce but a hand-edited file can. */
    private fun Double.sane(fallback: Double): Double = if (isFinite()) this else fallback

    companion object {
        /** A short, bright, unmistakably deliberate note — distinct from any sound the game plays on its own. */
        const val DEFAULT_SOUND: String = "minecraft:block.note_block.pling"

        const val PITCH_MIN: Double = 0.5
        const val PITCH_MAX: Double = 2.0
        const val VOLUME_MIN: Double = 0.0
        const val VOLUME_MAX: Double = 1.0

        /** Vanilla's own stay time, 70 ticks. */
        const val DEFAULT_TITLE_SECONDS: Double = 3.5

        const val TITLE_SECONDS_MIN: Double = 0.5
        const val TITLE_SECONDS_MAX: Double = 10.0
    }
}
