package net.trilleo.skyblock

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.trilleo.mixin.BossHealthOverlayAccessor
import net.trilleo.skyblock.SkyblockEvents.BUILTIN_EVENTS
import net.trilleo.skyblock.SkyblockEvents.SOON_WINDOW_MS
import net.trilleo.skyblock.SkyblockEvents.best
import net.trilleo.skyblock.SkyblockEvents.events
import net.trilleo.util.TextClean
import java.util.*

/**
 * Which Skyblock events are running, gathered from every place Hypixel is willing to say so.
 *
 * **Why this is not just a scoreboard read.** It used to be, and that was the bug: the sidebar names whatever
 * the island the player is standing on wants to show, which is almost never the event. Standing in the Dwarven
 * Mines during Hoppity's Hunt, the sidebar says nothing about Hoppity's Hunt; standing anywhere during a
 * mining event, the sidebar says nothing about the mining event either. Hypixel does state both — just
 * elsewhere. SkyHanni and Skyblocker hit the same wall and answer it the same way, by reading every channel
 * rather than the most obvious one:
 *
 *  - **the player list** ([TabList]) carries an `Event:` widget on every island, naming the Skyblock-wide
 *    event and how long is left — the single best source, and the one the sidebar was standing in for;
 *  - **the boss bar** is where a mining event lives (`2X POWDER`, `GOBLIN RAID`) and the only place it lives;
 *  - **the scoreboard sidebar** ([Sidebar]) still names island events with their countdowns, so it stays;
 *  - **chat** shouts an event's start and end, which is the earliest anything knows about it.
 *
 * **Every source is a claim, not an answer.** A source says "I can see this event"; this file decides what to
 * believe. Claims are held per source and replaced wholesale each poll, so a source that stops naming an event
 * withdraws its claim on its own — but a source that has gone *silent* (an empty player list mid-transfer, a
 * boss bar that glitched away) withdraws nothing, because silence is not evidence. Everything ages out through
 * [Source.ttlMs] regardless, so no claim can outlive its source by more than a few seconds and nothing here
 * can get permanently stuck on an event that ended.
 *
 * **Names are canonicalised, not filtered.** The event vocabulary — [BUILTIN_EVENTS] plus whatever the command
 * catalogue adds — is no longer the gate it was when the sidebar was the only source; a widget that says
 * `Event: Some Brand New Festival` is Hypixel naming an event outright, and refusing to believe it because the
 * name is not on a list would be exactly the old failure. So the vocabulary now serves to *stabilise* known
 * names (whatever decoration surrounds `dark auction` on a line, the claim reads `dark auction`, which is what
 * the ranker has been learning against all along) and an unrecognised name is taken as given, lower-cased.
 *
 * The consumer is [net.trilleo.suggest.context.ContextSources], which reads [current] as one context feature —
 * so like every other source in that snapshot, this reports null for "cannot tell" and never guesses.
 */
object SkyblockEvents {

    /**
     * Where a claim came from, in preference order — most specific first.
     *
     * The order only settles ties between events that state no countdown; an event that says how long is left
     * is ranked on that instead, since urgency predicts what the player is about to do better than which
     * channel happened to mention it.
     *
     * The TTLs are the real work here. A polled source is re-read every second, so a short life is enough to
     * bridge a dropped poll or a world change without letting a finished event linger. Chat is not polled at
     * all — it fires once, when the event starts — so its claim has to carry itself, and does so for as long
     * as a short Skyblock event tends to run.
     */
    private enum class Source(val ttlMs: Long) {
        BOSS_BAR(POLL_GRACE_MS),
        TAB_LIST(POLL_GRACE_MS),
        SIDEBAR(POLL_GRACE_MS),
        CHAT(CHAT_TTL_MS),
    }

