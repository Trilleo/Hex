package net.trilleo.region.gui

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.region.RegionCapture
import net.trilleo.region.RegionConfig
import net.trilleo.region.RegionRenderer
import net.trilleo.skyblock.SkyblockLocation

/**
 * The region editor: a scrolling [RegionList] plus a footer for capturing new regions and previewing them.
 *
 * Reachable from the **Regions** tab of `/hexa config` and from `/hexa region edit`.
 *
 * Regions are per island and a player has one island in mind at a time, so the list shows the current
 * island's by default. Without that filter the screen would fill with places the coordinates on it cannot
 * even be reached from, which is the opposite of useful when the reason for opening it was to find the one
 * region misbehaving where you are standing.
 */
class RegionsScreen(private val parent: Screen?) :
    Screen(Component.translatable("hex.regions.title")) {

    private var list: RegionList? = null

    /** Whether to show regions from every island rather than only the one the player is on. */
    private var showAll: Boolean = false

    private var filterButton: Button? = null
    private var previewButton: Button? = null

    override fun init() {
        val listHeight = height - TOP - FOOTER_HEIGHT
        list = addRenderableWidget(RegionList(minecraft, width, listHeight, TOP, this))

        addRenderableWidget(StringWidget(MARGIN, 12, width - MARGIN * 2, 12, title, font))

        val y = height - 28
        var x = width / 2 - (BUTTON_WIDTH * 5 + GAP * 4) / 2

        addRenderableWidget(
            Button.builder(Component.translatable("hex.regions.add_here")) { addHere() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.regions.add_here.tooltip")))
                .build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.regions.walk")) { startWalk() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.regions.walk.tooltip")))
                .build(),
        )
        x += BUTTON_WIDTH + GAP

        previewButton = addRenderableWidget(
            Button.builder(previewLabel()) { togglePreview() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.regions.preview.tooltip")))
                .build(),
        )
        x += BUTTON_WIDTH + GAP

        filterButton = addRenderableWidget(
            Button.builder(filterLabel()) { toggleFilter() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.regions.done")) { onClose() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )

        refreshRows()
    }

    /** Re-reads the regions into the list. Called after every add, delete, and return from the sub-editor. */
    fun refreshRows() {
        val island = SkyblockLocation.current
        val all = RegionConfig.settings.regions
        val shown = if (showAll) all else all.filter { it.island.isEmpty() || it.island == island }
        val hint = if (all.isEmpty() || showAll) {
            Component.translatable("hex.regions.empty")
        } else {
            Component.translatable("hex.regions.empty_here")
        }
        list?.show(shown, hint)
    }

    /**
     * Creates a region around the player and opens it.
     *
     * Deliberately usable from inside this screen: the world is still there behind it, the player has not
     * moved, and having to close the menu to press a keybind would make the obvious first action the awkward
     * one.
     */
    private fun addHere() {
        val player = minecraft.player
        if (player == null) {
            // No position to build around. Nothing to say here that the empty list does not already say.
            return
        }
        val region = RegionCapture.around(Component.translatable("hex.regions.new_name").string, player.position())
        RegionConfig.settings.regions.add(region)
        RegionConfig.normalizeNow()
        RegionConfig.save()
        refreshRows()
        list?.scrollToBottom()
        // Straight into the editor — a region with a placeholder message is never what anyone wanted.
        minecraft.setScreen(RegionEditScreen(this, region))
    }

    /**
     * Starts a walk capture and returns to the world.
     *
     * Closed outright rather than through [onClose]: the parent may be the config menu, and walking an
     * outline is impossible from behind two screens. The next thing to do is walk.
     */
    private fun startWalk() {
        val player = minecraft.player ?: return
        RegionCapture.beginWalk(player.position())
        minecraft.setScreen(null)
    }

    private fun togglePreview() {
        RegionRenderer.previewAll = !RegionRenderer.previewAll
        previewButton?.message = previewLabel()
    }

    private fun toggleFilter() {
        showAll = !showAll
        filterButton?.message = filterLabel()
        refreshRows()
    }

    private fun previewLabel(): Component = Component.translatable(
        if (RegionRenderer.previewAll) "hex.regions.preview.on" else "hex.regions.preview.off",
    )

    private fun filterLabel(): Component = Component.translatable(
        if (showAll) "hex.regions.filter.all" else "hex.regions.filter.here",
    )

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun removed() {
        // Edits mark the config dirty as they happen; this makes leaving the screen a definite save point
        // rather than waiting on the debounce.
        RegionConfig.save()
    }

    private companion object {
        const val MARGIN = 24
        const val TOP = 32
        const val FOOTER_HEIGHT = 40
        const val BUTTON_WIDTH = 74
        const val BUTTON_HEIGHT = 20
        const val GAP = 6
    }
}
