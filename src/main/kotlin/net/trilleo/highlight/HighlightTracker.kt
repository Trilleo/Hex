package net.trilleo.highlight

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.trilleo.highlight.HighlightTracker.wearerOf
import net.trilleo.highlight.model.Highlight
import net.trilleo.highlight.model.HighlightMatch
import net.trilleo.skyblock.SkyblockLocation
import net.trilleo.util.HexColor
import net.trilleo.util.TextClean
import java.util.*

/**
 * Works out which entities the highlight rules claim, and reports the ones that are new.
 *
 * **Everything expensive happens here, on a tick, and nothing happens on the render thread.** The result is a
 * table keyed by entity id which [HighlightLookup] reads once per entity per frame; matching, name-tag
 * resolution and distance maths never run inside a render hook. That split is the whole performance story of
 * this feature, and it is why the rescan can afford to walk every entity in the world.
 *
 * The scan runs every [HighlightSettings.scanIntervalTicks] ticks rather than every one. An entity keeps its id
 * for as long as it exists, so a mob that matched at the last scan goes on glowing smoothly in between; the
 * interval only decides how quickly a *newly spawned* one is picked up.
 *
 * Shaped after [net.trilleo.region.RegionTracker] — the same per-island candidate cache, the same cooldown map,
 * the same reset on a world change — because it is the same job with a different notion of "inside".
 */
object HighlightTracker {

    /**
     * What a matched entity gets.
     *
     * @param color the rule's colour, opaque. Colours the outline, and the marks around a [nameTag].
     * @param outline whether writing that colour to the render state can actually draw something. False for a
     *   marker armor stand, whose renderer returns no render type at all: writing it there would draw nothing
     *   while still switching the whole outline pass on for the frame, which is a cost with no picture.
     * @param label the floating label, when the rule asked for one. Never set alongside a [nameTag] — the
     *   entity already has a name on screen, and [HighlightLookup] would skip the label anyway.
     * @param nameTag the marked-up replacement for a name tag this mod cannot glow. See [nameTagFor].
     */
    class Match(
        val color: Int,
        val outline: Boolean,
        val label: Component?,
        val nameTag: Component?,
    )

    /**
     * The published table, read by [HighlightLookup] on the render thread.
     *
     * Volatile and replaced wholesale rather than mutated, so a reader either sees the previous scan's table or
     * this one's, never a half-built map. An immutable snapshot costs one allocation every few ticks and
     * removes the only piece of cross-thread sharing this feature has.
     */
    @Volatile
    private var matches: Map<Int, Match> = emptyMap()

    /** Rule id to how many entities it is matching right now, for the list screen's live count. */
    @Volatile
    private var counts: Map<String, Int> = emptyMap()

    /**
     * Every name tag this scan published, by identity, so the render path can recognise one it is about to draw.
     *
     * Identity rather than equality, and a set rather than a flag on the render state: the components in here
     * were freshly built by this scan and handed to exactly one entity each, so "is this the object we wrote?"
     * is both the cheapest question and the exact one. It saves adding a field to every `EntityRenderState` the
     * game owns and writing it for every entity in the world on every frame, when the number of name tags
     * actually drawn in a frame is a handful.
     */
    @Volatile
    private var markedTags: Set<Component> = emptySet()

    /** Ticks since the last rescan. */
    private var sinceScan: Int = 0

    /** Rule id to the ids of the entities it has already announced. Not persisted — see [reset]. */
    private val announced: MutableMap<String, MutableSet<Int>> = HashMap()

    /** Rule id to the epoch millisecond its cooldown expires. Deliberately in memory only. */
    private val quietUntil: MutableMap<String, Long> = HashMap()

    /** The rules that apply where the player is now, recomputed only when the island or the config changes. */
    private var candidates: List<Highlight> = emptyList()
    private var candidateIsland: String? = null
    private var candidateRevision: Int = -1

    /** The glow and label for the entity with this id, or null. The render thread's only entry point. */
    fun matchFor(entityId: Int): Match? = matches[entityId]

