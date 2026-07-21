package net.trilleo.config.cloth

import com.mojang.blaze3d.platform.InputConstants
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry
import me.shedaniel.clothconfig2.api.ConfigBuilder
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder
import me.shedaniel.clothconfig2.api.Modifier
import me.shedaniel.clothconfig2.api.ModifierKeyCode
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.config.ActionEntry
import net.trilleo.config.BooleanEntry
import net.trilleo.config.ColorEntry
import net.trilleo.config.ConfigEntry
import net.trilleo.config.ConfigProfiles
import net.trilleo.config.ConfigRegistry
import net.trilleo.config.CycleEntry
import net.trilleo.config.EnumEntry
import net.trilleo.config.KeyCombo
import net.trilleo.config.KeybindEntry
import net.trilleo.config.SliderEntry
import net.trilleo.config.TextEntry
import net.trilleo.feature.Features
import java.util.Optional
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import net.trilleo.config.ConfigCategory as HexCategory

/**
 * Renders the backend-agnostic settings model ([HexCategory] / [ConfigEntry]) as a Cloth Config screen.
 *
 * This is the only file that knows Cloth exists, which is the point: features describe their settings in the
 * mod's own vocabulary and this translates. Swapping the GUI toolkit again would mean rewriting this file and
 * nothing else.
 *
 * Two Cloth quirks shape the code below:
 * - Cloth has **no double slider**, only int and long, so every [SliderEntry] is projected onto an integer
 *   grid of `(max - min) / step` notches and mapped back on the way out.
 * - Cloth has **no button entry**, so [ActionEntry] is rendered by the hand-written [ActionListEntry].
 */
object ClothConfigFactory {

    /** Builds the config screen, returning to [parent] when closed. */
    fun create(parent: Screen?): Screen {
        // Filled in as entries are built; see LivePreview for why the menu applies as you drag.
        val appliers = mutableListOf<() -> Unit>()

        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.translatable("hex.config.title"))
            // Cloth buffers edits and applies them on Save, so this is where the writes actually land.
            .setSavingRunnable {
                LivePreview.markSaved()
                ConfigRegistry.flushAll()
            }
            .setAfterInitConsumer { screen -> LivePreview.arm(screen, appliers) }

        // Not chainable: both of these return void rather than the builder.
        builder.setGlobalized(true)
        builder.setGlobalizedExpanded(false)

