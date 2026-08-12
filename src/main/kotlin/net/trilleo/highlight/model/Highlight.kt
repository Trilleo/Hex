package net.trilleo.highlight.model

import net.trilleo.reminder.model.ReminderAction
import java.util.*

/**
 * How a [Highlight] decides whether an entity is one of its own.
 *
 * Persisted by name, so constants may be appended but not renamed or reordered. A name this build does not
 * know deserializes to null and is repaired to [NAME_CONTAINS] by the config's normalizer, which is what lets
 * a newer Hex's file load in an older one instead of failing.
 */
enum class HighlightMatch {
    /**
     * The entity's name tag contains [Highlight.value].
     *
     * "Contains" rather than "equals" because the names worth matching on Hypixel are decorated — a level, a
     * health bar, a rarity colour — and only a fragment of them is stable. See
     * [net.trilleo.highlight.HighlightTracker] for what counts as an entity's name tag, which on Hypixel is
     * usually not the entity's own.
     */
    NAME_CONTAINS,

    /** The entity's type id equals [Highlight.value], e.g. `minecraft:zombie` — every one of that kind. */
    ENTITY_TYPE,
}

/**
 * One rule saying which entities to light up, in what colour, and whether finding a new one should announce
 * itself.
 *
 * **The payload is one generic [value] rather than a field per kind**, the idiom
 * [net.trilleo.skyblock.item.ItemRule] established: a third way to match later is an appended [HighlightMatch]
 * constant and one branch in [matches], with no file migration, and an older build reading a newer file sees an
 * unknown kind, normalizes it, and carries on.
 *
 * A deliberately plain class rather than a data class, so equality is identity — the editor screens hold rows
 * by reference to delete them, exactly as [net.trilleo.region.model.Region] does. `var`-only with a no-arg
 * constructor because GSON instantiates it reflectively and never runs Kotlin's defaults.
 */
class Highlight {
    /**
     * Stable identity, a UUID string. What the tracker keys its already-announced set and its cooldown on, so
     * both survive a rename.
     */
    var id: String = ""

    /**
     * The rule's name, lowercased.
     *
     * Folded and de-duplicated by the config's normalizer, as [net.trilleo.region.model.Region.name] is: it is
     * what `/hexa highlight` names a rule by, and what the floating label shows.
     */
    var name: String = ""

    var enabled: Boolean = true

    var kind: HighlightMatch = HighlightMatch.NAME_CONTAINS

    /**
     * What [kind] compares against — a fragment of a name tag, or a namespaced entity type id.
     *
     * Already case-folded to suit [kind] by [normalizeValue], so matching is a plain `contains` or `==` with no
     * per-call case conversion on what is walked once per scanned entity.
     */
    var value: String = ""

    /**
     * The Skyblock island this rule applies on, lowercased, or `""` for "anywhere".
     *
     * Optional here in a way it is not for a region — a mob name means the same thing wherever it appears — but
     * worth having, because the same fragment can name two unrelated mobs on two islands, and a rule scoped to
     * one of them stops the other lighting up.
     */
    var island: String = ""

    /**
     * The glow: `"#RRGGBB"`, `"chroma"` for an outline that flows through the rainbow, or `""` to use the
     * tab's default colour. The outline pass ignores alpha. See [net.trilleo.color.ColorValue].
     */
    var color: String = ""

    /** How far away a match still counts, in blocks. Beyond it the entity neither glows nor announces itself. */
    var maxDistance: Double = DEFAULT_MAX_DISTANCE

    /** Whether to float this rule's [name] above every entity it matches. */
    var showLabel: Boolean = false

    /** Whether that label also carries the distance, e.g. `boss  27m`. Ignored unless [showLabel]. */
    var labelDistance: Boolean = false

    /** Whether finding a new matching entity fires [actions]. */
    var notify: Boolean = false

