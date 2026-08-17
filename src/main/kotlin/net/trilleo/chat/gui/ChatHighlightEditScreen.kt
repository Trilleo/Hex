package net.trilleo.chat.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.chat.ChatHighlightAlerts
import net.trilleo.chat.ChatHighlightConfig
import net.trilleo.chat.ChatHighlighter
import net.trilleo.chat.model.ChatChannel
import net.trilleo.chat.model.ChatHighlight
import net.trilleo.chat.model.ChatScope
import net.trilleo.config.ConfigCategory
import net.trilleo.config.gui.ConfigEntryList
import net.trilleo.reminder.model.ActionKind
import net.trilleo.reminder.model.ReminderAction
import net.trilleo.skyblock.SkyblockLocation
import net.trilleo.title.gui.TitleEditScreen
import net.trilleo.util.Notify
import java.util.*

/**
 * Edits one chat highlight rule.
 *
 * Reuses [ConfigEntryList] by building a throwaway [ConfigCategory] whose entries close over this rule, exactly
 * the way [net.trilleo.highlight.gui.HighlightEditScreen] does — inheriting scrolling, keyboard navigation,
 * per-row reset, inline validation and tooltips for nothing, and keeping the editor looking like the rest of the
 * mod.
 *
 * **Which rows exist depends on the choices above them**, which is why several setters call [rebuild]: the
 * notification's title and sound settings only appear once there is a notification to configure. Showing all of
 * them at once would make a rule look far more complicated than the two fields most of them need.
 *
 * ### The preview
 *
 * The line above the footer is not a description of the rule; it is the rule, applied. It goes through the same
 * [ChatHighlighter] that paints real chat, so the colour, the format flags, the markers and the scope are all
 * shown as chat will show them — and it is rebuilt every frame, so a chroma rule flows here exactly as it will
 * there. This is the part that replaces the crosshair the entity editor has: chat cannot be pointed at, so the
 * only way to know a rule looks right is to be shown it looking right.
 */
