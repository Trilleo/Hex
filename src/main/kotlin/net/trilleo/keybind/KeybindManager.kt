package net.trilleo.keybind

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import java.util.*

/**
 * Detects configured key combos each client tick and applies their effect — running a command sequence or
 * cycling a Minecraft control, depending on the binding's [KeybindType].
 *
 * Detection is done by polling key state (rather than a mixin) so it naturally ignores input while a
 * screen or chat is focused, and is resilient to input-system changes. Modifier matching is *exact*:
 * a `Ctrl+G` binding fires only when Ctrl is held and Shift/Alt are not, so it never collides with a
 * plain-`G` binding.
 */
object KeybindManager {

    /** Bindings whose combo was down last tick — used to fire once per press (edge detection). */
    private val held: MutableSet<Keybind> =
        java.util.Collections.newSetFromMap(IdentityHashMap<Keybind, Boolean>())

    private class Scheduled(var ticksRemaining: Int, val line: String)

    /** Pending command lines awaiting their delay. */
    private val queue = ArrayList<Scheduled>()

    /** Called from the END_CLIENT_TICK handler. */
    fun onEndTick(client: Minecraft) {
        drainQueue(client)
        detect(client)
    }

    private fun detect(client: Minecraft) {
        // Don't fire while typing, in a menu, or not in a world. Clear held so re-entering the world
        // with a key still down doesn't immediately re-fire.
        if (client.player == null || client.screen != null) {
            held.clear()
            return
        }

        val window = client.window
        val ctrl = InputConstants.isKeyDown(window, InputConstants.KEY_LCONTROL) ||
                InputConstants.isKeyDown(window, InputConstants.KEY_RCONTROL)
        val shift = InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT) ||
                InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT)
        val alt = InputConstants.isKeyDown(window, InputConstants.KEY_LALT) ||
                InputConstants.isKeyDown(window, InputConstants.KEY_RALT)

        for (kb in KeybindConfig.keybinds) {
            if (!kb.enabled || !kb.isBound) {
                held.remove(kb)
                continue
            }
            val match = InputConstants.isKeyDown(window, kb.keyCode) &&
                    kb.ctrl == ctrl && kb.shift == shift && kb.alt == alt
            if (match) {
                if (held.add(kb)) fire(client, kb)
            } else {
                held.remove(kb)
            }
        }
    }

    /** Apply a binding's effect. Detection above is shared; only what happens on press differs by type. */
    private fun fire(client: Minecraft, kb: Keybind) {
        when (kb.type) {
            KeybindType.COMMAND -> queueActions(kb)
            KeybindType.CONTROL_SWITCH -> ControlSwitch.apply(client, kb)
        }
    }

    private fun queueActions(kb: Keybind) {
        var delay = 0
        for (action in kb.actions) {
            delay += action.delayTicks.coerceAtLeast(0)   // gap to wait before this action
            val trimmed = action.command.trim()
            if (trimmed.isEmpty()) continue               // skip blank line, but keep its delay contribution
            queue.add(Scheduled(delay, trimmed))
        }
    }

    private fun drainQueue(client: Minecraft) {
        if (queue.isEmpty()) return
        val player = client.player
        if (player == null) {
            queue.clear()
            return
        }
        val ready = ArrayList<String>()
        val it = queue.iterator()
        while (it.hasNext()) {
            val s = it.next()
            if (s.ticksRemaining <= 0) {
                ready.add(s.line)
                it.remove()
            } else {
                s.ticksRemaining--
            }
        }
        for (line in ready) execute(player, line)
    }

    private fun execute(player: LocalPlayer, line: String) {
        val connection = player.connection
        if (line.startsWith("/")) {
            connection.sendCommand(line.substring(1))
        } else {
            connection.sendChat(line)
        }
    }
}
