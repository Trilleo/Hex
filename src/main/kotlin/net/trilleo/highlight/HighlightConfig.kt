package net.trilleo.highlight

import com.google.gson.reflect.TypeToken
import net.trilleo.config.ConfigHandle
import net.trilleo.config.ConfigRegistry
import net.trilleo.config.JsonConfig
import net.trilleo.highlight.model.Highlight
import net.trilleo.highlight.model.HighlightMatch
import net.trilleo.reminder.model.ActionKind
import net.trilleo.reminder.model.ReminderAction
import java.util.*

/**
 * Everything about entity highlights that is a *setting*, persisted at `config/hex/highlights.json`.
 *
 * @property enabled the feature's master switch. Nullable for the same reason
 *   [net.trilleo.region.RegionSettings.enabled] is: GSON leaves an absent `boolean` at the JVM default of
 *   `false`, so a hand-written file omitting the key would load as *disabled*, the opposite of what omitting a
 *   setting should mean. Read it through [HighlightConfig.active].
 */
data class HighlightSettings(
    var enabled: Boolean? = null,
    var highlights: MutableList<Highlight> = mutableListOf(),

    /**
     * How many client ticks between two rescans of the world.
     *
     * The one number that decides what this feature costs. Matching is stable between scans because the result
     * table is keyed by entity id and an entity keeps its id while it lives, so raising this slows down only
     * how quickly a *newly spawned* mob starts glowing — and a fifth of a second of that is imperceptible,
     * while rescanning every tick on a crowded island is not free.
     */
    var scanIntervalTicks: Int = 4,

    /** Fallback glow colour for a rule that names none of its own. */
    var defaultColor: String = "#FFFF55",

    /**
     * How much bigger a marked name tag is drawn than the game would draw it.
     *
     * The one thing that decides whether the far half of a highlight is *noticeable*. A marked name is the whole
     * of what a rule can show for a mob Hypixel has not sent yet — see
     * [net.trilleo.highlight.HighlightTracker.Match.nameTag] — and at the range that happens, a name at its
     * ordinary size is a few pixels tall. Above 1 it grows about its own anchor, so the tag stays over the spot
     * the mob will appear at rather than climbing away from it.
     *
     * Global rather than per-rule because it is a legibility setting, not a property of any one rule: it depends
     * on the screen and the player's eyes, and someone who wants bigger wants it for every rule at once.
     */
    var nameTagScale: Double = HighlightConfig.DEFAULT_NAME_TAG_SCALE,

    /** Whether highlights are only live while the scoreboard looks like Skyblock's. */
    var skyblockOnly: Boolean = false,
)

/**
 * Loads and holds the singleton [HighlightSettings].
 *
 * Its own file rather than a section of another, for the reason
 * [net.trilleo.region.RegionConfig] is separate: the Highlights tab's reset button should restore the sliders
 * without also destroying a list of rules the player built up over weeks. Registering with [ConfigRegistry]
 * means highlights join config profiles and clipboard export at no extra cost — and a curated set of rules for
 * an island is exactly the kind of thing worth handing to someone else.
 */
object HighlightConfig {
    private val config = JsonConfig(
        name = "highlights",
        type = object : TypeToken<HighlightSettings>() {}.type,
        default = { HighlightSettings() },
        normalizer = ::normalize,
    )

    var settings: HighlightSettings = HighlightSettings()
        private set

    /**
     * Bumped whenever the rules may have changed.
     *
     * [HighlightTracker] caches the subset of rules that apply to the current island, and that cache has to be
     * dropped when a rule is added, edited, deleted, or swapped wholesale by a profile switch. A counter rather
     * than an explicit `invalidate()` at every mutation site, because the mutation sites are the edit screens
     * and one missed call there would show up as a rule that silently never fires.
     */
    var revision: Int = 0
        private set

    val handle = ConfigRegistry.register(
        ConfigHandle(
            config,
            adopt = { settings = it; revision++ },
            current = { settings },
        ),
    )

    /** Whether the feature is switched on, treating an absent key as on. */
    val active: Boolean get() = settings.enabled != false

    fun load() = handle.loadInitial()

    /** Writes immediately. Prefer [markDirty] from anything that fires repeatedly, such as a slider. */
    fun save() {
        revision++
        handle.saveNow()
    }

    /** Records that something changed; the write is batched and lands about a second later. */
    fun markDirty() {
        revision++
        handle.markDirty()
    }

    /** Repairs the live settings in place — for a rule added by code, which has never been through a load. */
    fun normalizeNow() = handle.json.normalize(settings)

    /** The rule with this id, or null. Linear, but the list is a few dozen entries at most. */
    fun byId(id: String): Highlight? = settings.highlights.firstOrNull { it.id == id }

    /** The rule with this (already lowercased) name, or null. */
    fun byName(name: String): Highlight? = settings.highlights.firstOrNull { it.name == name }

    /**
     * A name like [wanted] that no rule other than [except] already holds.
     *
     * Names are how a rule is referred to in chat and on its label, so two rules sharing one would make both
     * unidentifiable. Rather than refusing the edit, a suffix is appended — the same repair the normalizer
     * applies to a hand-edited file.
     */
    fun uniqueName(wanted: String, except: Highlight? = null): String {
        val base = wanted.trim().lowercase(Locale.ROOT).ifBlank { "highlight" }
        val taken = settings.highlights.filter { it !== except }.mapTo(HashSet()) { it.name }
        if (base !in taken) return base
        var n = 2
        while ("$base $n" in taken) n++
        return "$base $n"
    }

