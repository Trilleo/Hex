package net.trilleo.title

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentContents
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import net.trilleo.color.ColorValue
import net.trilleo.sound.SoundPlayer
import net.trilleo.title.model.TitleSpec
import net.trilleo.util.Chroma
import net.trilleo.util.Notify

/**
 * The one way this mod puts a title on the screen.
 *
 * Every feature that announces something — a reminder coming due, a region crossed, a mob found, a chat line
 * matched — hands a [TitleSpec] and two lines of source to [show] and is done. Nothing else calls
 * `Gui.setTitle`, so how a title looks, how long it lasts and what it sounds like is decided in exactly one
 * place, and a feature added later gets colours, styles, chroma, timings and a sound without writing any of it.
 *
 * ### It takes finished source, not a spec to interpret
 *
 * [show] is handed the two lines already merged with the alert's message and already substituted for capture
 * groups. It is a renderer: the only decisions left to it are the ones about *drawing* — the fallback colour,
 * the chroma clock, the fade lengths. Merging lives in [net.trilleo.reminder.ReminderActions], which is the one
 * place that knows what an alert's message is.
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
 *
 * Everything is scheduled through [Minecraft.execute] for the same reason [Notify] is: callers may be on the
 * client tick while the title belongs to the render thread's gui.
 */
object Titles {

    /** The gui counts a title's three phases in ticks; a [TitleSpec] stores seconds. */
    private const val TICKS_PER_SECOND = 20.0

    /** Vanilla's own floor, and a ceiling generous enough for the longest stay the editor offers. */
    private const val TICKS_MIN = 0
    private const val TICKS_MAX = 600

    /**
     * Shows a title.
     *
     * @param title the big line's finished source — codes and words, with the alert's message already merged
     *   in. Blank, or codes with no words, is dropped rather than shown: vanilla renders an empty title as a
     *   silent flash of nothing, and the caller would have no way to tell that from a title that never fired.
     * @param subtitle the smaller line's finished source, or blank for none.
     */
    fun show(client: Minecraft, spec: TitleSpec, title: String, subtitle: String = "") {
        if (!TitleConfig.active) return
        if (title.isBlank()) return

        val settings = TitleConfig.settings
        // Built out here rather than inside the lambda: a LiveText renders on the render thread, and building
        // one is the only work in this method that reads config.
        val titleText = componentFor(title, settings.defaultTitleColor)
        val subtitleText =
            if (subtitle.isBlank()) Component.empty()
            else componentFor(subtitle, settings.defaultSubtitleColor)

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

    /**
     * [raw] as the component a title line draws to, live when it has anything to animate and plain when it
     * does not.
     *
     * Also what the editor's preview calls, so what is drawn there is built by the same code that will build
     * the real thing — a preview that could be wrong about the colours would be worse than none.
     *
     * @param fallbackColor the colour for text that names none of its own, from the Titles tab.
     */
    fun componentFor(raw: String, fallbackColor: String): Component =
        if (Chroma.uses(raw, ColorValue.isChroma(fallbackColor))) {
            LiveText(raw, fallbackColor)
        } else {
            build(raw, fallbackColor)
        }

    /**
     * Plays the title's own sound, if it has one and the master switch allows it.
     *
     * [net.trilleo.title.model.TitleSpec.sound] is a [net.trilleo.sound.SoundValue], so a title's sting can
     * be a saved sequence as well as a single sound — and a blank still means a title that only appears.
     */
    private fun playSound(client: Minecraft, spec: TitleSpec) {
        // The Titles switch is checked here rather than inside SoundPlayer, so turning title sounds off does
        // not also silence the rest of the mod. It is named for titles, and it does what it says.
        if (!TitleConfig.soundsOn || spec.sound.isBlank()) return
        SoundPlayer.play(client, spec.sound, spec.pitch, spec.volume)
    }

    private fun ticks(seconds: Double): Int =
        (seconds * TICKS_PER_SECOND).toInt().coerceIn(TICKS_MIN, TICKS_MAX)

    /**
     * One rendering of [raw], sampled at this instant.
     *
     * [fallbackColor] is the base every character starts from and each `&` code overrides from where it
     * appears, which falls straight out of how [Chroma.build] works. It may itself be chroma, which is how the
     * Titles tab can set every unstyled title flowing at once.
     */
    private fun build(raw: String, fallbackColor: String): MutableComponent {
        val chroma = ColorValue.isChroma(fallbackColor)
        // Masked to RGB: a style colour carries no alpha, and a "#AARRGGBB" pasted into the field would
        // otherwise land in the red channel. Null when chroma, which colours every character itself.
        val base = if (chroma) null else ColorValue.parse(fallbackColor)?.and(ColorValue.RGB_MASK)
        val settings = TitleConfig.settings
        return Chroma.build(
            raw,
            all = chroma,
            baseColor = base,
            seconds = settings.chromaSeconds,
            width = settings.chromaWidth,
        )
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
    private class LiveText(private val raw: String, private val fallbackColor: String) : Component {

        private var frame: Int = Chroma.STATIC
        private var cached: Component = Component.empty()

        private fun current(): Component {
            val now = Chroma.frame()
            if (now != frame) {
                cached = build(raw, fallbackColor)
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
