package net.trilleo.title.gui

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.config.ConfigCategory
import net.trilleo.config.gui.ConfigEntryList
import net.trilleo.title.Titles
import net.trilleo.title.model.TitleLine
import net.trilleo.title.model.TitlePreset
import net.trilleo.title.model.TitleSpec
import net.trilleo.util.Notify
import java.util.*

/**
 * Edits one [TitleSpec] — the whole of how a title looks and sounds.
 *
 * **This screen is the reason the title helper is worth having.** Four features can pop a title, and before
 * this each of them carried its own three rows for a subtitle, a colour and a duration; every knob added to
 * titles had to be added four times, in four language-file blocks, and could be forgotten in one of them.
 * Here the rows exist once and every feature reaches them through a single **Title style…** button.
 *
 * Reuses [ConfigEntryList] by building a throwaway [ConfigCategory] whose entries close over the spec, in
 * exactly the way [net.trilleo.reminder.gui.ReminderEditScreen] does — inheriting scrolling, keyboard
 * navigation, per-row reset, inline validation and tooltips for nothing.
 *
 * **Preview fires the real title, not a mock-up.** The HUD is drawn before the open screen, so a title shown
 * from here appears behind this menu exactly as it will in play — same fades, same sound, same chroma. A
 * mock-up drawn inside the list would be the one thing that could be wrong about it.
 *
 * ### There is no row for the big line's text
 *
 * The words come from whatever fires the title — a reminder's message, a region's, a rule's — and that is what
 * makes `$0`–`$9` captures work on the big line at all: the owner resolves them once when it arms. A field here
 * that overrode the message would either lose that substitution or need a third resolved value threaded through
 * [net.trilleo.reminder.ReminderState] to keep it, for a second place to type words that already have one. The
 * *subtitle's* text does live here, because nothing else has a second line to offer.
 *
 * @param ownerText the owner's message, for the preview. Read lazily: the owner's text field may be edited
 *   between opening this screen and pressing Preview.
 * @param onChange records the edit with whatever owns the spec — marking a config dirty, marking a reminder
 *   hand-edited. Called on every setter, so this screen never has to know which config it is editing.
 */
