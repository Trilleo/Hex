package net.trilleo.chat

import net.trilleo.chat.model.ChatChannel

/**
 * Works out which channel a chat line arrived on, from the tag Hypixel puts at the front of it.
 *
 * ### Why the tags are not translated
 *
 * `Party > `, `Guild > ` and the rest are matched against Hypixel's own English output, not against anything this
 * mod writes, so they stay untranslated exactly as `private island` and `dwarven mines` do — see
 * `CLAUDE.md`. A player running a Chinese client still receives `Party > ` from the server, and a translated
 * constant here would simply stop matching.
 *
 * ### Why the shapes are pinned down rather than guessed
 *
 * The tempting version of this is "a line with a colon in it is somebody talking", and it is wrong in a way that
 * matters: Hypixel's own system messages are full of colons (`Your new API key is: …`), and a rule scoped to
 * public chat would then fire on all of them. So a line only counts as somebody talking when what precedes the
 * colon has the shape of a player's name, optionally wrapped in a rank prefix and a guild tag — [SPEAKER]. A
 * line that fails that test is a broadcast, and reports as `null`: no channel at all, which only a
 * [ChatChannel.ANY] rule will take.
 *
 * The one thing this cannot see is a *lie*: nothing stops a player typing `Party > x: hi` in public chat. It does
 * not matter, because only the leading tag is ever read, and in public chat that text arrives behind the
 * speaker's own name.
 */
object ChatChannels {

    /**
     * A player's name as it reaches the client, with the two decorations Hypixel hangs on it: a rank prefix in
     * front (`[MVP+] `) and a guild tag behind (` [TAG]`), both optional.
     *
     * Anchored, and bounded at every step — a name is at most sixteen characters and a bracketed tag is short —
     * so this is linear on any input and needs none of the machinery
     * [net.trilleo.reminder.ChatMatcher] exists to provide. That is the whole reason it is a constant here
     * rather than something a player can type.
     */
    private val SPEAKER = Regex("""^(?:\[[^\[\]]{1,24}] )?\w{1,16}(?: \[[^\[\]]{1,24}])?: """)

    /** The tagged channels, longest-lived first. Order does not matter — no tag is a prefix of another. */
    private val TAGGED = listOf(
        "Party > " to ChatChannel.PARTY,
        "Guild > " to ChatChannel.GUILD,
        "Officer > " to ChatChannel.OFFICER,
        "Co-op > " to ChatChannel.COOP,
    )

    /** Hypixel's two directions of direct message. Both report as [ChatChannel.PRIVATE]. */
    private val DIRECT = listOf("From ", "To ")

    /**
     * The channel [line] arrived on, or null when it is a broadcast rather than somebody talking.
     *
     * [line] must already have been through [net.trilleo.util.TextClean.strip] — the tags are compared against
     * plain text, and Hypixel pads its lines with characters that would otherwise sit in front of them.
     *
     * Never returns [ChatChannel.ANY]: that is a value a *rule* holds, meaning "do not care", and no message is
     * ever detected as it.
     */
    fun of(line: String): ChatChannel? {
        for ((tag, channel) in TAGGED) {
            if (line.startsWith(tag)) {
                // The tag alone is not enough. "Guild > Player joined." is the server talking about the guild,
                // not somebody talking in it, and a rule watching guild chat should not see it.
                return if (SPEAKER.containsMatchIn(line.substring(tag.length))) channel else null
            }
        }
        for (tag in DIRECT) {
            if (line.startsWith(tag) && SPEAKER.containsMatchIn(line.substring(tag.length))) {
                return ChatChannel.PRIVATE
            }
        }
        return if (SPEAKER.containsMatchIn(line)) ChatChannel.ALL else null
    }
}
