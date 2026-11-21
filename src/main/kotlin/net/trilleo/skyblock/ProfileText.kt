package net.trilleo.skyblock

import java.util.*

/**
 * The game modes a Skyblock profile can be in, with the names the trilleo.net API uses for them.
 *
 * Hypixel calls Stranded "island" in its own API; the site calls it `stranded`, and so does this.
 */
enum class SkyblockGameMode(val apiName: String) {
    CLASSIC("classic"),
    IRONMAN("ironman"),
    STRANDED("stranded"),
    BINGO("bingo"),
}

/**
 * The pure text half of [SkyblockProfile]: every line Hypixel writes about the current profile, and what it
 * says. Kept free of Minecraft types so it can be tested without a running game.
 *
 * Every input is a line already passed through [net.trilleo.util.TextClean.strip], so colour codes and
 * Hypixel's invisible padding are gone and only the visible text is left.
 */
object ProfileText {

    /** Hypixel's dashed UUID form; the dashes are optional so an undashed one reads too. */
    private val PROFILE_ID = Regex(
        """^Profile ID: ([0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12})$""",
    )

    /** Sent when the player joins Skyblock, and again on every server change inside it. */
    private val PLAYING_ON = Regex("""^You are playing on profile: (.+)$""")

    /** Sent when the player switches profile with the profile menu. */
    private val CHANGED_TO = Regex("""^Your profile was changed to: (.+)$""")

    /** The player list's profile widget: `Profile: Apple ♲`. */
    private val TAB_PROFILE = Regex("""^Profile: ([A-Za-z0-9_' -]+?)\s*([♲☀Ⓑ].*)?$""")

    /** Co-op profiles carry this after their name in both chat messages. */
    private const val COOP_SUFFIX = "(Co-op)"

    private const val IRONMAN = '♲'
    private const val STRANDED = '☀'
    private const val BINGO = 'Ⓑ'

    /**
     * The profile ID on a `Profile ID: …` line, as 32 lowercase hex characters with no dashes, or null when
     * [line] is anything else. The API accepts either form; one canonical form keeps two reads of the same
     * profile comparing equal.
     */
    fun profileId(line: String): String? =
        PROFILE_ID.matchEntire(line.trim())?.groupValues?.get(1)?.replace("-", "")?.lowercase(Locale.ROOT)

    /** Which of the two profile chat messages [line] is, if either. */
    enum class NameSource { JOINED, CHANGED }

    /**
     * The profile name in a "playing on" or "changed to" message, and which one it was, or null for any other
     * line. The co-op suffix and any game-mode symbol after the name are removed.
     */
    fun profileNameFromChat(line: String): Pair<String, NameSource>? {
        val text = line.trim()
        PLAYING_ON.matchEntire(text)?.let { m -> cleanName(m.groupValues[1])?.let { return it to NameSource.JOINED } }
        CHANGED_TO.matchEntire(text)?.let { m -> cleanName(m.groupValues[1])?.let { return it to NameSource.CHANGED } }
        return null
    }

    /** The name and game mode on the player list's `Profile:` line, or null when [line] is not that line. */
    fun tabProfile(line: String): Pair<String, SkyblockGameMode>? {
        val m = TAB_PROFILE.matchEntire(line.trim()) ?: return null
        val name = m.groupValues[1].trim().takeIf { it.isNotEmpty() } ?: return null
        return name to modeFromSymbols(m.groupValues[2])
    }

    /**
     * The game mode the sidebar shows, or null when it cannot tell.
     *
     * The title carries the mode as a symbol beside `SKYBLOCK`, and the lines below it repeat it as text
     * (`♲ Ironman`, `☀ Stranded`, `Ⓑ Bingo`). A title saying `GUEST` means the player is visiting someone
     * else's island, where the sidebar describes the host's profile rather than theirs — that is "cannot
     * tell", not "classic".
     */
    fun gameMode(title: String, lines: List<String>): SkyblockGameMode? {
        if (!title.uppercase(Locale.ROOT).contains("SKYBLOCK")) return null
        if (title.uppercase(Locale.ROOT).contains("GUEST")) return null
        val fromTitle = modeFromSymbols(title)
        if (fromTitle != SkyblockGameMode.CLASSIC) return fromTitle
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed == "$IRONMAN Ironman" -> return SkyblockGameMode.IRONMAN
                trimmed == "$STRANDED Stranded" -> return SkyblockGameMode.STRANDED
                trimmed.startsWith("$BINGO Bingo") -> return SkyblockGameMode.BINGO
            }
        }
        return SkyblockGameMode.CLASSIC
    }

    private fun modeFromSymbols(text: String): SkyblockGameMode = when {
        text.indexOf(IRONMAN) >= 0 -> SkyblockGameMode.IRONMAN
        text.indexOf(STRANDED) >= 0 -> SkyblockGameMode.STRANDED
        text.indexOf(BINGO) >= 0 -> SkyblockGameMode.BINGO
        else -> SkyblockGameMode.CLASSIC
    }

    /** Removes the co-op suffix and any trailing game-mode symbol; null when nothing is left. */
    private fun cleanName(raw: String): String? {
        var name = raw.trim()
        if (name.endsWith(COOP_SUFFIX, ignoreCase = true)) name = name.dropLast(COOP_SUFFIX.length).trim()
        name = name.trimEnd(IRONMAN, STRANDED, BINGO, ' ')
        return name.takeIf { it.isNotEmpty() }
    }
}
