package net.trilleo.skyblock

import java.util.*

/**
 * The Skyblock date, the Skyblock clock, and whatever event is running — all read off the sidebar.
 *
 * A view over [Sidebar], like [SkyblockLocation], and held to the same standard: it recognises what it is sure
 * about and reports null for everything else.
 *
 * **Why this is worth reading at all.** Skyblock keeps its own calendar, and it moves fast — a Skyblock day is
 * twenty real minutes, a year is about two and a half real days — so it is a completely different signal from
 * the wall clock. What a player does at 3am *Skyblock* time (zealots, mobs, anything that needs darkness) has
 * nothing to do with what they do at 3am their own time, and both are worth knowing. The season is what makes
 * Spooky Festival and Jerry's Workshop predictable, and the event line is the single most direct statement of
 * intent anywhere on the sidebar: a Dark Auction countdown is very nearly the player announcing they are about
 * to warp to it.
 *
 * **Two parsing strategies, chosen per field.** The date and time lines get strict regular expressions,
 * because their formats — `Late Summer 14th`, `3:40am ☽` — have been stable for years and a strict pattern
 * that fails loudly to match is the right way to read a stable format. Events get a substring vocabulary
 * instead, the same technique [net.trilleo.suggest.context.ChatCues] uses on chat, because event lines are
 * decorated with timers and suffixes that vary per event and per Hypixel update. Matching known names inside
 * a line survives all of that, and an event nobody has listed simply is not detected — which costs one context
 * feature and breaks nothing.
 */
object SkyblockCalendar {

    /** The Skyblock month, lower-cased (`"late summer"`, `"winter"`), or null when unknown. */
    @Volatile
    var month: String? = null
        private set

    /** The day of the Skyblock month, 1–31, or 0 when unknown. */
    @Volatile
    var day: Int = 0
        private set

    /** The Skyblock hour on a 24-hour clock, or -1 when unknown. */
    @Volatile
    var hour: Int = -1
        private set

    /** The Skyblock minute, or -1 when unknown. */
    @Volatile
    var minute: Int = -1
        private set

    /** The running or imminent event, lower-cased (`"dark auction"`), or null when none is named. */
    @Volatile
    var event: String? = null
        private set

    /**
     * Whether it is daylight, from the sun/moon glyph Hypixel puts on the time line, or null when absent.
     *
     * Worth reading separately from the clock because it is Hypixel *stating* the answer rather than this
     * file inferring it. Any boundary picked from the hour is a guess about where Skyblock's night begins;
     * the glyph is the server's own view, and it stays right through any change to the day length.
     */
    @Volatile
    var daylight: Boolean? = null
        private set

    /**
     * Events Hypixel names on the sidebar, lower-cased.
     *
     * Chosen for the same property [net.trilleo.suggest.context.ChatCues]'s list is: each is something a
     * player *does something about*. An event that merely happens, however often it is announced, would raise
     * a tag on most lines and dilute the feature into noise.
     *
     * A name Hypixel has since changed simply stops matching, which costs one context feature on one event
     * and nothing else — so this list is safe to ship despite being another server's vocabulary. The
     * catalogue extends it without a code change through [installEvents].
     *
     * Declared before [events] rather than beside the other constants at the foot of the file, because an
     * object's property initialisers run in declaration order and [events] is seeded from it.
     */
    private val BUILTIN_EVENTS = listOf(
        "dark auction",
        "jacob's contest",
        "jacob's farming contest",
        "farming contest",
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
    )

    /** The active event vocabulary. Replaced wholesale by [installEvents]; never mutated in place. */
    @Volatile
    private var events: List<String> = BUILTIN_EVENTS.sortedByDescending { it.length }

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

    /**
     * The season, without the `early`/`late` qualifier (`"summer"`), or null.
     *
     * Separate from [month] because the two answer different questions and have very different sparsity:
     * twelve months is a lot to learn a habit against, four seasons is not, and most of what the calendar
     * actually predicts — the Spooky Festival being an autumn thing — lives at the coarser grain.
     */
    val season: String?
        get() = month?.substringAfterLast(' ')