    /**
     * One source's sighting of one event.
     *
     * [endsAtMs] and [startsAtMs] are both optional because most channels state neither: the boss bar counts
     * down, the player list says `Ends In` or `Starts In`, the sidebar sometimes appends a timer, and chat
     * says only that something happened. A claim with neither is still a claim — it simply ranks behind one
     * that knows how urgent it is.
     */
    private class Claim(
        val name: String,
        val source: Source,
        val seenAtMs: Long,
        val endsAtMs: Long?,
        val startsAtMs: Long?,
    ) {
        /**
         * Whether this claim is too old to believe.
         *
         * Two ways to go stale, and both matter: the source has not repeated itself within its TTL, or the
         * event's own countdown has run out. The second is what stops a claim seen a second before the end
         * from outliving the event by the whole grace window.
         */
        fun expired(now: Long): Boolean =
            now - seenAtMs > source.ttlMs || (endsAtMs != null && now > endsAtMs)

        /** Whether the event is under way, as opposed to one the player list says is still to come. */
        fun running(now: Long): Boolean = startsAtMs == null || startsAtMs <= now
    }

    /**
     * Events worth naming, lower-cased, used to canonicalise a name and to recognise one inside a decorated
     * line.
     *
     * Two kinds live here. The Skyblock-wide events are the ones the calendar makes predictable and a player
     * plans around; the mining events are the ones Hypixel only ever shouts (`2X POWDER STARTED!`) or draws on
     * a boss bar, where there is no `Event:` label to read a name out of and a known name is the only way in.
     *
     * A name Hypixel has since changed simply stops matching, which now costs only the *stable* form of that
     * one name — the event is still detected through the widget that named it. The catalogue extends this
     * without a code change through [installEvents].
     *
     * Declared before [events] rather than beside the other constants at the foot of the file, because an
     * object's property initialisers run in declaration order and [events] is seeded from it.
     */
    private val BUILTIN_EVENTS = listOf(
        // Skyblock-wide and island events.
        "dark auction",
        "jacob's contest",
        "jacob's farming contest",
        "farming contest",
        "agatha's contest",
        "miria's contest",
        "spooky festival",
        "great spook",
        "new year celebration",
        "traveling zoo",
        "travelling zoo",
        "jerry's workshop",
        "season of jerry",
        "winter island",
        "cult of the fallen star",
        "fallen star cult",
        "mining fiesta",
        "fishing festival",
        "mayor election",
        "election over",
        "bingo",
        "hoppity's hunt",
        "carnival",
        "bank interest",
        // Mining events, which live on the boss bar and in chat and are named nowhere else.
        "2x powder",
        "double powder",
        "goblin raid",
        "raffle",
        "mithril gourmand",
        "better together",
        "gone with the wind",
    )

    /** The active event vocabulary. Replaced wholesale by [installEvents]; never mutated in place. */
    @Volatile
    private var events: List<String> = BUILTIN_EVENTS.sortedByDescending { it.length }

    /**
     * Live claims, keyed by the source that made them.
     *
     * Replaced wholesale rather than mutated for the same reason [Sidebar.lines] is: a reader must see one
     * source's complete view rather than a half-written one, and the maps involved are a handful of entries.
     */
    @Volatile
    private var claims: Map<Source, List<Claim>> = emptyMap()

    private var ticksUntilPoll = 0

    /**
     * The event that best describes right now, lower-cased (`"dark auction"`), or null when nothing is
     * running.
     *
     * Running events come first, the most urgent first — a mining event with four minutes left says more about
     * what the player is about to type than a month-long festival does. An event that has not started is only
     * offered once it is within [SOON_WINDOW_MS], because a countdown to something a day away is not evidence
     * of anything, while a Dark Auction five minutes out very nearly is.
     *
     * Computed on read rather than cached, so it cannot be stale: every claim is checked against the clock as
     * it is used, and a missed tick can only cost a refresh, never leave a finished event standing.
     */
    val current: String?
        get() = runCatching { best(System.currentTimeMillis()) }.getOrNull()

    /**
     * Adds the catalogue's event names on top of the built-in ones. Idempotent — always rebuilt from
     * [BUILTIN_EVENTS], so loading the catalogue twice cannot double the list.
     *
     * Longer names are matched first, so `"cult of the fallen star"` cannot be pre-empted by a shorter entry
     * that happens to be a substring of the same line.
     */
    fun installEvents(extra: List<String>) {
        val all = (BUILTIN_EVENTS + extra.map { it.trim().lowercase(Locale.ROOT) })
            .filter { it.isNotEmpty() }
            .distinct()
        events = all.sortedByDescending { it.length }
    }

