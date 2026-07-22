package net.trilleo.config.gui

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component
import net.trilleo.config.AutoSwitchKind
import net.trilleo.config.ConfigProfiles
import net.trilleo.config.ProfileDirtyTracker
import net.trilleo.config.ProfileEntry
import java.util.concurrent.TimeUnit

/**
 * The scrolling list of profiles in [ProfilesScreen].
 *
 * Built on the same [ContainerObjectSelectionList] foundation as [ConfigEntryList], for the same reasons —
 * scrolling, the scrollbar, keyboard navigation and mouse routing all come for free, and rows draw themselves
 * through `extractContent` to match this build's render pipeline.
 *
 * Rows are taller than a settings row because each carries two lines: the name and its state on top, and the
 * description or the profile's dates underneath. That second line is what makes a list of five similarly
 * named profiles tellable apart, which was the whole problem with the old one-row-per-action tab.
 */
class ProfileList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    top: Int,
    private val screen: ProfilesScreen,
) : ContainerObjectSelectionList<ProfileList.Row>(minecraft, width, height, top, ROW_HEIGHT) {

    override fun getRowWidth(): Int = width - 24

    override fun scrollBarX(): Int = x + width - 8

    /** Rebuilds the rows from the current profile list, keeping the scroll position. */
    fun refresh() {
        val scroll = scrollAmount()
        clearEntries()
        val deletable = ConfigProfiles.entries().size > 1
        ConfigProfiles.entries().forEach { addEntry(Row(it, deletable, screen)) }
        setScrollAmount(scroll)
    }

    /** One profile: its identity on the left, its actions on the right. */
    class Row(
        private val entry: ProfileEntry,
        deletable: Boolean,
        screen: ProfilesScreen,
    ) : ContainerObjectSelectionList.Entry<Row>() {

        private val isActive get() = entry.name == ConfigProfiles.settings.active

        private val switchButton = Button.builder(SWITCH) { screen.requestSwitch(entry.name) }
            .tooltip(TIP_SWITCH)
            .build()

        private val duplicateButton = Button.builder(DUPLICATE) { screen.requestDuplicate(entry.name) }
            .tooltip(TIP_DUPLICATE)
            .build()

        private val editButton = Button.builder(EDIT) { screen.requestEdit(entry.name) }
            .tooltip(TIP_EDIT)
            .build()

        private val deleteButton = Button.builder(DELETE) { screen.requestDelete(entry.name) }
            .tooltip(if (deletable) TIP_DELETE else TIP_DELETE_LAST)
            .build()
            .apply { active = deletable }

        private val widgets = listOf(switchButton, duplicateButton, editButton, deleteButton)

        override fun children(): List<AbstractWidget> = widgets

        override fun narratables(): List<NarratableEntry> = widgets

        override fun extractContent(
            extractor: GuiGraphicsExtractor,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            delta: Float,
        ) {
            val font = Minecraft.getInstance().font

            // Buttons are laid out from the right edge inwards so the text column takes whatever is left.
            var x = contentRight
            val y = contentYMiddle - BUTTON_HEIGHT / 2
            listOf(deleteButton, editButton, duplicateButton, switchButton).forEach { button ->
                val w = if (button === switchButton) SWITCH_WIDTH else ICON_WIDTH
                x -= w
                button.x = x
                button.y = y
                button.width = w
                x -= GAP
            }
            // Switching to where you already are is a no-op; showing it as available would invite a click
            // that does nothing.
            switchButton.active = !isActive

            val textWidth = (x - contentX).coerceAtLeast(MIN_TEXT_WIDTH)
            val topY = contentYMiddle - font.lineHeight - 1
            val bottomY = contentYMiddle + 1

            extractor.text(font, nameLine(), contentX, topY, if (isActive) ACTIVE_COLOR else NAME_COLOR)
            extractor.text(font, truncate(font, subtitle(), textWidth), contentX, bottomY, MUTED_COLOR)

            widgets.forEach { it.extractRenderState(extractor, mouseX, mouseY, delta) }
        }

        /** The name, marked as active and/or holding unsaved changes. */
        private fun nameLine(): Component {
            val name = Component.literal(entry.name)
            if (!isActive) return name
            val line = Component.literal("● ").append(name)
            // The marker only means anything for the active profile: it is the only one whose saved state
            // can differ from the live settings.
            return if (ProfileDirtyTracker.isDirty) line.append(UNSAVED) else line
        }

        /** Description if there is one, otherwise the dates — whichever tells the profiles apart. */
        private fun subtitle(): Component {
            val parts = mutableListOf<String>()
            if (entry.description.isNotBlank()) parts += entry.description
            parts += "saved ${relativeTime(entry.modifiedAt)}"
            entry.autoSwitch?.let { rule ->
                parts += when (rule.kind) {
                    AutoSwitchKind.SINGLEPLAYER -> "auto: singleplayer"
                    AutoSwitchKind.SERVER -> "auto: ${rule.pattern}"
                    AutoSwitchKind.SKYBLOCK_ISLAND -> "auto: ${rule.pattern}"
                }
            }
            return Component.literal(parts.joinToString(" · "))
        }

        private fun truncate(font: net.minecraft.client.gui.Font, text: Component, available: Int): Component {
            if (font.width(text) <= available) return text
            val ellipsis = font.width("…")
            return Component.literal(font.plainSubstrByWidth(text.string, available - ellipsis) + "…")
        }
    }

    companion object {
        const val ROW_HEIGHT = 34

        private const val BUTTON_HEIGHT = 20
        private const val SWITCH_WIDTH = 54
        private const val ICON_WIDTH = 20
        private const val GAP = 4
        private const val MIN_TEXT_WIDTH = 40

        private const val NAME_COLOR = 0xFFFFFFFF.toInt()
        private const val ACTIVE_COLOR = 0xFF7FE0C0.toInt()
        private const val MUTED_COLOR = 0xFF9A9A9A.toInt()

        private val SWITCH: Component = Component.translatable("hex.profiles.switch")
        private val DUPLICATE: Component = Component.literal("⧉")
        private val EDIT: Component = Component.literal("✎")
        private val DELETE: Component = Component.literal("✕")
        private val UNSAVED: Component =
            Component.translatable("hex.profiles.unsaved_marker").withStyle(ChatFormatting.YELLOW)

        private val TIP_SWITCH: Tooltip = Tooltip.create(Component.translatable("hex.profiles.switch.tooltip"))
        private val TIP_DUPLICATE: Tooltip =
            Tooltip.create(Component.translatable("hex.profiles.duplicate.tooltip"))
        private val TIP_EDIT: Tooltip = Tooltip.create(Component.translatable("hex.profiles.edit.tooltip"))
        private val TIP_DELETE: Tooltip = Tooltip.create(Component.translatable("hex.profiles.delete.tooltip"))
        private val TIP_DELETE_LAST: Tooltip =
            Tooltip.create(Component.translatable("hex.profiles.delete.last.tooltip"))

        /**
         * A coarse "how long ago", precise enough to tell profiles apart without pulling in date formatting
         * or a locale-dependent layout. A zero timestamp means the profile predates metadata being recorded.
         */
        fun relativeTime(millis: Long): String {
            if (millis <= 0L) return "unknown"
            val elapsed = System.currentTimeMillis() - millis
            if (elapsed < 0L) return "just now"
            val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
            val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
            val days = TimeUnit.MILLISECONDS.toDays(elapsed)
            return when {
                minutes < 1L -> "just now"
                minutes < 60L -> "${minutes}m ago"
                hours < 24L -> "${hours}h ago"
                days < 365L -> "${days}d ago"
                else -> "over a year ago"
            }
        }
    }
}
