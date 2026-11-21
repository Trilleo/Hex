package net.trilleo.sync

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.trilleo.skyblock.SkyblockGameMode
import net.trilleo.skyblock.SkyblockProfile
import net.trilleo.util.Notify
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

/**
 * Turns menu reads into syncs: waits a moment after a menu closes, combines what piled up, sends it, and
 * retries what failed.
 *
 * **Pacing.** A sync goes out [DEBOUNCE_MILLIS] after the last menu closed, so opening the Attribute Menu and
 * then the Hunting Box sends one request rather than two, and never sooner than [MIN_GAP_MILLIS] after the
 * previous one. The site asks for a sync after a menu closes, not on a timer, and says one a minute is
 * plenty. `/hexa sync now` skips both waits.
 *
 * **Retrying.** Each request gets a fresh `syncKey` the first time it is built and keeps it through every
 * retry, so a request that reached the site but whose answer was lost is not applied twice — the site answers
 * the repeat with the first result. Reads that arrive while a request is retrying wait for the next one
 * rather than changing a request whose key the site may already have seen.
 *
 * Everything here runs on [SyncThread]; the game thread only calls [submit] and the command entry points,
 * which hand over to it.
 */
object SyncQueue {
    private val LOGGER = LoggerFactory.getLogger("hex/sync")

    private const val DEBOUNCE_MILLIS = 3_000L
    private const val MIN_GAP_MILLIS = 60_000L
    private val BACKOFF_SECONDS = longArrayOf(5, 30, 120)
    private const val MAX_ENTRIES = 600

    /** Which profile a read belongs to, captured when it was read. */
    private data class ProfileRef(
        val id: String?,
        val name: String?,
        val gameMode: SkyblockGameMode?,
        val generation: Int,
    )

    private class Pending(var ref: ProfileRef) {
        var levels: ListPart? = null
        var held: ListPart? = null
        var readAt: Instant = Instant.now()
    }

    private class Outgoing(val profileId: String, val body: JsonObject, val manual: Boolean) {
        var attempts = 0
    }

    private val pending = LinkedHashMap<String, Pending>()
    private var outgoing: Outgoing? = null
    private var lastSentAt = 0L
    private var flushToken = 0
    private var toldWaitingForId = false
    private var toldManual = false

    /** How many profiles have reads waiting; for `/hexa sync status`. */
    @Volatile
    var waiting: Int = 0
        private set

    /** When the last sync succeeded, epoch millis, or 0. */
    @Volatile
    var lastSyncAt: Long = 0
        private set

    /** Hands a finished menu read over. Called on the game thread. */
    fun submit(snapshot: MenuSnapshot) {
        val ref = ProfileRef(
            SkyblockProfile.id, SkyblockProfile.name, SkyblockProfile.gameMode, SkyblockProfile.generation,
        )
        val client = Minecraft.getInstance()
        SyncThread.run { accept(client, ref, snapshot) }
    }

    private fun accept(client: Minecraft, ref: ProfileRef, snapshot: MenuSnapshot) {
        val key = ref.id ?: "unknown:${ref.generation}"
        val entry = pending.getOrPut(key) { Pending(ref) }
        entry.ref = ProfileRef(
            ref.id, ref.name ?: entry.ref.name, ref.gameMode ?: entry.ref.gameMode, ref.generation,
        )
        entry.readAt = Instant.now()
        val part = ListPart.of(snapshot)
        when (snapshot.menu) {
            ShardMenu.ATTRIBUTES -> entry.levels = SyncBatch.coalesce(entry.levels, part)
            ShardMenu.HUNTING_BOX -> entry.held = SyncBatch.coalesce(entry.held, part)
        }
        waiting = pending.size

        if (ref.id == null && !toldWaitingForId) {
            toldWaitingForId = true
            Notify.chat(client, Component.translatable("hex.sync.waiting_for_profile"), ChatFormatting.GRAY)
        }
        if (SyncConfig.settings.autoSync) {
            scheduleFlush(client)
        } else if (!toldManual) {
            toldManual = true
            Notify.chat(
                client,
                Component.translatable("hex.sync.manual_hint", TrilleoAccount.command("/hexa sync now")),
                ChatFormatting.GRAY,
            )
        }
    }

