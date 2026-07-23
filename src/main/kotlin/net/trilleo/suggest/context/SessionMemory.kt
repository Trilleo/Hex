package net.trilleo.suggest.context

/**
 * The part of the context that is about *this* session rather than about the world — what you have already
 * run, and how long you have been here.
 *
 * Deliberately not persisted. "The last command you ran" is meaningless across a restart, and carrying it
 * over would have the model open every session by predicting the continuation of whatever you happened to be
 * doing when you quit yesterday. Cleared on both join and leave, like [net.trilleo.skyblock.item.HeldItem]
 * and [net.trilleo.skyblock.SkyblockLocation] are, so nothing from one server can be read as context on the
 * next.
 */
object SessionMemory {

    /** The most recent command key, or null. Feeds [ContextSnapshot.PREV1]. */
    @Volatile
    var prev1: String? = null
        private set

    /** The one before that. Feeds [ContextSnapshot.PREV2]. */
    @Volatile
    var prev2: String? = null
        private set

    /** Ticks since the world was joined, saturating at [SETTLED_TICKS] so it cannot overflow a long session. */
    @Volatile
    var ticksSinceJoin: Int = 0
        private set

    /**
     * Every command key run since joining.
     *
     * Feeds the ranker's "used this session" feature, which exists because habit and intent pull in opposite
     * directions here: a command you have run once this session is often one you are about to run again
     * (checking `/pv` on player after player), and sometimes the one thing you certainly do *not* need again
     * (`/warp` when you have already arrived). Which of those is true is exactly the sort of thing the ranker
     * is there to learn, per player, rather than something to decide here.
     */
    private val usedThisSession = LinkedHashSet<String>()

    fun used(key: String): Boolean = key in usedThisSession

    /** Clears everything. Called on both world join and world leave. */
    fun reset() {
        prev1 = null
        prev2 = null
        ticksSinceJoin = 0
        usedThisSession.clear()
    }

    /** One client tick. The only per-tick work this feature does. */
    fun tick() {
        if (ticksSinceJoin < SETTLED_TICKS) ticksSinceJoin++
    }

    /** Records that [key] was just run, shifting the history along. */
    fun note(key: String) {
        prev2 = prev1
        prev1 = key
        // Bounded, because a very long session with a script-happy player must not grow this without limit.
        if (usedThisSession.size >= MAX_SESSION_KEYS) {
            usedThisSession.iterator().let { if (it.hasNext()) { it.next(); it.remove() } }
        }
        usedThisSession += key
    }

    /** Which phase of the session this is, as [ContextSnapshot.SESSION]'s value. */
    fun phase(): String = when {
        ticksSinceJoin < JOINED_TICKS -> "joined"
        ticksSinceJoin < SETTLED_TICKS -> "early"
        else -> "settled"
    }

    /**
     * Fifteen seconds. The window in which the commands people run are overwhelmingly *arrival* commands —
     * `/is`, `/warp`, `/pl` — rather than whatever they came online to do.
     */
    private const val JOINED_TICKS = 20 * 15

    /** Three minutes, after which one session looks like any other and the feature stops distinguishing. */
    private const val SETTLED_TICKS = 20 * 180

    private const val MAX_SESSION_KEYS = 256
}
