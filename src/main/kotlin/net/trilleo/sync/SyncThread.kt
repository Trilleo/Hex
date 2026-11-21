package net.trilleo.sync

import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * The one background thread every trilleo.net call runs on.
 *
 * A single thread rather than a pool, on purpose: linking, account checks and syncs then never overlap, so
 * the state they share — the token, the waiting reads, the request in flight — needs no locking as long as it
 * is only touched from here. Anything the game thread wants done is handed over with [run].
 */
internal object SyncThread {
    private val LOGGER = LoggerFactory.getLogger("hex/sync")

    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "Hex-Sync").apply { isDaemon = true }
    }

    /** Runs [block] on the sync thread as soon as it is free. */
    fun run(block: () -> Unit) {
        executor.execute { guarded(block) }
    }

    /** Runs [block] on the sync thread after [delayMillis]. */
    fun schedule(delayMillis: Long, block: () -> Unit) {
        executor.schedule({ guarded(block) }, delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
    }

    /** One failing task must not stop the thread from running the next. */
    private fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            LOGGER.error("trilleo.net sync task failed", e)
        }
    }
}
