package net.trilleo.suggest.context

/**
 * Everything the model knows about the moment a command was typed, as a fixed set of categorical features.
 *
 * **All features are categorical strings, and that is a design choice rather than a shortcut.** It means one
 * counting rule covers every signal — "how often was this command run when this feature had this value" — so
 * adding a new context source is a line in [ContextSources] and nothing else: no scaling, no normalisation,
 * no bucketing decisions pushed into the ranker, and no numeric feature quietly dominating because it happens
 * to be measured in thousands. Continuous quantities ([POSITION], [FULLNESS], [HOUR]) are bucketed at the
 * point they are read, where the sensible bucket width is obvious, instead of at the point they are used.
 *
 * A feature that cannot be read becomes [UNKNOWN] rather than being left out. That keeps every snapshot the
 * same shape, and — more usefully — makes "I could not tell where you were" a thing the model can learn
 * from in its own right, since it correlates strongly with having just joined a server.
 *
 * Snapshots are never persisted. Only the (feature, value) pairs are, folded into counters by
 * [net.trilleo.suggest.model.SuggestModel].
 */
class ContextSnapshot(val features: Map<String, String>) {

    operator fun get(feature: String): String = features[feature] ?: UNKNOWN

    /** Whether this feature was actually readable — the dashboard greys out the ones that were not. */
    fun known(feature: String): Boolean = features[feature]?.takeIf { it != UNKNOWN } != null

    override fun toString(): String =
        features.entries.filter { it.value != UNKNOWN }.joinToString(", ") { "${it.key}=${it.value}" }

    companion object {
        /** The value of a feature that could not be read. A real category, not a null. */
        const val UNKNOWN: String = "?"

        /** The server's host, without its port. Separates a Skyblock habit from a survival-server one. */
        const val SERVER: String = "srv"

        /** The Skyblock island, from the scoreboard sidebar. */
        const val ISLAND: String = "isl"

        /**
         * A coarse cell of the world — the island plus the player's position quantised to 32 blocks.
         *
         * The most quietly powerful feature here, because on Skyblock *where you are standing* is very nearly
         * a statement of intent: the few square metres by the dungeon entrance predict `/joindungeon` far
         * better than the island as a whole ever could, and it costs one shift per axis to know.
         */
        const val CELL: String = "cell"

        /** The Skyblock id of the item in the main hand. */
        const val HELD: String = "held"

        /** The main-hand item's broad class (pickaxe, rod, sword, …), from the catalogue. */
        const val HELD_KIND: String = "kind"

        /** A hash of the Skyblock ids across the hotbar — in effect, which loadout is equipped. */
        const val HOTBAR: String = "hot"

        /** A hash of the four worn armour pieces' Skyblock ids. */
        const val ARMOR: String = "arm"

        /** How full the inventory is, in fifths. */
        const val FULLNESS: String = "full"

        /** The real-world hour, in four-hour buckets. Catches "what I do in the evening". */
        const val HOUR: String = "hr"

        /** Weekday or weekend. */
        const val DAY: String = "day"

        /**
         * The Skyblock season — `spring`, `summer`, `autumn`, `winter`.
         *
         * A different clock entirely from [HOUR], and worth both: a Skyblock year passes in about two and a
         * half real days, so the season turns over often enough to be learned from within a week of play,
         * and it is what makes the seasonal events predictable before they are announced.
         *
         * The season rather than the twelve-month name, because four categories can carry a habit and twelve
         * mostly carry sparsity — most of what the calendar predicts lives at the coarser grain.
         */
        const val SB_SEASON: String = "sbseason"

        /**
         * Where in the Skyblock day it is — `night`, `dawn`, `day`, `dusk`.
         *
         * Skyblock's own clock, not the player's. What someone does when it is dark on Skyblock (anything
         * that wants mobs) has nothing to do with what they do when it is dark where they live, and a
         * Skyblock day lasts twenty real minutes, so this cycles several times an hour.
         */
        const val SB_TIME: String = "sbtime"

        /**
         * The Skyblock event the sidebar is naming — `dark auction`, `spooky festival`, and so on.
         *
         * The most direct statement of intent anywhere in the context. Everything else is circumstantial
         * evidence about what the player might want; a Dark Auction countdown on screen is very nearly the
         * player saying out loud that they are about to warp to it.
         */
        const val SB_EVENT: String = "sbevent"

        /** How long since joining this world: just-joined, early, or settled. */
        const val SESSION: String = "sess"

        /** The previous command's key. The order-1 Markov context. */
        const val PREV1: String = "prev1"

        /** The command before that. Together with [PREV1], the order-2 Markov context. */
        const val PREV2: String = "prev2"

        /**
         * A tag for what chat said in the last few seconds — `party-invite`, `visit-request`, and so on.
         *
         * This is what lets the model learn a *reply*: the reason `/party accept` is the right suggestion is
         * almost never where you are standing, it is that somebody invited you eight seconds ago.
         */
        const val CUE: String = "cue"

        /** Every feature, in display order. The dashboard and the "why" view both walk this. */
        val ALL: List<String> = listOf(
            SERVER, ISLAND, CELL, HELD, HELD_KIND, HOTBAR, ARMOR,
            FULLNESS, HOUR, DAY, SB_SEASON, SB_TIME, SB_EVENT, SESSION, PREV1, PREV2, CUE,
        )

        /** Human-readable names for the "why" view, so it never shows a three-letter key. */
        val LABELS: Map<String, String> = mapOf(
            SERVER to "server",
            ISLAND to "island",
            CELL to "area",
            HELD to "held item",
            HELD_KIND to "item type",
            HOTBAR to "hotbar",
            ARMOR to "armour",
            FULLNESS to "inventory",
            HOUR to "time of day",
            DAY to "day",
            SB_SEASON to "Skyblock season",
            SB_TIME to "Skyblock time",
            SB_EVENT to "Skyblock event",
            SESSION to "session",
            PREV1 to "last command",
            PREV2 to "command before",
            CUE to "recent chat",
        )

        /** An all-unknown snapshot, for a prediction made with no world loaded. */
        val EMPTY: ContextSnapshot = ContextSnapshot(emptyMap())
    }
}