    /**
     * Repairs a loaded value.
     *
     * Every step covers a way GSON's reflective construction differs from Kotlin: absent objects arrive null,
     * absent primitives arrive zeroed, and an enum name this build does not know arrives null exactly like an
     * absent one. Beyond that it folds case so matching can stay a plain `contains`, and bounds every number a
     * hand-edited file could put out of range.
     *
     * It deliberately does **not** validate a sound id or an entity type id, for the same reason
     * [net.trilleo.region.RegionConfig] does not validate a sound: neither registry is necessarily populated
     * when configs load at feature init. Both are checked inline by the editor, which can actually report the
     * problem, and an unknown type id simply matches nothing.
     */
    private fun normalize(settings: HighlightSettings) {
        @Suppress("SENSELESS_COMPARISON")
        if (settings.highlights == null) settings.highlights = mutableListOf()
        @Suppress("SENSELESS_COMPARISON")
        if (settings.defaultColor == null) settings.defaultColor = HighlightSettings().defaultColor

        settings.scanIntervalTicks = settings.scanIntervalTicks.coerceIn(SCAN_INTERVAL_MIN, SCAN_INTERVAL_MAX)
        // Zero is how an absent key arrives — GSON does not run Kotlin's default — and it is also below the
        // slider's floor, so it cannot be a value anyone chose. Reading it as "not set" is what lets a config
        // written before this setting existed pick the new size up, rather than loading a marked name at the
        // size it was never legible at.
        if (!settings.nameTagScale.isFinite() || settings.nameTagScale <= 0.0) {
            settings.nameTagScale = DEFAULT_NAME_TAG_SCALE
        }
        settings.nameTagScale = settings.nameTagScale.coerceIn(NAME_TAG_SCALE_MIN, NAME_TAG_SCALE_MAX)

        val seenIds = HashSet<String>()
        val seenNames = HashSet<String>()
        settings.highlights.forEach { highlight -> normalizeHighlight(highlight, seenIds, seenNames) }
    }

    private fun normalizeHighlight(
        highlight: Highlight,
        seenIds: MutableSet<String>,
        seenNames: MutableSet<String>,
    ) {
        @Suppress("SENSELESS_COMPARISON")
        if (highlight.id == null) highlight.id = ""
        @Suppress("SENSELESS_COMPARISON")
        if (highlight.name == null) highlight.name = ""
        @Suppress("SENSELESS_COMPARISON")
        if (highlight.value == null) highlight.value = ""
        @Suppress("SENSELESS_COMPARISON")
        if (highlight.island == null) highlight.island = ""
        @Suppress("SENSELESS_COMPARISON")
        if (highlight.color == null) highlight.color = ""
        @Suppress("SENSELESS_COMPARISON")
        if (highlight.notifyText == null) highlight.notifyText = ""
        @Suppress("SENSELESS_COMPARISON")
        if (highlight.kind == null) highlight.kind = HighlightMatch.NAME_CONTAINS
        @Suppress("SENSELESS_COMPARISON")
        if (highlight.actions == null) highlight.actions = mutableListOf()

        // A blank or repeated id would alias another rule's cooldown and its already-announced set, so both get
        // a fresh one. Repeats are the realistic case: someone duplicates an entry by hand to make a similar rule.
        if (highlight.id.isBlank() || !seenIds.add(highlight.id)) {
            highlight.id = UUID.randomUUID().toString()
            seenIds.add(highlight.id)
        }

        highlight.island = highlight.island.trim().lowercase(Locale.ROOT)
        highlight.name = highlight.name.trim().lowercase(Locale.ROOT)
        if (highlight.name.isBlank()) highlight.name = "highlight"
        // Two rules with one name would make either one unidentifiable in chat and on its label.
        if (!seenNames.add(highlight.name)) {
            var n = 2
            while (!seenNames.add("${highlight.name} $n")) n++
            highlight.name = "${highlight.name} $n"
        }

        highlight.normalizeValue()

        highlight.maxDistance = highlight.maxDistance.sane(Highlight.DEFAULT_MAX_DISTANCE)
            .coerceIn(Highlight.MAX_DISTANCE_MIN, Highlight.MAX_DISTANCE_MAX)
        highlight.cooldownSeconds = highlight.cooldownSeconds.sane(Highlight.DEFAULT_COOLDOWN_SECONDS)
            .coerceIn(Highlight.COOLDOWN_MIN, Highlight.COOLDOWN_MAX)

        highlight.actions.forEach { it.normalize() }
        // The panel belongs to reminders, which have a phase to draw; a highlight carrying a HUD action would
        // fire and do nothing, which reads exactly like a broken rule.
        highlight.actions.removeAll { it.kind == ActionKind.HUD }
        // A rule that announces itself and makes no sign of it is indistinguishable from one that never fired.
        if (highlight.notify && highlight.actions.isEmpty()) {
            highlight.actions.add(ReminderAction().apply { kind = ActionKind.TITLE })
        }

        if (highlight.notifyText.isBlank()) highlight.notifyText = highlight.name
    }

    /** Replaces a NaN or infinite value — which no slider can produce but a hand-edited file can. */
    private fun Double.sane(fallback: Double): Double = if (isFinite()) this else fallback

    const val SCAN_INTERVAL_MIN: Int = 1
    const val SCAN_INTERVAL_MAX: Int = 20

    /** Half again as big: plainly not an ordinary name tag, without shouting over the world it hangs in. */
    const val DEFAULT_NAME_TAG_SCALE: Double = 1.5

    /** 1 is "the size the game would have drawn it". Smaller than that is nobody's idea of a highlight. */
    const val NAME_TAG_SCALE_MIN: Double = 1.0

    /** Past this a tag a hundred blocks away starts covering the thing it is pointing at. */
    const val NAME_TAG_SCALE_MAX: Double = 4.0
}
