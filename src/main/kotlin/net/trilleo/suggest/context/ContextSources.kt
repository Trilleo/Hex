package net.trilleo.suggest.context

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.EquipmentSlot
import net.trilleo.skyblock.SkyblockLocation
import net.trilleo.skyblock.item.HeldItem
import net.trilleo.skyblock.item.SkyblockItem
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.*

/**
 * Reads a [ContextSnapshot] off the live game.
 *
 * **This is deliberately the only expensive thing in the feature, and it is called about twice per chat
 * screen.** Reading the hotbar and the worn armour costs one `CustomData.copyTag()` per stack — the cost
 * [SkyblockItem] warns about in its own documentation — so thirteen deep tag copies go into one snapshot.
 * That would be indefensible per keystroke and is unnoticeable twice.
 *
 * Twice is enough because of a property of the chat screen itself: **nothing here can change while it is
 * open.** The player cannot move, cannot change hotbar slot, and cannot open their inventory without closing
 * chat first. So [net.trilleo.suggest.SuggestSession] takes one snapshot when chat opens and every keystroke
 * in between re-uses it; the recorder takes a fresh one at send time, which is the only other moment the
 * answer matters. Nothing samples this on a tick.
 *
 * Every read is individually guarded. These are best-effort sources — the sidebar is Hypixel's to change and
 * the player may have no world at all — and a feature that cannot be read is [ContextSnapshot.UNKNOWN],
 * never an exception and never a missing entry.
 */
object ContextSources {

    /**
     * Maps a Skyblock item id to a broad class (`"pickaxe"`, `"rod"`, `"sword"`, …), or null for unknown.
     *
     * Supplied by the catalogue, as a hook for the same reason [net.trilleo.suggest.model.CommandParser]'s
     * slot provider is one: this object is reachable long before any asset has loaded.
     *
     * The class matters more than the id it comes from. There are dozens of pickaxes on Skyblock and the
     * player will own two of them, so learning against the id alone means every upgrade throws away what was
     * learned about mining. The class survives the upgrade.
     */
    @Volatile
    var kindProvider: (String) -> String? = { null }

    /** Reads every feature. Never throws. */
    fun snapshot(client: Minecraft): ContextSnapshot {
        val features = HashMap<String, String>(ContextSnapshot.ALL.size)

        fun put(key: String, value: String?) {
            features[key] = value?.trim()?.takeIf { it.isNotEmpty() } ?: ContextSnapshot.UNKNOWN
        }

        put(ContextSnapshot.SERVER, read { host(client) })
        val island = read { SkyblockLocation.current }
        put(ContextSnapshot.ISLAND, island)
        put(ContextSnapshot.CELL, read { cell(client, island) })

        val held = read { HeldItem.id }
        put(ContextSnapshot.HELD, held)
        put(ContextSnapshot.HELD_KIND, read { held?.let(kindProvider) })

        put(ContextSnapshot.HOTBAR, read { hotbarSignature(client) })
        put(ContextSnapshot.ARMOR, read { armorSignature(client) })
        put(ContextSnapshot.FULLNESS, read { fullness(client) })

        val now = LocalDateTime.now()
        put(ContextSnapshot.HOUR, read { "h${now.hour / HOUR_BUCKET}" })
        put(ContextSnapshot.DAY, read { if (isWeekend(now.dayOfWeek)) "weekend" else "weekday" })
        put(ContextSnapshot.SESSION, read { SessionMemory.phase() })

        put(ContextSnapshot.PREV1, SessionMemory.prev1)
        put(ContextSnapshot.PREV2, SessionMemory.prev2)
        put(ContextSnapshot.CUE, read { ChatCues.current() })

        return ContextSnapshot(features)
    }

    /** One guarded read. A source that throws contributes [ContextSnapshot.UNKNOWN] like one that is absent. */
    private inline fun read(block: () -> String?): String? = runCatching(block).getOrNull()

