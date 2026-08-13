package net.trilleo.title

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentContents
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import net.trilleo.color.ColorValue
import net.trilleo.title.model.TitleLine
import net.trilleo.title.model.TitleSpec
import net.trilleo.util.Chroma
import net.trilleo.util.Notify

/**
 * The one way this mod puts a title on the screen.
 *
 * Every feature that announces something — a reminder coming due, a region crossed, a mob found, a chat line
 * matched — hands a [TitleSpec] to [show] and is done. Nothing else calls `Gui.setTitle`, so how a title looks,
 * how long it lasts and what it sounds like is decided in exactly one place, and a feature added later gets
 * colours, styles, chroma, timings and a sound without writing any of it.
 *
 * ### Times are set on every show, never once at startup
 *
 * The gui's fade values are global and any other mod — or the server, via a title packet — may have moved them
 * since; re-stating them is three field writes and makes a title look the same however it was reached.
 *
 * ### Why chroma works here
 *
 * A title is a `Component` handed to the gui once and drawn for the next few seconds, which is normally exactly
 * the situation the project's rule about chroma forbids: a colour baked into a cached component cannot animate.
 * The way out is [LiveText] — `Gui.setTitle` takes the `Component` *interface*, and `Gui.extractTitle` re-reads
 * the field every frame, so a component that rebuilds itself when asked animates with no mixin and no ticking.
 * That is the whole reason the colour rows in [net.trilleo.title.gui.TitleEditScreen] may offer chroma at all.
 *
 * Everything is scheduled through [Minecraft.execute] for the same reason [Notify] is: callers may be on the
 * client tick while the title belongs to the render thread's gui.
 */
object Titles {

    /** The gui counts a title's three phases in ticks; a [TitleSpec] stores seconds. */
    private const val TICKS_PER_SECOND = 20.0

    /** Vanilla's own floor and ceiling for a phase length. */
    private const val TICKS_MIN = 0
    private const val TICKS_MAX = 600

    /**
     * Shows the title [spec] describes.
     *
     * @param text the big line, for a caller that supplies its own words — a reminder's resolved message, a
     *   region's name. Null falls back to the spec's own [TitleLine.text], which is what a title that owns its
     *   words uses.
     * @param subtitle the same for the second line. Null falls back to the spec's own.
     *
     * A blank main line is dropped rather than shown, because vanilla would render an empty title as a silent
     * flash of nothing and the caller would have no way to tell that from a title that never fired.
     */
    fun show(client: Minecraft, spec: TitleSpec, text: String? = null, subtitle: String? = null) {
        if (!TitleConfig.active) return

        val main = text ?: spec.title.text
        if (main.isBlank()) return
        val second = subtitle ?: spec.subtitle.text

        val settings = TitleConfig.settings
        val titleText = componentFor(main, spec.title, settings.defaultTitleColor)
        // Built out here rather than inside the lambda: a LiveText renders on the render thread, and building
        // one is the only work in this method that reads config.
        val subtitleText =
            if (second.isBlank()) Component.empty()
            else componentFor(second, spec.subtitle, settings.defaultSubtitleColor)

        client.execute {
            val gui = client.gui
            gui.setTimes(ticks(spec.fadeInSeconds), ticks(spec.staySeconds), ticks(spec.fadeOutSeconds))
            // Always set, even to nothing: the gui holds the subtitle until something replaces it, so an alert
            // with no subtitle would otherwise inherit the last one that had one.
            gui.setSubtitle(subtitleText)
            // Last, because this is the call that starts the countdown the two are drawn for.
            gui.setTitle(titleText)
        }

        playSound(client, spec)
    }

    /** Clears whatever title is on screen. */
    fun clear(client: Minecraft) {
        client.execute { client.gui.clearTitles() }
    }

    /** Plays the title's own sound, if it has one and the master switch allows it. */
    private fun playSound(client: Minecraft, spec: TitleSpec) {
        if (!TitleConfig.soundsOn || spec.sound.isBlank()) return
        Notify.uiSound(client, spec.sound, spec.pitch.toFloat(), spec.volume.toFloat())
    }