    /** Sends whatever is waiting once the debounce and the gap allow. */
    private fun scheduleFlush(client: Minecraft) {
        val now = System.currentTimeMillis()
        val at = maxOf(now + DEBOUNCE_MILLIS, lastSentAt + MIN_GAP_MILLIS)
        val token = ++flushToken
        SyncThread.schedule(at - now) { if (token == flushToken) flush(client, manual = false) }
    }

    /** After linking: send anything read while unlinked. */
    fun kick() = SyncThread.run {
        if (pending.isNotEmpty() && SyncConfig.settings.autoSync) scheduleFlush(Minecraft.getInstance())
    }

    /** Drops everything waiting; the account it was for is gone. */
    fun clear() = SyncThread.run {
        pending.clear()
        outgoing = null
        flushToken++
        waiting = 0
    }

    /** `/hexa sync now`: send what is waiting immediately. */
    fun syncNow(client: Minecraft) = SyncThread.run {
        when {
            TrilleoAccount.token() == null ->
                Notify.chat(
                    client,
                    Component.translatable("hex.sync.not_linked", TrilleoAccount.command("/hexa sync link")),
                    ChatFormatting.RED,
                )

            outgoing != null ->
                Notify.chat(client, Component.translatable("hex.sync.now.retrying"), ChatFormatting.GRAY)

            pending.isEmpty() ->
                Notify.chat(client, Component.translatable("hex.sync.now.nothing"), ChatFormatting.GRAY)

            else -> {
                flushToken++
                flush(client, manual = true)
            }
        }
    }

    private fun flush(client: Minecraft, manual: Boolean) {
        if (outgoing != null) return
        TrilleoAccount.token() ?: return

        val iterator = pending.entries.iterator()
        while (iterator.hasNext()) {
            val (_, entry) = iterator.next()
            val profileId = entry.ref.id ?: run {
                when {
                    // Read before Hypixel said which profile this is, but nothing has switched since.
                    SkyblockProfile.generation == entry.ref.generation && SkyblockProfile.id != null -> {
                        entry.ref = entry.ref.copy(id = SkyblockProfile.id)
                        entry.ref.id
                    }

                    SkyblockProfile.generation != entry.ref.generation -> {
                        // The profile changed before its ID ever arrived; there is no telling whose this is.
                        LOGGER.info("Dropping a shard read whose profile was never identified")
                        iterator.remove()
                        null
                    }

                    else -> null
                }
            } ?: continue

            val requests = SyncBatch.requests(entry.levels, entry.held)
            val (levels, held) = requests.firstOrNull() ?: run { iterator.remove(); continue }
            if (levels != null) entry.levels = null
            if (held != null) entry.held = null
            if (entry.levels == null && entry.held == null) iterator.remove()
            waiting = pending.size

            val body = buildBody(entry, levels, held)
            val out = Outgoing(profileId, body, manual)
            outgoing = out
            send(client, out)
            return
        }
        waiting = pending.size
    }

    private fun buildBody(entry: Pending, levels: ListPart?, held: ListPart?): JsonObject = JsonObject().apply {
        entry.ref.name?.let { addProperty("name", it) }
        entry.ref.gameMode?.let { addProperty("gameMode", it.apiName) }
        val mode = (levels ?: held)?.mode ?: SyncMode.MERGE
        addProperty("mode", mode.apiName)
        levels?.let { add("levels", entries(it, "level")) }
        held?.let { add("held", entries(it, "count")) }
        addProperty("syncKey", UUID.randomUUID().toString())
        addProperty("capturedAt", entry.readAt.toString())
    }

    /** One list as the API wants it: `[{id, level}]` or `[{id, count}]`, names resolved to item IDs. */
    private fun entries(part: ListPart, field: String): JsonArray {
        val byId = LinkedHashMap<String, Int>()
        part.entries.forEach { (name, value) -> byId[ShardCatalog.resolve(name)] = value }
        return JsonArray().apply {
            byId.entries.take(MAX_ENTRIES).forEach { (id, value) ->
                add(JsonObject().apply {
                    addProperty("id", id)
                    addProperty(field, value)
                })
            }
        }
    }

