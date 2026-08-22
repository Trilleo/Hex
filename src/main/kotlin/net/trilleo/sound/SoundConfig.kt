package net.trilleo.sound

import com.google.gson.reflect.TypeToken
import net.trilleo.config.ConfigHandle
import net.trilleo.config.ConfigRegistry
import net.trilleo.config.JsonConfig
import net.trilleo.sound.model.SoundSequence
import java.util.*

/**
 * One feedback slot's sound — what a named piece of mod feedback plays.
 *
 * Plain and `var`-only for the same GSON reasons as everything else persisted here. [value] is a
 * [SoundValue], so a slot may hold a plain sound, a whole sequence, or silence.
 */
class SoundSlotSettings {
    var value: String = DEFAULT_SOUND
    var pitch: Double = 1.0
    var volume: Double = 1.0

    /** Repairs this slot in place. [defaultPitch] is the slot's own stock pitch, not a global one. */
    fun normalize(defaultPitch: Double) {
        @Suppress("SENSELESS_COMPARISON")
        if (value == null) value = DEFAULT_SOUND
        value = SoundValue.normalize(value)
        pitch = (if (pitch.isFinite()) pitch else defaultPitch).coerceIn(PITCH_MIN, PITCH_MAX)
        volume = (if (volume.isFinite()) volume else 1.0).coerceIn(0.0, 1.0)
    }

    companion object {
        /** Vanilla's UI click — what every one of these slots played before they were settings. */
        const val DEFAULT_SOUND: String = "minecraft:ui.button.click"

        const val PITCH_MIN: Double = 0.5
        const val PITCH_MAX: Double = 2.0

        /** A slot with a stock pitch other than 1.0, which is most of them. */
        fun of(pitch: Double): SoundSlotSettings = SoundSlotSettings().also { it.pitch = pitch }
    }
}

/**
 * The feedback slots, as named fields rather than a map.
 *
 * The same choice [net.trilleo.reminder.hud.HudSettings] made: a map would serialise to keys nobody chose
 * and would let a hand-edited file invent a slot that nothing reads. Named fields keep `sounds.json` legible
 * and make an unknown key exactly as inert as it should be.
 */
data class SoundSlots(
    var toggleOn: SoundSlotSettings = SoundSlotSettings.of(1.4),
    var toggleOff: SoundSlotSettings = SoundSlotSettings.of(0.8),
    var denied: SoundSlotSettings = SoundSlotSettings.of(0.7),
    var captured: SoundSlotSettings = SoundSlotSettings.of(1.8),
    var switched: SoundSlotSettings = SoundSlotSettings.of(1.2),
)

/**
 * Everything about sounds that is a *setting*, persisted at `config/hex/sounds.json`.
 *
 * @property enabled the feature's master switch. Nullable for the same reason
 *   [net.trilleo.region.RegionSettings.enabled] is: GSON leaves an absent `boolean` at the JVM default of
 *   `false`, so a hand-written file omitting the key would load as *disabled*, the opposite of what omitting
 *   a setting should mean. Read it through [SoundConfig.active].
 */
data class SoundSettings(
    var enabled: Boolean? = null,

    /** Scales everything this mod plays. Vanilla's own volume sliders still apply on top. */
    var masterVolume: Double = 1.0,

    /** The tempo a newly created sequence opens on. */
    var defaultBpm: Double = SoundSequence.DEFAULT_BPM,

    var sequences: MutableList<SoundSequence> = mutableListOf(),

    var slots: SoundSlots = SoundSlots(),
)

/**
 * Loads and holds the singleton [SoundSettings].
 *
 * Registered with [ConfigRegistry] and deliberately **not** `global`, so sequences travel with a config
 * profile and ride along in the clipboard export. A set of alert sounds is exactly the kind of thing worth
 * sharing, and it is a loadout rather than an installation detail — the same argument
 * [net.trilleo.region.RegionConfig] makes for regions.
 */
object SoundConfig {
    private val config = JsonConfig(
        name = "sounds",
        type = object : TypeToken<SoundSettings>() {}.type,
        default = { SoundSettings() },
        normalizer = ::normalize,
    )

    var settings: SoundSettings = SoundSettings()
        private set

    /**
     * Bumped whenever the sequences may have changed.
     *
     * A `"@id"` reference is resolved on every play, and the picker rebuilds its list from this. A counter is
     * used rather than an explicit invalidate call at each mutation site because the mutation sites are the
     * edit screens, and one missed call there would show up as a sequence that silently plays its old shape.
     */
    var revision: Int = 0
        private set

    val handle = ConfigRegistry.register(
        ConfigHandle(
            config,
            adopt = { settings = it; revision++ },
            current = { settings },
        ),
    )

    /** Whether the feature is switched on, treating an absent key as on. */
    val active: Boolean get() = settings.enabled != false