    /** Forgets every claim. Called by [Sidebar] when the sidebar says this is no longer Skyblock. */
    internal fun reset() {
        if (claims.isNotEmpty()) claims = emptyMap()
    }

    // ---- the sources -------------------------------------------------------------------------------------

    /**
     * Reads the scoreboard sidebar. Called by [Sidebar] after each poll, only on Skyblock.
     *
     * A vocabulary sweep, as it always was, because sidebar event lines are decorated with timers and suffixes
     * that vary per event and per Hypixel update — matching a known name inside the line survives all of that.
     * What is new is that every named line is claimed rather than only the first, and that a countdown found
     * after the name is kept: the sidebar does not say whether it counts to the start or to the end, so it is
     * recorded as time remaining, which is what it means on every line that has one today.
     */
    internal fun acceptSidebar(lines: List<String>) {
        if (lines.isEmpty()) return
        val now = System.currentTimeMillis()
        val found = ArrayList<Claim>(MAX_CLAIMS)
        for (line in lines) {
            if (found.size >= MAX_CLAIMS) break
            val folded = line.lowercase(Locale.ROOT)
            val name = events.firstOrNull { folded.contains(it) } ?: continue
            if (found.any { it.name == name }) continue
            val remaining = durationMs(folded.substringAfter(name))
            found += Claim(name, Source.SIDEBAR, now, remaining?.let { now + it }, null)
        }
        replace(Source.SIDEBAR, found)
    }

    /**
     * Reads the player list's widgets. Called by [TabList] after each poll.
     *
     * Two shapes, both of them Hypixel labelling a line for us:
     *
     *  - `Event: Hoppity's Hunt` — the Skyblock-wide event, on every island, with ` Ends In: 26h` beneath it.
     *    Also `Mining Event: 2x Powder`, which is the same shape for the current island's mining event.
     *  - `Jacob's Contest:` and its foraging and fishing counterparts, whose own lines list the crops. A
     *    contest widget is claimed **only** when a countdown is found with it, because the widget is also how
     *    Hypixel advertises the *next* contest and its bare presence does not say which of the two this is.
     *
     * An empty list is silence, not an absence of events: the player list is empty for a moment after a world
     * change, and dropping every claim on that would make the event flicker off on every island hop.
     */
    internal fun acceptTabList(lines: List<String>) {
        if (lines.isEmpty()) return
        val now = System.currentTimeMillis()
        val found = ArrayList<Claim>(MAX_CLAIMS)
        for ((index, line) in lines.withIndex()) {
            if (found.size >= MAX_CLAIMS) break

            val event = EVENT_WIDGET.find(line.trim())
            if (event != null) {
                val name = canonical(event.groupValues[2]) ?: continue
                if (found.any { it.name == name }) continue
                // No countdown is still an event: the widget naming it is the claim.
                found += timed(name, Source.TAB_LIST, now, lines, index)
                    ?: Claim(name, Source.TAB_LIST, now, null, null)
                continue
            }

            val contest = CONTEST_WIDGET.find(line.trim()) ?: continue
            val name = canonical(contest.groupValues[1]) ?: continue
            if (found.any { it.name == name }) continue
            // Timer required: see the note above on advertised contests.
            found += timed(name, Source.TAB_LIST, now, lines, index) ?: continue
        }
        replace(Source.TAB_LIST, found)
    }

