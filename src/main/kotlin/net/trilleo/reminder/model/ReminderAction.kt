package net.trilleo.reminder.model

import net.trilleo.color.ColorValue
import net.trilleo.title.TitleConfig
import net.trilleo.title.model.TitleSpec
import net.trilleo.util.Chroma

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
     * [ActionKind.HUD] and [ActionKind.TITLE] — a title's own sound lives in [title], because it is part of
     * how that title lands rather than a separate alert, and the two are set independently.
     *
     * Deliberately not validated by the normalizer: the sound registry is not necessarily populated when
     * configs load at feature init, so an id is resolved when it is played (falling back to the standard UI
     * click) and checked inline by the editor, which can actually report the problem.
     */
    var value: String = DEFAULT_SOUND

    var pitch: Double = 1.0
    var volume: Double = 1.0

    /**
     * [ActionKind.TITLE] only: everything about how the title looks and sounds.
     *
     * One object rather than the handful of loose fields this used to carry, because four features fire titles
     * through [net.trilleo.title.Titles] and each new knob would otherwise have to be added to all of them.
     * Its big line is usually nothing but `&` codes: the words come from whatever owns this action — a
     * reminder's resolved text, a region's name — and the codes dress them. Both of its lines carry the same
     * `$0`–`$9` capture substitution the message does, so a chat-armed reminder can put the number it matched
     * on either.
     */
    var title: TitleSpec = TitleSpec()

    // ---- legacy fields, folded into [title] by [normalize] and then dropped ------------------------------

    /**
     * Nullable, and null is what a file written by this build holds — [normalize] moves the value into
     * [title] and clears the field, so GSON leaves the key out of the next write entirely. Reading one is
     * still supported indefinitely: a config carried back from an older install, or hand-edited from a wiki
     * page that predates the title helper, still means what it said.
     */
    @Deprecated("Folded into title.subtitle by normalize()")
    var subtitle: String? = null

    @Deprecated("Folded into title.title as a leading &#RRGGBB code by normalize()")
    var titleColor: String? = null

    @Deprecated("Folded into title.staySeconds by normalize()")
    var titleSeconds: Double? = null

    /**
     * Repairs this action in place, covering GSON's reflection gaps and bounding every number.
     *
     * A member rather than a step inside one config's normalizer — the same shape
     * [net.trilleo.skyblock.item.ItemRule.normalizeValue] takes — because four configs now hold action lists
     * (`reminders.json`, `regions.json`, `highlights.json`, `chathighlights.json`) and a repair that lived in
     * one of them would silently not apply to the others. It is also where the legacy title fields are
     * migrated, which is the same argument twice over.
     */
    @Suppress("DEPRECATION")
    fun normalize() {
        @Suppress("SENSELESS_COMPARISON")
        if (kind == null) kind = ActionKind.HUD
        @Suppress("SENSELESS_COMPARISON")
        if (value == null) value = DEFAULT_SOUND
        @Suppress("SENSELESS_COMPARISON")
        if (title == null) title = TitleConfig.newSpec()

        // Migration, before the spec is normalized so the folded values are bounded like any other. A legacy
        // field wins over the new block on the rare occasion a hand-edited file carries both: the old key is
        // the one such a file was written to use, and this build's own writes never leave one behind.
        subtitle?.let { title.subtitle = it }
        // The old colour becomes a leading code and nothing else, which is exactly what a title whose words
        // come from the alert's message is: `&#FF5555` says "my message, in red". See TitleFormat.merge.
        titleColor?.let { spec -> ColorValue.parse(spec)?.let { title.title = codeFor(it) + title.title } }
        titleSeconds?.let { title.staySeconds = it }
        subtitle = null
        titleColor = null
        titleSeconds = null

        title.normalize()

        if (kind == ActionKind.SOUND && value.isBlank()) value = DEFAULT_SOUND

        pitch = pitch.sane(1.0).coerceIn(PITCH_MIN, PITCH_MAX)
        volume = volume.sane(1.0).coerceIn(VOLUME_MIN, VOLUME_MAX)
    }

    /** A field-for-field copy of this action, for duplicating whatever owns it. */
    fun copy(): ReminderAction = ReminderAction().also {
        it.kind = kind
        it.value = value
        it.pitch = pitch
        it.volume = volume
        it.title = title.copy()
    }

    /** Replaces a NaN or infinite value — which no slider can produce but a hand-edited file can. */
    private fun Double.sane(fallback: Double): Double = if (isFinite()) this else fallback

    /** [rgb] as the `&#RRGGBB` code that writes it, for folding an old colour field into a title line. */
    private fun codeFor(rgb: Int): String =
        "&" + Chroma.HEX_MARKER + ColorValue.format(rgb, alpha = false).removePrefix("#")

    companion object {
        /** A short, bright, unmistakably deliberate note — distinct from any sound the game plays on its own. */
        const val DEFAULT_SOUND: String = "minecraft:block.note_block.pling"

        const val PITCH_MIN: Double = 0.5
        const val PITCH_MAX: Double = 2.0
        const val VOLUME_MIN: Double = 0.0
        const val VOLUME_MAX: Double = 1.0

        /**
         * A title action, timed the way this installation likes its titles.
         *
         * Every place that switches a title on goes through this rather than a bare [ReminderAction], so the
         * seed timings in the Titles tab reach a newly created title. A plain `ReminderAction()` — which GSON
         * and the normalizer still produce — falls back to vanilla's own lengths, which is the right answer
         * for a value that is about to be overwritten by a file anyway.
         */
        fun title(): ReminderAction = ReminderAction().also {
            it.kind = ActionKind.TITLE
            it.title = TitleConfig.newSpec()
        }
    }
}