    /**
     * The connected server's host, without its port and lower-cased, or `"single"` in singleplayer.
     *
     * The same rule [net.trilleo.config.ProfileAutoSwitch] matches profiles on, so a player who has both a
     * Hypixel profile and a survival profile sees the two agree about which server they are on.
     */
    private fun host(client: Minecraft): String? {
        if (client.isLocalServer) return "single"
        return client.currentServer?.ip
            ?.substringBefore(':')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() }
    }

    /**
     * The island plus the player's position quantised to [CELL_SIZE] blocks, or null off Skyblock.
     *
     * Quantised rather than exact for the obvious reason and one less obvious one: a 32-block cell is roughly
     * "the bit of the hub you are standing in", which is the granularity at which intent is actually stable —
     * fine enough to tell the dungeon entrance from the bazaar, coarse enough that you do not leave the cell
     * by turning around. Y is ignored, because on Skyblock height is almost never what distinguishes one
     * place from another.
     *
     * Requires the island, so this is null on servers where the sidebar says nothing. A bare coordinate with
     * no island to anchor it would collide across worlds and mean nothing in the dashboard.
     */
    private fun cell(client: Minecraft, island: String?): String? {
        if (island == null) return null
        val pos = client.player?.blockPosition() ?: return null
        return "$island@${pos.x shr CELL_SHIFT},${pos.z shr CELL_SHIFT}"
    }

    /** A hash of the Skyblock ids across the hotbar, order-independent. In effect: which loadout is equipped. */
    private fun hotbarSignature(client: Minecraft): String? {
        val items = client.player?.inventory?.nonEquipmentItems ?: return null
        val ids = sortedSetOf<String>()
        for (slot in 0 until minOf(HOTBAR_SLOTS, items.size)) {
            SkyblockItem.idOf(items[slot])?.let { ids += it }
        }
        return signature(ids)
    }

    /** A hash of the four worn armour pieces' Skyblock ids, in slot order. */
    private fun armorSignature(client: Minecraft): String? {
        val player = client.player ?: return null
        val ids = ARMOR_SLOTS.map { slot ->
            SkyblockItem.idOf(player.getItemBySlot(slot)) ?: ""
        }
        return signature(ids)
    }

    /**
     * A short stable tag for a collection of ids, or null when there were none.
     *
     * Hashed rather than stored verbatim because a full armour set spells out four ids of twenty characters
     * apiece, and this string becomes a map key repeated across every command ever run in that set. The hash
     * is FNV-1a rather than [String.hashCode] so the value is stable by this file's own definition and cannot
     * shift under a JDK change, which would silently orphan everything learned against it.
     *
     * Collisions are harmless: two loadouts sharing a bucket makes one context feature slightly less
     * informative, which the ranker discovers on its own. Nothing is decided by this string except which
     * counter to look in.
     */
    private fun signature(ids: Collection<String>): String? {
        if (ids.all { it.isEmpty() }) return null
        var hash = FNV_OFFSET
        ids.forEach { id ->
            id.forEach { ch ->
                hash = (hash xor ch.code.toLong()) * FNV_PRIME
            }
            hash = (hash xor SEPARATOR) * FNV_PRIME
        }
        return java.lang.Long.toHexString(hash and 0xFFFFFFFFL)
    }

    /** How full the main inventory is, in fifths. Costs no tag copies — emptiness is a field read. */
    private fun fullness(client: Minecraft): String? {
        val items = client.player?.inventory?.nonEquipmentItems ?: return null
        if (items.isEmpty()) return null
        val used = items.count { !it.isEmpty }
        return "f${(used * FULLNESS_BUCKETS) / items.size}"
    }

    private fun isWeekend(day: DayOfWeek): Boolean = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY

    /** 32 blocks (`1 shl 5`); see [cell]. A shift because it is applied to every snapshot. */
    private const val CELL_SHIFT = 5

    private const val HOTBAR_SLOTS = 9

    private val ARMOR_SLOTS = listOf(
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
    )

    /** Fifths: empty, quarter, half, three-quarters, full. Finer would split habits without informing them. */
    private const val FULLNESS_BUCKETS = 5

    /** Four-hour buckets — six a day, enough to separate a morning routine from an evening one. */
    private const val HOUR_BUCKET = 4

    private const val FNV_OFFSET = -0x340d631b7bdddcdbL
    private const val FNV_PRIME = 0x100000001b3L
    private const val SEPARATOR = 0x1FL
}
