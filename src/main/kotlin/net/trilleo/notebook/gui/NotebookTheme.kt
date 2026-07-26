package net.trilleo.notebook.gui

import net.trilleo.notebook.NotebookConfig

/**
 * The notebook's surfaces, tinted by [NotebookConfig.backgroundOpacity].
 *
 * The browser and the editor share one look, so the colours live here rather than as a companion constant in
 * each screen: a slider that lightened the editor but left the list behind it flat would read as a bug.
 *
 * Every colour is read per frame rather than cached, because the slider's setter fires continuously while the
 * handle is dragged and the point of the setting is watching the panels fade as you drag it.
 *
 * The relative weights are what keep the layering legible at any setting. The body of the editor is the
 * darkest surface and the sidebar the lightest, exactly as they were at fixed alphas before the setting
 * existed — at the default they come out on the same values the screens used to hardcode.
 */
object NotebookTheme {

    /** Header and footer bars. */
    fun panel(): Int = tint(PANEL_RGB, PANEL_WEIGHT)

    /** The browser's filter sidebar, a shade lighter than the bars so the split is visible. */
    fun sidebar(): Int = tint(SIDEBAR_RGB, SIDEBAR_WEIGHT)

    /**
     * Behind the editor's text area.
     *
     * Drawn by the screen rather than by [net.minecraft.client.gui.components.MultiLineEditBox] itself, whose
     * background sprite is flat black with no say in the matter — that sprite is what the setting exists to
     * get rid of.
     */
    fun body(): Int = tint(BODY_RGB, BODY_WEIGHT)

    /** Packs [rgb] with an alpha of the configured opacity scaled by [weight]. */
    private fun tint(rgb: Int, weight: Double): Int {
        val alpha = (NotebookConfig.backgroundOpacity * weight * 255.0).toInt().coerceIn(0, 255)
        return (alpha shl 24) or rgb
    }

    private const val PANEL_RGB = 0x101010
    private const val SIDEBAR_RGB = 0x000000
    private const val BODY_RGB = 0x000000

    private const val PANEL_WEIGHT = 1.0
    private const val SIDEBAR_WEIGHT = 0.67
    private const val BODY_WEIGHT = 1.0

    /** Dividers and the outline around the text area: chrome, so it stays put as the panels fade. */
    const val DIVIDER_COLOR: Int = 0x60FFFFFF
}
