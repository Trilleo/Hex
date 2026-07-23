package net.trilleo.suggest.model

import net.trilleo.suggest.model.CommandParser.DEFAULT_SLOTS
import net.trilleo.suggest.model.CommandParser.learnableValue
import java.util.*

/**
 * What a command's argument slot holds, which decides whether it may be learned.
 *
 * This is the privacy boundary, and it is deliberately expressed as a property of the *slot* rather than as a
 * filter over the *value*: "is this word sensitive" is unanswerable, whereas "is the third word of `/msg` a
 * message body" is a fact about the command.
 */
enum class SlotKind {
    /** A value from a small closed set — a warp name, a subcommand. Learned. */
    ENUM,

    /** A player name. Learned only when the player has opted in, since it is someone else's identity. */
    PLAYER,

    /** Free text. **Never** learned, and stops the key: everything from here on is discarded. */
    TEXT,
}

/**
 * One command line, reduced to the form the model counts.
 *
 * @property name the command word, lower-cased (`"warp"`). Always learnable unless blocklisted.
 * @property key the canonical learnable line — the name plus however many leading arguments survived
 *   redaction (`"warp dungeon_hub"`). This, not the bare name, is what the model counts, ranks and offers,
 *   because what a player wants suggested is a whole line rather than a command with the interesting part
 *   left blank.
 * @property truncated whether redaction dropped anything, i.e. [key] is shorter than what was typed. Purely
 *   for the dashboard, so a player can see that `/msg` really is stored without its message.
 */
class ParsedCommand(
    val name: String,
    val key: String,
    val truncated: Boolean,
)

/**
 * Turns a raw command line into the [ParsedCommand] the model records, discarding anything that must not be
 * learned on the way.
 *
 * **Why keys are whole lines.** An earlier shape counted command names and kept a separate distribution over
 * each argument slot. Folding both into one key is simpler *and* strictly more useful: `/warp dungeon_hub`
 * and `/warp hub` become different things to rank, which is what the player actually chooses between, and
 * completing a half-typed line becomes a prefix search over keys rather than a join across two models.
 * Generalisation across a command's arguments is not lost — it moves into the ranker, which sees the
 * command-name frequency as a feature of its own ([Ranker] `x5`).
 *
 * **Redaction is positional and it stops rather than skips.** Once a slot is [SlotKind.TEXT] — or holds a
 * value that does not look like the kind of token that slot is supposed to hold — the key ends there. It
 * never drops a middle argument and keeps a later one, because the result would be a line the player never
 * typed and could not have meant.
 *
 * The slot layouts below are the built-in floor. [net.trilleo.suggest.model.CommandCatalog] installs the full
 * Skyblock set over them through [slotProvider]; everything here still applies when the catalogue is missing,
 * fails to load, or has never heard of the command being typed.
 */
object CommandParser {

    /**
     * Where slot layouts come from. Returns the slots for a command, or null to fall back to [DEFAULT_SLOTS].
     *
     * A hook rather than a direct call into the catalogue so this object stays free of load-order concerns —
     * it is used by the recorder, which runs long before any asset is read.
     */
    @Volatile
    var slotProvider: (name: String, firstArg: String?) -> List<SlotKind>? = { _, _ -> null }

    /**
     * The layout used for a command nothing knows about: one learnable argument, then free text.
     *
     * The single learnable slot is what makes an unknown `/foo bar` still useful, and stopping immediately
     * after it is what keeps an unknown `/note buy the dragon armour tonight` from being recorded as anything
     * more than `note buy`. The shape test in [learnableValue] then throws out most of even that.
     */
    private val DEFAULT_SLOTS = listOf(SlotKind.ENUM, SlotKind.TEXT)

