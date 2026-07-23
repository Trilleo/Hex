package net.trilleo.suggest.model

import com.google.gson.reflect.TypeToken
import net.trilleo.config.JsonConfig
import net.trilleo.suggest.SuggestConfig
import net.trilleo.suggest.model.ModelStore.CLEAN
import net.trilleo.suggest.model.ModelStore.DEBOUNCE_TICKS
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Where the learned model lives, and the only thing that reads or writes it.
 *
 * **Deliberately not registered with [net.trilleo.config.ConfigRegistry].** That registry's own documentation
 * draws the line — only *settings* belong in it, because everything in it is captured into config profiles
 * and pasted into the clipboard blob — and a record of every command a player has typed is emphatically not a
 * setting. Registering this would mean switching profiles silently swapped one person's command history for
 * another's, and "copy profile to clipboard" handed it to whoever they sent it to.
 * [net.trilleo.update.UpdateStaging] sits outside the registry for the same structural reason; this one has
 * a privacy reason on top.
 *
 * **Writes are debounced, off-thread and atomic.** Debounced because the model changes on every command and
 * the file is the largest thing Hex writes; off-thread because serialising it is milliseconds rather than
 * microseconds; atomic because a truncated model file loads as an empty one, and losing months of learning to
 * a crash during a save would be unrecoverable. The write goes to a sibling `.tmp` and is then moved over the
 * real file, so the file on disk is only ever a complete model — the previous one or the new one.
 *
 * The background writer takes [SuggestModel]'s lock for as long as serialisation runs, which can in principle
 * stall a command being recorded on the client thread. In practice that is one collision between a
 * once-a-minute write and a once-a-minute command, costing a fraction of a frame; paying for it with a full
 * defensive copy of the model on every save would cost far more, far more often.
 */
object ModelStore {
    private val LOGGER = LoggerFactory.getLogger("hex/suggest")

    private val json = JsonConfig(
        name = "suggest/model",
        type = object : TypeToken<ModelData>() {}.type,
        default = { ModelData() },
    )

