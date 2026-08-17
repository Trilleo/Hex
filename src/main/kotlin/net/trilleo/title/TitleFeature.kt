package net.trilleo.title

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.trilleo.config.ConfigCategory
import net.trilleo.feature.Feature
import net.trilleo.title.model.TitleSpec
import net.trilleo.util.Chroma
import java.util.*

/**
 * The settings shared by every title the mod shows.
 *
 * Not a feature that *does* anything — nothing here ticks, and no key opens it. It exists so the things that
 * are true of titles in general have somewhere to live: the master switches, the fallback colours, and the
 * timings a newly created title starts from. Without it those would have to be repeated in four features'
 * settings, which is exactly the duplication [Titles] was written to end.
 *
 * Like [net.trilleo.region.RegionFeature], this deliberately leaves [enabled] at `true` and gates behaviour on
 * [TitleConfig.active] instead: [net.trilleo.feature.Features.categories] hides a disabled feature's tab, so
 * wiring the master switch to [enabled] would make it impossible to switch back on from the menu.
 *
 * Registered before the features that fire titles, so its config is loaded by the time one of them shows one.
 */
object TitleFeature : Feature {
    override val id: String = "titles"

    override fun onInit() {
        TitleConfig.load()
    }

    override fun settingsCategory(): ConfigCategory = ConfigCategory.build("titles") {
        toggle(
            "enabled",
            default = true,
            get = { TitleConfig.active },
            set = { TitleConfig.settings.enabled = it; TitleConfig.save() },
        )
        toggle(
            "sound_enabled",
            default = true,
            get = { TitleConfig.soundsOn },
            set = { TitleConfig.settings.soundEnabled = it; TitleConfig.save() },
        )

        // Fallbacks: read every time a title is shown, so changing one restyles every title that never named a
        // colour of its own. Chroma is offered because Titles hands the gui a component that rebuilds itself.
        color(
            "default_title_color",
            default = "",
            chroma = true,
            get = { TitleConfig.settings.defaultTitleColor },
            set = { TitleConfig.settings.defaultTitleColor = it; TitleConfig.save() },
        )
        color(
            "default_subtitle_color",
            default = "",
            chroma = true,
            get = { TitleConfig.settings.defaultSubtitleColor },
            set = { TitleConfig.settings.defaultSubtitleColor = it; TitleConfig.save() },
        )

        slider(
            "chroma_speed",
            min = Chroma.SECONDS_MIN,
            max = Chroma.SECONDS_MAX,
            step = Chroma.SECONDS_STEP,
            default = Chroma.SECONDS_DEFAULT,
            get = { TitleConfig.settings.chromaSeconds },
            set = { TitleConfig.settings.chromaSeconds = it; TitleConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.1fs", it) },
        )
        slider(
            "chroma_width",
            min = Chroma.WIDTH_MIN,
            max = Chroma.WIDTH_MAX,
            step = Chroma.WIDTH_STEP,
            default = Chroma.WIDTH_DEFAULT,
            get = { TitleConfig.settings.chromaWidth },
            set = { TitleConfig.settings.chromaWidth = it; TitleConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.0f", it) },
        )

        // Seeds: copied into a title when it is created and never read again, so an alert whose pacing has
        // already been tuned is not silently retimed by a change here.
        seconds(
            "default_fade_in", TitleSpec.FADE_MIN, TitleSpec.FADE_MAX, TitleSpec.DEFAULT_FADE_IN,
            { TitleConfig.settings.defaultFadeInSeconds }, { TitleConfig.settings.defaultFadeInSeconds = it })
        seconds(
            "default_stay", TitleSpec.STAY_MIN, TitleSpec.STAY_MAX, TitleSpec.DEFAULT_STAY,
            { TitleConfig.settings.defaultStaySeconds }, { TitleConfig.settings.defaultStaySeconds = it })
        seconds(
            "default_fade_out", TitleSpec.FADE_MIN, TitleSpec.FADE_MAX, TitleSpec.DEFAULT_FADE_OUT,
            { TitleConfig.settings.defaultFadeOutSeconds }, { TitleConfig.settings.defaultFadeOutSeconds = it })

        action("preview") { _ -> preview() }

        resetsTo(TitleConfig.handle)
    }

    /** One of the three seed sliders, all of which read the same and differ only in their bounds. */
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
            set = { set(it); TitleConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.1fs", it) },
        )
    }

    /**
     * Shows a sample title with nothing but the defaults on it.
     *
     * The only way to judge a fallback colour and a set of timings without leaving the menu, drawing a region
     * and walking into it. The HUD extracts before the open screen, so it appears behind the settings.
     */
    private fun preview() {
        val spec = TitleConfig.newSpec()
        Titles.show(
            Minecraft.getInstance(),
            spec,
            title = Component.translatable("hex.titles.preview.text").string,
            subtitle = Component.translatable("hex.titles.preview.subtitle").string,
        )
    }
}
