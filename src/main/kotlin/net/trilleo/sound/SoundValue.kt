package net.trilleo.sound

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import java.util.*

/**
 * What a sound setting in this mod may hold, and how that text becomes something audible.
 *
 * Every sound the player can choose — a reminder's alert, a region's chime, a title's sting, the click a
 * toggle makes — is stored as one of exactly three things:
 *
 *  - **`"minecraft:block.note_block.pling"`** — one registered sound event, named by its id.
 *  - **`"@boss-alarm"`** — a [net.trilleo.sound.model.SoundSequence] saved in `sounds.json`, named by its id.
 *    A sequence is not a sound but a *score*, and it lives in the same field as one on purpose: the field
 *    answers the question "what does this play?", and making sequences a second setting beside it would only
 *    create two controls that could disagree. The same argument [net.trilleo.color.ColorValue] makes for
 *    keeping chroma in the colour field rather than beside it.
 *  - **`""`** — silence, for settings where "make no sound" is a real answer: a title that only appears.
 *
 * **`@` can never collide with a sound id.** It is not a legal character in an [Identifier], so
 * [Identifier.tryParse] rejects it outright — the three cases are distinguishable by looking at the first
 * character, with no marker to escape and no wrapper type. It is also what makes this a value type rather
 * than a schema change: [net.trilleo.reminder.model.ReminderAction.value] and
 * [net.trilleo.title.model.TitleSpec.sound] are the same `String` fields they always were, every existing
 * config keeps working untouched, and a build of this mod from before sequences existed reads a
 * `"@boss-alarm"` as an unparseable id and falls back to the standard UI click rather than throwing.
 *
 * ### Reading a value
 *
 * Playback calls [SoundPlayer.play], which folds all three cases into the right behaviour and never fails.
 * Anything that must know *which* case it has — a picker deciding what to show, a validator deciding what to
 * complain about — asks [isNone] or [isSequence] first; both are pure string tests that build nothing.
 *
 * ### Adding a sound setting to a feature
 *
 * Use `ConfigCategory.Builder.sound`, which produces the row that opens
 * [net.trilleo.sound.gui.SoundPickerScreen]. Do not hand-roll a sound field: every sound in the mod is picked
 * through that one screen, so a new feature inherits browsing, search, preview and sequences for free — and
 * stays consistent with the rest of the mod without trying to.
 */
object SoundValue {

    /** The value that means "make no sound". */
    const val NONE: String = ""

    /** Marks a value as naming a saved sequence. Not a legal [Identifier] character; see above. */
    const val SEQUENCE_PREFIX: Char = '@'

    /**
     * The longest a stored value gets. A sound id is the long case — a namespace plus a dotted path — and a
     * sequence reference is far shorter, so one limit covers both.
     */
    const val MAX_LENGTH: Int = 128

    /** Whether [spec] asks for silence. */
    fun isNone(spec: String): Boolean = spec.isBlank()

    /** Whether [spec] names a saved sequence rather than a single sound. */
    fun isSequence(spec: String): Boolean = spec.trim().firstOrNull() == SEQUENCE_PREFIX

    /** The sequence id inside `"@boss-alarm"`, or null when [spec] does not name one. */
    fun sequenceId(spec: String): String? =
        if (isSequence(spec)) spec.trim().substring(1).takeIf { it.isNotBlank() } else null

    /** The value that names the sequence [id]. */
    fun forSequence(id: String): String = SEQUENCE_PREFIX + id

    /**
     * [spec] in its canonical spelling.
     *
     * A sequence reference has its id slugged, so `"@Boss Alarm"` and `"@boss-alarm"` end up stored
     * identically and a row's "is this still the default?" test can stay a plain comparison. A sound id is
     * only trimmed and lowercased — every registered id is lowercase, and correcting the case is friendlier
     * than rejecting a value that is one shifted keystroke away from right.
     */
    fun normalize(spec: String): String = when {
        isNone(spec) -> NONE
        isSequence(spec) -> sequenceId(spec)?.let { forSequence(slug(it)) } ?: NONE
        else -> spec.trim().lowercase(Locale.ROOT)
    }

    /**
     * The sound event [spec] names, or null when it is silence, a sequence, malformed, or names nothing this
     * client has registered.
     *
     * The registry is only populated once the game has bootstrapped, so this must not be called at class-init
     * time — it is safe from a tick, a screen, or a config validator. Moved here from `Notify.soundFor` when
     * audio stopped being a notification concern and became this package's.
     */
    fun eventFor(spec: String): SoundEvent? {
        if (isNone(spec) || isSequence(spec)) return null
        val parsed = Identifier.tryParse(spec.trim()) ?: return null
        return BuiltInRegistries.SOUND_EVENT.getOptional(parsed).orElse(null)
    }

    /**
     * Why [spec] cannot be played, or null when it can — for a row's inline error.
     *
     * Resolving a sequence reference against the saved sequences is the check the sound fields could not make
     * before this existed: a text field validating with `eventFor(...) == null` can say "no such sound", but
     * only this can say "no such sequence", and the two are different mistakes with different fixes.
     *
     * @param optional whether silence is a real answer for the setting being checked. On a
     *   [net.trilleo.reminder.model.ReminderAction] it is not — that class's normalizer rewrites a blank
     *   sound value back to its default, so offering silence there would ship a control that reverts on the
     *   next load.
     */
    fun problem(spec: String, optional: Boolean): Component? = when {
        isNone(spec) -> if (optional) null else Component.translatable("hex.sound.empty")

        isSequence(spec) -> if (SoundConfig.byId(sequenceId(spec).orEmpty()) != null) {
            null
        } else {
            Component.translatable("hex.sound.unknown_sequence")
        }

        eventFor(spec) == null -> Component.translatable("hex.sound.unknown")

        else -> null
    }

    /**
     * What a picker row's button says: a sequence's name, a sound's short id, or "None".
     *
     * A sequence that no longer exists is named by its id rather than reported as broken here — the row draws
     * [problem] underneath it, and a button that said "missing" would throw away the one piece of information
     * needed to work out what happened.
     *
     * [Component.literal] throughout for the two naming cases: a sequence's name is the player's own words
     * and a sound id is an id. Neither is language, and neither belongs in a lang file.
     */
    fun describe(spec: String): Component = when {
        isNone(spec) -> Component.translatable("hex.sound.none")
        isSequence(spec) -> {
            val id = sequenceId(spec).orEmpty()
            Component.literal(SoundConfig.byId(id)?.name?.ifBlank { id } ?: id)
        }
        else -> Component.literal(SoundIds.shortName(spec))
    }

    /**
     * [wanted] as a sequence id: lowercase, with everything outside `a-z`, `0-9`, `_` and `-` folded to a
     * dash.
     *
     * Never blank, because a blank id could not be referenced and every blank one would collide. Runs of
     * dashes collapse, so "Boss  Alarm!!" and "Boss-Alarm" produce the same readable slug rather than one
     * with the punctuation embalmed in it.
     */
    fun slug(wanted: String): String {
        val folded = wanted.trim().lowercase(Locale.ROOT)
            .map { if (it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-') it else '-' }
            .joinToString("")
            .replace(DASH_RUN, "-")
            .trim('-')
        return folded.take(MAX_SLUG_LENGTH).trim('-').ifBlank { FALLBACK_SLUG }
    }

    /** Long enough to stay readable, short enough that a list row can show it beside the name. */
    private const val MAX_SLUG_LENGTH: Int = 40

    private const val FALLBACK_SLUG: String = "sequence"

    private val DASH_RUN = Regex("-{2,}")
}
