package net.trilleo.highlight.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EntityType
import net.trilleo.config.ConfigCategory
import net.trilleo.config.gui.ConfigEntryList
import net.trilleo.highlight.*
import net.trilleo.highlight.model.Highlight
import net.trilleo.highlight.model.HighlightMatch
import net.trilleo.reminder.model.ActionKind
import net.trilleo.reminder.model.ReminderAction
import net.trilleo.title.gui.TitleEditScreen
import net.trilleo.util.Notify
import java.util.*

/**
 * Edits one highlight rule.
 *
 * Reuses [ConfigEntryList] by building a throwaway [ConfigCategory] whose entries close over this rule, in
 * exactly the way [net.trilleo.region.gui.RegionEditScreen] does — inheriting scrolling, keyboard navigation,
 * per-row reset, inline validation and tooltips for nothing, and keeping the editor looking like the rest of
 * the mod.
 *
 * **Which rows exist depends on the choices above them**, which is why nearly every setter calls [rebuild]: the
 * value field's label and validator follow the match kind, and the notification's title and sound settings only
 * appear once there is a notification to configure. Showing all of them at once would make a rule look far more
 * complicated than the two fields most of them need.
 */
class HighlightEditScreen(
    private val parent: HighlightsScreen?,
    private val highlight: Highlight,
) : Screen(Component.translatable("hex.highlights.edit.title")) {

    private var list: ConfigEntryList? = null
    private var summary: StringWidget? = null

    override fun init() {
        val listHeight = height - TOP - FOOTER_HEIGHT
        list = addRenderableWidget(ConfigEntryList(minecraft, width, listHeight, TOP, this))

        addRenderableWidget(StringWidget(MARGIN, 12, width - MARGIN * 2, 12, title, font))
        summary = addRenderableWidget(
            StringWidget(MARGIN, height - 44, width - MARGIN * 2, 10, summaryText(), font),
        )

        val y = height - 28
        var x = width / 2 - (BUTTON_WIDTH * 3 + GAP * 2) / 2

        addRenderableWidget(
            Button.builder(Component.translatable("hex.highlights.edit.pick")) { pick() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.highlights.edit.test")) { test() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.highlights.edit.done")) { onClose() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )

        rebuild(preserveScroll = false)
    }

    /** Rebuilds the rows, since which fields apply depends on the match kind and the notification switch. */
    private fun rebuild(preserveScroll: Boolean = true) {
        val category = buildCategory()
        list?.show(listOf(category.title to category.entries), preserveScroll)
        summary?.message = summaryText()
    }

    private fun touch() {
        HighlightConfig.markDirty()
        summary?.message = summaryText()
    }

    private fun summaryText(): Component = Component.literal(highlight.summary())

    private fun buildCategory(): ConfigCategory = ConfigCategory.build("highlight_edit") {
        toggle(
            "enabled",
            default = true,
            get = { highlight.enabled },
            set = { highlight.enabled = it; HighlightConfig.save() },
        )
        text(
            "name",
            default = "",
            get = { highlight.name },
            set = { highlight.name = HighlightConfig.uniqueName(it, except = highlight); touch() },
            validate = { typed ->
                if (typed.isBlank()) Component.translatable("hex.highlights.edit.name.blank") else null
            },
        )

        enum(
            "kind",
            default = HighlightMatch.NAME_CONTAINS,
            get = { highlight.kind },
            set = {
                highlight.kind = it
                // The stored value was folded for the old kind and is compared differently by the new one, so
                // it is re-folded rather than left in whatever shape the previous kind wanted.
                highlight.normalizeValue()
                touch()
                rebuild()
            },
        )
        // One field, two identities: the label, tooltip and validator all follow the kind, so the row reads as
        // "the name fragment" or "the entity id" rather than as a generic "value" the player has to interpret.
        text(
            valueKey(),
            default = "",
            get = { highlight.value },
            set = { highlight.value = it; highlight.normalizeValue(); touch() },
            validate = ::validateValue,
            // Only the type field has a vocabulary. A name fragment is whatever Hypixel decided to call
            // something, which no registry on this client has ever heard of.
            suggestions = {
                if (highlight.kind == HighlightMatch.ENTITY_TYPE) EntityTypes.ids() else emptyList()
            },
        )

        text(
            "island",
            default = "",
            get = { highlight.island },
            set = { highlight.island = it.trim().lowercase(Locale.ROOT); touch() },
        )
        color(
            "color",
            default = HighlightConfig.settings.defaultColor,
            chroma = true,
            get = { HighlightTracker.colorOf(highlight) },
            set = { highlight.color = it; touch() },
        )
        slider(
            "max_distance",
            min = Highlight.MAX_DISTANCE_MIN,
            max = Highlight.MAX_DISTANCE_MAX,
            step = 4.0,
            default = Highlight.DEFAULT_MAX_DISTANCE,
            get = { highlight.maxDistance },
            set = { highlight.maxDistance = it; touch() },
            format = { String.format(Locale.ROOT, "%.0f blocks", it) },
        )

        toggle(
            "show_label",
            default = false,
            get = { highlight.showLabel },
            set = { highlight.showLabel = it; touch(); rebuild() },
        )
        if (highlight.showLabel) {
            toggle(
                "label_distance",
                default = false,
                get = { highlight.labelDistance },
                set = { highlight.labelDistance = it; touch() },
            )
        }

        toggle(
            "notify",
            default = false,
            get = { highlight.notify },
            set = {
                highlight.notify = it
                // A rule that announces itself and does nothing reads exactly like a broken one, so switching
                // notifications on brings a title with it rather than an empty action list.
                if (it && highlight.actions.isEmpty()) setAction(ActionKind.TITLE, true)
                touch()
                rebuild()
            },
        )
        if (highlight.notify) {
            text(
                "notify_text",
                default = "",
                get = { highlight.notifyText },
                set = { highlight.notifyText = it; touch() },
            )
            slider(
                "cooldown",
                min = Highlight.COOLDOWN_MIN,
                max = Highlight.COOLDOWN_MAX,
                step = 5.0,
                default = Highlight.DEFAULT_COOLDOWN_SECONDS,
                get = { highlight.cooldownSeconds },
                set = { highlight.cooldownSeconds = it; touch() },
                format = { String.format(Locale.ROOT, "%.0fs", it) },
            )

            // Actions are switches rather than a list, for the same reason the region editor's are: there are
            // only two kinds a highlight can carry, and a list editor for a two-item set is more machinery than
            // the thing it edits.
            toggle(
                "action_title",
                default = true,
                get = { highlight.actions.any { it.kind == ActionKind.TITLE } },
                set = { setAction(ActionKind.TITLE, it); rebuild() },
            )
            actionOf(ActionKind.TITLE)?.let { titleAction ->
                action("title_style") { screen ->
                    Minecraft.getInstance().setScreen(
                        TitleEditScreen(
                            screen,
                            titleAction.title,
                            ownerText = { highlight.notifyText.ifBlank { highlight.name } },
                            onChange = this@HighlightEditScreen::touch,
                        ),
                    )
                }
            }

            toggle(
                "action_sound",
                default = false,
                get = { highlight.actions.any { it.kind == ActionKind.SOUND } },
                set = { setAction(ActionKind.SOUND, it); rebuild() },
            )
            actionOf(ActionKind.SOUND)?.let { sound ->
                text(
                    "sound_id",
                    default = ReminderAction.DEFAULT_SOUND,
                    get = { sound.value },
                    set = { sound.value = it; touch() },
                    validate = { id ->
                        if (Notify.soundFor(id) == null) {
                            Component.translatable("hex.highlights.edit.sound.unknown")
                        } else {
                            null
                        }
                    },
                )
                slider(
                    "sound_pitch",
                    min = ReminderAction.PITCH_MIN,
                    max = ReminderAction.PITCH_MAX,
                    step = 0.05,
                    default = 1.0,
                    get = { sound.pitch },
                    set = { sound.pitch = it; touch() },
                    format = { String.format(Locale.ROOT, "%.2f", it) },
                )
                slider(
                    "sound_volume",
                    min = ReminderAction.VOLUME_MIN,
                    max = ReminderAction.VOLUME_MAX,
                    step = 0.05,
                    default = 1.0,
                    get = { sound.volume },
                    set = { sound.volume = it; touch() },
                    format = { String.format(Locale.ROOT, "%.0f%%", it * 100) },
                )
            }
        }
    }

    /** Which translation key the value row uses, so its label and tooltip describe the current kind. */
    private fun valueKey(): String = when (highlight.kind) {
        HighlightMatch.NAME_CONTAINS -> "value_name"
        HighlightMatch.ENTITY_TYPE -> "value_type"
    }

    /**
     * The value row's inline error, or null.
     *
     * An entity id is checked against the registry, the same way the region editor checks a sound id — a typo
     * there produces a rule that matches nothing at all, with nothing on screen to say why. A name fragment
     * cannot be checked against anything, so only the empty case is reported.
     */
    private fun validateValue(typed: String): Component? {
        if (typed.isBlank()) return Component.translatable("hex.highlights.edit.value.blank")
        if (highlight.kind != HighlightMatch.ENTITY_TYPE) return null
        return if (EntityType.byString(typed.trim().lowercase(Locale.ROOT)).isPresent) {
            null
        } else {
            Component.translatable("hex.highlights.edit.value.unknown_type")
        }
    }

    private fun setAction(kind: ActionKind, on: Boolean) {
        if (on) {
            if (highlight.actions.none { it.kind == kind }) {
                // Through the factory for a title, so a new one is timed the way the Titles tab says.
                val action = if (kind == ActionKind.TITLE) {
                    ReminderAction.title()
                } else {
                    ReminderAction().apply { this.kind = kind }
                }
                highlight.actions.add(action)
            }
        } else {
            highlight.actions.removeAll { it.kind == kind }
            // Never leave a notifying rule that fires and does nothing — it reads exactly like a broken one.
            if (highlight.notify && highlight.actions.isEmpty()) {
                highlight.actions.add(ReminderAction.title())
            }
        }
        touch()
    }

    private fun actionOf(kind: ActionKind): ReminderAction? = highlight.actions.firstOrNull { it.kind == kind }

    /**
     * Refills the match kind and value from the entity under the crosshair, keeping everything else.
     *
     * The colour, the label and the notification are the parts a player tuned; what they usually got wrong is
     * the name, and re-typing a Hypixel mob's name by hand is precisely what this feature exists to avoid.
     */
    private fun pick() {
        val captured = HighlightCapture.fromCrosshair(minecraft) ?: return
        highlight.kind = captured.kind
        highlight.value = captured.value
        touch()
        rebuild()
    }

    /** Fires the rule's actions now, so a sound and a title can be judged before waiting for a mob. */
    private fun test() {
        HighlightAlerts.test(Minecraft.getInstance(), highlight)
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun removed() {
        HighlightConfig.save()
        parent?.refreshRows()
    }

    private companion object {
        const val MARGIN = 24
        const val TOP = 32
        const val FOOTER_HEIGHT = 52
        const val BUTTON_WIDTH = 90
        const val BUTTON_HEIGHT = 20
        const val GAP = 6
    }
}