    /**
     * Which part of the Skyblock day it is: `night`, `dawn` or `day`. Null when the time is unknown.
     *
     * Bucketed rather than exposed as the raw hour: twenty-four categories would be too sparse to learn a
     * habit from before the habit itself had decayed, and three is enough to carry the distinction that
     * actually drives behaviour — whether it is dark, which is when the mobs worth going out for exist.
     *
     * **The glyph decides, and the clock only refines.** [daylight] is Hypixel saying whether it is dark;
     * the hour is this file guessing at a boundary. So the sun and moon settle day against night, and the
     * hour is consulted only to carve the short dawn out of the daylight side — and as the fallback for the
     * whole question when the glyph is missing.
     */
    val phase: String?
        get() {
            val dark = daylight?.not() ?: darkByHour() ?: return null
            if (dark) return "night"
            return if (hour in DAWN_START until DAY_START) "dawn" else "day"
        }

    /** Whether the hour alone says it is dark, or null when the hour is unknown. The fallback for [phase]. */
    private fun darkByHour(): Boolean? =
        if (hour < 0) null else hour >= NIGHT_START || hour < DAWN_START

    /** Forgets everything. Called by [Sidebar]. */
    internal fun reset() {
        month = null
        day = 0
        hour = -1
        minute = -1
        event = null
        daylight = null
    }

    /**
     * Re-derives the calendar from a fresh set of sidebar lines. Called by [Sidebar] after each poll.
     *
     * Each field is resolved independently and independently allowed to fail: a Hypixel change that breaks the
     * time line must not also cost the season, which is read from a different line by a different pattern.
     */
    internal fun accept(lines: List<String>) {
        var foundDate = false
        var foundTime = false

        for (line in lines) {
            val folded = line.lowercase(Locale.ROOT)

            if (!foundDate) {
                DATE.find(folded)?.let { match ->
                    val qualifier = match.groupValues[1].trim()
                    val season = match.groupValues[2]
                    // Skyblock's middle month carries no qualifier at all; "mid" is accepted and folded away
                    // so a future rename to "Mid Summer" would not read as a thirteenth month.
                    month = if (qualifier.isEmpty() || qualifier == "mid") season else "$qualifier $season"
                    day = match.groupValues[3].toIntOrNull()?.takeIf { it in 1..31 } ?: 0
                    foundDate = true
                }
            }

            if (!foundTime) {
                TIME.find(folded)?.let { match ->
                    val raw = match.groupValues[1].toIntOrNull()
                    val minutes = match.groupValues[2].toIntOrNull()
                    val meridiem = match.groupValues[3]
                    if (raw != null && minutes != null && raw in 1..12 && minutes in 0..59) {
                        hour = to24Hour(raw, meridiem)
                        minute = minutes
                        // Read off the same line, since that is the only place the glyph appears — looking
                        // for it anywhere would find the moon in an event name or a player's chat.
                        daylight = when {
                            line.contains(SUN) -> true
                            line.contains(MOON) -> false
                            else -> null
                        }
                        foundTime = true
                    }
                }
            }
        }

        if (!foundDate) {
            month = null
            day = 0
        }
        if (!foundTime) {
            hour = -1
            minute = -1
            daylight = null
        }

        event = eventIn(lines)
    }

    /** The first known event named anywhere on the sidebar, or null. */
    private fun eventIn(lines: List<String>): String? {
        val vocabulary = events
        for (line in lines) {
            val folded = line.lowercase(Locale.ROOT)
            vocabulary.firstOrNull { folded.contains(it) }?.let { return it }
        }
        return null
    }

    private fun to24Hour(hour: Int, meridiem: String): Int = when {
        meridiem == "am" && hour == 12 -> 0
        meridiem == "pm" && hour != 12 -> hour + 12
        else -> hour
    }

    /**
     * `Late Summer 14th`, anchored so it cannot match a fragment of some other line.
     *
     * The qualifier is optional because Skyblock's middle month is bare — `Summer 14th`, between
     * `Early Summer` and `Late Summer`.
     */
    private val DATE = Regex("^(early |mid |late )?(spring|summer|autumn|winter) (\\d{1,2})(?:st|nd|rd|th)\\b")

    /** `3:40am ☽`, anchored to the start so a time inside some other line cannot be mistaken for the clock. */
    private val TIME = Regex("^(\\d{1,2}):(\\d{2})\\s*(am|pm)\\b")

    /** The glyphs Hypixel puts after the Skyblock time. */
    private const val SUN = '☀'
    private const val MOON = '☽'

    private const val DAWN_START = 5
    private const val DAY_START = 8

    /** Only reached when the glyph is missing; roughly where vanilla's own night begins. */
    private const val NIGHT_START = 20
}
