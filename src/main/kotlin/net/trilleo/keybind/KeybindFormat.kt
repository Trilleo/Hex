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

    /**
     * Short one-line summary of a binding's actions for the list screen: the first command (truncated), with
     * a `(+N more)` suffix when there are extra actions, or `No actions` when empty.
     */
    fun summary(kb: Keybind): String {
        val nonEmpty = kb.actions.filter { it.command.isNotBlank() }
        if (nonEmpty.isEmpty()) return "No actions"
        val first = nonEmpty.first().command.trim()
        val head = if (first.length > 28) first.take(27) + "…" else first
        val extra = nonEmpty.size - 1
        return if (extra > 0) "$head  (+$extra more)" else head
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