    fun load() = handle.loadInitial()

    /** Writes immediately. Prefer [markDirty] from anything that fires repeatedly, such as a drag. */
    fun save() {
        revision++
        handle.saveNow()
    }

    /** Records that something changed; the write is batched and lands about a second later. */
    fun markDirty() {
        revision++
        handle.markDirty()
    }

    /** Repairs the live settings in place — for a sequence added by code, which has never been through a load. */
    fun normalizeNow() = handle.json.normalize(settings)

    /** The sequence with this id, or null. Linear, but the list is a few dozen entries at most. */
    fun byId(id: String): SoundSequence? =
        if (id.isBlank()) null else settings.sequences.firstOrNull { it.id == id }

    /**
     * An id like [wanted] that no sequence other than [except] already holds.
     *
     * An id is permanent once assigned (see [SoundSequence.id]), so this runs once per sequence, at creation.
     * Rather than refusing a duplicate name, a numeric suffix is appended — the same repair the normalizer
     * applies to a hand-edited file.
     */
    fun uniqueId(wanted: String, except: SoundSequence? = null): String {
        val base = SoundValue.slug(wanted)
        val taken = settings.sequences.filter { it !== except }.mapTo(HashSet()) { it.id }
        if (base !in taken) return base
        var n = 2
        while ("$base-$n" in taken) n++
        return "$base-$n"
    }

    /**
     * Repairs a loaded value.
     *
     * Every step covers a way GSON's reflective construction differs from Kotlin: absent objects arrive null
     * and absent primitives arrive zeroed. Beyond that it bounds every number a hand-edited file could put
     * out of range, and gives any sequence that arrived without an id one that is unique.
     *
     * It deliberately does **not** validate a sound id or a `"@ref"`, for the reasons
     * [net.trilleo.region.RegionConfig] gives and one more of its own: the sound registry is not populated
     * when configs load at feature init, and a `"@ref"` may point at a sequence that a *later* profile
     * restore is about to bring in. Both are checked where they can be reported — the editor, and the row.
     */
    private fun normalize(settings: SoundSettings) {
        @Suppress("SENSELESS_COMPARISON")
        if (settings.sequences == null) settings.sequences = mutableListOf()
        @Suppress("SENSELESS_COMPARISON")
        if (settings.slots == null) settings.slots = SoundSlots()

        settings.masterVolume = settings.masterVolume.sane(1.0).coerceIn(0.0, 1.0)
        // Zero is how an absent key arrives — GSON does not run Kotlin's default — and it is also below the
        // floor, so a file written before this setting existed picks the default up rather than ruling the
        // editor's grid at no tempo at all.
        settings.defaultBpm = settings.defaultBpm.sane(SoundSequence.DEFAULT_BPM)
            .takeIf { it > 0.0 }?.coerceIn(SoundSequence.BPM_MIN, SoundSequence.BPM_MAX)
            ?: SoundSequence.DEFAULT_BPM

        val seenIds = HashSet<String>()
        settings.sequences.forEach { sequence ->
            sequence.normalize()
            // An id is what every reference names, so a blank or duplicated one is not a cosmetic problem:
            // two sequences sharing an id would make "@that" ambiguous, and the second would be unreachable.
            var id = SoundValue.slug(sequence.id.ifBlank { sequence.name })
            if (id in seenIds) {
                var n = 2
                while ("$id-$n" in seenIds) n++
                id = "$id-$n"
            }
            sequence.id = id
            seenIds += id
            if (sequence.name.isBlank()) sequence.name = id
        }

        normalizeSlots(settings.slots)
    }

    private fun normalizeSlots(slots: SoundSlots) {
        val stock = SoundSlots()
        @Suppress("SENSELESS_COMPARISON")
        if (slots.toggleOn == null) slots.toggleOn = stock.toggleOn
        @Suppress("SENSELESS_COMPARISON")
        if (slots.toggleOff == null) slots.toggleOff = stock.toggleOff
        @Suppress("SENSELESS_COMPARISON")
        if (slots.denied == null) slots.denied = stock.denied
        @Suppress("SENSELESS_COMPARISON")
        if (slots.captured == null) slots.captured = stock.captured
        @Suppress("SENSELESS_COMPARISON")
        if (slots.switched == null) slots.switched = stock.switched

        slots.toggleOn.normalize(stock.toggleOn.pitch)
        slots.toggleOff.normalize(stock.toggleOff.pitch)
        slots.denied.normalize(stock.denied.pitch)
        slots.captured.normalize(stock.captured.pitch)
        slots.switched.normalize(stock.switched.pitch)
    }

    /** Replaces a NaN or infinite value — which no slider can produce but a hand-edited file can. */
    private fun Double.sane(fallback: Double): Double = if (isFinite()) this else fallback
}