    /**
     * Built-in slot layouts, checked before [DEFAULT_SLOTS].
     *
     * Keyed on the command name, or on `"<name> <first argument>"` for commands whose real shape depends on
     * their subcommand — `/party invite Someone` and `/party chat something private` cannot share a layout,
     * and the two-token key is how they are told apart before either is recorded.
     *
     * The last entry of a layout repeats for every remaining slot, so `[PLAYER, TEXT]` means "one name, then
     * a message however long".
     */
    private val BUILTIN: Map<String, List<SlotKind>> = buildMap {
        // Whispers and channel chat: the recipient is worth learning, the message never is.
        listOf("msg", "m", "w", "whisper", "tell", "message").forEach {
            put(it, listOf(SlotKind.PLAYER, SlotKind.TEXT))
        }
        // No recipient at all — the whole tail is a message.
        listOf("r", "reply", "pc", "ac", "gc", "oc", "sc", "me", "say", "shout").forEach {
            put(it, listOf(SlotKind.TEXT))
        }
        // Subcommand, then usually a name.
        listOf("party", "p", "guild", "g", "f", "friend", "coop", "co-op").forEach {
            put(it, listOf(SlotKind.ENUM, SlotKind.PLAYER, SlotKind.TEXT))
        }
        // …except the chat subcommands, which are messages.
        listOf("party chat", "p chat", "guild chat", "g chat", "coop chat").forEach {
            put(it, listOf(SlotKind.ENUM, SlotKind.TEXT))
        }
    }

    /**
     * The parsed form of [line] (a command *without* its leading slash, as
     * `ClientSendMessageEvents.COMMAND` delivers it), or null when there is nothing to learn — a blank line,
     * or a command the player has blocklisted.
     */
    fun parse(line: String, blocklist: Set<String> = emptySet(), learnPlayerNames: Boolean = true): ParsedCommand? {
        val tokens = line.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        val name = tokens[0].lowercase(Locale.ROOT).removePrefix("/")
        if (name.isEmpty() || name in blocklist) return null

        val args = tokens.drop(1)
        val slots = slotsFor(name, args.firstOrNull())

        val kept = StringBuilder(name)
        var truncated = false
        for (i in args.indices) {
            val kind = slots[i.coerceAtMost(slots.size - 1)]
            val value = learnableValue(args[i], kind, learnPlayerNames)
            if (value == null) {
                truncated = true
                break
            }
            kept.append(' ').append(value)
        }

        return ParsedCommand(name, kept.toString(), truncated)
    }

    /**
     * The slot layout for a command, most specific first: the catalogue's two-token entry, the catalogue's
     * name entry, then the built-ins in the same order, then [DEFAULT_SLOTS].
     */
    private fun slotsFor(name: String, firstArg: String?): List<SlotKind> {
        val sub = firstArg?.lowercase(Locale.ROOT)
        val fromCatalogue = runCatching { slotProvider(name, sub) }.getOrNull()
        if (fromCatalogue != null && fromCatalogue.isNotEmpty()) return fromCatalogue
        if (sub != null) BUILTIN["$name $sub"]?.let { return it }
        return BUILTIN[name] ?: DEFAULT_SLOTS
    }

    /**
     * The form of [raw] to record for a slot of this kind, or null when it must not be recorded — which also
     * ends the key, per this object's "stop rather than skip" rule.
     *
     * The shape tests matter as much as the slot kinds do. A slot the catalogue does not describe is assumed
     * to hold an identifier, and a value that does not *look* like one is far more likely to be prose that
     * landed in an argument position than a warp nobody has heard of. Refusing it costs a suggestion that
     * would probably have been wrong anyway, and buys the guarantee that free text cannot reach the model
     * through a command the catalogue has never seen.
     */
    private fun learnableValue(raw: String, kind: SlotKind, learnPlayerNames: Boolean): String? = when (kind) {
        SlotKind.TEXT -> null
        // Case-folded, because Hypixel treats `/warp Hub` and `/warp hub` as one command and the model must
        // too — otherwise a stray capital splits a habit in half.
        SlotKind.ENUM -> raw.lowercase(Locale.ROOT).takeIf { IDENTIFIER.matches(it) }
        // Kept exactly as typed: a player name is someone's identity, and folding it would render it wrong in
        // the very suggestion it exists to produce.
        SlotKind.PLAYER -> if (learnPlayerNames) raw.takeIf { PLAYER_NAME.matches(it) } else null
    }

    private val WHITESPACE = Regex("\\s+")

    /** What an enumerable argument may look like: one token of identifier characters, and not an essay. */
    private val IDENTIFIER = Regex("^[a-z0-9_.:#\\-]{1,32}$")

    /** Minecraft's own rule for a name, which is also a decent filter against prose. */
    private val PLAYER_NAME = Regex("^[A-Za-z0-9_]{2,16}$")
}