    /** Whether anything at all is matched, so the render hook can leave without touching a map. */
    val anyMatched: Boolean get() = matches.isNotEmpty()

    /** Whether [isMarkedTag] can say yes to anything, so the name tag hook can leave on one read. */
    val anyMarked: Boolean get() = markedTags.isNotEmpty()

    /** Whether [tag] is a name tag this feature wrote, rather than one the game did. */
    fun isMarkedTag(tag: Component): Boolean = tag in markedTags

    /** How many entities [highlight] is matching right now, for the list screen. */
    fun countFor(highlight: Highlight): Int = counts[highlight.id] ?: 0

    /**
     * Forgets everything, so a match cannot straddle two servers.
     *
     * The announced set is cleared alongside the table: entity ids are per level, so carrying one across a
     * rejoin would silently suppress the first alert on arrival — and arriving somewhere new is exactly when a
     * player wants to be told what is there.
     */
    fun reset() {
        matches = emptyMap()
        counts = emptyMap()
        markedTags = emptySet()
        announced.clear()
        quietUntil.clear()
        sinceScan = 0
        candidateIsland = null
        candidateRevision = -1
    }

    /**
     * Rescans if it is time to, and reports every entity a notifying rule has not announced before.
     *
     * [onFound] is called with the rule and the entity that triggered it; whether it actually shows anything is
     * [HighlightAlerts]' decision, since that is where the cooldown lives.
     */
    fun tick(client: Minecraft, onFound: (Highlight, Entity) -> Unit) {
        val level = client.level
        val player = client.player
        if (level == null || player == null) {
            // Not "nothing matches any more": there is nothing to judge by, and publishing an empty table is
            // the honest reading while the world is loading. The announced set survives, so a brief hitch does
            // not re-announce the whole island.
            clearTable()
            return
        }
        if (!HighlightConfig.active) {
            clearTable()
            return
        }
        if (HighlightConfig.settings.skyblockOnly && !SkyblockLocation.onSkyblock) {
            clearTable()
            return
        }

        if (++sinceScan < HighlightConfig.settings.scanIntervalTicks) return
        sinceScan = 0

        val rules = candidatesFor(SkyblockLocation.current)
        if (rules.isEmpty()) {
            clearTable()
            return
        }

        scan(level, player, rules, onFound)
    }

    private fun clearTable() {
        if (matches.isNotEmpty()) matches = emptyMap()
        if (counts.isNotEmpty()) counts = emptyMap()
        if (markedTags.isNotEmpty()) markedTags = emptySet()
    }

    /**
     * Whether [highlight] may announce now, and if so starts its cooldown.
     *
     * Called by [HighlightAlerts] rather than from the scan, for the same reason
     * [net.trilleo.region.RegionTracker.claimCooldown] is: the scan's job is to decide what is new, and the
     * cooldown's job is to decide how often that may be said out loud.
     */
    fun claimCooldown(highlight: Highlight, now: Long): Boolean {
        if (now < (quietUntil[highlight.id] ?: 0L)) return false
        quietUntil[highlight.id] = now + (highlight.cooldownSeconds * 1000.0).toLong()
        return true
    }

    // ---- the scan --------------------------------------------------------------------------------------

