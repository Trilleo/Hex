package net.trilleo.region

import com.google.gson.reflect.TypeToken
import net.minecraft.world.phys.Vec3
import net.trilleo.config.ConfigHandle
import net.trilleo.config.ConfigRegistry
import net.trilleo.config.JsonConfig
import net.trilleo.reminder.model.ActionKind
import net.trilleo.reminder.model.ReminderAction
import net.trilleo.region.model.Region
import net.trilleo.region.model.RegionShape
import java.util.*

/**
 * Everything about regions that is a *setting*, persisted at `config/hex/regions.json`.
 *
 * @property enabled the feature's master switch. Nullable for the same reason
 *   [net.trilleo.reminder.ReminderSettings.enabled] is: GSON leaves an absent `boolean` at the JVM default of
 *   `false`, so a hand-written file omitting the key would load as *disabled*, the opposite of what omitting a
 *   setting should mean. Read it through [RegionConfig.active].
 */
data class RegionSettings(
    var enabled: Boolean? = null,
    var regions: MutableList<Region> = mutableListOf(),

    /** How far out a "region here" capture reaches horizontally. */
    var defaultRadius: Double = 12.0,

    /** How far up and down a "region here" capture reaches. */
    var defaultHeight: Double = 6.0,

    /**
     * How far outside a region the player must go before it counts as having been left.
     *
     * The whole of the anti-flap defence: without it, standing on a boundary and breathing would alternate
     * inside and outside every tick, firing on every other one.
     */
    var exitMargin: Double = 0.5,

    /** Fallback preview colour for a region that names none of its own. */
    var previewColor: String = "#5555FF55",

    /** Colour of a capture in progress, deliberately distinct from a finished region's. */
    var draftColor: String = "#60FFAA00",

    /** Whether the preview draws through terrain. */
    var previewSeeThrough: Boolean = true,

    /** Whether the preview labels each region with its name. */
    var previewNames: Boolean = true,

    /** Whether regions are only live while the scoreboard looks like Skyblock's. */
    var skyblockOnly: Boolean = false,
)

/**
 * Loads and holds the singleton [RegionSettings].
 *
 * Kept in its own file rather than folded into `reminders.json`, following the same reasoning
 * [net.trilleo.hand.SwingItemsConfig] is separate from `hand.json`: the Regions tab's reset button should
 * restore the sliders without also destroying a hand-drawn set of regions. Registering with [ConfigRegistry]
 * means regions join config profiles and clipboard export at no extra cost — and unlike a running countdown,
 * a set of regions is exactly the kind of thing worth sharing.
 */
object RegionConfig {
    private val config = JsonConfig(
        name = "regions",
        type = object : TypeToken<RegionSettings>() {}.type,
        default = { RegionSettings() },
        normalizer = ::normalize,
    )

    var settings: RegionSettings = RegionSettings()
        private set

    /**
     * Bumped whenever the regions may have changed.
     *
     * [RegionTracker] caches the subset of regions that apply to the current island, and that cache has to be
     * dropped when a region is added, edited, deleted, or swapped wholesale by a profile switch. A counter is
     * used rather than an explicit `invalidate()` call at every mutation site because the mutation sites are
     * the edit screens, and one missed call there would show up as a region that silently never fires.
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

    /** Writes immediately. Prefer [markDirty] from anything that fires repeatedly, such as a slider. */
    fun save() {
        revision++
        handle.saveNow()
    }

    /** Records that something changed; the write is batched and lands about a second later. */
    fun markDirty() {
        revision++
        handle.markDirty()
    }

    /** Repairs the live settings in place — for a region added by code, which has never been through a load. */
    fun normalizeNow() = handle.json.normalize(settings)

    /** The region with this id, or null. Linear, but the list is a few dozen entries at most. */
    fun byId(id: String): Region? = settings.regions.firstOrNull { it.id == id }

    /** The region with this (already lowercased) name, or null. What a reminder trigger resolves through. */
    fun byName(name: String): Region? = settings.regions.firstOrNull { it.name == name }

    /**
     * A name like [wanted] that no region other than [except] already holds.
     *
     * Names are what reminder triggers reference, so two regions sharing one would make a trigger ambiguous.
     * Rather than refusing the edit, a suffix is appended — the same repair the normalizer applies to a
     * hand-edited file.
     */
    fun uniqueName(wanted: String, except: Region? = null): String {
        val base = wanted.trim().lowercase(Locale.ROOT).ifBlank { "region" }
        val taken = settings.regions.filter { it !== except }.mapTo(HashSet()) { it.name }
        if (base !in taken) return base
        var n = 2
        while ("$base $n" in taken) n++
        return "$base $n"
    }

    /**
     * Repairs a loaded value.
     *
     * Every step covers a way GSON's reflective construction differs from Kotlin: absent objects arrive null,
     * absent primitives arrive zeroed, and an enum name this build does not know arrives null exactly like an
     * absent one. Beyond that it folds case so matching can stay a plain `==`, orders each axis, and bounds
     * every number a hand-edited file could put out of range.
     *
     * It deliberately does **not** validate a sound id, for the same reason [net.trilleo.reminder.ReminderConfig]
     * does not: the sound registry is not necessarily populated when configs load at feature init.
     */
    private fun normalize(settings: RegionSettings) {
        @Suppress("SENSELESS_COMPARISON")
        if (settings.regions == null) settings.regions = mutableListOf()
        @Suppress("SENSELESS_COMPARISON")
        if (settings.previewColor == null) settings.previewColor = RegionSettings().previewColor
        @Suppress("SENSELESS_COMPARISON")
        if (settings.draftColor == null) settings.draftColor = RegionSettings().draftColor

        settings.defaultRadius = settings.defaultRadius.sane(12.0).coerceIn(1.0, 256.0)
        settings.defaultHeight = settings.defaultHeight.sane(6.0).coerceIn(1.0, 256.0)
        settings.exitMargin = settings.exitMargin.sane(0.5).coerceIn(0.0, 8.0)

        val seenIds = HashSet<String>()
        val seenNames = HashSet<String>()
        settings.regions.forEach { region -> normalizeRegion(region, seenIds, seenNames) }
    }