    private fun ticks(seconds: Double): Int =
        (seconds * TICKS_PER_SECOND).toInt().coerceIn(TICKS_MIN, TICKS_MAX)

    /**
     * [raw] as a component styled by [line], live when it has anything to animate and plain when it does not.
     *
     * The plain case is the common one and costs one build; a title that never changes must not pay for a
     * rebuild on every frame it is on screen.
     */
    private fun componentFor(raw: String, line: TitleLine, fallbackColor: String): Component {
        val colorSpec = line.color.ifBlank { fallbackColor }
        return if (Chroma.uses(raw, ColorValue.isChroma(colorSpec))) {
            // Copied, not referenced: a title is what it was at the moment it was shown, and the line this came
            // from is live config that an open editor is free to keep changing. Without the copy the two paths
            // would disagree — a plain title bakes its styles here, and a flowing one would go on reading them.
            LiveText(raw, line.copy(), colorSpec)
        } else {
            build(raw, line, colorSpec)
        }
    }

    /**
     * One rendering of [raw] in [line]'s look, sampled at this instant.
     *
     * The line's colour and flags are the baseline and the `&` codes inside the text override it from where
     * they appear, which falls straight out of how [Chroma.build] works: it sets a style on each child for only
     * the things a code turned on, so anything untouched inherits from the parent this sets here.
     */
    private fun build(raw: String, line: TitleLine, colorSpec: String): MutableComponent {
        val chroma = ColorValue.isChroma(colorSpec)
        // Masked to RGB: a style colour carries no alpha, and a "#AARRGGBB" pasted into the field would
        // otherwise land in the red channel. Null when chroma, which colours every character itself.
        val base = if (chroma) null else ColorValue.parse(colorSpec)?.and(ColorValue.RGB_MASK)
        val settings = TitleConfig.settings
        val component = Chroma.build(
            raw,
            all = chroma,
            baseColor = base,
            seconds = settings.chromaSeconds,
            width = settings.chromaWidth,
        )
        if (!line.styled()) return component
        return component.withStyle { style -> styled(style, line) }
    }

    /**
     * [style] with [line]'s flags applied.
     *
     * Only the flags that are *on* are set, so anything untouched stays null and inherits — which is what lets
     * a `&l` inside the text add bolding to a line that did not ask for it, rather than being overruled.
     */
    private fun styled(style: Style, line: TitleLine): Style {
        var result = style
        if (line.bold) result = result.withBold(true)
        if (line.italic) result = result.withItalic(true)
        if (line.underline) result = result.withUnderlined(true)
        if (line.strikethrough) result = result.withStrikethrough(true)
        if (line.obfuscated) result = result.withObfuscated(true)
        return result
    }

    /**
     * A component that re-renders itself as the chroma clock moves.
     *
     * `Gui.extractTitle` reads the `title` field afresh on every frame and asks it for its width and its
     * visual-order text, and both of those questions land here — so rebuilding on a new [Chroma.frame] is all
     * it takes to make a title flow. The build is cached within a frame because a single draw asks more than
     * once (the width, then the glyphs, then the backdrop), and re-sampling the clock between those would tear
     * the colours across one frame.
     *
     * Every method delegates rather than being computed here: `visit` and `getString` are default methods on
     * [Component] that walk [getContents] and [getSiblings], so forwarding those three carries the rest.
     *
     * Not thread-safe, and does not need to be — a title component is built on whichever thread called
     * [show] and then read only by the render thread.
     */
    private class LiveText(
        private val raw: String,
        private val line: TitleLine,
        private val colorSpec: String,
    ) : Component {

        private var frame: Int = Chroma.STATIC
        private var cached: Component = Component.empty()

        private fun current(): Component {
            val now = Chroma.frame()
            if (now != frame) {
                cached = build(raw, line, colorSpec)
                frame = now
            }
            return cached
        }

        override fun getStyle(): Style = current().style

        override fun getContents(): ComponentContents = current().contents

        override fun getSiblings(): MutableList<Component> = current().siblings

        override fun getVisualOrderText(): FormattedCharSequence = current().visualOrderText
    }
}
