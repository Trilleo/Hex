package net.trilleo.keybind

import com.mojang.blaze3d.platform.InputConstants

/** Small helpers shared between the runtime manager and the config GUI. */
object KeybindFormat {

    /** Human-readable combo, e.g. `Ctrl + Shift + G`, or `Unbound` when no key is set. */
    fun comboLabel(kb: Keybind): String {
        if (!kb.isBound) return "Unbound"
        val sb = StringBuilder()
        if (kb.ctrl) sb.append("Ctrl + ")
        if (kb.shift) sb.append("Shift + ")
        if (kb.alt) sb.append("Alt + ")
        sb.append(keyName(kb.keyCode))
        return sb.toString()
    }

    /** Short one-line summary of what a binding does, for the list screen. */
    fun summary(kb: Keybind): String = when (kb.type) {
        KeybindType.COMMAND -> commandSummary(kb)
        KeybindType.CONTROL_SWITCH -> switchSummary(kb)
    }

    /**
     * The first command (truncated), with a `(+N more)` suffix when there are extra actions, or
     * `No actions` when empty.
     */
    private fun commandSummary(kb: Keybind): String {
        val nonEmpty = kb.actions.filter { it.command.isNotBlank() }
        if (nonEmpty.isEmpty()) return "No actions"
        val first = nonEmpty.first().command.trim()
        val head = if (first.length > 28) first.take(27) + "…" else first
        val extra = nonEmpty.size - 1
        return if (extra > 0) "$head  (+$extra more)" else head
    }

    /**
     * The target control and the keys it cycles between, e.g. `Attack/Destroy: Left Button → J`. Reports
     * the specific misconfiguration instead when the binding would not actually switch anything.
     */
    private fun switchSummary(kb: Keybind): String {
        if (kb.switchTarget.isEmpty()) return "No control selected"
        if (ControlSwitch.resolve(kb) == null) return "Missing control: ${kb.switchTarget}"
        if (kb.switchKeys.size < 2) return "${ControlSwitch.targetLabel(kb)}: needs 2+ keys"

        val target = ControlSwitch.targetLabel(kb)
        // Only the first two keys fit alongside the control name; the rest are counted.
        val shown = kb.switchKeys.take(2).joinToString(" → ") { ControlSwitch.keyLabel(it) }
        val extra = kb.switchKeys.size - 2
        return if (extra > 0) "$target: $shown  (+$extra more)" else "$target: $shown"
    }

    private fun keyName(keyCode: Int): String =
        InputConstants.Type.KEYSYM.getOrCreate(keyCode).displayName.string

    /** True if [keyCode] is one of the left/right Ctrl/Shift/Alt keys. */
    fun isModifierKey(keyCode: Int): Boolean = when (keyCode) {
        InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL,
        InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT,
        InputConstants.KEY_LALT, InputConstants.KEY_RALT -> true

        else -> false
    }
}
