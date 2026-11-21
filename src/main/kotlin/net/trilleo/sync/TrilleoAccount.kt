package net.trilleo.sync

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.trilleo.config.JsonConfig
import net.trilleo.util.AtomicWrite
import net.trilleo.util.Notify
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/** The token and username for one API address. */
data class StoredAccount(var token: String = "", var username: String = "")

/** `config/hex/trilleo-account.json`: one [StoredAccount] per API address. */
data class AccountFile(var accounts: MutableMap<String, StoredAccount> = mutableMapOf())

/**
 * The link between this install and a trilleo.net account: the personal token, how to get one, and how to
 * give it up.
 *
 * **Where the token lives.** In `trilleo-account.json`, a file of its own that is never registered with
 * [net.trilleo.config.ConfigRegistry]. Registered configs are what profiles snapshot and what the clipboard
 * export copies, and a token pasted into a Discord channel inside a shared profile would be a leak nobody
 * meant to make. It is written owner-readable only where the filesystem supports that, and is never logged,
 * printed or sent anywhere but the API in its `Authorization` header.
 *
 * **One token per address.** Tokens are keyed by the API address they were issued by, so pointing the
 * settings at a local test copy of the site neither sends the real token there nor loses it.
 *
 * Everything that touches the account state runs on [SyncThread].
 */
object TrilleoAccount {
    private val LOGGER = LoggerFactory.getLogger("hex/sync")

    private const val CLIENT_NAME = "Hex"
    private const val SCOPE = "skyblock:sync"

    private val store = JsonConfig(
        name = "trilleo-account",
        type = object : TypeToken<AccountFile>() {}.type,
        default = { AccountFile() },
        normalizer = { f ->
            // GSON fills what the file says, nulls included, whatever the Kotlin types claim.
            @Suppress("SENSELESS_COMPARISON")
            if (f.accounts == null) f.accounts = mutableMapOf()
            f.accounts.values.removeIf { (it as StoredAccount?)?.token.isNullOrBlank() }
        },
    )

    @Volatile
    private var file = AccountFile()

    /** A device link waiting for the player to confirm it on the site. */
    private class LinkAttempt(
        val base: String,
        val deviceCode: String,
        val userCode: String,
        val url: String,
        val deadline: Long,
        var interval: Long,
    )

    @Volatile
    private var attempt: LinkAttempt? = null

    /** Whether the token has been checked against the site this session. */
    @Volatile
    private var verified = false

    fun load() {
        file = store.load()
    }

    /** The token for the current API address, or null when this install is not linked to it. */
    fun token(): String? = file.accounts[TrilleoApi.base()]?.token?.takeIf { it.isNotBlank() }

    /** The username the current token belongs to, or null when unlinked or not yet known. */
    fun username(): String? = file.accounts[TrilleoApi.base()]?.username?.takeIf { it.isNotBlank() }

    val linked: Boolean get() = token() != null

    /** Whether a link is waiting for the player to confirm it. */
    val linking: Boolean get() = attempt != null

    /**
     * Starts linking, or shows the code again if a link is already waiting.
     */
    fun startLink(client: Minecraft) = SyncThread.run {
        val waiting = attempt
        if (waiting != null && waiting.base == TrilleoApi.base() && System.currentTimeMillis() < waiting.deadline) {
            showCode(client, waiting)
            return@run
        }
        attempt = null
        val body = JsonObject().apply {
            addProperty("clientName", CLIENT_NAME)
            add("scopes", JsonArray().apply { add(SCOPE) })
        }
        when (val result = TrilleoApi.call("POST", "/v1/auth/device", body = body)) {
            is ApiResult.Ok -> {
                val b = result.body
                val deviceCode = b.stringOrNull("deviceCode")
                val userCode = b.stringOrNull("userCode")
                val url = b.stringOrNull("verificationUriComplete") ?: b.stringOrNull("verificationUri")
                if (deviceCode == null || userCode == null || url == null) {
                    fail(client, "bad_response")
                    return@run
                }
                val expiresIn = b.longOrNull("expiresIn") ?: 600
                val interval = b.longOrNull("interval") ?: DeviceLinkPolicy.DEFAULT_INTERVAL
                val next = LinkAttempt(
                    TrilleoApi.base(), deviceCode, userCode, url,
                    System.currentTimeMillis() + expiresIn * 1000, interval,
                )
                attempt = next
                showCode(client, next)
                SyncThread.schedule(interval * 1000) { poll(client, next) }
            }

            is ApiResult.Error -> {
                val e = result.error
                LOGGER.warn("Could not start linking: {} (request {})", e.code, e.requestId)
                if (e.code == "rate_limited") {
                    Notify.chat(
                        client,
                        Component.translatable("hex.sync.link.rate_limited", e.retryAfterSeconds ?: 60),
                        ChatFormatting.RED,
                    )
                } else {
                    fail(client, e.code)
                }
            }

            is ApiResult.Network -> offline(client, result.reason)
        }
    }