    /**
     * Reads the boss bars. Called each client tick from [net.trilleo.feature.Features].
     *
     * This is the only way to see a mining event, which is why it is worth a mixin. Hypixel writes the bar in
     * one of two shapes — an active event with the sub-area it is in, or a passive one that simply runs — and
     * both end in a `mm:ss` countdown:
     *
     * ```
     * EVENT GOBLIN RAID ACTIVE IN GOBLIN BURROWS for 02:00
     * PASSIVE EVENT 2X POWDER RUNNING FOR 12:34
     * ```
     *
     * A bar that does not match either shape is still swept for a known event name, because bar text is
     * Hypixel's own — there is no player-supplied text here to be careful about.
     *
     * No bars at all withdraws nothing, for the same reason an empty player list does not: a boss bar that
     * vanishes is as likely to be a client-side glitch as an event ending, and the TTL settles it either way.
     */
    fun tick(client: Minecraft) {
        if (--ticksUntilPoll > 0) return
        ticksUntilPoll = POLL_INTERVAL_TICKS
        if (!Sidebar.onSkyblock) return
        runCatching { readBossBars(client) }
    }

    private fun readBossBars(client: Minecraft) {
        val bars = (client.gui.bossOverlay as BossHealthOverlayAccessor).`hex$events`()
        if (bars.isEmpty()) return

        val now = System.currentTimeMillis()
        val found = ArrayList<Claim>(MAX_CLAIMS)
        for (bar in bars.values) {
            if (found.size >= MAX_CLAIMS) break
            val text = TextClean.strip(bar.name.string)
            if (text.isEmpty()) continue

            val match = BOSS_BAR_EVENT.find(text)
            // A bar Hypixel has labelled `EVENT` names its own event, whatever that name turns out to be. Any
            // other bar counts only if it names an event this already knows — most boss bars are bosses, and
            // "Revenant Horror IV" is not an event however loudly it is drawn.
            val name = if (match != null) {
                canonical(match.groupValues[1])
            } else {
                val folded = text.lowercase(Locale.ROOT)
                events.firstOrNull { folded.contains(it) }
            } ?: continue
            if (found.any { it.name == name }) continue
            val remaining = match?.groupValues?.get(2)?.let { durationMs(it) }
            found += Claim(name, Source.BOSS_BAR, now, remaining?.let { now + it }, null)
        }
        replace(Source.BOSS_BAR, found)
    }

    /**
     * Reads one incoming chat line. Called from [net.trilleo.feature.Features] ahead of the feature fan-out,
     * and never swallows anything.
     *
     * Deliberately narrow: a known event name in a line that also shouts `STARTED!` or `ENDED!`. Hypixel's
     * event broadcasts look exactly like that (`§b§l2X POWDER STARTED!`) and ordinary sentences do not, so the
     * exclamation mark is doing real work — it is the difference between a broadcast and a player typing about
     * an event in party chat. An `ENDED!` withdraws the matching claim immediately rather than waiting for it
     * to age out, which is the whole reason to read the end as well as the start.
     */
    fun onChatReceive(message: Component) {
        runCatching {
            if (!Sidebar.onSkyblock) return@runCatching
            val raw = message.string
            // Same cap and same reasoning as ChatCues: the interesting part of a line is at the front.
            val line = TextClean.strip(if (raw.length > MAX_INPUT) raw.substring(0, MAX_INPUT) else raw)
            if (line.isEmpty()) return@runCatching

            val folded = line.lowercase(Locale.ROOT)
            val started = folded.contains("started!")
            val ended = folded.contains("ended!")
            if (!started && !ended) return@runCatching

            val name = events.firstOrNull { folded.contains(it) } ?: return@runCatching
            val now = System.currentTimeMillis()
            val kept = claims[Source.CHAT].orEmpty().filter { it.name != name && !it.expired(now) }
            replace(Source.CHAT, if (started) kept + Claim(name, Source.CHAT, now, null, null) else kept)
        }
    }

    // ---- merging -----------------------------------------------------------------------------------------

    /** Swaps in one source's current view of the world, dropping the claims it no longer makes. */
    private fun replace(source: Source, found: List<Claim>) {
        claims = if (found.isEmpty()) claims - source else claims + (source to found)
    }

