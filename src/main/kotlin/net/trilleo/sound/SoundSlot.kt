package net.trilleo.sound

/**
 * A named piece of mod feedback, so the short clicks features make are settings rather than literals.
 *
 * Before this existed, six features called the sound API directly with a pitch constant of their own —
 * 1.4 here for "on", 0.7 there for "that did nothing". The pitches were chosen to be distinguishable from
 * each other, which is exactly what makes them a small shared vocabulary rather than six private decisions,
 * and none of them was reachable from the settings menu.
 *
 * Each constant's [defaultPitch] is the pitch that call site already used, so a fresh install sounds
 * identical to the build before them. Because a slot holds a [SoundValue], any of them can now be a whole
 * sequence instead.
 *
 * **There is deliberately no `DELETED`.** No call site plays a sound when something is removed, so shipping
 * the slot would produce a setting that visibly does nothing — the same argument
 * [net.trilleo.reminder.model.ActionKind] makes about declaring a constant ahead of implementing it. Adding
 * one later is an appended constant, a field on [SoundSlots], and one row on the tab.
 */
enum class SoundSlot(val defaultPitch: Double) {

    /** Something was switched on, added, or armed. */
    TOGGLE_ON(1.4),

    /** Something was switched off or removed. */
    TOGGLE_OFF(0.8),

    /** The action was refused — nothing to act on, or not allowed here. */
    DENIED(0.7),

    /** Something was captured out of the world: a region, an entity, an item. */
    CAPTURED(1.8),

    /** A control switch flipped the player between two bindings. */
    SWITCHED(1.2),
    ;

    /** This slot's live settings. */
    fun settings(): SoundSlotSettings = SoundConfig.settings.slots.let {
        when (this) {
            TOGGLE_ON -> it.toggleOn
            TOGGLE_OFF -> it.toggleOff
            DENIED -> it.denied
            CAPTURED -> it.captured
            SWITCHED -> it.switched
        }
    }

    /** The key suffix this slot's row uses on the Sounds tab, e.g. `feedback_toggle_on`. */
    fun configKey(): String = "feedback_" + name.lowercase()
}
