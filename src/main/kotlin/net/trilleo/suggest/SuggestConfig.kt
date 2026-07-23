package net.trilleo.suggest

import com.google.gson.reflect.TypeToken
import net.trilleo.config.ConfigHandle
import net.trilleo.config.ConfigRegistry
import net.trilleo.config.JsonConfig
import java.util.*

/**
 * How fast the model forgets, as a half-life on every learned count.
 *
 * Offered as three named speeds rather than a slider over days because the number itself means nothing to
 * anybody — what a player knows is whether their play has changed lately, not how many days of evidence they
 * would like weighted at one half.
 */
enum class Adaptation(val halfLifeMs: Long) {
    /** Sixty days. For settled play, where a habit from last month is still a habit. */
    SLOW(60L * 24 * 60 * 60 * 1000),

    /** Fourteen days. */
    NORMAL(14L * 24 * 60 * 60 * 1000),

    /** Three days. For play that changes week to week — a new island, a new grind. */
    FAST(3L * 24 * 60 * 60 * 1000),
}

/** Which key accepts the inline completion. */
enum class AcceptKey {
    /** Tab only, matching vanilla's completion key. */
    TAB,

    /** The right arrow only, matching how a shell accepts a suggestion. */
    ARROW,

    BOTH,
}

/**
 * Everything about command suggestions that is a *setting*, persisted at `config/hex/suggest.json`.
 *
 * Registered with [ConfigRegistry], so a player's preferences here travel with their config profile like any
 * other feature's. **What the model has learned is emphatically not here** — that is
 * [net.trilleo.suggest.model.ModelStore], in its own unregistered file, so it can never be captured into a
 * profile snapshot or pasted into somebody else's game along with the rest of a loadout.
 *
 * @property enabled the master switch. Nullable for the reason [net.trilleo.reminder.ReminderSettings.enabled]
 *   is: GSON leaves an absent `boolean` at the JVM default of `false`, so a hand-written file omitting the key
 *   would load as *disabled*, the opposite of what omitting a setting should mean.
 */
data class SuggestSettings(
    var enabled: Boolean? = null,
    var learning: Boolean? = null,
    var popup: Boolean? = null,
    var ghostText: Boolean? = null,
    var nextCommand: Boolean? = null,
    var rows: Int = 5,
    var confidence: Double = 0.6,
    var adaptation: Adaptation = Adaptation.NORMAL,
    var cataloguePriors: Boolean? = null,
    var learnPlayerNames: Boolean? = null,
    var acceptKey: AcceptKey = AcceptKey.BOTH,
    /** Commands never recorded at all, lower-cased and without their slash. */
    var blocklist: MutableList<String> = mutableListOf(),
)

object SuggestConfig {
    private val config = JsonConfig(
        name = "suggest",
        type = object : TypeToken<SuggestSettings>() {}.type,
        default = { SuggestSettings() },
        normalizer = ::normalize,
    )

    var settings: SuggestSettings = SuggestSettings()
        private set

    val handle: ConfigHandle<SuggestSettings> = ConfigRegistry.register(
        ConfigHandle(
            config,
            adopt = { settings = it; blocked = it.blocklist.toHashSet() },
            current = { settings },
        ),
    )

    /**
     * The blocklist as a set, rebuilt whenever the settings object is replaced.
     *
     * Membership is tested once per command sent, which is hardly a hot path — this exists so the recorder
     * cannot be made slow by a player with a hundred blocked commands, and because a list that is only ever
     * asked "is this in you" is the wrong shape to leave as a list.
     */
    @Volatile
    private var blocked: Set<String> = emptySet()

    /** Whether the feature is switched on, treating an absent key as on. */
    val active: Boolean get() = settings.enabled != false

    /** Whether new commands are being recorded. Off means "keep suggesting, stop learning". */
    val learning: Boolean get() = settings.learning != false

    val popup: Boolean get() = settings.popup != false

    val ghostText: Boolean get() = settings.ghostText != false

    val nextCommand: Boolean get() = settings.nextCommand != false

    val cataloguePriors: Boolean get() = settings.cataloguePriors != false

    val learnPlayerNames: Boolean get() = settings.learnPlayerNames != false

    val halfLifeMs: Long get() = settings.adaptation.halfLifeMs

    val blocklist: Set<String> get() = blocked

    fun load() = handle.loadInitial()

    fun save() = handle.saveNow()

    fun markDirty() = handle.markDirty()

    /** Adds a command to the blocklist and persists. Returns false when it was already there. */
    fun block(name: String): Boolean {
        val folded = name.trim().removePrefix("/").lowercase(Locale.ROOT)
        if (folded.isEmpty() || folded in blocked) return false
        settings.blocklist.add(folded)
        blocked = settings.blocklist.toHashSet()
        save()
        return true
    }

    private fun normalize(settings: SuggestSettings) {
        @Suppress("SENSELESS_COMPARISON")
        if (settings.adaptation == null) settings.adaptation = Adaptation.NORMAL
        @Suppress("SENSELESS_COMPARISON")
        if (settings.acceptKey == null) settings.acceptKey = AcceptKey.BOTH
        @Suppress("SENSELESS_COMPARISON")
        if (settings.blocklist == null) settings.blocklist = mutableListOf()

        // Zero means "absent" rather than "show nothing": a hand-written file that omits the key should get
        // the default list, not a popup that can never appear.
        settings.rows = (if (settings.rows <= 0) DEFAULT_ROWS else settings.rows).coerceIn(ROWS_MIN, ROWS_MAX)
        settings.confidence = (if (settings.confidence.isFinite()) settings.confidence else DEFAULT_CONFIDENCE)
            .coerceIn(0.0, 1.0)

        settings.blocklist.replaceAll { it.trim().removePrefix("/").lowercase(Locale.ROOT) }
        settings.blocklist.removeAll { it.isEmpty() }
    }

    const val ROWS_MIN: Int = 1
    const val ROWS_MAX: Int = 8
    const val DEFAULT_ROWS: Int = 5

    /**
     * How sure the ranker must be before the inline completion appears.
     *
     * Set where it is because ghost text is the one surface that puts a guess in front of the player without
     * being asked. A popup that offers five things is honest about being a list; a greyed-out completion
     * reads as an assertion, so it should only appear when the model would bet on it.
     */
    const val DEFAULT_CONFIDENCE: Double = 0.6
}
