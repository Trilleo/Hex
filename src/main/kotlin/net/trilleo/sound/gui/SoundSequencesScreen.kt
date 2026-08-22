package net.trilleo.sound.gui

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.sound.SoundConfig
import net.trilleo.sound.SoundPlayer
import net.trilleo.sound.model.SoundSequence
import net.trilleo.sound.preset.gui.SoundPresetsScreen

/**
 * The sequence library: a scrolling [SoundSequenceList] plus a footer for adding and for the shipped presets.
 *
 * Reachable from the **Sounds** tab of `/hexa config`, and from the sound picker's **Sequences** button — so
 * "none of the game's sounds is right" leads somewhere rather than dead-ending.
 */
class SoundSequencesScreen(private val parent: Screen?) :
    Screen(Component.translatable("hex.sounds.title")) {

    private var list: SoundSequenceList? = null

    override fun init() {
        val listHeight = height - TOP - FOOTER_HEIGHT
        list = addRenderableWidget(SoundSequenceList(minecraft, width, listHeight, TOP, this))

        addRenderableWidget(StringWidget(MARGIN, 12, width - MARGIN * 2, 12, title, font))

        val y = height - 28
        var x = width / 2 - (BUTTON_WIDTH * 3 + GAP * 2) / 2

        addRenderableWidget(
            Button.builder(Component.translatable("hex.sounds.add")) { add() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.sounds.add.tooltip")))
                .build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.sounds.presets")) {
                minecraft.setScreen(SoundPresetsScreen(this))
            }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("hex.sounds.presets.tooltip")))
                .build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.sounds.done")) { onClose() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )

        refreshRows()
    }

    /** Re-reads the sequences into the list. Called after every add, delete, and return from the editor. */
    fun refreshRows() {
        list?.show(SoundConfig.settings.sequences, Component.translatable("hex.sounds.empty"))
    }

    /**
     * Creates an empty sequence and opens it.
     *
     * Straight into the editor, matching [net.trilleo.region.gui.RegionsScreen]: a sequence with no steps is
     * never what anyone wanted, and the list has nothing useful to show about one.
     */
    private fun add() {
        val name = Component.translatable("hex.sounds.new_name").string
        val sequence = SoundSequence().also {
            it.id = SoundConfig.uniqueId(name)
            it.name = name
            it.bpm = SoundConfig.settings.defaultBpm
        }
        SoundConfig.settings.sequences.add(sequence)
        SoundConfig.normalizeNow()
        SoundConfig.save()
        refreshRows()
        list?.scrollToBottom()
        minecraft.setScreen(SoundSequenceEditScreen(this, sequence))
    }

    override fun onClose() {
        SoundPlayer.stopAll()
        minecraft.setScreen(parent)
    }

    override fun removed() {
        // Edits mark the config dirty as they happen; this makes leaving the screen a definite save point
        // rather than waiting on the debounce.
        SoundConfig.save()
        SoundPlayer.stopAll()
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
