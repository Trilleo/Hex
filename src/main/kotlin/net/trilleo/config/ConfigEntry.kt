package net.trilleo.config

import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * One configurable row inside a [ConfigCategory], rendered by
 * [net.trilleo.config.gui.ConfigScreen]. Kept as a small sealed hierarchy so the screen switches on the
 * concrete type in a single `when`; adding a new control (e.g. a cycle or a number) is a new subtype plus
 * one branch there, and nothing else.
 */
sealed interface ConfigEntry {
    /** The row's left-hand label. */
    val label: Component

    /** Optional hover text for the row's control, or null for none. */
    val tooltip: Component?
}

/**
 * A boolean setting, rendered as an On/Off toggle button. [get] is read each time the screen is built and
 * [set] is called with the flipped value on click — so a setter can persist immediately (e.g. call
 * `save()`) rather than relying on a later flush.
 */
class BooleanEntry(
    override val label: Component,
    override val tooltip: Component?,
    val get: () -> Boolean,
    val set: (Boolean) -> Unit,
) : ConfigEntry

/**
 * A button that performs an action. [onClick] receives the current [Screen] so it can open a sub-screen
 * (via `minecraft.setScreen`) or run one-off logic such as an on-demand check.
 */
class ActionEntry(
    override val label: Component,
    override val tooltip: Component?,
    val onClick: (Screen) -> Unit,
) : ConfigEntry

/**
 * A multiple-choice setting, rendered as a button that cycles through [options] on click. [get] returns the
 * current option's index (read each time the screen is built) and [set] receives the next index on click,
 * so a setter can persist immediately like [BooleanEntry].
 */
class CycleEntry(
    override val label: Component,
    override val tooltip: Component?,
    val options: List<Component>,
    val get: () -> Int,
    val set: (Int) -> Unit,
) : ConfigEntry

/**
 * A numeric setting, rendered as a drag slider over `[min, max]` snapped to [step]. [format] turns the
 * current value into the slider's label (e.g. `"1.25x"`).
 *
 * Unlike the other entries, [set] fires continuously while the handle is dragged rather than once per
 * click — so a setter that persists must be cheap enough to run every frame of a drag. Values are read
 * through [get] only when the screen is built; the live value during a drag lives in the widget.
 */
class SliderEntry(
    override val label: Component,
    override val tooltip: Component?,
    val min: Double,
    val max: Double,
    val step: Double,
    val get: () -> Double,
    val set: (Double) -> Unit,
    val format: (Double) -> Component,
) : ConfigEntry
