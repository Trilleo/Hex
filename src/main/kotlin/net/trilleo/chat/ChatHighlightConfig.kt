package net.trilleo.chat

import com.google.gson.reflect.TypeToken
import net.trilleo.chat.model.ChatChannel
import net.trilleo.chat.model.ChatHighlight
import net.trilleo.chat.model.ChatScope
import net.trilleo.color.ColorValue
import net.trilleo.config.ConfigHandle
import net.trilleo.config.ConfigRegistry
import net.trilleo.config.JsonConfig
import net.trilleo.reminder.model.ActionKind
import net.trilleo.reminder.model.ReminderAction
import net.trilleo.util.Chroma
import java.util.*

/**
 * Everything about chat highlights that is a *setting*, persisted at `config/hex/chathighlights.json`.
 *
 * @property enabled the feature's master switch. Nullable for the same reason
 *   [net.trilleo.highlight.HighlightSettings.enabled] is: GSON leaves an absent `boolean` at the JVM default of
 *   `false`, so a hand-written file omitting the key would load as *disabled*, the opposite of what omitting a
 *   setting should mean. Read it through [ChatHighlightConfig.active].
 */
data class ChatHighlightSettings(
    var enabled: Boolean? = null,
    var highlights: MutableList<ChatHighlight> = mutableListOf(),

    /** Fallback colour for a rule that names none of its own. */
    var defaultColor: String = "#FFFF55",

    /**
     * How long one full trip through the rainbow takes, in seconds.
     *
     * Global rather than per rule, the choice [net.trilleo.itemcustom.ItemCustomizeSettings.chromaSeconds]
     * already made: chroma is one visual effect the player tunes to taste once, and two rules flowing at
     * different rates in the same chat window reads as a glitch rather than as a setting.
     */
    var chromaSeconds: Double = Chroma.SECONDS_DEFAULT,

    /** How many characters one full rainbow spans. Lower packs more colour into fewer letters. */
    var chromaWidth: Double = Chroma.WIDTH_DEFAULT,

    /** Whether chat highlights are only live while the scoreboard looks like Skyblock's. */
    var skyblockOnly: Boolean = false,
)

/**
 * Loads and holds the singleton [ChatHighlightSettings].
 *
 * Its own file rather than a section of `highlights.json`, for the reason every rule list in this mod has one:
 * the tab's reset button should restore the sliders without also destroying a list of rules the player built up
 * over weeks, and a set of rules is exactly the kind of thing worth handing to someone else — which registering
 * with [ConfigRegistry] gets for free, along with config profiles.
 */
object ChatHighlightConfig {
    private val config = JsonConfig(
        name = "chathighlights",
        type = object : TypeToken<ChatHighlightSettings>() {}.type,
        default = { ChatHighlightSettings() },
        normalizer = ::normalize,
    )

    var settings: ChatHighlightSettings = ChatHighlightSettings()
        private set

    /**
     * Bumped whenever the rules may have changed.
     *
     * [ChatHighlighter] caches the subset of rules that apply to the current island, and [anyChroma] caches
     * whether the render mixin has anything to do at all; both have to be dropped when a rule is added, edited,
     * deleted, or swapped wholesale by a profile switch. A counter rather than an explicit `invalidate()` at
     * every mutation site, because the mutation sites are the edit screens and one missed call there would show
     * up as a rule that silently never fires.
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

    val chromaSeconds: Double get() = settings.chromaSeconds
    val chromaWidth: Double get() = settings.chromaWidth

    /**
     * The colour [rule] actually paints with — its own, or the tab's default when it names none.
     *
     * One place rather than an `ifBlank` at each call site, because chroma is now one of the values this can
     * come back as: the highlighter, the list screen and [anyChroma] all have to agree about whether a rule
     * flows, and they only can if they are all asking the same question.
     */
    fun colorOf(rule: ChatHighlight): String = rule.color.ifBlank { settings.defaultColor }

    /** Whether [rule] flows through the rainbow, default colour included. */
    fun isChroma(rule: ChatHighlight): Boolean = ColorValue.isChroma(colorOf(rule))

    private var chromaRevision: Int = -1
    private var chromaCached: Boolean = false

    /**
     * Whether any rule that is switched on asks for chroma.
     *
     * This is the render mixin's bail-out, and it is why chroma costs a player who does not use it nothing at
     * all: [ChatChroma.recolor] hands the sequence straight back when this is false, so the only per-frame work
     * is one comparison. Recomputed on [revision] rather than eagerly, so the edit screens do not have to
     * remember to maintain it.
     */
    val anyChroma: Boolean
        get() {
            if (chromaRevision != revision) {
                chromaRevision = revision
                chromaCached = settings.highlights.any { it.enabled && isChroma(it) }
            }
            return chromaCached
        }

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
    fun byId(id: String): ChatHighlight? = settings.highlights.firstOrNull { it.id == id }

    /** The rule with this (already lowercased) name, or null. */
    fun byName(name: String): ChatHighlight? = settings.highlights.firstOrNull { it.name == name }

