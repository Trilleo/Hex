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

	/** Each entry is one line: a leading `/` is sent as a command, otherwise as chat. */
	var commands: MutableList<String> = mutableListOf()

	/** Ticks to wait between consecutive lines (0 = all in the same tick). */
	var delayTicks: Int = 0

	var enabled: Boolean = true

	val isBound: Boolean
		get() = keyCode >= 0
}
