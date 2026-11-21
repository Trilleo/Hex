package net.trilleo.sync

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import net.trilleo.util.TextClean

/**
 * Watches for the Attribute Menu and the Hunting Box, reads them while open, and hands one [MenuSnapshot] to
 * [SyncQueue] when the player leaves the menu.
 *
 * Polled from the tick rather than hooked into the screen, for the same reason as the rest of the mod's
 * Skyblock readers: a best-effort reader should degrade when Hypixel changes something, not fail to load.
 * The menu is re-read every few ticks while open because its items arrive over several packets after the
 * screen appears.
 *
 * All the text interpretation is [ShardText]'s; this only walks the slots.
 */
object ShardReader {

    private var session: MenuSession? = null
    private var ticksUntilRead = 0

    /** Drops a half-read visit without sending it; the next server is not where it was read. */
    fun reset() {
        session = null
        ticksUntilRead = 0
    }

    fun tick(client: Minecraft) {
        val screen = client.gui.screen() as? AbstractContainerScreen<*>
        val page = screen?.let { ShardText.menuPage(TextClean.strip(it.title.string)) }

        val current = session
        if (current != null && page?.menu != current.menu) {
            session = null
            current.snapshot()?.let { SyncQueue.submit(it) }
        }
        if (page == null) return

        if (--ticksUntilRead > 0) return
        ticksUntilRead = READ_INTERVAL_TICKS

        val menu = screen.menu as? ChestMenu ?: return
        val active = session ?: MenuSession(page.menu).also { session = it }
        runCatching { read(menu, page, active) }
    }

    private fun read(menu: ChestMenu, page: MenuPage, into: MenuSession) {
        val container = menu.container
        val size = minOf(container.containerSize, menu.rowCount * 9)
        val entries = LinkedHashMap<String, Int>()
        var loaded = false
        var searching = false

        for (slot in 0 until size) {
            val stack = container.getItem(slot)
            if (stack.isEmpty) continue
            loaded = true
            val name = TextClean.strip(stack.hoverName.string)
            val lore = loreOf(stack)
            when (page.menu) {
                ShardMenu.ATTRIBUTES -> {
                    if (ShardText.isSearchActive(lore)) searching = true
                    ShardText.attributeEntry(name, lore)?.let { (shard, level) -> entries[shard] = level }
                }

                ShardMenu.HUNTING_BOX -> {
                    if (!ShardText.isContentSlot(slot)) {
                        if (ShardText.isSearchActive(lore)) searching = true
                        continue
                    }
                    ShardText.heldEntry(name, lore)?.let { (shard, count) -> entries[shard] = count }
                }
            }
        }
        into.accept(page, entries, searching, loaded)
    }

    private fun loreOf(stack: ItemStack): List<String> =
        stack.get(DataComponents.LORE)?.lines()?.map { TextClean.strip(it.string) }.orEmpty()

    private const val READ_INTERVAL_TICKS = 5
}