class ChatHighlightEditScreen(
    private val parent: ChatHighlightsScreen?,
    private val rule: ChatHighlight,
) : Screen(Component.translatable("hex.chat_highlights.edit.title")) {

    private var list: ConfigEntryList? = null

    override fun init() {
        val listHeight = height - TOP - FOOTER_HEIGHT
        list = addRenderableWidget(ConfigEntryList(minecraft, width, listHeight, TOP, this))

        addRenderableWidget(StringWidget(MARGIN, 12, width - MARGIN * 2, 12, title, font))

        val y = height - 28
        var x = width / 2 - (BUTTON_WIDTH * 2 + GAP) / 2

        addRenderableWidget(
            Button.builder(Component.translatable("hex.chat_highlights.edit.test")) { test() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.chat_highlights.edit.done")) { onClose() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )

        rebuild(preserveScroll = false)
    }

    /** Rebuilds the rows, since which fields apply depends on chroma and on the notification switch. */
    private fun rebuild(preserveScroll: Boolean = true) {
        val category = buildCategory()
        list?.show(listOf(category.title to category.entries), preserveScroll)
    }

    private fun touch() {
        ChatHighlightConfig.markDirty()
    }

    override fun extractRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(extractor, mouseX, mouseY, delta)

        // Built here rather than held in a StringWidget, and rebuilt on every frame rather than on every edit,
        // because chroma is a colour that changes with the clock: a preview cached even for a tick would step
        // rather than flow, and this is the one place a player judges whether chroma looks right.
        extractor.centeredText(
            font,
            previewLine(),
            width / 2,
            height - PREVIEW_ABOVE_FOOTER,
            PREVIEW_COLOR,
        )
    }

    /**
     * A sample chat line with this rule applied to it.
     *
     * The sample sentence comes out of the language files, so the rule's text is dropped into whatever position
     * reads naturally in the player's own language — and its offset is then *found* rather than assumed, which is
     * what keeps that true for a translation that puts the placeholder somewhere else entirely.
     */
    private fun previewLine(): Component {
        if (rule.text.isBlank()) return Component.translatable("hex.chat_highlights.edit.preview_empty")
        val sample = Component.translatable("hex.chat_highlights.edit.preview_line", rule.text).string
        return ChatHighlighter.preview(rule, sample, sample.indexOf(rule.text))
    }

    private fun buildCategory(): ConfigCategory = ConfigCategory.build("chat_highlight_edit") {
        toggle(
            "enabled",
            default = true,
            get = { rule.enabled },
            set = { rule.enabled = it; ChatHighlightConfig.save() },
        )
        text(
            "name",
            default = "",
            get = { rule.name },
            set = { rule.name = ChatHighlightConfig.uniqueName(it, except = rule); touch() },
            validate = { typed ->
                if (typed.isBlank()) Component.translatable("hex.chat_highlights.edit.name.blank") else null
            },
        )

        // ---- what it catches -----------------------------------------------------------------------------

        text(
            "text",
            default = "",
            get = { rule.text },
            set = { rule.text = it; touch() },
            validate = { typed ->
                if (typed.isBlank()) Component.translatable("hex.chat_highlights.edit.text.blank") else null
            },
        )
        toggle(
            "case_sensitive",
            default = false,
            get = { rule.caseSensitive },
            set = { rule.caseSensitive = it; touch() },
        )
        enum(
            "channel",
            default = ChatChannel.ANY,
            get = { rule.channel },
            set = { rule.channel = it; touch() },
        )
        text(
            "islands",
            default = "",
            get = { rule.islands },
            set = { rule.islands = ChatHighlight.normalizeIslands(it); touch() },
            // The island the player is standing on, plus every island any rule already names. Between them that
            // is almost always the one being typed, and it saves guessing at the spelling the scoreboard uses.
            suggestions = ::islandSuggestions,
        )

        // ---- how it looks --------------------------------------------------------------------------------

        enum(
            "scope",
            default = ChatScope.MATCH,
            get = { rule.scope },
            set = { rule.scope = it; touch() },
        )
        // Chroma is one of the values this row can hold rather than a switch beside it — see
        // net.trilleo.color.ColorValue. That is also why the row no longer appears and disappears: there is
        // nothing left for it to contradict.
        color(
            "color",
            default = ChatHighlightConfig.settings.defaultColor,
            chroma = true,
            get = { ChatHighlightConfig.colorOf(rule) },
            set = { rule.color = it; touch() },
        )

        toggle("bold", default = false, get = { rule.bold }, set = { rule.bold = it; touch() })
        toggle("italic", default = false, get = { rule.italic }, set = { rule.italic = it; touch() })
        toggle(
            "underlined",
            default = false,
            get = { rule.underlined },
            set = { rule.underlined = it; touch() },
        )
        toggle(
            "strikethrough",
            default = false,
            get = { rule.strikethrough },
            set = { rule.strikethrough = it; touch() },
        )
        toggle(
            "obfuscated",
            default = false,
            get = { rule.obfuscated },
            set = { rule.obfuscated = it; touch() },
        )

        text(
            "prefix",
            default = "",
            get = { rule.prefix },
            set = { rule.prefix = it.take(ChatHighlight.MARKER_MAX_LENGTH); touch() },
        )
        text(
            "suffix",
            default = "",
            get = { rule.suffix },
            set = { rule.suffix = it.take(ChatHighlight.MARKER_MAX_LENGTH); touch() },
        )

        // ---- what else it does ---------------------------------------------------------------------------

        toggle(
            "hide",
            default = false,
            get = { rule.hide },
            set = { rule.hide = it; ChatHighlightConfig.save() },
        )

        toggle(
            "notify",
            default = false,
            get = { rule.notify },
            set = {
                rule.notify = it
                // A rule that announces itself and does nothing reads exactly like a broken one, so switching
                // notifications on brings a title with it rather than an empty action list.
                if (it && rule.actions.isEmpty()) setAction(ActionKind.TITLE, true)
                touch()
                rebuild()
            },
        )
        if (rule.notify) {
            text(
                "notify_text",
                default = "",
                get = { rule.notifyText },
                set = { rule.notifyText = it; touch() },
            )
            slider(
                "cooldown",
                min = ChatHighlight.COOLDOWN_MIN,
                max = ChatHighlight.COOLDOWN_MAX,
                step = 5.0,
                default = ChatHighlight.DEFAULT_COOLDOWN_SECONDS,
                get = { rule.cooldownSeconds },
                set = { rule.cooldownSeconds = it; touch() },
                format = { String.format(Locale.ROOT, "%.0fs", it) },
            )

            // Actions are switches rather than a list, for the same reason the entity editor's are: there are
            // only two kinds a chat highlight can carry, and a list editor for a two-item set is more machinery
            // than the thing it edits.
            toggle(
                "action_title",
                default = true,
                get = { rule.actions.any { it.kind == ActionKind.TITLE } },
                set = { setAction(ActionKind.TITLE, it); rebuild() },
            )
            actionOf(ActionKind.TITLE)?.let { titleAction ->
                action("title_style") { screen ->
                    Minecraft.getInstance().setScreen(
                        TitleEditScreen(
                            screen,
                            titleAction.title,
                            ownerText = { rule.notifyText.ifBlank { rule.name } },
                            onChange = this@ChatHighlightEditScreen::touch,
                        ),
                    )
                }
            }

            toggle(
                "action_sound",
                default = false,
                get = { rule.actions.any { it.kind == ActionKind.SOUND } },
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
                            Component.translatable("hex.chat_highlights.edit.sound.unknown")
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

    private fun islandSuggestions(): List<String> {
        val seen = LinkedHashSet<String>()
        SkyblockLocation.current?.let(seen::add)
        ChatHighlightConfig.settings.highlights.forEach { other ->
            other.islands.splitToSequence(ChatHighlight.ISLAND_SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach(seen::add)
        }
        return seen.toList()
    }

    private fun setAction(kind: ActionKind, on: Boolean) {
        if (on) {
            if (rule.actions.none { it.kind == kind }) {
                // Through the factory for a title, so a new one is timed the way the Titles tab says.
                val action = if (kind == ActionKind.TITLE) {
                    ReminderAction.title()
                } else {
                    ReminderAction().apply { this.kind = kind }
                }
                rule.actions.add(action)
            }
        } else {
            rule.actions.removeAll { it.kind == kind }
            // Never leave a notifying rule that fires and does nothing — it reads exactly like a broken one.
            if (rule.notify && rule.actions.isEmpty()) rule.actions.add(ReminderAction.title())
        }
        touch()
    }

    private fun actionOf(kind: ActionKind): ReminderAction? = rule.actions.firstOrNull { it.kind == kind }

    /** Fires the rule's actions now, so a sound and a title can be judged before waiting for a message. */
    private fun test() {
        ChatHighlightAlerts.test(Minecraft.getInstance(), rule)
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun removed() {
        ChatHighlightConfig.save()
        parent?.refreshRows()
    }

    private companion object {
        const val MARGIN = 24
        const val TOP = 32
        const val FOOTER_HEIGHT = 52
        const val BUTTON_WIDTH = 90
        const val BUTTON_HEIGHT = 20
        const val GAP = 6

        /** Where the preview sits: above the footer buttons, in the gap the entity editor gives its summary. */
        const val PREVIEW_ABOVE_FOOTER = 44

        /** Only the *unhighlighted* part of the preview uses this; the rest is whatever the rule paints. */
        const val PREVIEW_COLOR = 0xFFA0A0A0.toInt()
    }
}