    /**
     * One daemon thread, so a save in flight can never hold the game open at exit, and two saves can never
     * interleave into the same temporary file.
     */
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "hex-suggest-save").apply { isDaemon = true }
    }

    /** Ticks until the pending write lands, or [CLEAN] when there is nothing to write. */
    @Volatile
    private var ticksUntilSave: Int = CLEAN

    /** Set when the model on disk is known to be unusable, so a later save does not overwrite a good file. */
    @Volatile
    private var refusedFile: Boolean = false

    /**
     * Reads the model into [SuggestModel], or starts fresh.
     *
     * A file this build cannot read is *left alone* rather than overwritten: the player may simply have
     * launched an older Hex by accident, and destroying their model as a side effect of that would be the
     * worst possible response to a recoverable mistake.
     */
    fun load() {
        val loaded = json.load()
        if (!SuggestModel.normalize(loaded)) {
            refusedFile = true
            LOGGER.warn(
                "Command-suggestion model is version {} but this Hex reads at most {} — starting fresh and leaving the file alone",
                loaded.version, SuggestModel.VERSION,
            )
            SuggestModel.adopt(ModelData())
            return
        }
        SuggestModel.adopt(loaded)
    }

    /** Note that the model changed; the write lands once [DEBOUNCE_TICKS] ticks pass without another change. */
    fun markDirty() {
        if (refusedFile) return
        ticksUntilSave = DEBOUNCE_TICKS
    }

    /** Advances the debounce timer. Called once per client tick from the feature. */
    fun tick() {
        if (ticksUntilSave > 0 && --ticksUntilSave == 0) flush()
    }

    /** Writes now if there is anything pending. Called on world leave, on chat close, and at shutdown. */
    fun flush() {
        if (ticksUntilSave == CLEAN || refusedFile) return
        ticksUntilSave = CLEAN
        submit()
    }

    /** Writes now whether or not anything is pending — for a wipe, which must not be recoverable by a crash. */
    fun saveNow() {
        if (refusedFile) return
        ticksUntilSave = CLEAN
        submit()
    }

    /**
     * Blocks until any in-flight write finishes, then stops the writer. Called from the feature's shutdown
     * hook, after [flush], so the last thing learned in a session survives closing the game.
     */
    fun shutdown() {
        flush()
        writer.shutdown()
        runCatching { writer.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS) }
    }

    private fun submit() {
        runCatching {
            writer.execute {
                runCatching { write() }.onFailure { LOGGER.error("Failed to save the command-suggestion model", it) }
            }
        }.onFailure {
            // The executor is shut down (we are exiting). Write on this thread rather than lose the session.
            runCatching { write() }.onFailure { e -> LOGGER.error("Failed to save the command-suggestion model", e) }
        }
    }

    /** Prunes and serialises under the model lock, then writes outside it. */
    private fun write() {
        val text = SuggestModel.withLock { model ->
            prune(model)
            JsonConfig.GSON.toJson(model, json.typeToken())
        }
        val target = json.fileIn(json.defaultDir())
        Files.createDirectories(target.parent)
        val temp = target.resolveSibling(target.fileName.toString() + ".tmp")
        Files.writeString(temp, text)
        moveOver(temp, target)
    }

    /**
     * Replaces [target] with [temp], atomically where the filesystem allows it.
     *
     * `ATOMIC_MOVE` is supported on both NTFS and every mainstream Unix filesystem, but not on all of them —
     * some network shares refuse it — so a failure falls back to a plain replace. That gives up the guarantee
     * rather than giving up the save, which is the right way round: a non-atomic write is a small risk taken
     * rarely, and refusing to write at all is a certainty of losing everything learned.
     */
    private fun moveOver(temp: Path, target: Path) {
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    // ---- pruning ---------------------------------------------------------------------------------------

    /**
     * Drops what has decayed to nothing, and caps what is left.
     *
     * Runs at save time rather than on a timer because that is the one moment the whole structure is being
     * walked anyway — pruning is free there and would otherwise be a periodic pass over every counter in the
     * model for no other reason.
     *
     * The order matters. Negligible entries go first, and on a model of any realistic size that is the whole
     * job; the hard caps below it exist only to bound the pathological case, and they cut by decayed weight
     * so what survives is what the player actually still does.
     */
    private fun prune(model: ModelData) {
        val now = System.currentTimeMillis()
        val halfLife = SuggestConfig.halfLifeMs

        model.keys.entries.removeAll { (_, stat) ->
            !stat.pinned && !stat.blocked && stat.count.negligible(now, halfLife)
        }
        model.names.entries.removeAll { it.value.negligible(now, halfLife) }

        model.keys.values.forEach { stat ->
            stat.ctx.values.forEach { values ->
                values.entries.removeAll { it.value.negligible(now, halfLife) }
                capBy(values, MAX_CTX_VALUES) { it.value(now, halfLife) }
            }
            stat.ctx.entries.removeAll { it.value.isEmpty() }
        }

        model.marginals.values.forEach { values ->
            values.entries.removeAll { it.value.negligible(now, halfLife) }
        }
        model.marginals.entries.removeAll { it.value.isEmpty() }

        model.joint.values.forEach { nexts ->
            nexts.entries.removeAll { it.value.negligible(now, halfLife) }
        }
        model.joint.entries.removeAll { it.value.isEmpty() }

        if (model.keys.size > MAX_KEYS) {
            val doomed = model.keys.entries
                .asSequence()
                .filter { !it.value.pinned }
                .sortedBy { it.value.count.value(now, halfLife) }
                .take(model.keys.size - MAX_KEYS)
                .map { it.key }
                .toList()
            doomed.forEach { model.keys.remove(it) }
        }
        capBy(model.joint, MAX_JOINT) { nexts -> nexts.values.maxOfOrNull { it.value(now, halfLife) } ?: 0.0 }
    }

    /** Keeps the [cap] heaviest entries of [map], by whatever [weight] says an entry is worth. */
    private fun <V> capBy(map: MutableMap<String, V>, cap: Int, weight: (V) -> Double) {
        if (map.size <= cap) return
        val doomed = map.entries
            .asSequence()
            .sortedBy { weight(it.value) }
            .take(map.size - cap)
            .map { it.key }
            .toList()
        doomed.forEach { map.remove(it) }
    }

    private const val CLEAN = -1

    /**
     * Twenty seconds at 20 tps. Far longer than [net.trilleo.config.ConfigHandle.DEBOUNCE_TICKS], because
     * this file is orders of magnitude larger than a settings file and what it holds is a running tally
     * rather than something the player just typed and expects to see saved.
     */
    private const val DEBOUNCE_TICKS = 20 * 20

    /** A player has a few hundred command lines. This is the bound on the pathological case, not the target. */
    const val MAX_KEYS: Int = 2000

    /** Per feature, per key. Beyond a dozen values a context feature is not describing a habit any more. */
    private const val MAX_CTX_VALUES = 12

    private const val MAX_JOINT = 2000

    private const val SHUTDOWN_WAIT_SECONDS = 3L
}
