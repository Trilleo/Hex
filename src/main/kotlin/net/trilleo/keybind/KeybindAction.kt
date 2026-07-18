package net.trilleo.keybind

/**
 * A single step within a [Keybind]: one command/chat line plus the delay to wait before it runs.
 *
 * A plain (identity-based) class, matching [Keybind] — the GUI references specific action rows by
 * identity while editing them in place.
 */
class KeybindAction {
    /** One line: a leading `/` is sent as a command, otherwise as chat. */
    var command: String = ""

    /** Ticks to wait before this action runs, measured from the previous action (20 ticks = 1 second). */
    var delayTicks: Int = 0
}