    private fun poll(client: Minecraft, current: LinkAttempt) {
        if (attempt !== current) return // cancelled or replaced
        if (System.currentTimeMillis() >= current.deadline) {
            attempt = null
            Notify.chat(client, Component.translatable("hex.sync.link.expired"), ChatFormatting.RED)
            return
        }
        val body = JsonObject().apply { addProperty("deviceCode", current.deviceCode) }
        val step = when (val result = TrilleoApi.call("POST", "/v1/auth/device/token", body = body)) {
            is ApiResult.Ok -> {
                val token = result.body.stringOrNull("token")
                if (token == null) PollStep.Failed("bad_response") else {
                    attempt = null
                    finishLink(client, current.base, token)
                    return
                }
            }

            is ApiResult.Error -> DeviceLinkPolicy.afterError(result.error, current.interval)
            is ApiResult.Network -> DeviceLinkPolicy.afterNetworkFailure(current.interval)
        }
        when (step) {
            is PollStep.Wait -> {
                current.interval = step.seconds
                SyncThread.schedule(step.seconds * 1000) { poll(client, current) }
            }

            PollStep.Denied -> {
                attempt = null
                Notify.chat(client, Component.translatable("hex.sync.link.denied"), ChatFormatting.RED)
            }

            PollStep.Expired -> {
                attempt = null
                Notify.chat(client, Component.translatable("hex.sync.link.expired"), ChatFormatting.RED)
            }

            is PollStep.Failed -> {
                attempt = null
                fail(client, step.code)
            }
        }
    }

    private fun finishLink(client: Minecraft, base: String, token: String) {
        file.accounts[base] = StoredAccount(token = token)
        save()
        verified = false
        val name = fetchUsername(client, base, token)
        Notify.chat(
            client,
            if (name != null) Component.translatable("hex.sync.link.done", name)
            else Component.translatable("hex.sync.link.done_unnamed"),
            ChatFormatting.GREEN,
        )
        SyncQueue.kick()
    }

    /**
     * Checks the token once a session with `GET /v1/account`, refreshing the stored username — and finding
     * out early, rather than at the next sync, if the player revoked it on the site.
     */
    fun verifyOnce(client: Minecraft) = SyncThread.run {
        if (verified) return@run
        val token = token() ?: return@run
        if (fetchUsername(client, TrilleoApi.base(), token) != null) verified = true
    }

    /** Reads and stores the username for [token]; null when the call failed (a dead token is forgotten). */
    private fun fetchUsername(client: Minecraft, base: String, token: String): String? {
        when (val result = TrilleoApi.call("GET", "/v1/account", token = token)) {
            is ApiResult.Ok -> {
                val name = result.body.stringOrNull("username") ?: return null
                file.accounts[base]?.let {
                    if (it.username != name) {
                        it.username = name
                        save()
                    }
                }
                return name
            }

            is ApiResult.Error -> {
                if (result.error.forgetsToken) forget(client, result.error.code)
                else LOGGER.warn("Could not read the account: {} (request {})", result.error.code, result.error.requestId)
            }

            is ApiResult.Network -> LOGGER.info("Could not reach trilleo.net to check the account: {}", result.reason)
        }
        return null
    }

