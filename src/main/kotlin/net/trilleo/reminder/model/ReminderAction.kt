package net.trilleo.reminder.model

/**
 * What a reminder does when it fires.
 *
 * Only the two delivery routes the mod actually implements are declared. Chat lines and on-screen titles are
 * both plausible additions, and adding either is an appended constant plus one branch in
 * [net.trilleo.reminder.ReminderActions] — exactly the cheap operation this model is shaped for. Declaring
 * them ahead of implementing them would be strictly worse than not declaring them: the constant would survive
 * the normalizer (it is a name this build knows), so a hand-edited file naming it would produce a reminder
 * that fires and does nothing at all, which is indistinguishable from a broken one.
 */
enum class ActionKind {
    /** Show the reminder on the HUD panel and flash it. */
    HUD,

    /** Play a sound. */
    SOUND,
}

/**
 * One thing a reminder does when it fires. A reminder holds a list of these, so it can flash and beep at once.
 *
 * Plain, `var`-only and no-arg constructible for the same GSON reasons as [Trigger].
 */
class ReminderAction {
    var kind: ActionKind = ActionKind.HUD

    /**
     * For [ActionKind.SOUND], the sound event id, e.g. `"minecraft:block.note_block.pling"`. Unused by
     * [ActionKind.HUD].
     *
     * Deliberately not validated by the normalizer: the sound registry is not necessarily populated when
     * configs load at feature init, so an id is resolved when it is played (falling back to the standard UI
     * click) and checked inline by the editor, which can actually report the problem.
     */
    var value: String = DEFAULT_SOUND

    var pitch: Double = 1.0
    var volume: Double = 1.0

    companion object {
        /** A short, bright, unmistakably deliberate note — distinct from any sound the game plays on its own. */
        const val DEFAULT_SOUND: String = "minecraft:block.note_block.pling"

        const val PITCH_MIN: Double = 0.5
        const val PITCH_MAX: Double = 2.0
        const val VOLUME_MIN: Double = 0.0
        const val VOLUME_MAX: Double = 1.0
    }
}