        val entryBuilder = builder.entryBuilder()
        // Profiles come last: they act on every other tab, so they read as a footer rather than a peer.
        for (category in Features.categories() + ConfigProfiles.category()) {
            val clothCategory = builder.getOrCreateCategory(category.title)
            for (entry in category.entries) {
                clothCategory.addEntry(translate(entryBuilder, entry, appliers))
            }
        }
        return builder.build()
    }

    /**
     * Turns one model row into its Cloth widget, adding to [appliers] if the row is safe to preview live.
     *
     * Only rows whose setter is a plain assignment get an applier. Cycle, text and keybind rows are left out
     * deliberately: the Profiles tab uses them for switching profile and naming a new one, and running those
     * setters every tick would switch profiles continuously and create a profile per keystroke.
     *
     * Tooltips are applied inside each branch rather than through a shared helper on purpose:
     * `FieldBuilder<T, A, SELF>` uses recursive generics that Kotlin's inference handles badly, and a generic
     * extension to factor this out costs far more than the repetition saves.
     */
    private fun translate(
        builder: ConfigEntryBuilder,
        entry: ConfigEntry,
        appliers: MutableList<() -> Unit>,
    ): AbstractConfigListEntry<*> =
        when (entry) {
            is BooleanEntry -> {
                val b = builder.startBooleanToggle(entry.label, entry.get())
                    .setDefaultValue(entry.default)
                    // Cloth defaults to Yes/No; the mod has always said On/Off.
                    .setYesNoTextSupplier { on ->
                        Component.translatable(if (on) "hex.config.on" else "hex.config.off")
                    }
                    .setSaveConsumer { value -> entry.set(value) }
                entry.tooltip?.let { b.setTooltip(it) }
                b.build().also { built -> appliers += { entry.set(built.value) } }
            }

            is SliderEntry -> {
                val steps = max(1, ((entry.max - entry.min) / entry.step).roundToLong().toInt())
                val b = builder.startIntSlider(entry.label, toNotch(entry, entry.get()), 0, steps)
                    .setDefaultValue(toNotch(entry, entry.default))
                    .setTextGetter { notch -> entry.format(fromNotch(entry, notch)) }
                    .setSaveConsumer { notch -> entry.set(fromNotch(entry, notch)) }
                entry.tooltip?.let { b.setTooltip(it) }
                b.build().also { built -> appliers += { entry.set(fromNotch(entry, built.value)) } }
            }

            is CycleEntry -> {
                val indices: Array<Int> = Array(entry.options.size) { it }
                val current = entry.get().coerceIn(0, max(0, entry.options.size - 1))
                val b = builder.startSelector(entry.label, indices, current)
                    .setDefaultValue(entry.default)
                    .setNameProvider { index -> entry.options.getOrElse(index) { Component.empty() } }
                    .setSaveConsumer { index -> entry.set(index) }
                entry.tooltip?.let { b.setTooltip(it) }
                b.build()
            }

            is TextEntry -> {
                val b = builder.startStrField(entry.label, entry.get())
                    .setDefaultValue(entry.default)
                    .setErrorSupplier { value -> Optional.ofNullable(entry.validate(value)) }
                    .setSaveConsumer { value -> entry.set(value) }
                entry.tooltip?.let { b.setTooltip(it) }
                b.build()
            }

            is ColorEntry -> {
                val b = builder.startColorField(entry.label, parseColor(entry.get(), entry.alpha))
                    .setAlphaMode(entry.alpha)
                    .setDefaultValue(parseColor(entry.default, entry.alpha))
                    .setSaveConsumer { packed -> entry.set(formatColor(packed, entry.alpha)) }
                entry.tooltip?.let { b.setTooltip(it) }
                b.build().also { built ->
                    appliers += { entry.set(formatColor(built.value, entry.alpha)) }
                }
            }

            is KeybindEntry -> {
                val b = builder.startModifierKeyCodeField(entry.label, toKeyCode(entry.get()))
                    .setAllowModifiers(true)
                    .setModifierDefaultValue { toKeyCode(entry.default) }
                    .setModifierSaveConsumer { code -> entry.set(fromKeyCode(code)) }
                entry.tooltip?.let { b.setTooltip(it) }
                b.build()
            }

            // The star projection has to be reintroduced as a concrete type parameter for the builder call.
            // Enum generics are erased at runtime, so the placeholder below is only ever a compile-time name.
            is EnumEntry<*> -> {
                @Suppress("UNCHECKED_CAST")
                buildEnum(builder, entry as EnumEntry<PlaceholderEnum>, appliers)
            }

            is ActionEntry -> ActionListEntry(
                entry.label,
                { Optional.ofNullable(entry.tooltip?.let { arrayOf(it) }) },
                entry.onClick,
            )
        }

    private fun <T : Enum<T>> buildEnum(
        builder: ConfigEntryBuilder,
        entry: EnumEntry<T>,
        appliers: MutableList<() -> Unit>,
    ): AbstractConfigListEntry<*> {
        val b = builder.startEnumSelector(entry.label, entry.type, entry.get())
            .setDefaultValue(entry.default)
            // Cloth deliberately leaves this callback unparameterised, so the constant comes back as a raw Enum.
            .setEnumNameProvider { constant ->
                @Suppress("UNCHECKED_CAST")
                entry.nameOf(constant as T)
            }
            .setSaveConsumer { value -> entry.set(value) }
        entry.tooltip?.let { b.setTooltip(it) }
        return b.build().also { built -> appliers += { entry.set(built.value) } }
    }

    /** Stand-in for the erased enum type in [translate]; never instantiated. */
    private enum class PlaceholderEnum

    // ---- slider quantisation -------------------------------------------------------------------------

    /** Which notch on the integer grid a value sits at. */
    private fun toNotch(entry: SliderEntry, value: Double): Int {
        val steps = max(1, ((entry.max - entry.min) / entry.step).roundToLong().toInt())
        return ((value - entry.min) / entry.step).roundToInt().coerceIn(0, steps)
    }

    /**
     * The value a notch stands for, rounded to the precision the step implies. Without that rounding a
     * 0.01-step slider walks off into values like `0.30000000000000004`, which then reach the config file.
     */
    private fun fromNotch(entry: SliderEntry, notch: Int): Double {
        val decimals = when {
            entry.step >= 1.0 -> 0
            entry.step >= 0.1 -> 1
            entry.step >= 0.01 -> 2
            else -> 3
        }
        var factor = 1.0
        repeat(decimals) { factor *= 10.0 }
        val raw = entry.min + notch * entry.step
        return ((raw * factor).roundToLong() / factor).coerceIn(entry.min, entry.max)
    }

    // ---- colour <-> packed int ----------------------------------------------------------------------

    /** Parses `"#RRGGBB"` / `"#AARRGGBB"`; an unparseable value falls back to opaque white. */
    private fun parseColor(value: String, alpha: Boolean): Int {
        val hex = value.removePrefix("#")
        val parsed = hex.toLongOrNull(16) ?: return if (alpha) -1 else 0xFFFFFF
        return if (alpha) parsed.toInt() else (parsed.toInt() and 0xFFFFFF)
    }

    /** Renders a packed colour back to the hex string kept in the config file. */
    private fun formatColor(packed: Int, alpha: Boolean): String =
        if (alpha) {
            String.format(Locale.ROOT, "#%08X", packed)
        } else {
            String.format(Locale.ROOT, "#%06X", packed and 0xFFFFFF)
        }

    // ---- keybind <-> Cloth key code -----------------------------------------------------------------

    /** Modifier flag order is (alt, control, shift) — verified against `Modifier.of`'s bit assignment. */
    private fun toKeyCode(combo: KeyCombo): ModifierKeyCode = ModifierKeyCode.of(
        InputConstants.Type.KEYSYM.getOrCreate(combo.keyCode),
        Modifier.of(combo.alt, combo.ctrl, combo.shift),
    )

    private fun fromKeyCode(code: ModifierKeyCode): KeyCombo = KeyCombo(
        keyCode = code.keyCode.value,
        ctrl = code.modifier.hasControl(),
        shift = code.modifier.hasShift(),
        alt = code.modifier.hasAlt(),
    )
}
