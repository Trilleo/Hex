package net.trilleo.reminder.model

import net.trilleo.region.RegionTracker
import net.trilleo.skyblock.SkyblockLocation
import net.trilleo.skyblock.item.HeldItem

/**
 * What a [Condition] tests.
 *
 * Persisted by name; an unknown constant normalizes to [ON_SKYBLOCK], which is the mildest possible repair —
 * a condition that is true wherever the mod is useful at all.
 */
enum class ConditionKind {
    /** True while the scoreboard looks like Skyblock's. Needs no value. */
    ON_SKYBLOCK,

    /** True while the player is on the island named by [Condition.value]. */
    ON_ISLAND,

    /** True while the player is anywhere but the island named by [Condition.value]. */
    NOT_ON_ISLAND,

    /** True while the main hand holds the Skyblock item named by [Condition.value]. */
    HOLDING_ITEM,

    /** True while the player stands inside the [net.trilleo.region.model.Region] named by [Condition.value]. */
    IN_REGION,

    /** True while the player stands anywhere but inside the region named by [Condition.value]. */
    NOT_IN_REGION,
}

/**
 * A test that must pass at the moment a reminder would fire, or the reminder is suppressed.
 *
 * **Evaluated at fire time, not at arm time.** A countdown started on the Rift should still be running when
 * you get back to it, so the question a condition answers is "is this worth telling you *now*", not "was it
 * worth starting". A suppressed fire is not an error: a repeating reminder simply rearms and a one-shot goes
 * idle, so leaving an island quietly stops its reminders instead of losing them.
 *
 * Plain, `var`-only and no-arg constructible for the same GSON reasons as [Trigger].
 */
class Condition {
    var kind: ConditionKind = ConditionKind.ON_SKYBLOCK

    /**
     * The island name or Skyblock item id to test against, case-folded to suit [kind] by the owning config's
     * normalizer. Unused by [ConditionKind.ON_SKYBLOCK].
     */
    var value: String = ""

    /** Whether this kind reads [value] at all — a blank value on a kind that needs one is dropped on load. */
    fun usesValue(): Boolean = kind != ConditionKind.ON_SKYBLOCK

    /**
     * Whether this condition currently holds.
     *
     * Reads the same best-effort sources the rest of the mod does, and inherits their caution: an unknown
     * island (`null`) satisfies neither [ConditionKind.ON_ISLAND] nor [ConditionKind.NOT_ON_ISLAND], because
     * [SkyblockLocation] reports null for "I cannot tell" rather than for "somewhere else", and firing on a
     * guess is worse than staying quiet for one more poll.
     */
    fun holds(): Boolean {
        val island = SkyblockLocation.current
        return when (kind) {
            ConditionKind.ON_SKYBLOCK -> SkyblockLocation.onSkyblock
            ConditionKind.ON_ISLAND -> island != null && island == value
            ConditionKind.NOT_ON_ISLAND -> island != null && island != value
            ConditionKind.HOLDING_ITEM -> HeldItem.id != null && HeldItem.id == value
            // Asked of the tracker rather than recomputed, so a condition agrees with the alert the same
            // region just fired instead of disagreeing by a tick on the boundary.
            ConditionKind.IN_REGION -> RegionTracker.isInsideNamed(value)
            ConditionKind.NOT_IN_REGION -> !RegionTracker.isInsideNamed(value)
        }
    }
}