class TitleEditScreen(
    private val parent: Screen?,
    private val spec: TitleSpec,
    private val ownerText: () -> String,
    private val onChange: () -> Unit,
) : Screen(Component.translatable("hex.titles.edit.title")) {

    private var list: ConfigEntryList? = null

    override fun init() {
        val listHeight = height - TOP - FOOTER_HEIGHT
        list = addRenderableWidget(ConfigEntryList(minecraft, width, listHeight, TOP, this))

        addRenderableWidget(StringWidget(MARGIN, 12, width - MARGIN * 2, 12, title, font))

        val y = height - 28
        var x = width / 2 - (BUTTON_WIDTH * 2 + GAP) / 2

        addRenderableWidget(
            Button.builder(Component.translatable("hex.titles.edit.preview")) { preview() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.titles.edit.done")) { onClose() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )

        rebuild(preserveScroll = false)
    }

    /** Rebuilds the rows, since a preset rewrites values other rows are already showing. */
    private fun rebuild(preserveScroll: Boolean = true) {
        val category = buildCategory()
        list?.show(listOf(category.title to category.entries), preserveScroll)
    }

    private fun touch() = onChange()

    private fun buildCategory(): ConfigCategory = ConfigCategory.build("title_edit") {
        // First, because it is the fastest way to a title that looks deliberate — and because everything below
        // is then a tweak to what it wrote rather than a blank form to fill in.
        enum(
            "preset",
            default = TitlePreset.CUSTOM,
            get = { TitlePreset.of(spec) },
            set = { preset ->
                preset.applyTo(spec)
                touch()
                // Colours, flags and the sound have all just changed underneath the rows showing them.
                rebuild()
            },
        )

        // ---- the big line ------------------------------------------------------------------------------

        color(
            "color",
            default = "",
            chroma = true,
            get = { spec.title.color },
            set = { spec.title.color = it; touch() },
        )
        styleToggles("", spec.title)

        // ---- the smaller line --------------------------------------------------------------------------

        text(
            "subtitle_text",
            default = "",
            get = { spec.subtitle.text },
            set = { spec.subtitle.text = it; touch() },
        )
        color(
            "subtitle_color",
            default = "",
            chroma = true,
            get = { spec.subtitle.color },
            set = { spec.subtitle.color = it; touch() },
        )
        styleToggles("subtitle_", spec.subtitle)

        // ---- how long it lasts -------------------------------------------------------------------------

        seconds("fade_in", TitleSpec.FADE_MIN, TitleSpec.FADE_MAX, TitleSpec.DEFAULT_FADE_IN,
            { spec.fadeInSeconds }, { spec.fadeInSeconds = it })
        seconds("stay", TitleSpec.STAY_MIN, TitleSpec.STAY_MAX, TitleSpec.DEFAULT_STAY,
            { spec.staySeconds }, { spec.staySeconds = it })
        seconds("fade_out", TitleSpec.FADE_MIN, TitleSpec.FADE_MAX, TitleSpec.DEFAULT_FADE_OUT,
            { spec.fadeOutSeconds }, { spec.fadeOutSeconds = it })

        // ---- what it sounds like -----------------------------------------------------------------------

        // A blank id is what "silent" is stored as, but a blank text field reads as a field nobody has filled
        // in yet rather than as a decision — so the decision gets a switch of its own.
        toggle(
            "sound",
            default = false,
            get = { spec.sound.isNotBlank() },
            set = { on ->
                spec.sound = if (on) DEFAULT_SOUND else ""
                touch()
                rebuild()
            },
        )
        if (spec.sound.isNotBlank()) {
            text(
                "sound_id",
                default = DEFAULT_SOUND,
                get = { spec.sound },
                set = { spec.sound = it; touch() },
                validate = { id ->
                    // Blank is accepted on its way to being retyped; the switch above is what turns sound off.
                    if (id.isBlank() || Notify.soundFor(id) != null) {
                        null
                    } else {
                        Component.translatable("hex.titles.edit.sound.unknown")
                    }
                },
            )
            slider(
                "sound_pitch",
                min = TitleSpec.PITCH_MIN,
                max = TitleSpec.PITCH_MAX,
                step = 0.05,
                default = 1.0,
                get = { spec.pitch },
                set = { spec.pitch = it; touch() },
                format = { String.format(Locale.ROOT, "%.2f", it) },
            )
            slider(
                "sound_volume",
                min = TitleSpec.VOLUME_MIN,
                max = TitleSpec.VOLUME_MAX,
                step = 0.05,
                default = 1.0,
                get = { spec.volume },
                set = { spec.volume = it; touch() },
                format = { String.format(Locale.ROOT, "%.0f%%", it * 100) },
            )
        }
    }

    /**
     * The five style switches for one line.
     *
     * Both lines get the same five, under keys that differ only by [prefix], so the two halves of the screen
     * cannot drift apart and adding a sixth style is one line here rather than two.
     */
    private fun ConfigCategory.Builder.styleToggles(prefix: String, line: TitleLine) {
        toggle("${prefix}bold", default = false, get = { line.bold }, set = { line.bold = it; touch() })
        toggle("${prefix}italic", default = false, get = { line.italic }, set = { line.italic = it; touch() })
        toggle(
            "${prefix}underline",
            default = false,
            get = { line.underline },
            set = { line.underline = it; touch() },
        )
        toggle(
            "${prefix}strikethrough",
            default = false,
            get = { line.strikethrough },
            set = { line.strikethrough = it; touch() },
        )
        toggle(
            "${prefix}obfuscated",
            default = false,
            get = { line.obfuscated },
            set = { line.obfuscated = it; touch() },
        )
    }

    /** One of the three phase-length sliders, all of which read the same and differ only in their bounds. */
    private fun ConfigCategory.Builder.seconds(
        key: String,
        min: Double,
        max: Double,
        default: Double,
        get: () -> Double,
        set: (Double) -> Unit,
    ) {
        slider(
            key,
            min = min,
            max = max,
            step = 0.1,
            default = default,
            get = get,
            set = { set(it); touch() },
            format = { String.format(Locale.ROOT, "%.1fs", it) },
        )
    }

    /**
     * Shows the title as configured, right now.
     *
     * Falls back to a sample message when there is nothing to say yet — a preview that correctly showed
     * nothing, because [Titles.show] drops a blank line, would read as a broken button.
     */
    private fun preview() {
        val text = ownerText().ifBlank { Component.translatable("hex.titles.preview.text").string }
        val subtitle = spec.subtitle.text
            .ifBlank { Component.translatable("hex.titles.preview.subtitle").string }
        Titles.show(minecraft, spec, text = text, subtitle = subtitle)
    }

    override fun onClose() {
        // The title from a preview belongs to this screen, not to the world behind it.
        Titles.clear(minecraft)
        minecraft.setScreen(parent)
    }

    private companion object {
        /** A short, bright, unmistakably deliberate note — the same one a sound action starts from. */
        const val DEFAULT_SOUND = "minecraft:block.note_block.pling"

        const val MARGIN = 24
        const val TOP = 32
        const val FOOTER_HEIGHT = 40
        const val BUTTON_WIDTH = 100
        const val BUTTON_HEIGHT = 20
        const val GAP = 6
    }
}
