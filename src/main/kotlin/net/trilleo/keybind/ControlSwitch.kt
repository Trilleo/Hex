package net.trilleo.keybind

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.trilleo.util.Notify

/**
 * Runs [KeybindType.CONTROL_SWITCH] bindings: cycles a Minecraft control to the next key in the binding's
 * list, exactly as if the player had rebound it in Options → Controls.
 *
 * Reading a mapping's *current* key needs a little care — `KeyMapping.key` is protected and has no public
 * getter, but [KeyMapping.saveString] returns that key's name (`key.keyboard.j`, `key.mouse.left`), which
 * [InputConstants.getKey] parses back. Storing [Keybind.switchKeys] in that same string form means the
 * current key can be matched against the list directly, and no accessor mixin is needed.
 *
 * Also hosts the label helpers the config screens use, so a control and a key are formatted identically
 * wherever they appear.
 */
object ControlSwitch {

    /** A switch needs at least this many keys to have somewhere to cycle to. */
    private const val MIN_KEYS = 2

    /** Pitch for the confirmation click — above a normal button press so the switch is audibly distinct. */
    private const val SOUND_PITCH = 1.2f

    /**
     * Advance [kb]'s target control to its next key, then notify the player.
     *
     * Does nothing (silently) when the binding is not usable: an unset or unresolvable target, or fewer
     * than [MIN_KEYS] valid keys. Those cases are the player's to fix and are flagged in the config screen,
     * so failing quietly beats spamming chat on every keypress.
     */
    fun apply(client: Minecraft, kb: Keybind) {
        val mapping = resolve(kb) ?: return

        // Drop unparseable names rather than failing the whole cycle, so one bad entry can't brick a binding.
        val keys = kb.switchKeys.mapNotNull { name -> runCatching { InputConstants.getKey(name) }.getOrNull() }
        if (keys.size < MIN_KEYS) return

        // If the control was rebound outside the mod its key isn't in the list; start the cycle over at the
        // first key rather than doing nothing.
        val current = keys.indexOfFirst { it.name == mapping.saveString() }
        val next = keys[if (current < 0) 0 else (current + 1) % keys.size]

        mapping.setKey(next)
        // Release first so a key held right now can't leave the action stuck down, then rebuild the
        // key -> mappings index — without resetMapping the options screen shows the new key while input
        // still fires on the old one.
        KeyMapping.releaseAll()
        KeyMapping.resetMapping()
        client.options.save()

        // Note: the action's name is `translatable(mapping.name)` — `getTranslatedKeyMessage()` would give
        // the *bound key*'s name, which is the right-hand side of this message, not the left.
        Notify.send(
            client,
            Notify.line("", ChatFormatting.AQUA)
                .append(Component.translatable(mapping.name).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" → ").withStyle(ChatFormatting.GRAY))
                .append(next.displayName.copy().withStyle(ChatFormatting.GREEN)),
        )
        Notify.uiSound(client, SOUND_PITCH)
    }

    /** The target [KeyMapping], or null when unset or no longer registered (e.g. its mod was removed). */
    fun resolve(kb: Keybind): KeyMapping? =
        if (kb.switchTarget.isEmpty()) null else KeyMapping.get(kb.switchTarget)

    /** True when [kb] is fully configured and would actually switch something. */
    fun isUsable(kb: Keybind): Boolean = resolve(kb) != null && kb.switchKeys.size >= MIN_KEYS

    /** Display name of [kb]'s target control, or a self-explaining placeholder when it can't be shown. */
    fun targetLabel(kb: Keybind): String = when {
        kb.switchTarget.isEmpty() -> "No control selected"
        else -> KeyMapping.get(kb.switchTarget)?.let { Component.translatable(it.name).string }
            ?: "Missing: ${kb.switchTarget}"
    }

    /** Display name for a stored key name, e.g. `key.mouse.left` -> `Left Button`. */
    fun keyLabel(name: String): String =
        runCatching { InputConstants.getKey(name).displayName.string }.getOrDefault("?")
}