    /**
     * One pass over the world, in four stages: collect, resolve names, match, publish.
     *
     * @param player what a rule's reach is measured from — never the camera. The freecam moves the camera while
     *   the body stays put, and measuring from a camera the player flew across the island would light up half of
     *   it. The same reasoning [net.trilleo.region.RegionTracker] takes for containment.
     */
    private fun scan(
        level: ClientLevel,
        player: Entity,
        rules: List<Highlight>,
        onFound: (Highlight, Entity) -> Unit,
    ) {
        val origin = player.position()
        val reach = rules.maxOf { it.maxDistance }
        val reachSq = reach * reach
        val wantsNames = rules.any { it.kind == HighlightMatch.NAME_CONTAINS }

        val targets = ArrayList<Entity>()
        val namedStands = ArrayList<ArmorStand>()
        // The entity's own name, stripped and folded. Only entities that carry one appear here.
        val ownTag = HashMap<Int, String>()

        level.entitiesForRendering().forEach { entity ->
            // Never yourself: a glow you cannot see in first person and can in third is a distraction either
            // way, and nobody writes a rule to be told where they are standing.
            if (entity === player) return@forEach
            if (entity.isRemoved) return@forEach
            if (entity.distanceToSqr(origin) > reachSq) return@forEach

            targets.add(entity)
            if (!wantsNames) return@forEach

            val custom = entity.customName ?: return@forEach
            val text = TextClean.strip(custom.string).lowercase(Locale.ROOT)
            if (text.isEmpty()) return@forEach
            ownTag[entity.id] = text
            if (entity is ArmorStand) namedStands.add(entity)
        }

        // The name a mob wears on a separate armor stand, keyed by the mob. Only filled for stands whose text
        // some rule actually cares about, which on Hypixel turns a query-per-nametag into a query-per-match.
        val attachedTag = HashMap<Int, String>()
        namedStands.forEach { stand ->
            val text = ownTag[stand.id] ?: return@forEach
            if (rules.none { it.kind == HighlightMatch.NAME_CONTAINS && it.matches(text, "") }) return@forEach
            val wearer = wearerOf(level, stand) ?: return@forEach
            // The wearer's own name wins: an entity that names itself is telling the truth about itself, and a
            // stand that happens to float above it is not.
            if (wearer.id in ownTag) return@forEach
            attachedTag[wearer.id] = text
            // The stand has handed its name over, so it must not also match on it — the mob is what the player
            // is looking for, and marking its name a second time would only double the picture. A stand nobody
            // claimed keeps its name and matches normally: that is a standalone hologram, and it is also every
            // mob Hypixel has not sent yet, which is what [nameTagFor] draws instead of a glow.
            ownTag.remove(stand.id)
        }

        val table = HashMap<Int, Match>()
        val tally = HashMap<String, Int>()
        // Identity, not equality: two mobs of a kind carry name tags that read the same and are different
        // objects, and it is the object the render path will be holding.
        val marks = Collections.newSetFromMap(IdentityHashMap<Component, Boolean>())
        targets.forEach { entity ->
            val tag = ownTag[entity.id] ?: attachedTag[entity.id]
            val typeId = typeIdOf(entity)
            val distanceSq = entity.distanceToSqr(origin)

            // First rule wins, in list order, so an entity has exactly one colour and one label rather than
            // whichever of several the iteration happened to reach last. Reordering the list is therefore how a
            // player resolves an overlap — a specific rule above a broad one.
            val rule = rules.firstOrNull { candidate ->
                distanceSq <= candidate.maxDistance * candidate.maxDistance && candidate.matches(tag, typeId)
            } ?: return@forEach

            val match = matchOf(rule, entity, distanceSq)
            table[entity.id] = match
            match.nameTag?.let { marks.add(it) }
            tally[rule.id] = (tally[rule.id] ?: 0) + 1
            if (rule.notify && announced.getOrPut(rule.id) { HashSet() }.add(entity.id)) {
                onFound(rule, entity)
            }
        }

        // The marks go up before the table does, so a frame can never find a name tag it cannot recognise: the
        // render path only ever reaches one through the table it is published with.
        markedTags = marks
        matches = table
        counts = tally
        prune(level, rules)
    }