    private fun send(client: Minecraft, out: Outgoing) {
        if (outgoing !== out) return
        val token = TrilleoAccount.token() ?: run { outgoing = null; return }
        val result = TrilleoApi.call(
            "POST", "/v1/skyblock/me/profiles/${out.profileId}/sync", token = token, body = out.body,
        )
        when (result) {
            is ApiResult.Ok -> {
                outgoing = null
                lastSentAt = System.currentTimeMillis()
                lastSyncAt = lastSentAt
                report(client, result.body, out.manual)
                // What is left is either the other half of a split request, or reads that came in meanwhile.
                if (pending.isNotEmpty()) {
                    if (out.manual) SyncThread.run { flush(client, manual = true) } else scheduleFlush(client)
                }
            }

            is ApiResult.Error -> handleError(client, out, result.error)
            is ApiResult.Network -> retry(client, out, null, result.reason)
        }
    }

    private fun handleError(client: Minecraft, out: Outgoing, error: ApiError) {
        when {
            error.forgetsToken || error.code == "insufficient_scope" -> {
                outgoing = null
                TrilleoAccount.forget(client, error.code)
            }

            error.retryable -> retry(client, out, error.retryAfterSeconds, error.code)

            error.code == "profile_limit" -> {
                outgoing = null
                Notify.chat(
                    client,
                    Component.translatable("hex.sync.profile_limit", error.limit ?: 10),
                    ChatFormatting.RED,
                )
            }

            else -> {
                outgoing = null
                val problems = error.details?.get("problems")?.toString().orEmpty()
                LOGGER.warn("Sync refused: {} (request {}) {}", error.code, error.requestId, problems)
                Notify.chat(client, Component.translatable("hex.sync.failed", error.code), ChatFormatting.RED)
            }
        }
    }

    private fun retry(client: Minecraft, out: Outgoing, retryAfter: Long?, reason: String) {
        if (out.attempts >= BACKOFF_SECONDS.size) {
            outgoing = null
            LOGGER.warn("Giving up on a sync after {} retries: {}", out.attempts, reason)
            Notify.chat(client, Component.translatable("hex.sync.gave_up", reason), ChatFormatting.RED)
            return
        }
        val delay = retryAfter ?: BACKOFF_SECONDS[out.attempts]
        out.attempts++
        LOGGER.info("Sync failed ({}); retrying in {}s", reason, delay)
        if (out.manual) {
            Notify.chat(client, Component.translatable("hex.sync.retrying", delay), ChatFormatting.GRAY)
        }
        SyncThread.schedule(delay * 1000) { send(client, out) }
    }

    /** Tells the player what changed, if they want to know. */
    private fun report(client: Minecraft, body: JsonObject, manual: Boolean) {
        body.getAsJsonArray("unknownIds")?.takeIf { it.size() > 0 }?.let {
            LOGGER.warn("trilleo.net did not recognise these shard IDs: {}", it)
        }
        if (body.get("created")?.asBoolean == true) {
            val name = body.getAsJsonObject("profile")?.stringOrNull("name").orEmpty()
            Notify.chat(client, Component.translatable("hex.sync.created", name), ChatFormatting.GREEN)
        }
        if (!SyncConfig.settings.chatSummary && !manual) return

        val changes = body.getAsJsonObject("changes")
        val levels = changes?.getAsJsonArray("levels") ?: JsonArray()
        val held = changes?.getAsJsonArray("held") ?: JsonArray()
        if (levels.size() == 0 && held.size() == 0) {
            if (manual) Notify.chat(client, Component.translatable("hex.sync.unchanged"), ChatFormatting.GRAY)
            return
        }

        val hover = Component.empty()
        appendChanges(hover, "hex.sync.changes.levels", levels)
        appendChanges(hover, "hex.sync.changes.held", held)
        val line = Notify.line(
            Component.translatable("hex.sync.done", levels.size(), held.size()),
            ChatFormatting.GREEN,
        ).withStyle { it.withHoverEvent(HoverEvent.ShowText(hover)) }
        Notify.send(client, line)
    }

    private fun appendChanges(into: net.minecraft.network.chat.MutableComponent, header: String, list: JsonArray) {
        if (list.size() == 0) return
        if (into.siblings.isNotEmpty()) into.append("\n")
        into.append(Component.translatable(header).withStyle(ChatFormatting.GOLD))
        list.forEach { element ->
            val change = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val name = change.stringOrNull("name") ?: change.stringOrNull("id") ?: return@forEach
            into.append("\n")
            into.append(
                Component.translatable(
                    "hex.sync.changes.line", name,
                    change.longOrNull("from") ?: 0, change.longOrNull("to") ?: 0,
                ).withStyle(ChatFormatting.GRAY),
            )
        }
    }
}
