package net.trilleo.chat.model

import net.minecraft.network.chat.Component
import net.trilleo.reminder.model.ReminderAction
import java.util.*

/**
 * Which Hypixel chat channel a line arrived on.
 *
 * Persisted by name, so constants may be appended but not renamed or reordered. A name this build does not know
 * deserializes to null and is repaired to [ANY] by the config's normalizer, which is what lets a newer Hex's file
 * load in an older one instead of failing — the same contract [net.trilleo.highlight.model.HighlightMatch] has.
 *
 * [ANY] is the value a rule holds, never one a message is detected as: it means "do not care", and it is
 * deliberately the default, because a player writing their first rule is thinking about the *words*, not about
 * where they were said.
 */
enum class ChatChannel {
    /** Rule-side only: match whatever channel the line came from, including a system broadcast. */
    ANY,

    /** Public chat — anything with a `name:` shape that carries none of the tags below. */
    ALL,

    PARTY,
    GUILD,
    OFFICER,
    COOP,

    /** A direct message, in either direction: Hypixel's `From x:` and `To x:`. */
    PRIVATE,
}

/**
 * How much of the line a rule restyles.
 *
 * [MATCH] is the point of the feature; [LINE] exists because some lines are worth spotting whole — a boss
 * broadcast is easier to see as one coloured row than as one coloured word inside a grey one.
 */
enum class ChatScope {
    /** Only the text that matched. */
    MATCH,

    /** The entire message. */
    LINE,
}

/**
 * One rule saying which chat text to look for, how to paint it, and what else finding it should do.
 *
 * A deliberately plain class rather than a data class, so equality is identity — the editor screens hold rows by
 * reference to delete them, exactly as [net.trilleo.highlight.model.Highlight] and
 * [net.trilleo.region.model.Region] do. `var`-only with a no-arg constructor because GSON instantiates it
 * reflectively and never runs Kotlin's defaults.
 *
 * **Matching is a literal substring, never a regex.** Reminders already own the regex-shaped job, and with it
 * [net.trilleo.reminder.ChatMatcher]'s whole backtracking defence; a highlight that only ever calls `indexOf`
 * cannot hang the client no matter what is typed into it, which is worth more here than the expressiveness. The
 * one knob is [caseSensitive].
 */
class ChatHighlight {
    /**
     * Stable identity, a UUID string. What the cooldown map is keyed on, so a quiet period survives a rename.
     */
    var id: String = ""

    /**
     * The rule's name, lowercased. Folded and de-duplicated by the config's normalizer, because it is what
     * `/hexa chat list` names a rule by and what a notification falls back to showing.
     */
    var name: String = ""

    var enabled: Boolean = true

    /**
     * The text to look for.
     *
     * Stored exactly as typed rather than case-folded, because [caseSensitive] decides at match time how it is
     * compared — folding it here would throw away the only thing that setting needs.
     */
    var text: String = ""

    /** Whether [text] must match the line's capitalisation. Off suits a name; on suits an all-caps broadcast. */
    var caseSensitive: Boolean = false

    /** The channel this rule listens on. [ChatChannel.ANY] listens to all of them. */
    var channel: ChatChannel = ChatChannel.ANY

    /**
     * The Skyblock islands this rule applies on, comma-separated and lowercased, or `""` for "anywhere".
     *
     * A list rather than the single island [net.trilleo.highlight.model.Highlight.island] holds, because chat
     * follows the player in a way a mob does not: the same broadcast is worth catching on three islands and not
     * on the other twenty, and three near-identical rules would then have to be edited in step forever.
     */
    var islands: String = ""

    /** Whether the paint covers only the matched text or the whole message. */
    var scope: ChatScope = ChatScope.MATCH

    /** `"#RRGGBB"` for the highlight, or `""` to use the tab's default colour. Ignored while [chroma] is on. */
    var color: String = ""

    /** Whether the highlighted text flows through the rainbow instead of holding [color]. */
    var chroma: Boolean = false

    var bold: Boolean = false
    var italic: Boolean = false
    var underlined: Boolean = false
    var strikethrough: Boolean = false
    var obfuscated: Boolean = false

    /**
     * Text placed immediately before the highlighted span, or `""` for none.
     *
     * The reason this exists is not decoration: a colour is invisible to a colour-blind player and survives no
     * screenshot compression worth the name, so a rule that matters can be given a mark that does not depend on
     * hue at all.
     */
    var prefix: String = ""

    /** Text placed immediately after the highlighted span, or `""` for none. */
    var suffix: String = ""

    /**
     * Whether a matching message is swallowed instead of shown.
     *
     * On the same rule as the paint rather than in a separate blacklist, and that pairing is the point: hiding
     * and announcing are not opposites. A rule can drop a line of spam from the chat and still play its sound,
     * which is exactly what someone watching for one broadcast inside a firehose wants.
     */
    var hide: Boolean = false