    /**
     * The name tag [entity] reads as, stripped and folded, resolved exactly as the scan resolves it — the
     * entity's own name, or the name of the stand floating above it.
     *
     * For the two paths that need one entity's answer rather than the whole world's: creating a rule from what
     * the player is looking at, and `/hexa highlight nearby`. Deliberately routed through [wearerOf] rather
     * than a mirrored search of its own, so what these report can never disagree with what actually matches.
     */
    fun tagOf(level: ClientLevel, entity: Entity): String? {
        entity.customName?.let { own ->
            val text = TextClean.strip(own.string).lowercase(Locale.ROOT)
            if (text.isNotEmpty()) return text
        }

        val pos = entity.position()
        val box = AABB(
            pos.x - SEARCH_RADIUS,
            pos.y + entity.bbHeight - SEARCH_RISE,
            pos.z - SEARCH_RADIUS,
            pos.x + SEARCH_RADIUS,
            pos.y + entity.bbHeight + SEARCH_DROP,
            pos.z + SEARCH_RADIUS,
        )
        return level.getEntities(entity, box) { it is ArmorStand && it.customName != null }
            .asSequence()
            .filterIsInstance<ArmorStand>()
            .sortedBy { horizontalDistanceSq(it.position(), pos) }
            .firstOrNull { wearerOf(level, it)?.id == entity.id }
            ?.customName
            ?.let { TextClean.strip(it.string).lowercase(Locale.ROOT) }
            ?.takeIf { it.isNotEmpty() }
    }

    /**
     * The entity a name-carrying armor stand is labelling, or null when it is labelling nothing.
     *
     * Hypixel floats a mob's name on an invisible stand roughly a head above it, so the search is a shallow box
     * hanging below the stand: narrow horizontally, because two mobs standing shoulder to shoulder each have
     * their own stand, and short vertically, because a tall stack of holograms must not claim the mob under the
     * bottom one. The closest candidate horizontally wins, and armor stands are excluded so a column of them
     * cannot label each other.
     */
    private fun wearerOf(level: ClientLevel, stand: ArmorStand): Entity? {
        val pos = stand.position()
        val box = AABB(
            pos.x - SEARCH_RADIUS,
            pos.y - SEARCH_DROP,
            pos.z - SEARCH_RADIUS,
            pos.x + SEARCH_RADIUS,
            pos.y + SEARCH_RISE,
            pos.z + SEARCH_RADIUS,
        )
        return level.getEntities(stand, box) { it is LivingEntity && it !is ArmorStand && !it.isRemoved }
            .minByOrNull { horizontalDistanceSq(it.position(), pos) }
    }

    private fun horizontalDistanceSq(a: Vec3, b: Vec3): Double {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return dx * dx + dz * dz
    }

    private fun matchOf(rule: Highlight, entity: Entity, distanceSq: Double): Match {
        // Forced opaque: the outline pass has no alpha to spend, so a colour written with one would otherwise
        // come out as a darker version of itself rather than a translucent one.
        val color = ARGB.opaque(
            HexColor.parseOrDefault(
                rule.color.ifBlank { HighlightConfig.settings.defaultColor },
                DEFAULT_COLOR,
                alpha = false,
            ),
        )
        val nameTag = nameTagFor(entity, color)
        return Match(
            color = color,
            outline = entity !is ArmorStand || !entity.isMarker,
            label = if (nameTag == null) labelFor(rule, color, distanceSq) else null,
            nameTag = nameTag,
        )
    }

    /**
     * The marked-up name tag for a match that has no body to glow, or null for an ordinary entity.
     *
     * **This is what makes a rule work before you are close enough to see the mob.** Hypixel sends a distant
     * mob as its floating name and nothing else: the name stand arrives, the mob does not, so [wearerOf] finds
     * nobody, the stand keeps its own name and the rule matches *the stand*. That is the right match — it is
     * the only thing in the world carrying the name — but a glow on it draws nothing, because
     * `ArmorStandRenderer` returns no render type at all for a marker stand and only an armor-stand silhouette
     * for an invisible one. So the visible half of the highlight moves to the one thing that *is* on screen:
     * the name itself, wrapped in the rule's colour.
     *
     * Nothing hands over explicitly. Walk closer, the mob arrives, [wearerOf] gives it the stand's name, the
     * stand stops matching and the mob glows — the ordinary highlight, from the ordinary path.
     *
     * The name is taken as Hypixel wrote it, colours and health bar and all, and only bracketed: it is
     * information the player is reading, and a rule that repainted it would be taking away more than it adds.
     * Built here rather than in the render hook for the reason [labelFor] is, and stale by the same scan
     * interval — a fifth of a second behind a health bar on a mob too far away to fight.
     */
    private fun nameTagFor(entity: Entity, color: Int): Component? {
        if (entity !is ArmorStand) return null
        // A plain armor stand glows perfectly well; only one you cannot see needs its name doing the work.
        if (!entity.isMarker && !entity.isInvisible) return null
        val name = entity.customName ?: return null
        return Component.empty()
            .append(Component.literal(MARK_LEFT).withColor(color))
            .append(name)
            .append(Component.literal(MARK_RIGHT).withColor(color))
    }