    /**
     * The best single event at [now], or null.
     *
     * One claim survives per event name — the one that knows most about it, since a claim with a countdown can
     * be ranked and one without cannot. Running events then sort by how little time is left, and events still
     * to come are considered only once they are imminent.
     */
    private fun best(now: Long): String? {
        val live = claims.values.flatten().filter { !it.expired(now) }
        if (live.isEmpty()) return null

        val distinct = live.groupBy { it.name }.values.map { group -> group.minWith(BY_CONFIDENCE) }
        val (running, upcoming) = distinct.partition { it.running(now) }

        running.minWithOrNull(BY_URGENCY)?.let { return it.name }
        return upcoming
            .filter { it.startsAtMs != null && it.startsAtMs - now <= SOON_WINDOW_MS }
            .minByOrNull { it.startsAtMs ?: Long.MAX_VALUE }
            ?.name
    }

    /** Which of two claims for the same event to keep: the one that can be ranked, then the closer source. */
    private val BY_CONFIDENCE: Comparator<Claim> = compareBy(
        { if (it.endsAtMs == null) 1 else 0 },
        { it.source.ordinal },
    )

    /** Which running event matters most: the one ending soonest, then the closer source. */
    private val BY_URGENCY: Comparator<Claim> = compareBy(
        { it.endsAtMs ?: Long.MAX_VALUE },
        { it.source.ordinal },
    )

    // ---- reading the text --------------------------------------------------------------------------------

    /**
     * The claim for [name] built from a countdown near the widget line at [index], or null when there is none.
     *
     * Hypixel writes the countdown on the line under the label, so the search is a couple of lines wide and
     * stops at the blank entry that separates one widget from the next. `Starts In` beyond
     * [SOON_WINDOW_MS] is dropped outright rather than stored: an event a day out is not a fact about now, and
     * keeping it would only give [best] something it must remember to ignore.
     */
    private fun timed(name: String, source: Source, now: Long, lines: List<String>, index: Int): Claim? {
        for (offset in 1..TIMER_WINDOW) {
            val line = lines.getOrNull(index + offset)?.trim() ?: return null
            if (line.isEmpty()) return null
            // Another label ends this widget. Without this, an event with no countdown of its own would
            // quietly adopt the next widget's — reading `Mining Event: 2x Powder`'s four minutes as the time
            // left on a month-long festival listed above it.
            if (EVENT_WIDGET.containsMatchIn(line) || CONTEST_WIDGET.containsMatchIn(line)) return null
            val match = TIMER.find(line) ?: continue
            val millis = durationMs(match.groupValues[2]) ?: continue
            val starting = match.groupValues[1].lowercase(Locale.ROOT) == "starts"
            if (starting && millis > SOON_WINDOW_MS) return null
            return if (starting) {
                Claim(name, source, now, null, now + millis)
            } else {
                Claim(name, source, now, now + millis, null)
            }
        }
        return null
    }

