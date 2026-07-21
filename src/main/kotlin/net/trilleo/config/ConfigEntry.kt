package net.trilleo.config

import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * One configurable row inside a [ConfigCategory]. Kept as a small sealed hierarchy so a renderer switches on
 * the concrete type in a single `when`; adding a new control is a new subtype plus one branch there, and
 * nothing else.
 *
 * This is a backend-agnostic description of a setting, deliberately free of any GUI-toolkit types — it says
 * what the setting *is*, not how it is drawn. [net.trilleo.config.gui.ConfigEntryList] turns each one into a
 * widget row, and could be replaced wholesale without any feature changing.
 *
 * Every value-carrying entry reports its `default`, which is what lets a renderer offer "reset this row"
 * without each feature hand-rolling its own reset action.
 */
sealed interface ConfigEntry {
    /** The row's left-hand label. */
    val label: Component

    /** Optional hover text for the row's control, or null for none. */
    val tooltip: Component?
}

/**
 * A boolean setting, rendered as an on/off toggle. [get] is read when the screen is built and [set] receives
 * the flipped value, so a setter can persist immediately (or mark dirty) rather than relying on a later flush.
 */
class BooleanEntry(
    override val label: Component,
    override val tooltip: Component?,
    val default: Boolean,
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
 * A multiple-choice setting over [options]. [get] returns the current option's index and [set] receives the
 * chosen index.
 *
 * Prefer [EnumEntry] when the choices are a Kotlin enum — it keeps the type rather than flattening to an
 * index. This entry is for choices that are genuinely dynamic, such as the list of saved profile names.
 */
class CycleEntry(
    override val label: Component,
    override val tooltip: Component?,
    val options: List<Component>,
    val default: Int,
    val get: () -> Int,
    val set: (Int) -> Unit,
) : ConfigEntry

/**
 * A numeric setting over `[min, max]` snapped to [step]. [format] turns the current value into the handle's
 * label (e.g. `"1.25x"`).
 *
 * Unlike the other entries, [set] can fire continuously while the handle is dragged rather than once per
 * click, so a setter that persists should mark the config dirty rather than writing the file outright.
 */
class SliderEntry(
    override val label: Component,
    override val tooltip: Component?,
    val min: Double,
    val max: Double,
    val step: Double,
    val default: Double,
    val get: () -> Double,
    val set: (Double) -> Unit,
    val format: (Double) -> Component,
) : ConfigEntry

/**
 * A free-text setting. [validate] returns an error to show beneath the field for an unacceptable value, or
 * null when the value is fine; a renderer is expected to refuse to save while an error stands.
 */
class TextEntry(
    override val label: Component,
    override val tooltip: Component?,
    val default: String,
    val get: () -> String,
    val set: (String) -> Unit,
    val validate: (String) -> Component? = { null },
) : ConfigEntry

/**
 * A colour setting, carried as `"#RRGGBB"` (or `"#AARRGGBB"` when [alpha] is set) rather than a packed int,
 * so the JSON stays readable for anyone editing a config file by hand.
 */
class ColorEntry(
    override val label: Component,
    override val tooltip: Component?,
    val default: String,
    val alpha: Boolean,
    val get: () -> String,
    val set: (String) -> Unit,
) : ConfigEntry

/**
 * A single key combination, expressed in the mod's own [KeyCombo] vocabulary so that no GUI-toolkit type
 * leaks into the settings model.
 */
class KeybindEntry(
    override val label: Component,
    override val tooltip: Component?,
    val default: KeyCombo,
    val get: () -> KeyCombo,
    val set: (KeyCombo) -> Unit,
) : ConfigEntry

/**
 * A choice among an enum's constants. [nameOf] renders one constant; keep it translation-backed rather than
 * relying on [Enum.name].
 */
class EnumEntry<T : Enum<T>>(
    override val label: Component,
    override val tooltip: Component?,
    val type: Class<T>,
    val default: T,
    val get: () -> T,
    val set: (T) -> Unit,
    val nameOf: (T) -> Component,
) : ConfigEntry

/**
 * A key plus its modifiers — the same shape [net.trilleo.keybind.Keybind] already persists, so a keybind row
 * and a stored binding speak the same language without either depending on the GUI toolkit.
 */
data class KeyCombo(
    val keyCode: Int,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
)