    /**
     * The floating label, or null when the rule wants none.
     *
     * Built here rather than in the render hook because it is the only allocation this feature makes per match,
     * and doing it once per scan instead of once per frame is the difference between "free" and "noticeable".
     * The distance it carries is therefore as of the last scan, which at the default interval is a fifth of a
     * second old — well under what anyone reads off a number that is changing as they walk.
     */
    private fun labelFor(rule: Highlight, color: Int, distanceSq: Double): Component? {
        if (!rule.showLabel) return null
        val text = if (rule.labelDistance) {
            String.format(Locale.ROOT, "%s  %.0fm", rule.name, kotlin.math.sqrt(distanceSq))
        } else {
            rule.name
        }
        // A rule's name is the player's own words, never a translation key, so a literal is right here.
        return Component.literal(text).withColor(color)
    }

    /** [entity]'s namespaced type id, e.g. `minecraft:zombie` — what an `ENTITY_TYPE` rule compares against. */
    fun typeIdOf(entity: Entity): String = EntityType.getKey(entity.type).toString()

    /**
     * Drops what the announced set no longer needs: entities that have left the level, and rules that are gone.
     *
     * Keyed on the entity still existing rather than on it still being matched, so a mob that wanders out of a
     * rule's reach and back does not announce itself twice — it was never *newly found*, only briefly out of
     * sight. Without this the set would grow for as long as the session lasts.
     */
    private fun prune(level: ClientLevel, rules: List<Highlight>) {
        if (announced.isEmpty()) return
        val live = rules.mapTo(HashSet()) { it.id }
        announced.keys.retainAll(live)
        announced.values.forEach { ids -> ids.removeAll { level.getEntity(it) == null } }
    }

    /**
     * The enabled rules that could apply on [island], cached until the island or the config changes.
     *
     * A rule with no island matches anywhere, which is what makes highlights work in singleplayer and off
     * Skyblock; every other one is filtered out here so the scan only walks plausible candidates.
     */
    private fun candidatesFor(island: String?): List<Highlight> {
        if (island == candidateIsland && HighlightConfig.revision == candidateRevision) return candidates

        candidateIsland = island
        candidateRevision = HighlightConfig.revision
        candidates = HighlightConfig.settings.highlights.filter { highlight ->
            highlight.enabled && (highlight.island.isEmpty() || highlight.island == island)
        }
        return candidates
    }

    /** Fallback when a colour will not parse — a visibly wrong glow beats an invisible one. */
    private const val DEFAULT_COLOR = 0xFFFFFF55.toInt()

    /** What a marked name tag is bracketed with. Arrows rather than words, so no locale owns them. */
    private const val MARK_LEFT = "▶ "
    private const val MARK_RIGHT = " ◀"

    /** How far either side of a name stand to look for the mob wearing it, in blocks. */
    private const val SEARCH_RADIUS = 0.75

    /** How far below a name stand to look. A mob's head, plus enough for a tall one. */
    private const val SEARCH_DROP = 2.5

    /** How far above, for a stand sitting slightly low. Small, so a stacked hologram cannot reach past it. */
    private const val SEARCH_RISE = 0.3
}