    /** Whether a match fires [actions]. */
    var notify: Boolean = false

    /** The message the notification shows. Falls back to [name] when blank — a blank title draws as nothing. */
    var notifyText: String = ""

    /** How long after firing this rule stays quiet, in seconds. Stops a busy channel machine-gunning titles. */
    var cooldownSeconds: Double = DEFAULT_COOLDOWN_SECONDS

    /**
     * Run when a match arrives. Never empty while [notify] is on — the normalizer adds a title action.
     *
     * The same type a reminder, a region and an entity highlight hold, run by the same
     * [net.trilleo.reminder.ReminderActions.run], so there is one implementation of "turn an action into a title
     * or a sound" rather than one per feature. [net.trilleo.reminder.model.ActionKind.HUD] is meaningless here —
     * the panel draws reminder phases, and a chat highlight has none — so the normalizer strips it and the editor
     * offers only the title and the sound.
     */
    var actions: MutableList<ReminderAction> = mutableListOf()

    /**
     * Where [text] next appears in [subject] at or after [from], or `-1`.
     *
     * An empty [text] never matches, so a half-typed row in the editor cannot claim every line in chat for the
     * keystroke between `d` and `drop`.
     */
    fun indexIn(subject: String, from: Int): Int {
        if (text.isEmpty()) return -1
        return subject.indexOf(text, from, ignoreCase = !caseSensitive)
    }

    /**
     * Whether this rule applies on [island], which is null when the client cannot tell which island it is on.
     *
     * A restricted rule declines an unknown island rather than firing on it: guessing wrong here means painting
     * chat somewhere the player deliberately excluded, and "not yet" resolves within a second or two anyway. An
     * unrestricted rule does not care either way. Same reading of null as
     * [net.trilleo.highlight.HighlightTracker]'s candidate filter.
     */
    fun matchesIsland(island: String?): Boolean {
        if (islands.isEmpty()) return true
        if (island == null) return false
        return islands.splitToSequence(ISLAND_SEPARATOR).any { it.trim() == island }
    }

    /** Whether this rule listens on [channel], which is null for a line that names no sender at all. */
    fun matchesChannel(channel: ChatChannel?): Boolean =
        this.channel == ChatChannel.ANY || this.channel == channel

    /**
     * A short description of what this rule catches, for the list screen and `/hexa chat list`.
     *
     * A [Component] rather than the plain [String] [net.trilleo.highlight.model.Highlight.summary] returns,
     * because every piece of it is language: the island fallback and the channel names both have to read in the
     * player's own, and this feature is new enough to start out right rather than match a sibling's oversight.
     */
    fun summary(): Component = Component.translatable(
        "hex.chat_highlights.summary",
        text,
        if (islands.isEmpty()) Component.translatable("hex.chat_highlights.any_island") else islands,
        Component.translatable("hex.config.chat_highlight_edit.channel.${channel.name.lowercase(Locale.ROOT)}"),
    )

    /** A copy carrying a fresh [id], used when duplicating a rule. */
    fun copyDefinition(into: ChatHighlight) {
        into.name = name
        into.enabled = enabled
        into.text = text
        into.caseSensitive = caseSensitive
        into.channel = channel
        into.islands = islands
        into.scope = scope
        into.color = color
        into.chroma = chroma
        into.bold = bold
        into.italic = italic
        into.underlined = underlined
        into.strikethrough = strikethrough
        into.obfuscated = obfuscated
        into.prefix = prefix
        into.suffix = suffix
        into.hide = hide
        into.notify = notify
        into.notifyText = notifyText
        into.cooldownSeconds = cooldownSeconds
        into.actions = actions.mapTo(mutableListOf()) { it.copy() }
    }

    companion object {
        /** What [islands] is split on. A comma, because no Hypixel island name contains one. */
        const val ISLAND_SEPARATOR: Char = ','

        /**
         * Puts a raw [islands] value into its stored form: lowercase, trimmed, comma-separated, with no blank and
         * no repeated entry.
         *
         * On the model rather than in the config, so the editor's setter and the config's normalizer cannot drift
         * apart: a value typed by hand and a value loaded from a file end up byte-identical, which is what lets
         * [matchesIsland] be a plain `==` per entry instead of re-trimming and re-folding on every chat line.
         */
        fun normalizeIslands(raw: String): String =
            raw.split(ISLAND_SEPARATOR)
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString("$ISLAND_SEPARATOR ")

        const val DEFAULT_COOLDOWN_SECONDS: Double = 10.0

        const val COOLDOWN_MIN: Double = 0.0
        const val COOLDOWN_MAX: Double = 300.0

        /**
         * The longest a marker may be.
         *
         * Markers are drawn on every match, inside a chat line that has a width — a long one would push the
         * message it is marking off the edge, which is the opposite of making it easier to see.
         */
        const val MARKER_MAX_LENGTH: Int = 16
    }
}
