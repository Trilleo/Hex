package net.trilleo.keybind

/**
 * A single user-defined shortcut: a key (optionally with modifiers) that runs a sequence of
 * commands/chat lines when pressed.
 *
 * Intentionally a plain class (not a `data class`) so equality is identity-based — the GUI and the
 * runtime manager reference specific bindings by identity, and two freshly-added empty bindings must
 * not compare equal.
 */
class Keybind {
    /** Optional display name. Currently unused by the GUI but persisted for future use. */
    var label: String = ""

    /** GLFW key code; `< 0` means unbound. */
    var keyCode: Int = -1

    var ctrl: Boolean = false
    var shift: Boolean = false
    var alt: Boolean = false

    /** The sequence of actions to run when the combo is pressed, each with its own command and delay. */
    var actions: MutableList<KeybindAction> = mutableListOf()

    /**
     * Legacy: the pre-per-action command list. Kept only so [KeybindConfig] can read old JSON and migrate
     * it into [actions]; no longer written or read at runtime.
     */
    var commands: MutableList<String> = mutableListOf()

    /** Legacy: the pre-per-action single delay. Kept only for migration — see [commands]. */
    var delayTicks: Int = 0

    var enabled: Boolean = true

    val isBound: Boolean
        get() = keyCode >= 0
}
