package net.trilleo.suggest.model

import com.google.gson.reflect.TypeToken
import net.trilleo.config.JsonConfig
import net.trilleo.skyblock.SkyblockCalendar
import net.trilleo.suggest.context.ChatCues
import net.trilleo.suggest.context.ContextSources
import org.slf4j.LoggerFactory
import java.io.InputStreamReader
import java.util.*

/**
 * One command the catalogue describes.
 *
 * @property name the command word, without its slash.
 * @property slots what each argument position holds, by name (`"enum"`, `"player"`, `"text"`). The last
 *   entry repeats for every remaining position, so `["player", "text"]` is "one name then a message".
 * @property values known values for the **first** argument only — warp destinations, subcommands. Deeper
 *   positions are left to what the player actually types, because the useful values there are personal
 *   (which players you message, which items you look up) rather than universal.
 * @property prior how strongly to suggest this before the player has ever used it, from 0 to 1.
 * @property bare whether the command on its own is a thing anyone runs. False for the likes of `/warp`,
 *   which is never useful without a destination and would otherwise sit at the top of the list doing nothing.
 */
class CatalogCommand {
    var name: String = ""
    var slots: MutableList<String> = mutableListOf()
    var values: MutableList<String> = mutableListOf()
    var prior: Double = 0.5
    var bare: Boolean = true
}

/** A slot layout for a command *and its first argument*, for subcommands that change the shape. */
class CatalogSubcommand {
    var name: String = ""
    var sub: String = ""
    var slots: MutableList<String> = mutableListOf()
}

/** A chat cue: the tag to raise and the phrases that raise it. */
class CatalogCue {
    var tag: String = ""
    var phrases: MutableList<String> = mutableListOf()
}

class CatalogFile {
    var version: Int = 1
    var commands: MutableList<CatalogCommand> = mutableListOf()
    var subcommands: MutableList<CatalogSubcommand> = mutableListOf()

    /**
     * Broad item classes, as `kind -> substrings matched against the uppercase Skyblock id`.
     *
     * Substrings rather than a list of ids, because Skyblock has hundreds of pickaxes and will have more next
     * month. `"PICKAXE"` and `"DRILL"` between them cover every mining tool that exists or will exist, in two
     * entries, with nothing to maintain when a new one ships.
     */
    var itemKinds: MutableMap<String, MutableList<String>> = mutableMapOf()

    var cues: MutableList<CatalogCue> = mutableListOf()

    /**
     * Extra Skyblock event names to recognise on the scoreboard sidebar, on top of the built-in ones.
     *
     * Here rather than in code because Hypixel adds and renames events far faster than a mod ships, and an
     * event name is exactly the kind of thing that should be a data edit.
     */
    var events: MutableList<String> = mutableListOf()
}

/**
 * The bundled knowledge of what Hypixel Skyblock's commands are, so day one is not blank.
 *
 * **Everything here is a weak prior and nothing here is a fact the model defends.** A catalogue entry the
 * player never uses decays out of the shortlist; one they use becomes a real learned key with a real count,
 * at which point the catalogue's opinion is a rounding error against their own behaviour. That is what makes
 * it safe to ship a list that is inevitably a little wrong: Hypixel renames warps and adds commands, and the
 * cost of a stale entry is one suggestion nobody picks rather than a wrong answer anybody is stuck with. The
 * **Command Suggestions** tab can switch the priors off entirely for anyone who would rather start clean.
 *
 * Read from the **classpath** rather than through the resource manager, for the reasons
 * [net.trilleo.reminder.preset.ReminderPresets] sets out: it is needed at feature init, before the first
 * resource reload, and a resource pack must not be able to override it.
 *
 * Installing itself into [CommandParser], [ContextSources] and [ChatCues] through their hooks — rather than
 * having them import this — is what keeps those three usable before any asset has loaded, and testable
 * without one.
 */
object CommandCatalog {
    private val LOGGER = LoggerFactory.getLogger("hex/suggest")

    private const val RESOURCE = "/assets/hex/suggest/commands.json"

    /** Every line the catalogue suggests, with its prior. Built once at load. */
    private var priors: Map<String, Double> = emptyMap()

    /** Slot layouts, keyed by `name` and by `"name sub"`. */
    private var slots: Map<String, List<SlotKind>> = emptyMap()

    /** `substring -> kind`, scanned linearly; a handful of entries, checked once per context snapshot. */
    private var itemKinds: List<Pair<String, String>> = emptyList()

    val size: Int get() = priors.size

    /**
     * Reads the catalogue and installs it into everything that consumes it.
     *
     * A failure here is logged and otherwise ignored. The player's own model is the part that matters and it
     * lives in a different file entirely; a broken catalogue costs cold-start suggestions and nothing else.
     */
    fun load() {
        val file = runCatching {
            val stream = javaClass.getResourceAsStream(RESOURCE) ?: return@runCatching null
            InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                JsonConfig.GSON.fromJson<CatalogFile>(reader, object : TypeToken<CatalogFile>() {}.type)
            }
        }.onFailure {
            LOGGER.error("Failed to read the bundled command catalogue", it)
        }.getOrNull()

        if (file == null) {
            LOGGER.warn("No bundled command catalogue found; suggestions will start from scratch")
            return
        }
        normalize(file)

        priors = buildPriors(file)
        slots = buildSlots(file)
        itemKinds = buildItemKinds(file)

