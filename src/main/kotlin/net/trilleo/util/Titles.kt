package net.trilleo.util

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * The big centre-of-screen title, driven client-side.
 *
 * Vanilla's title is a three-phase animation the [net.minecraft.client.gui.Gui] owns outright, so showing one
 * is a matter of setting the times and then the text — there is no state to keep here, and nothing to tick.
 * Everything is scheduled through [Minecraft.execute] for the same reason [Notify] is: callers may be on the
 * client tick while the title belongs to the render thread's gui.
 *
 * **Times are set on every show, never once at startup.** The gui's fade values are global and any other mod —
 * or the server, via a title packet — may have moved them since; re-stating them is one field write and makes
 * a reminder's title look the same however it was reached.
 */
object Titles {

    /** Vanilla's own defaults, in ticks, and the range a setting is allowed to pick from. */
    const val FADE_IN_TICKS: Int = 10
    const val STAY_TICKS: Int = 70
    const val FADE_OUT_TICKS: Int = 20

    const val TICKS_MIN: Int = 0
    const val TICKS_MAX: Int = 200

    /**
     * Shows [text] as a title, with [subtitle] beneath it when it is not blank.
     *
     * A blank [text] is dropped rather than shown, because vanilla would render an empty title as a silent
     * flash of nothing and the caller would have no way to tell that from a title that never fired.
     */
    fun show(
        client: Minecraft,
        text: String,
        subtitle: String = "",
        color: Int? = null,
        fadeIn: Int = FADE_IN_TICKS,
        stay: Int = STAY_TICKS,
        fadeOut: Int = FADE_OUT_TICKS,
    ) {
        if (text.isBlank()) return
        client.execute {
            val gui = client.gui
            gui.setTimes(
                fadeIn.coerceIn(TICKS_MIN, TICKS_MAX),
                stay.coerceIn(TICKS_MIN, TICKS_MAX),
                fadeOut.coerceIn(TICKS_MIN, TICKS_MAX),
            )
            // Always set, even to nothing: the gui holds the subtitle until something replaces it, so an
            // alert with no subtitle would otherwise inherit the last one that had one.
            gui.setSubtitle(if (subtitle.isBlank()) Component.empty() else colored(subtitle, color))
            // Last, because this is the call that starts the countdown the two are drawn for.
            gui.setTitle(colored(text, color))
        }
    }

    /** Clears whatever title is on screen. */
    fun clear(client: Minecraft) {
        client.execute { client.gui.clearTitles() }
    }

    /**
     * [text] as a component, tinted when [color] is given.
     *
     * The text keeps its own `§` codes either way — a player who wrote them into the message means them, and
     * a style colour applies only to the parts that do not set one of their own.
     */
    private fun colored(text: String, color: Int?): Component {
        val component = Component.literal(text)
        // The alpha byte is dropped: text colours are RGB, and a stored "#FFRRGGBB" would otherwise render as
        // whatever the top byte happens to make of it.
        return if (color == null) component else component.withColor(color and 0xFFFFFF)
    }
}
