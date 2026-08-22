package net.trilleo.sound

import net.minecraft.client.Minecraft
import net.minecraft.sounds.SoundEvent

/**
 * Holds sounds that are due later and plays them when they come due.
 *
 * ### Why not `SoundManager.playDelayed`
 *
 * Vanilla can already delay a sound, but only by a whole number of ticks — 50 ms. A sixteenth note at 120 BPM
 * is 125 ms, which is two and a half ticks, so a rhythm built on vanilla's delay would quantise into uneven
 * pairs. Everything here is scheduled against [System.nanoTime] instead, and [advance] is called often enough
 * that the error is a frame rather than a tick.
 *
 * ### The clock has three drivers, and [advance] is idempotent
 *
 * [advance] is called from the client tick, from the HUD frame callback, and from the sequence editor's own
 * render. It has to be, because none of the three covers every case on its own: the tick always runs but is
 * only accurate to 50 ms, the HUD callback is per-frame but stops while the HUD is hidden with F1 and does
 * not exist outside a world at all, and the editor's render only happens while the editor is open — which is
 * precisely when the timing matters most and is also reachable from the title screen.
 *
 * Being called three times in one frame is harmless: each step is removed from the queue as it is played, so
 * a second call in the same instant finds nothing left to do.
 *
 * Sounds themselves are dispatched through [Minecraft.execute], which runs a task inline when it is already
 * on the client thread — so a step that comes due during a frame is heard in that frame rather than waiting
 * for the next tick. Without that, scheduling per frame would buy nothing.
 */
object SoundScheduler {

    /**
     * One sound waiting for its moment.
     *
     * [handle] groups every step that one call to [SoundPlayer.play] scheduled, so a sequence can be stopped
     * as a unit — which is what the editor's Stop button and leaving a world both need.
     */
    private class Pending(
        val atNanos: Long,
        val sound: SoundEvent,
        val pitch: Float,
        val volume: Float,
        val handle: Long,
    )

    /**
     * Kept sorted by [Pending.atNanos], so [advance] only ever inspects the head.
     *
     * An `ArrayList` with an inserting sort rather than a `PriorityQueue`: the queue is short, the common
     * case is appending a whole sequence in time order, and [stop] has to walk it anyway.
     */
    private val pending = ArrayList<Pending>()

    private var nextHandle: Long = 1L

    /** Sequences currently in flight, oldest first, for the [MAX_SEQUENCES] cap. */
    private val activeSequences = ArrayDeque<Long>()

    /**
     * A handle that names nothing — what [SoundPlayer.play] returns when there was nothing to play. Never a
     * real handle, so passing it to [stop] is safely a no-op.
     */
    const val NO_HANDLE: Long = 0L

    /**
     * How many sounds may be waiting at once.
     *
     * The sound engine has a finite channel pool, so this is load-bearing rather than tidiness: a config
     * hand-edited into a thousand-step sequence must degrade into a truncated sequence, not into a client
     * whose audio has stopped working until it is restarted.
     */
    const val MAX_PENDING: Int = 256

    /**
     * How many sounds one [advance] may actually start.
     *
     * A pathological sequence with sixty steps on the same instant spreads over the next few frames instead
     * of firing them all into one. Anything musical is far below this — a chord is three or four.
     */
    const val MAX_PER_ADVANCE: Int = 8

    /** How many sequences may be in flight together. Starting a further one stops the oldest. */
    const val MAX_SEQUENCES: Int = 4

    /** A fresh handle, and room in the sequence cap for it — stopping the oldest sequence if need be. */
    fun beginSequence(): Long {
        val handle = nextHandle++
        activeSequences.addLast(handle)
        while (activeSequences.size > MAX_SEQUENCES) {
            drop(activeSequences.removeFirst())
        }
        return handle
    }

    /**
     * Queues [sound] to play [afterMillis] from now under [handle].
     *
     * Returns false when the queue is full, which the caller uses to stop scheduling the rest of a sequence
     * rather than filling the queue with steps that would be refused one at a time.
     */
    fun scheduleIn(afterMillis: Double, sound: SoundEvent, pitch: Float, volume: Float, handle: Long): Boolean {
        if (pending.size >= MAX_PENDING) return false
        val at = System.nanoTime() + (afterMillis.coerceAtLeast(0.0) * NANOS_PER_MILLI).toLong()
        val entry = Pending(at, sound, pitch, volume, handle)
        // Insert in time order. A sequence arrives already sorted, so this is an append in the common case.
        var index = pending.size
        while (index > 0 && pending[index - 1].atNanos > at) index--
        pending.add(index, entry)
        return true
    }

    /**
     * Plays everything that has come due.
     *
     * Safe to call from any of the three drivers, any number of times per frame, and cheap when there is
     * nothing queued — which is almost always.
     */
    fun advance(client: Minecraft) {
        if (pending.isEmpty()) return
        val now = System.nanoTime()
        var played = 0
        while (pending.isNotEmpty() && played < MAX_PER_ADVANCE) {
            val head = pending[0]
            if (head.atNanos > now) break
            pending.removeAt(0)
            played++
            SoundPlayer.playNow(client, head.sound, head.pitch, head.volume)
        }
        if (pending.isEmpty()) activeSequences.clear()
    }

    /** Drops everything queued under [handle]. A handle that is finished or unknown is a no-op. */
    fun stop(handle: Long) {
        if (handle == NO_HANDLE) return
        drop(handle)
        activeSequences.remove(handle)
    }

    /** Drops everything queued. Called when a world is left and when a sound screen closes. */
    fun stopAll() {
        pending.clear()
        activeSequences.clear()
    }

    /** How many sounds are still waiting — for the editor's transport to know whether it is still playing. */
    fun pendingCount(): Int = pending.size

    /** Whether anything is still queued under [handle]. */
    fun isActive(handle: Long): Boolean =
        handle != NO_HANDLE && pending.any { it.handle == handle }

    private fun drop(handle: Long) {
        pending.removeAll { it.handle == handle }
    }

    private const val NANOS_PER_MILLI: Double = 1_000_000.0
}
