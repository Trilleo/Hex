package net.trilleo.sensitivity.model

/** Which half of the look direction an angle applies to. */
enum class StickyAxis {
    /** Left/right. Wraps, so 0 and 360 are the same place. */
    YAW,

    /** Up/down. Clamped to ±90 by the game itself, so it does not wrap. */
    PITCH,
}

/**
 * How far apart the evenly spaced sticky angles sit, or [OFF] for none of them.
 *
 * An interval rather than a list because the useful sets are all regular: every 90° is the four block faces,
 * every 45° adds the diagonals you actually build along, and the finer ones are for lining up on something in
 * between. Anything genuinely irregular is a [StickyAngle] instead.
 *
 * Read for pitch as well as yaw, where the same numbers mean level, straight up and straight down: 90 gives
 * −90 / 0 / +90, and 45 puts the halfway looks between them.
 */
enum class StickyInterval(val degrees: Double) {
    OFF(0.0),
    STRAIGHT(90.0),
    DIAGONAL(45.0),
    THIRTY(30.0),
    FIFTEEN(15.0),
}

/**
 * One angle the view sticks to, on top of whatever [StickyInterval] already covers — the way to catch on
 * something the world was not built square to.
 *
 * Deliberately not a data class, for the reason [net.trilleo.region.model.Region] is not: the editor deletes a
 * row by identity, and two entries holding the same axis and the same degrees are a perfectly ordinary thing
 * for a player to end up with while typing.
 *
 * @property axis whether [degrees] is a yaw or a pitch.
 * @property degrees the angle itself, in Minecraft's own units — yaw 0 is south and −90 is east, pitch −90 is
 *   straight up. Normalised on load: yaw into (−180, 180], pitch into [−90, 90].
 * @property enabled off keeps the entry in the list without letting it catch, which is how you find out
 *   whether one of several angles is the one getting in the way.
 */
class StickyAngle(
    var axis: StickyAxis = StickyAxis.YAW,
    var degrees: Double = 0.0,
    var enabled: Boolean = true,
)