    /**
     * A name like [wanted] that no rule other than [except] already holds.
     *
     * Names are how a rule is referred to in chat and in a notification, so two rules sharing one would make both
     * unidentifiable. Rather than refusing the edit, a suffix is appended — the same repair the normalizer
     * applies to a hand-edited file.
     */
    fun uniqueName(wanted: String, except: ChatHighlight? = null): String {
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
     * absent one. Beyond that it bounds every number a hand-edited file could put out of range.
     *
     * It deliberately does **not** validate a sound id, for the same reason
     * [net.trilleo.highlight.HighlightConfig] does not: the sound registry is not necessarily populated when
     * configs load at feature init. It is checked inline by the editor, which can actually report the problem.
     */
    private fun normalize(settings: ChatHighlightSettings) {
        @Suppress("SENSELESS_COMPARISON")
        if (settings.highlights == null) settings.highlights = mutableListOf()
        @Suppress("SENSELESS_COMPARISON")
        if (settings.defaultColor == null) settings.defaultColor = ChatHighlightSettings().defaultColor
        settings.defaultColor = ColorValue.normalize(settings.defaultColor, alpha = false)

        // Zero is how an absent key arrives — GSON does not run Kotlin's default — and it is also below either
        // slider's floor, so it cannot be a value anyone chose. Reading it as "not set" is what lets a config
        // written before these settings existed pick them up rather than freezing chroma solid.
        settings.chromaSeconds = settings.chromaSeconds.sane(Chroma.SECONDS_DEFAULT)
            .takeIf { it > 0.0 }?.coerceIn(Chroma.SECONDS_MIN, Chroma.SECONDS_MAX) ?: Chroma.SECONDS_DEFAULT
        settings.chromaWidth = settings.chromaWidth.sane(Chroma.WIDTH_DEFAULT)
            .takeIf { it > 0.0 }?.coerceIn(Chroma.WIDTH_MIN, Chroma.WIDTH_MAX) ?: Chroma.WIDTH_DEFAULT

        val seenIds = HashSet<String>()
        val seenNames = HashSet<String>()
        settings.highlights.forEach { highlight -> normalizeHighlight(highlight, seenIds, seenNames) }
    }

    private fun normalizeHighlight(
        highlight: ChatHighlight,
        seenIds: MutableSet<String>,
        seenNames: MutableSet<String>,
    ) {
        @Suppress("SENSELESS_COMPARISON")
        if (highlight.id == null) highlight.id = ""
        @Suppress("SENSELESS_COMPARISON")
        if (highlight.name == null) highlight.name = ""
        @Suppress("SENSELESS_COMPARISON")
        if (highlight.text == null) highlight.text = ""
        @Suppress("SENSELESS_COMPARISON")
        if (highlight.islands == null) highlight.islands = ""
        @Suppress("SENSELESS_COMPARISON")
        if (highlight.color == null) highlight.color = ""

        // Chroma used to be a flag beside the colour; it is now one of the values the colour can take. A rule
        // written by an older Hex is carried across here and the old key dropped, so a file only ever migrates
        // once and a rule that was flowing goes on flowing.
        if (highlight.legacyChroma == true) highlight.color = ColorValue.CHROMA
        highlight.legacyChroma = null
        highlight.color = ColorValue.normalize(highlight.color, alpha = false)
        @Suppress("SENSELESS_COMPARISON")
        if (highlight.prefix == null) highlight.prefix = ""
        @Suppress("SENSELESS_COMPARISON")
        if (highlight.suffix == null) highlight.suffix = ""
        @Suppress("SENSELESS_COMPARISON")
        if (highlight.notifyText == null) highlight.notifyText = ""
        @Suppress("SENSELESS_COMPARISON")
        if (highlight.channel == null) highlight.channel = ChatChannel.ANY
        @Suppress("SENSELESS_COMPARISON")
        if (highlight.scope == null) highlight.scope = ChatScope.MATCH
        @Suppress("SENSELESS_COMPARISON")
        if (highlight.actions == null) highlight.actions = mutableListOf()

        // A blank or repeated id would alias another rule's cooldown, so both get a fresh one. Repeats are the
        // realistic case: someone duplicates an entry by hand to make a similar rule.
        if (highlight.id.isBlank() || !seenIds.add(highlight.id)) {
            highlight.id = UUID.randomUUID().toString()
            seenIds.add(highlight.id)
        }

        highlight.name = highlight.name.trim().lowercase(Locale.ROOT)
        if (highlight.name.isBlank()) highlight.name = "highlight"
        // Two rules with one name would make either one unidentifiable in chat.
        if (!seenNames.add(highlight.name)) {
            var n = 2
            while (!seenNames.add("${highlight.name} $n")) n++
            highlight.name = "${highlight.name} $n"
        }

        // Not folded: caseSensitive decides how it is compared, and folding here would throw that away. Trimmed
        // all the same, because a trailing space is invisible in the editor and stops a rule matching anything.
        highlight.text = highlight.text.trim()
        highlight.islands = ChatHighlight.normalizeIslands(highlight.islands)
        highlight.prefix = highlight.prefix.take(ChatHighlight.MARKER_MAX_LENGTH)
        highlight.suffix = highlight.suffix.take(ChatHighlight.MARKER_MAX_LENGTH)

        highlight.cooldownSeconds = highlight.cooldownSeconds.sane(ChatHighlight.DEFAULT_COOLDOWN_SECONDS)
            .coerceIn(ChatHighlight.COOLDOWN_MIN, ChatHighlight.COOLDOWN_MAX)

        highlight.actions.forEach { it.normalize() }
        // The panel belongs to reminders, which have a phase to draw; a chat highlight carrying a HUD action
        // would fire and do nothing, which reads exactly like a broken rule.
        highlight.actions.removeAll { it.kind == ActionKind.HUD }
        // A rule that announces itself and makes no sign of it is indistinguishable from one that never fired.
        if (highlight.notify && highlight.actions.isEmpty()) {
            highlight.actions.add(ReminderAction.title())
        }

        if (highlight.notifyText.isBlank()) highlight.notifyText = highlight.name
    }

    /** Replaces a NaN or infinite value — which no slider can produce but a hand-edited file can. */
    private fun Double.sane(fallback: Double): Double = if (isFinite()) this else fallback
}