        CommandParser.slotProvider = { name, sub ->
            (if (sub != null) slots["$name $sub"] else null) ?: slots[name]
        }
        ContextSources.kindProvider = ::kindOf
        ChatCues.install(file.cues.filter { it.tag.isNotBlank() && it.phrases.isNotEmpty() }
            .map { ChatCues.Cue(it.tag, it.phrases.map { phrase -> phrase.lowercase(Locale.ROOT) }) })
        SkyblockCalendar.installEvents(file.events)

        Ranker.cataloguePrior = { key -> priors[key] ?: 0.0 }
        Ranker.catalogueKeys = { priors.keys }

        LOGGER.info("Loaded {} catalogued command line(s)", priors.size)
    }

    /**
     * Fills in the collections GSON left null for an entry that omitted them.
     *
     * The same repair every config in this mod does, and needed for the same reason: GSON builds objects
     * reflectively and never runs a Kotlin property initialiser for a key that was absent from the JSON, so
     * an entry as short as `{"name": "hub"}` arrives with three null lists.
     */
    private fun normalize(file: CatalogFile) {
        @Suppress("SENSELESS_COMPARISON")
        if (file.commands == null) file.commands = mutableListOf()
        @Suppress("SENSELESS_COMPARISON")
        if (file.subcommands == null) file.subcommands = mutableListOf()
        @Suppress("SENSELESS_COMPARISON")
        if (file.itemKinds == null) file.itemKinds = mutableMapOf()
        @Suppress("SENSELESS_COMPARISON")
        if (file.cues == null) file.cues = mutableListOf()
        @Suppress("SENSELESS_COMPARISON")
        if (file.events == null) file.events = mutableListOf()

        file.commands.forEach { command ->
            @Suppress("SENSELESS_COMPARISON")
            if (command.slots == null) command.slots = mutableListOf()
            @Suppress("SENSELESS_COMPARISON")
            if (command.values == null) command.values = mutableListOf()
            @Suppress("SENSELESS_COMPARISON")
            if (command.name == null) command.name = ""
        }
        file.subcommands.forEach { entry ->
            @Suppress("SENSELESS_COMPARISON")
            if (entry.slots == null) entry.slots = mutableListOf()
        }
        file.cues.forEach { cue ->
            @Suppress("SENSELESS_COMPARISON")
            if (cue.phrases == null) cue.phrases = mutableListOf()
        }
    }

    /**
     * Expands every command into the lines it suggests: the bare command, and one per known first argument.
     *
     * Expansions get a fraction of the command's own prior so that with nothing learned the bare `/party`
     * still leads its own subcommands — the player is more likely to be reaching for the command than for
     * any one particular thing to do with it, right up until they have shown otherwise.
     */
    private fun buildPriors(file: CatalogFile): Map<String, Double> {
        val out = HashMap<String, Double>()
        file.commands.forEach { command ->
            val name = command.name.trim().removePrefix("/").lowercase(Locale.ROOT)
            if (name.isEmpty()) return@forEach
            val prior = command.prior.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.5
            if (command.bare) out[name] = prior
            command.values.forEach { raw ->
                val value = raw.trim().lowercase(Locale.ROOT)
                if (value.isNotEmpty()) out["$name $value"] = prior * EXPANSION
            }
        }
        return out
    }

    private fun buildSlots(file: CatalogFile): Map<String, List<SlotKind>> {
        val out = HashMap<String, List<SlotKind>>()
        file.commands.forEach { command ->
            val name = command.name.trim().removePrefix("/").lowercase(Locale.ROOT)
            val kinds = command.slots.mapNotNull(::slotKind)
            if (name.isNotEmpty() && kinds.isNotEmpty()) out[name] = kinds
        }
        file.subcommands.forEach { entry ->
            val name = entry.name.trim().removePrefix("/").lowercase(Locale.ROOT)
            val sub = entry.sub.trim().lowercase(Locale.ROOT)
            val kinds = entry.slots.mapNotNull(::slotKind)
            if (name.isNotEmpty() && sub.isNotEmpty() && kinds.isNotEmpty()) out["$name $sub"] = kinds
        }
        return out
    }

    /**
     * A slot kind by name, or null when the catalogue names one this build does not know.
     *
     * Dropping the entry rather than guessing is the safe direction: an unrecognised layout falls back to
     * [CommandParser]'s built-in default, which learns one argument and treats everything after it as text.
     * Guessing `enum` for an unknown word could put a message body in the model.
     */
    private fun slotKind(raw: String): SlotKind? = when (raw.trim().lowercase(Locale.ROOT)) {
        "enum" -> SlotKind.ENUM
        "player" -> SlotKind.PLAYER
        "text" -> SlotKind.TEXT
        else -> null
    }

    private fun buildItemKinds(file: CatalogFile): List<Pair<String, String>> =
        file.itemKinds.flatMap { (kind, patterns) ->
            patterns.mapNotNull { pattern ->
                pattern.trim().uppercase(Locale.ROOT).takeIf { it.isNotEmpty() }?.let { it to kind }
            }
        }

    /** The broad class of a Skyblock item id, or null. First match wins, so order in the file matters. */
    private fun kindOf(id: String): String? {
        val folded = id.uppercase(Locale.ROOT)
        return itemKinds.firstOrNull { folded.contains(it.first) }?.second
    }

    /** How much of a command's prior each of its known arguments inherits. */
    private const val EXPANSION = 0.6
}
