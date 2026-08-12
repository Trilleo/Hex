package net.trilleo.chat

import net.trilleo.chat.model.ChatChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Channel detection, over the line shapes Hypixel actually sends.
 *
 * Testable for the same reason [net.trilleo.config.SuggestionsTest] is: [ChatChannels] turns a string into an
 * enum constant and touches nothing of Minecraft. What it covers is the part with the interesting edges — telling
 * somebody talking apart from the server talking, which is the whole reason the speaker shape is pinned down
 * rather than guessed at from a colon.
 */
class ChatChannelsTest {

    @Test
    fun `public chat is detected through a rank and a guild tag`() {
        assertEquals(ChatChannel.ALL, ChatChannels.of("Steve: hello"))
        assertEquals(ChatChannel.ALL, ChatChannels.of("[MVP+] Steve: hello"))
        assertEquals(ChatChannel.ALL, ChatChannels.of("[MVP++] Steve [HEX]: hello"))
    }

    @Test
    fun `each tagged channel is recognised`() {
        assertEquals(ChatChannel.PARTY, ChatChannels.of("Party > [VIP] Steve: invite me"))
        assertEquals(ChatChannel.GUILD, ChatChannels.of("Guild > Steve [Officer]: hi"))
        assertEquals(ChatChannel.OFFICER, ChatChannels.of("Officer > Steve: hi"))
        assertEquals(ChatChannel.COOP, ChatChannels.of("Co-op > Steve: hi"))
    }

    @Test
    fun `a direct message is private in either direction`() {
        assertEquals(ChatChannel.PRIVATE, ChatChannels.of("From [MVP+] Steve: hi"))
        assertEquals(ChatChannel.PRIVATE, ChatChannels.of("To [MVP+] Steve: hi"))
    }

    @Test
    fun `the server talking about a channel is not somebody talking in it`() {
        // The tag alone would be enough for a naive check, and a rule watching guild chat would then fire on
        // every join and leave. What separates them is that nobody is named before a colon.
        assertNull(ChatChannels.of("Guild > Steve joined."))
        assertNull(ChatChannels.of("Party > Steve left."))
    }

    @Test
    fun `a broadcast with a colon in it is still a broadcast`() {
        // The reason the speaker shape exists at all: Hypixel's own messages are full of colons, and reading any
        // of them as public chat would fire every channel-scoped rule on all of them.
        assertNull(ChatChannels.of("Your new API key is: 0000-0000"))
        assertNull(ChatChannels.of("RARE DROP! Enchanted Book (+10% Magic Find)"))
    }

    @Test
    fun `a faked tag inside public chat does not change the channel`() {
        // Nothing stops a player typing this. Only the leading tag is read, and in public chat it arrives behind
        // the speaker's own name — so the line is public, which is what it is.
        assertEquals(ChatChannel.ALL, ChatChannels.of("[MVP+] Steve: Party > fake: give me your coins"))
    }

    @Test
    fun `a name too long to be a player is not a speaker`() {
        // Sixteen characters is the limit, so this is prose with a colon in it rather than somebody talking.
        assertNull(ChatChannels.of("ThisNameIsFarTooLongToBeReal: hello"))
    }

    @Test
    fun `nothing is ever detected as ANY`() {
        // ANY is a value a rule holds, meaning "do not care". A message detected as it would make a rule scoped
        // to ANY behave differently from one scoped to a real channel, which is not what the constant means.
        val lines = listOf(
            "Steve: hello",
            "Party > Steve: hello",
            "From Steve: hello",
            "Your new API key is: 0000-0000",
            "",
        )

        lines.forEach { line -> assert(ChatChannels.of(line) != ChatChannel.ANY) { line } }
    }
}