    /**
     * Revokes the token on the site and forgets it here. The local copy is forgotten even when the site cannot
     * be reached — the player asked for this install to stop uploading, and it will — and the player is told
     * to revoke it on the site themselves in that case.
     */
    fun unlink(client: Minecraft) = SyncThread.run {
        attempt = null
        val base = TrilleoApi.base()
        val token = token()
        if (token == null) {
            Notify.chat(client, Component.translatable("hex.sync.unlink.not_linked"), ChatFormatting.GRAY)
            return@run
        }
        val result = TrilleoApi.call("DELETE", "/v1/account/token", token = token)
        file.accounts.remove(base)
        save()
        SyncQueue.clear()
        val revoked = result is ApiResult.Ok || (result is ApiResult.Error && result.error.forgetsToken)
        Notify.chat(
            client,
            Component.translatable(if (revoked) "hex.sync.unlink.done" else "hex.sync.unlink.local_only"),
            if (revoked) ChatFormatting.GREEN else ChatFormatting.YELLOW,
        )
    }

    /** Stops a waiting link, if any. */
    fun cancelLink() = SyncThread.run { attempt = null }

    /**
     * Forgets a token the site no longer accepts, and tells the player why and how to link again. Called from
     * the sync thread by anything that got one of the token errors back.
     */
    fun forget(client: Minecraft, code: String) {
        val base = TrilleoApi.base()
        if (file.accounts.remove(base) == null) return
        save()
        SyncQueue.clear()
        LOGGER.info("Forgot the trilleo.net token: {}", code)
        val reason = when (code) {
            "key_revoked" -> "hex.sync.token.revoked"
            "key_expired" -> "hex.sync.token.expired"
            "account_blocked" -> "hex.sync.token.blocked"
            else -> "hex.sync.token.invalid"
        }
        Notify.chat(client, Component.translatable(reason), ChatFormatting.RED)
        Notify.send(client, relinkHint())
    }

    private fun relinkHint(): Component =
        Notify.line(Component.translatable("hex.sync.token.relink", command("/hexa sync link")), ChatFormatting.GRAY)

    private fun showCode(client: Minecraft, link: LinkAttempt) {
        val url = Component.literal(link.url).withStyle {
            it.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(ClickEvent.OpenUrl(URI(link.url)))
                .withHoverEvent(HoverEvent.ShowText(Component.translatable("hex.sync.link.open")))
        }
        val code = Component.literal(link.userCode).withStyle {
            it.withColor(ChatFormatting.GOLD).withBold(true)
                .withClickEvent(ClickEvent.CopyToClipboard(link.userCode))
                .withHoverEvent(HoverEvent.ShowText(Component.translatable("hex.sync.link.copy")))
        }
        val minutes = ((link.deadline - System.currentTimeMillis()) / 60_000).coerceAtLeast(1)
        Notify.chat(client, Component.translatable("hex.sync.link.code", url, code), ChatFormatting.YELLOW)
        Notify.chat(client, Component.translatable("hex.sync.link.waiting", minutes), ChatFormatting.GRAY)
    }

    private fun fail(client: Minecraft, code: String) {
        Notify.chat(client, Component.translatable("hex.sync.link.failed", code), ChatFormatting.RED)
    }

    internal fun offline(client: Minecraft, reason: String) {
        LOGGER.info("Could not reach {}: {}", TrilleoApi.base(), reason)
        Notify.chat(client, Component.translatable("hex.sync.offline", TrilleoApi.base()), ChatFormatting.RED)
    }

    /** A clickable command suggestion. */
    internal fun command(text: String): Component = Component.literal(text).withStyle {
        it.withColor(ChatFormatting.WHITE).withClickEvent(ClickEvent.SuggestCommand(text))
    }

    private fun save() {
        val path = store.fileIn(store.defaultDir())
        runCatching {
            AtomicWrite.writeString(path, JsonConfig.GSON.toJson(file))
            runCatching {
                Files.setPosixFilePermissions(
                    path, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                )
            }
        }.onFailure { LOGGER.error("Could not save {}", path.fileName, it) }
    }
}