    private fun normalizeRegion(region: Region, seenIds: MutableSet<String>, seenNames: MutableSet<String>) {
        @Suppress("SENSELESS_COMPARISON")
        if (region.id == null) region.id = ""
        @Suppress("SENSELESS_COMPARISON")
        if (region.name == null) region.name = ""
        @Suppress("SENSELESS_COMPARISON")
        if (region.island == null) region.island = ""
        @Suppress("SENSELESS_COMPARISON")
        if (region.text == null) region.text = ""
        @Suppress("SENSELESS_COMPARISON")
        if (region.leaveText == null) region.leaveText = ""
        @Suppress("SENSELESS_COMPARISON")
        if (region.color == null) region.color = ""
        @Suppress("SENSELESS_COMPARISON")
        if (region.shape == null) region.shape = RegionShape.BOX
        @Suppress("SENSELESS_COMPARISON")
        if (region.actions == null) region.actions = mutableListOf()

        // A blank or repeated id would alias another region's cooldown, so both get a fresh one. Repeats are
        // the realistic case: someone duplicates an entry by hand to make a similar region.
        if (region.id.isBlank() || !seenIds.add(region.id)) {
            region.id = UUID.randomUUID().toString()
            seenIds.add(region.id)
        }

        // Folded so a reminder's REGION_ENTER trigger can match with a plain `==`, exactly as island names are.
        region.island = region.island.trim().lowercase(Locale.ROOT)
        region.name = region.name.trim().lowercase(Locale.ROOT)
        if (region.name.isBlank()) region.name = "region"
        // Two regions with one name would make a trigger naming it ambiguous.
        if (!seenNames.add(region.name)) {
            var n = 2
            while (!seenNames.add("${region.name} $n")) n++
            region.name = "${region.name} $n"
        }

        normalizeBounds(region)

        region.cooldownSeconds = region.cooldownSeconds.sane(Region.DEFAULT_COOLDOWN_SECONDS)
            .coerceIn(Region.COOLDOWN_MIN, Region.COOLDOWN_MAX)

        region.actions.forEach { it.normalize() }
        // The panel belongs to reminders, which have a phase to draw; a region carrying a HUD action would
        // fire and do nothing, which reads exactly like a broken region.
        region.actions.removeAll { it.kind == ActionKind.HUD }
        // A region that fires and does nothing is indistinguishable from one that never fired.
        if (region.actions.isEmpty()) {
            region.actions.add(ReminderAction().apply { kind = ActionKind.TITLE })
        }

        // An unnamed message renders as an empty title, which vanilla draws as a silent flash of nothing.
        if (region.text.isBlank()) region.text = region.name
    }

    /**
     * Orders each axis and bounds the box.
     *
     * The minimum extent matters more than it looks: a zero-thickness region is one a player can stand
     * "inside" only by landing on an exact coordinate, so it would read as a region that simply never works.
     * Growing it around its own centre keeps a mis-captured region where the player put it.
     */
    private fun normalizeBounds(region: Region) {
        region.minX = region.minX.sane(0.0)
        region.minY = region.minY.sane(0.0)
        region.minZ = region.minZ.sane(0.0)
        region.maxX = region.maxX.sane(0.0)
        region.maxY = region.maxY.sane(0.0)
        region.maxZ = region.maxZ.sane(0.0)

        // Captures hand in their two corners in whatever order the player marked them, and a hand-edited file
        // can hold any order at all, so the box is put the right way round here rather than at every reader.
        region.setCorners(
            Vec3(region.minX, region.minY, region.minZ),
            Vec3(region.maxX, region.maxY, region.maxZ),
        )

        if (region.sizeX() < Region.MIN_EXTENT) {
            val center = (region.minX + region.maxX) / 2.0
            region.minX = center - Region.MIN_EXTENT / 2.0
            region.maxX = center + Region.MIN_EXTENT / 2.0
        }
        if (region.sizeY() < Region.MIN_EXTENT) {
            val center = (region.minY + region.maxY) / 2.0
            region.minY = center - Region.MIN_EXTENT / 2.0
            region.maxY = center + Region.MIN_EXTENT / 2.0
        }
        if (region.sizeZ() < Region.MIN_EXTENT) {
            val center = (region.minZ + region.maxZ) / 2.0
            region.minZ = center - Region.MIN_EXTENT / 2.0
            region.maxZ = center + Region.MIN_EXTENT / 2.0
        }

        if (region.sizeX() > Region.MAX_EXTENT) region.maxX = region.minX + Region.MAX_EXTENT
        if (region.sizeY() > Region.MAX_EXTENT) region.maxY = region.minY + Region.MAX_EXTENT
        if (region.sizeZ() > Region.MAX_EXTENT) region.maxZ = region.minZ + Region.MAX_EXTENT
    }

    /** Replaces a NaN or infinite value — which no capture can produce but a hand-edited file can. */
    private fun Double.sane(fallback: Double): Double = if (isFinite()) this else fallback
}
