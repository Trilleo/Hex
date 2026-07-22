package net.trilleo.reminder.model

/**
 * What starts a [Reminder]'s countdown.
 *
 * Persisted by name, so constants may be appended but not renamed or reordered. A name this build does not
 * know deserializes to null and is repaired to [TIMER] by the reading config's normalizer, which is what lets
 * a newer Hex's file load in an older one instead of failing.
 */
enum class TriggerKind {
    /** Armed manually, on world join, or by a repeat — the countdown is the whole trigger. */
    TIMER,

    /** Armed when a chat line matches [Trigger.value]. */
    CHAT_MATCH,

    /** Armed on arriving at the Skyblock island named by [Trigger.value]. */
    ISLAND_ENTER,

    /** Armed on leaving the Skyblock island named by [Trigger.value]. */
    ISLAND_LEAVE,

    /** Armed on joining a world or server. */
    WORLD_JOIN,

    /** Armed when the main hand starts holding the Skyblock item named by [Trigger.value]. */
    HELD_ITEM,
}

/**
 * What arms a reminder, and how long after arming it fires.
 *
 * **Every trigger arms a countdown.** [seconds] is the delay between arming and firing, and `0` means fire on
 * the spot. That one decision is what collapses "remind me in five minutes", "chat says my potion started, so
 * warn me in forty-eight minutes", and "warn me as I arrive on the island" into a single code path — and it
 * is what makes the Skyblock presets expressible without a second concept.
 *
 * A deliberately plain class rather than a data class, and `var`-only with a no-arg constructor, because GSON
 * instantiates it reflectively and never runs Kotlin's defaults — the same shape as
 * [net.trilleo.skyblock.item.ItemRule].
 *
 * **Adding a way to arm a reminder is an enum constant and one branch in the engine, with no file migration.**
 * That falls out of the payload being one generic [value] string rather than a field per kind: an older build
 * reading a newer file sees an unknown [kind], normalizes it to [TriggerKind.TIMER], and carries on.
 */
class Trigger {
    var kind: TriggerKind = TriggerKind.TIMER

    /**
     * The payload, its meaning set by [kind] — a pattern, an island name, or a Skyblock item id. Unused by
     * [TriggerKind.TIMER] and [TriggerKind.WORLD_JOIN].
     *
     * Already case-folded to suit [kind] by the owning config's normalizer, so matching is a plain `==`
     * against [net.trilleo.skyblock.SkyblockLocation.current] or [net.trilleo.skyblock.item.HeldItem.id] with
     * no per-call case conversion. [TriggerKind.CHAT_MATCH] is the exception and is left exactly as typed: a
     * regular expression is case-sensitive by design, and folding it would silently change what it matches.
     */
    var value: String = ""

    /** Seconds between arming and firing. `0` fires immediately. Clamped to [MAX_SECONDS]. */
    var seconds: Double = 60.0

    /** Whether firing rearms the countdown instead of going idle. */
    var repeat: Boolean = false

    /** [TriggerKind.CHAT_MATCH] only: compare as a plain substring rather than as a regular expression. */
    var literal: Boolean = false

    /** Whether this kind reads [value] at all — the editor hides the field for the kinds that do not. */
    fun usesValue(): Boolean = when (kind) {
        TriggerKind.TIMER, TriggerKind.WORLD_JOIN -> false
        TriggerKind.CHAT_MATCH, TriggerKind.ISLAND_ENTER, TriggerKind.ISLAND_LEAVE, TriggerKind.HELD_ITEM -> true
    }

    companion object {
        /**
         * The longest countdown allowed, in seconds — seven days.
         *
         * Bounded so `seconds * 1000` stays far inside long range, and so a hand-edited file cannot produce a
         * countdown the editor has no way to walk back.
         */
        const val MAX_SECONDS: Double = 7.0 * 24 * 60 * 60
    }
}
