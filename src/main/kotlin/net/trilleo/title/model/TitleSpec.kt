package net.trilleo.title.model

/**
 * One fully described title: two styled lines, how long it holds, and what it sounds like.
 *
 * This is the mod's single answer to "what does a title look like". Every feature that pops one — reminders,
 * regions, entity highlights, chat highlights — stores one of these rather than its own scatter of colour and
 * duration fields, and [net.trilleo.title.Titles] is the only thing that reads it. Adding a knob to titles is
 * therefore a field here, a row in [net.trilleo.title.gui.TitleEditScreen], and nothing else — no feature has
 * to be visited, and none of them can drift from the others.
 *
 * ### Where the text comes from
 *
 * [title]'s own [TitleLine.text] is used only by a title that owns its words. A reminder, a region or a
 * highlight passes the message it already resolved — with `$0`–`$9` captures substituted — to
 * [net.trilleo.title.Titles.show], and that wins. The *subtitle's* text is stored here in every case, because
 * nothing else has a second line to offer.
 *
 * ### Times are seconds, not ticks
 *
 * The game counts a title's three phases in ticks, but a config file is read by people. Seconds are stored and
 * the conversion happens once, in [net.trilleo.title.Titles].
 *
 * Plain, `var`-only and no-arg constructible for the same GSON reasons
 * [net.trilleo.reminder.model.ReminderAction] is.
 */
class TitleSpec {

    /** The big line. Its [TitleLine.text] is ignored when the caller supplies a message of its own. */
    var title: TitleLine = TitleLine()

    /** The smaller line beneath it. Blank text means no subtitle at all. */
    var subtitle: TitleLine = TitleLine()

    /** How long the title takes to appear. */
    var fadeInSeconds: Double = DEFAULT_FADE_IN

    /** How long it holds at full opacity, once faded in. */
    var staySeconds: Double = DEFAULT_STAY

    /** How long it takes to disappear again. */
    var fadeOutSeconds: Double = DEFAULT_FADE_OUT

    /**
     * A sound event id played the moment the title appears, or `""` for a silent title.
     *
     * Deliberately its own sound rather than a reuse of [net.trilleo.reminder.model.ActionKind.SOUND]: that
     * action exists so an alert can beep *without* showing anything, and the two are set independently. A
     * title's sound is part of how the title lands, which is why it travels with the rest of its appearance.
     *
     * Not validated by the normalizer, for the same reason the sound action's id is not: the sound registry is
     * not necessarily populated when configs load at feature init. It is resolved when it is played and
     * checked inline by the editor, which can actually report the problem.
     */
    var sound: String = ""

    var pitch: Double = 1.0
    var volume: Double = 1.0

    /** Repairs this spec in place, covering GSON's reflection gaps and bounding every number. */
    fun normalize() {
        @Suppress("SENSELESS_COMPARISON")
        if (title == null) title = TitleLine()
        @Suppress("SENSELESS_COMPARISON")
        if (subtitle == null) subtitle = TitleLine()
        @Suppress("SENSELESS_COMPARISON")
        if (sound == null) sound = ""

        title.normalize()
        subtitle.normalize()

        fadeInSeconds = fadeInSeconds.sane(DEFAULT_FADE_IN).coerceIn(FADE_MIN, FADE_MAX)
        // Zero is how an absent key arrives — GSON does not run Kotlin's default — and it is also below the
        // floor, so a spec written before a field existed picks the default up rather than flashing by.
        staySeconds = staySeconds.sane(DEFAULT_STAY).takeIf { it > 0.0 }?.coerceIn(STAY_MIN, STAY_MAX)
            ?: DEFAULT_STAY
        fadeOutSeconds = fadeOutSeconds.sane(DEFAULT_FADE_OUT).coerceIn(FADE_MIN, FADE_MAX)

        pitch = pitch.sane(1.0).coerceIn(PITCH_MIN, PITCH_MAX)
        volume = volume.sane(1.0).coerceIn(VOLUME_MIN, VOLUME_MAX)
    }

    /** A deep copy, for duplicating whatever owns this spec. */
    fun copy(): TitleSpec = TitleSpec().also {
        it.title = title.copy()
        it.subtitle = subtitle.copy()
        it.fadeInSeconds = fadeInSeconds
        it.staySeconds = staySeconds
        it.fadeOutSeconds = fadeOutSeconds
        it.sound = sound
        it.pitch = pitch
        it.volume = volume
    }

    /** Replaces a NaN or infinite value — which no slider can produce but a hand-edited file can. */
    private fun Double.sane(fallback: Double): Double = if (isFinite()) this else fallback

    companion object {
        /** Vanilla's own three phases, 10 / 70 / 20 ticks, in seconds. */
        const val DEFAULT_FADE_IN: Double = 0.5
        const val DEFAULT_STAY: Double = 3.5
        const val DEFAULT_FADE_OUT: Double = 1.0

        const val FADE_MIN: Double = 0.0
        const val FADE_MAX: Double = 5.0

        /**
         * A stay of zero would draw the title only during its fades, which reads as a broken title rather than
         * as a short one; the ceiling is generous because "hold this until I have dealt with it" is a real
         * thing to want from a boss warning.
         */
        const val STAY_MIN: Double = 0.5
        const val STAY_MAX: Double = 30.0

        const val PITCH_MIN: Double = 0.5
        const val PITCH_MAX: Double = 2.0
        const val VOLUME_MIN: Double = 0.0
        const val VOLUME_MAX: Double = 1.0
    }
}
