package net.trilleo.reminder.hud

/** Which corner of the reminder panel the anchor pins, and therefore which way the panel grows. */
enum class HudCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
}

/** The order reminders are listed in on the panel. A firing reminder is pinned to the top regardless. */
enum class HudSort {
    /** Closest to firing first — what you want when several are counting down. */
    SOONEST_FIRST,

    /** The order the editor lists them in, so the panel can be arranged by hand. */
    LIST_ORDER,

    /** Most recently armed first. */
    NEWEST_FIRST,
}

/**
 * Where the reminder panel sits and how it looks.
 *
 * **Positions are fractions of the screen, not pixels.** Absolute coordinates put the panel off-screen the
 * moment the player changes resolution, toggles fullscreen, or changes GUI scale — all three move
 * `guiWidth()`/`guiHeight()` underneath a saved position. A fraction survives all of them. [corner] then says
 * which corner of the *panel* the fraction pins, so a right-anchored panel grows leftwards instead of sliding
 * off the edge as rows are added.
 *
 * `var`-only with defaults for GSON, and repaired by `ReminderConfig`'s normalizer.
 */
class HudSettings {
    /** Horizontal anchor, `0.0` (left edge) to `1.0` (right edge). */
    var anchorX: Double = 0.01

    /** Vertical anchor, `0.0` (top edge) to `1.0` (bottom edge). */
    var anchorY: Double = 0.35

    var corner: HudCorner = HudCorner.TOP_LEFT

    var scale: Double = 1.0

    var background: Boolean = true

    /** Panel background, `"#AARRGGBB"` — carried as a string so the JSON stays readable by hand. */
    var backgroundColor: String = "#80101010"

    var textColor: String = "#FFE0E0E0"

    /** The colour a reminder flashes in while it is firing. */
    var flashColor: String = "#FFFF5555"

    /** How many rows the panel shows before collapsing the rest into a "+N more" line. */
    var maxRows: Int = 8

    var sort: HudSort = HudSort.SOONEST_FIRST

    /** Draw the panel frame even with nothing counting down. Off by default so it stays out of the way. */
    var showWhenEmpty: Boolean = false

    /** Hide the panel entirely off Skyblock, where none of this is relevant. */
    var skyblockOnly: Boolean = true

    companion object {
        const val SCALE_MIN: Double = 0.5
        const val SCALE_MAX: Double = 3.0
        const val SCALE_STEP: Double = 0.05

        const val MAX_ROWS_MIN: Int = 1
        const val MAX_ROWS_MAX: Int = 32
    }
}
