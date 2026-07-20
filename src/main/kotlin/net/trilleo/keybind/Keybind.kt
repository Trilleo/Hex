package net.trilleo.keybind

/** What a [Keybind] does when its combo is pressed. */
enum class KeybindType {
    /** Runs the binding's [Keybind.actions] as chat lines / commands. */
    COMMAND,

    /** Cycles a Minecraft control keybind through [Keybind.switchKeys]. */
    CONTROL_SWITCH,
}

/**
 * A single user-defined shortcut: a key (optionally with modifiers) that, when pressed, either runs a
 * sequence of commands/chat lines or cycles a Minecraft control to its next configured key.
 *
 * Both kinds live in one class and one list so the trigger-combo capture, the enable/delete/paging GUI,
 * and the manager's per-tick edge detection are shared; only the effect differs — see [type].
 *
 * Intentionally a plain class (not a `data class`) so equality is identity-based — the GUI and the
 * runtime manager reference specific bindings by identity, and two freshly-added empty bindings must
 * not compare equal.
 */
class Keybind {
    /** Optional display name. Currently unused by the GUI but persisted for future use. */
    var label: String = ""

    /** Which effect this binding has. Absent in pre-switch configs; [KeybindConfig] defaults it to [KeybindType.COMMAND]. */
    var type: KeybindType = KeybindType.COMMAND

    /** GLFW key code; `< 0` means unbound. */
    var keyCode: Int = -1

    var ctrl: Boolean = false
    var shift: Boolean = false
    var alt: Boolean = false

    /**
     * [KeybindType.COMMAND] only: the sequence of actions to run when the combo is pressed, each with its
     * own command and delay.
     */
    var actions: MutableList<KeybindAction> = mutableListOf()

    /**
     * [KeybindType.CONTROL_SWITCH] only: the target control's name, i.e. its `KeyMapping` translation key
     * such as `key.attack`. Empty means unset. Stored by name (not by object) so it survives restarts and
     * stays inert — rather than crashing — if the owning mod is uninstalled.
     */
    var switchTarget: String = ""

    /**
     * [KeybindType.CONTROL_SWITCH] only: the keys to cycle the target through, in order, as
     * `InputConstants.Key` names (`key.keyboard.j`, `key.mouse.left`). This is the same string form
     * `KeyMapping.saveString()` returns, so the current key can be matched against this list directly.
     * Fewer than two entries makes the binding a no-op.
     */
    var switchKeys: MutableList<String> = mutableListOf()

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