    /** The message the notification shows. Falls back to [name] when blank — a blank title draws as nothing. */
    var notifyText: String = ""

    /** How long after announcing this rule stays quiet, in seconds. Stops a spawning pack machine-gunning. */
    var cooldownSeconds: Double = DEFAULT_COOLDOWN_SECONDS

    /**
     * Run when a new match is found. Never empty while [notify] is on — the normalizer adds a title action.
     *
     * The same type a reminder and a region hold, run by the same
     * [net.trilleo.reminder.ReminderActions.run], so there is one implementation of "turn an action into a
     * title or a sound" rather than one per feature.
     * [net.trilleo.reminder.model.ActionKind.HUD] is meaningless here — the panel draws reminder phases, and a
     * highlight has none — so the normalizer strips it and the editor offers only the title and the sound.
     */
    var actions: MutableList<ReminderAction> = mutableListOf()

    /**
     * Whether this rule claims an entity whose name tag reads [tag] and whose type id is [typeId].
     *
     * An empty [value] never matches, so a half-typed row in the editor cannot light up every entity in the
     * world for the keystroke between `l` and `lapis`.
     */
    fun matches(tag: String?, typeId: String): Boolean = when (kind) {
        HighlightMatch.NAME_CONTAINS -> value.isNotEmpty() && tag != null && tag.contains(value)
        HighlightMatch.ENTITY_TYPE -> value.isNotEmpty() && value == typeId
    }

    /** Case-folds [value] to the convention [kind] matches against. Called whenever either one changes. */
    fun normalizeValue() {
        // Both kinds fold to lowercase, but for different reasons: a name tag is compared against text
        // TextClean already lowercased, and an entity id is lowercase by the registry's own rules. Kept as one
        // branch per kind anyway, so a kind that folds differently is a one-line addition rather than a rewrite.
        value = when (kind) {
            HighlightMatch.NAME_CONTAINS -> value.trim().lowercase(Locale.ROOT)
            HighlightMatch.ENTITY_TYPE -> value.trim().lowercase(Locale.ROOT)
        }
    }

    /** A short description of what this rule catches, for the list screen and `/hexa highlight list`. */
    fun summary(): String {
        val what = when (kind) {
            HighlightMatch.NAME_CONTAINS -> "name contains \"$value\""
            HighlightMatch.ENTITY_TYPE -> value
        }
        val where = island.ifBlank { "any island" }
        // Locale.ROOT so the numbers read the same whatever language the client is in, as everywhere else this
        // mod formats one.
        return String.format(Locale.ROOT, "%s — %s — %.0fm", what, where, maxDistance)
    }

    /** A copy carrying a fresh [id], used when duplicating a rule. */
    fun copyDefinition(into: Highlight) {
        into.name = name
        into.enabled = enabled
        into.kind = kind
        into.value = value
        into.island = island
        into.color = color
        into.maxDistance = maxDistance
        into.showLabel = showLabel
        into.labelDistance = labelDistance
        into.notify = notify
        into.notifyText = notifyText
        into.cooldownSeconds = cooldownSeconds
        into.actions = actions.mapTo(mutableListOf()) { it.copy() }
    }

    companion object {
        const val DEFAULT_MAX_DISTANCE: Double = 48.0

        /**
         * The furthest a rule may reach.
         *
         * Not a performance limit — the scan is bounded by the entities the server sends, not by this — but a
         * meaningful one: nothing beyond the entity tracking range exists on the client to be highlighted, so a
         * larger number would only promise something it cannot deliver.
         */
        const val MAX_DISTANCE_MAX: Double = 128.0

        /** Below this a rule would drop a mob standing next to you, which reads as a rule that does not work. */
        const val MAX_DISTANCE_MIN: Double = 8.0

        const val DEFAULT_COOLDOWN_SECONDS: Double = 10.0

        const val COOLDOWN_MIN: Double = 0.0
        const val COOLDOWN_MAX: Double = 300.0
    }
}
