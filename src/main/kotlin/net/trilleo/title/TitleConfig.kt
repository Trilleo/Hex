package net.trilleo.title

import com.google.gson.reflect.TypeToken
import net.trilleo.color.ColorValue
import net.trilleo.config.ConfigHandle
import net.trilleo.config.ConfigRegistry
import net.trilleo.config.JsonConfig
import net.trilleo.title.model.TitleSpec
import net.trilleo.util.Chroma

/**
 * The settings every title in the mod shares, persisted at `config/hex/titles.json`.
 *
 * Three different kinds of setting live here, and the difference matters:
 *
 *  - **Masters** ([enabled], [soundEnabled]) — switches that win over any individual title. One place to
 *    silence every title in the mod, which is the thing you want at two in the morning and cannot get by
 *    visiting four editors.
 *  - **Fallbacks** ([TitleSettings.defaultTitleColor], [TitleSettings.defaultSubtitleColor],
 *    [TitleSettings.chromaSeconds], [TitleSettings.chromaWidth]) — read live, every time a title is shown, by
 *    any title that names no colour of its own. Changing one restyles every such title at once.
 *  - **Seeds** ([TitleSettings.defaultFadeInSeconds] and the other two) — copied into a title when it is
 *    *created*, and never read again. A title's dwell time is part of that title, so changing the seed leaves
 *    existing ones alone; the alternative would silently retime alerts a player had already tuned.
 *
 * @property enabled the feature's master switch. Nullable for the same reason
 *   [net.trilleo.reminder.ReminderSettings.enabled] is: GSON leaves an absent `boolean` at the JVM default of
 *   `false`, so a hand-written file omitting the key would load as *disabled*, the opposite of what omitting a
 *   setting should mean. Read it through [TitleConfig.active].
 * @property soundEnabled master switch for the sound a title plays. Nullable for the same reason; read it
 *   through [TitleConfig.soundsOn].
 */
data class TitleSettings(
    var enabled: Boolean? = null,
    var soundEnabled: Boolean? = null,

    /** The colour a title's big line falls back to when it names none. `""` leaves it vanilla white. */
    var defaultTitleColor: String = "",

    /** The same for the smaller line. */
    var defaultSubtitleColor: String = "",

    /**
     * How long one full trip through the rainbow takes, in seconds, for a title drawn in chroma.
     *
     * Shared by every title rather than set per title, the same choice
     * [net.trilleo.region.RegionSettings.chromaSeconds] made: two alerts flowing at different rates read as a
     * glitch rather than as a setting, and no player has ever wanted to tune this twice.
     */
    var chromaSeconds: Double = Chroma.SECONDS_DEFAULT,

    /** How many characters one full rainbow spans. Wide enough and a short word is one shifting colour. */
    var chromaWidth: Double = Chroma.WIDTH_DEFAULT,

    /** The fade-in a newly created title starts with. */
    var defaultFadeInSeconds: Double = TitleSpec.DEFAULT_FADE_IN,

    /** The dwell time a newly created title starts with. */
    var defaultStaySeconds: Double = TitleSpec.DEFAULT_STAY,

    /** The fade-out a newly created title starts with. */
    var defaultFadeOutSeconds: Double = TitleSpec.DEFAULT_FADE_OUT,
)

/**
 * Loads and holds the singleton [TitleSettings].
 *
 * Its own file rather than a corner of `reminders.json`, because titles are no longer a reminder's business:
 * four features fire them, and a config that belonged to one of them would be reset by that feature's reset
 * button and captured by profiles under the wrong name.
 */
object TitleConfig {
    private val config = JsonConfig(
        name = "titles",
        type = object : TypeToken<TitleSettings>() {}.type,
        default = { TitleSettings() },
        normalizer = ::normalize,
    )

    var settings: TitleSettings = TitleSettings()
        private set

    val handle = ConfigRegistry.register(
        ConfigHandle(config, adopt = { settings = it }, current = { settings }),
    )

    /** Whether titles are switched on at all, treating an absent key as on. */
    val active: Boolean get() = settings.enabled != false

    /** Whether a title may play its sound, treating an absent key as on. */
    val soundsOn: Boolean get() = settings.soundEnabled != false

    fun load() = handle.loadInitial()

    /** Writes immediately. Prefer [markDirty] from anything that fires repeatedly, such as a slider. */
    fun save() = handle.saveNow()

    /** Records that something changed; the write is batched and lands about a second later. */
    fun markDirty() = handle.markDirty()

    /**
     * A fresh title, timed the way this installation likes its titles.
     *
     * Every place that creates a title action goes through this rather than `TitleSpec()`, which is what makes
     * the three "default" sliders do anything at all.
     */
    fun newSpec(): TitleSpec = TitleSpec().also {
        it.fadeInSeconds = settings.defaultFadeInSeconds
        it.staySeconds = settings.defaultStaySeconds
        it.fadeOutSeconds = settings.defaultFadeOutSeconds
    }

    private fun normalize(settings: TitleSettings) {
        @Suppress("SENSELESS_COMPARISON")
        if (settings.defaultTitleColor == null) settings.defaultTitleColor = ""
        @Suppress("SENSELESS_COMPARISON")
        if (settings.defaultSubtitleColor == null) settings.defaultSubtitleColor = ""
        settings.defaultTitleColor = ColorValue.normalize(settings.defaultTitleColor, alpha = false)
        settings.defaultSubtitleColor = ColorValue.normalize(settings.defaultSubtitleColor, alpha = false)

        // Zero is how an absent key arrives — GSON does not run Kotlin's default — and it is also below each
        // slider's floor, so a file written before one of these existed picks the default up rather than
        // freezing a chroma title solid or timing one out instantly.
        settings.chromaSeconds = settings.chromaSeconds.sane(Chroma.SECONDS_DEFAULT)
            .takeIf { it > 0.0 }?.coerceIn(Chroma.SECONDS_MIN, Chroma.SECONDS_MAX) ?: Chroma.SECONDS_DEFAULT
        settings.chromaWidth = settings.chromaWidth.sane(Chroma.WIDTH_DEFAULT)
            .takeIf { it > 0.0 }?.coerceIn(Chroma.WIDTH_MIN, Chroma.WIDTH_MAX) ?: Chroma.WIDTH_DEFAULT

        settings.defaultFadeInSeconds = settings.defaultFadeInSeconds.sane(TitleSpec.DEFAULT_FADE_IN)
            .coerceIn(TitleSpec.FADE_MIN, TitleSpec.FADE_MAX)
        settings.defaultStaySeconds = settings.defaultStaySeconds.sane(TitleSpec.DEFAULT_STAY)
            .takeIf { it > 0.0 }?.coerceIn(TitleSpec.STAY_MIN, TitleSpec.STAY_MAX) ?: TitleSpec.DEFAULT_STAY
        settings.defaultFadeOutSeconds = settings.defaultFadeOutSeconds.sane(TitleSpec.DEFAULT_FADE_OUT)
            .coerceIn(TitleSpec.FADE_MIN, TitleSpec.FADE_MAX)
    }

    /** Replaces a NaN or infinite value — which no slider can produce but a hand-edited file can. */
    private fun Double.sane(fallback: Double): Double = if (isFinite()) this else fallback
}