    /**
     * An event name reduced to the form the rest of the mod stores, or null when the text is not a name.
     *
     * A known name wins whatever surrounds it, so every source agrees on the string the ranker has been
     * learning against. Anything else is taken at face value — Hypixel labelled it an event, and this file is
     * in no position to argue — after the checks that keep obvious non-answers (`None`, `Not announced`) and
     * anything that does not read like a name out of the context features.
     */
    private fun canonical(raw: String): String? {
        val cleaned = TextClean.strip(raw)
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE, " ")
            .trim()
            .trim { it in TRIMMED_PUNCTUATION }
        if (cleaned.length !in MIN_NAME..MAX_NAME) return null
        if (NON_EVENTS.any { cleaned == it || cleaned.startsWith("$it ") }) return null
        events.firstOrNull { cleaned.contains(it) }?.let { return it }
        return cleaned.takeIf { text -> text.all { it.isLetterOrDigit() || it in NAME_PUNCTUATION } }
    }

    /**
     * A Hypixel duration in milliseconds, or null when there is none to read.
     *
     * Both of the forms Hypixel uses are accepted, because both turn up in the places this reads: the unit
     * form (`27h`, `4m 20s`, `1d 3h`) on the player list and the sidebar, and the clock form (`02:00`,
     * `1:02:03`) on the boss bar. Anything else is no answer rather than a wrong one.
     */
    private fun durationMs(raw: String): Long? {
        val text = raw.trim().lowercase(Locale.ROOT)
        if (text.isEmpty()) return null

        if (CLOCK.matches(text)) {
            var seconds = 0L
            for (part in text.split(':')) {
                val value = part.toLongOrNull() ?: return null
                seconds = seconds * 60 + value
            }
            return seconds * 1000
        }

        var seconds = 0L
        var read = false
        for (match in UNITS.findAll(text)) {
            val value = match.groupValues[1].toLongOrNull() ?: continue
            val factor = when (match.groupValues[2]) {
                "d" -> 86_400L
                "h" -> 3_600L
                "m" -> 60L
                else -> 1L
            }
            seconds += value * factor
            read = true
        }
        return if (read) seconds * 1000 else null
    }

    /** `Event: Hoppity's Hunt`, `Mining Event: 2x Powder` — the label Hypixel puts on a player-list widget. */
    private val EVENT_WIDGET = Regex("^(mining )?event:\\s*(.+)$", RegexOption.IGNORE_CASE)

    /** `Jacob's Contest:` and the foraging and fishing contests that share its shape. */
    private val CONTEST_WIDGET = Regex("^((?:jacob|agatha|miria)'s contest):.*$", RegexOption.IGNORE_CASE)

    /** ` Ends In: 26h`, ` Starts In: 7h` — a widget's countdown, on the line under its label. */
    private val TIMER = Regex("^(ends|starts)\\s+in:?\\s*(.+)$", RegexOption.IGNORE_CASE)

    /** Hypixel's two boss-bar shapes for a mining event, colour codes already stripped. */
    private val BOSS_BAR_EVENT = Regex(
        "^(?:passive )?event (.+?) (?:active in .+? for|running for)\\s*([\\d:]+)$",
        RegexOption.IGNORE_CASE,
    )

    private val CLOCK = Regex("^\\d{1,2}(?::\\d{2}){1,2}$")

    private val UNITS = Regex("(\\d+)\\s*([dhms])")

    private val WHITESPACE = Regex("\\s+")

    /** Values that mean "no event" wherever Hypixel puts them in a slot that would otherwise name one. */
    private val NON_EVENTS = listOf("none", "no event", "not announced", "unknown", "n/a", "???", "nothing", "over")

    /** What a name may contain beyond letters and digits. Anything else is not a name Hypixel wrote. */
    private const val NAME_PUNCTUATION = " '&-."

    /** Decoration to shave off either end of a name before it is stored. */
    private const val TRIMMED_PUNCTUATION = " !.,:;-"

    private const val MIN_NAME = 3
    private const val MAX_NAME = 40

    /** How many claims one source may make in a single read. A guard, not a limit anything reaches. */
    private const val MAX_CLAIMS = 6

    /** Same cap and same reasoning as [net.trilleo.suggest.context.ChatCues]. */
    private const val MAX_INPUT = 256

    /** Lines below a widget's label to look for its countdown in. Hypixel puts it on the first. */
    private const val TIMER_WINDOW = 2

    /**
     * How long a polled claim outlives its last sighting: long enough to ride out a world change or a dropped
     * poll, short enough that an event which ended between two polls is gone within a few seconds.
     */
    private const val POLL_GRACE_MS = 15_000L

    /**
     * How long a `STARTED!` in chat is believed on its own — about as long as the shortest Skyblock events
     * run. A longer one is picked up again by the boss bar or the player list on the very next poll, so this
     * only has to cover the gap where chat is the only thing that knows.
     */
    private const val CHAT_TTL_MS = 5 * 60_000L

    /**
     * How close an event has to be before "not started yet" counts as the answer. Ten minutes is about the
     * distance at which a player starts moving towards an event rather than noting it for later — the Dark
     * Auction countdown being the case that matters.
     */
    private const val SOON_WINDOW_MS = 10 * 60_000L

    /** One second, matching [Sidebar] and [TabList]: nothing read here moves faster than a countdown. */
    private const val POLL_INTERVAL_TICKS = 20
}
