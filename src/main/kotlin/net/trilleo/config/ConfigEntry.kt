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
