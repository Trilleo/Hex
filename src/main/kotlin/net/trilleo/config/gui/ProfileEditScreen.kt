package net.trilleo.config.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.config.AutoSwitchKind
import net.trilleo.config.AutoSwitchRule
import net.trilleo.config.ConfigProfiles

/**
 * Names and describes a profile, and sets what it activates on.
 *
 * A separate screen rather than an inline field in the list, which is what fixes the old tab's worst bug:
 * there, the name field wrote on every keystroke and creating a profile called `abc` also left behind `a` and
 * `ab`. Here nothing is committed until Accept is pressed, so a half-typed name is just a half-typed name.
 *
 * The same screen serves creating, renaming and duplicating, since all three are "choose a name and settle
 * the details" — [onAccept] is what differs, and the caller supplies it.
 *
 * @param existingName the profile being edited, or null when creating or duplicating one.
 * @param onAccept receives the sanitized name, the description, and the rule; only called when the name is
 *   usable and free.
 */
class ProfileEditScreen(
    private val parent: Screen?,
    title: Component,
    private val existingName: String?,
    initialName: String,
    initialDescription: String,
    initialRule: AutoSwitchRule?,
    private val onAccept: (name: String, description: String, rule: AutoSwitchRule?) -> Unit,
) : Screen(title) {

    private var nameText: String = initialName
    private var descriptionText: String = initialDescription
    private var ruleKind: AutoSwitchKind? = initialRule?.kind
    private var rulePattern: String = initialRule?.pattern.orEmpty()

    /** Why the current name cannot be used, or null when it can. Recomputed on every keystroke. */
    private var nameError: Component? = null

    private lateinit var nameField: EditBox
    private lateinit var descriptionField: EditBox
    private lateinit var patternField: EditBox
    private lateinit var acceptButton: Button

    /**
     * Where each caption goes, worked out in [init] and drawn in [extractBackground].
     *
     * Positions are computed once and shared rather than derived again while drawing. The two passes used to
     * each walk their own way down the screen from a set of fixed offsets, and they disagreed: the name error
     * landed on top of the auto-switch button, and the Accept row landed on top of the pattern field.
     */
    private val captions = mutableListOf<Caption>()

    /** Baseline for the validation message, directly beneath the name field it is about. */
    private var errorY = 0

    private data class Caption(val text: Component, val x: Int, val y: Int)

    override fun init() {
        captions.clear()

        val fieldX = width / 2 - FIELD_WIDTH / 2
        val showPattern = ruleKind == AutoSwitchKind.SERVER || ruleKind == AutoSwitchKind.SKYBLOCK_ISLAND

        // Measure the block first so it can be centred as a whole. The pattern field appears and disappears
        // with the selected rule, so the height is not constant.
        val patternBlock = if (showPattern) PATTERN_GAP + FIELD_HEIGHT else 0
        val contentHeight = LABELLED_ROW + ERROR_ROW + ROW_GAP +
                LABELLED_ROW + ROW_GAP +
                LABELLED_ROW + patternBlock +
                ACTION_GAP + FIELD_HEIGHT

        // Never let the block start so high that it runs under the title.
        var y = maxOf((height - contentHeight) / 2, TITLE_TOP + TITLE_CLEARANCE)

        captions += Caption(LABEL_NAME, fieldX, y)
        y += LABEL_HEIGHT
        nameField = EditBox(font, fieldX, y, FIELD_WIDTH, FIELD_HEIGHT, LABEL_NAME).apply {
            setMaxLength(NAME_MAX_LENGTH)
            value = nameText
            setResponder { text ->
                // Safe to run per keystroke: this only updates local state and the error line. Nothing is
                // created or renamed until Accept.
                nameText = text
                validateName()
            }
        }
        addRenderableWidget(nameField)
        y += FIELD_HEIGHT

        // The message sits under the field it describes, and its space is reserved whether or not there is
        // anything to say, so the rest of the form does not jump around while you type.
        errorY = y + ERROR_PADDING
        y += ERROR_ROW + ROW_GAP

        captions += Caption(LABEL_DESCRIPTION, fieldX, y)
        y += LABEL_HEIGHT
        descriptionField = EditBox(font, fieldX, y, FIELD_WIDTH, FIELD_HEIGHT, LABEL_DESCRIPTION).apply {
            setHint(HINT_DESCRIPTION)
            setMaxLength(DESCRIPTION_MAX_LENGTH)
            value = descriptionText
            setResponder { descriptionText = it }
        }
        addRenderableWidget(descriptionField)
        y += FIELD_HEIGHT + ROW_GAP

        captions += Caption(LABEL_AUTO_SWITCH, fieldX, y)
        y += LABEL_HEIGHT
        addRenderableWidget(
            Button.builder(kindLabel()) { cycleKind() }
                .bounds(fieldX, y, FIELD_WIDTH, FIELD_HEIGHT)
                .build(),
        )
        y += FIELD_HEIGHT

        // Built either way so the field reference is always valid, but only placed and shown when the rule
        // actually takes a pattern — singleplayer matches on its own.
        y += PATTERN_GAP
        patternField = EditBox(font, fieldX, y, FIELD_WIDTH, FIELD_HEIGHT, LABEL_PATTERN).apply {
            setHint(patternHint())
            setMaxLength(PATTERN_MAX_LENGTH)
            value = rulePattern
            setResponder { rulePattern = it }
            isVisible = showPattern
        }
        addRenderableWidget(patternField)
        if (showPattern) y += FIELD_HEIGHT else y -= PATTERN_GAP

        val buttonY = y + ACTION_GAP
        acceptButton = Button.builder(ACCEPT) { accept() }
            .bounds(width / 2 - BUTTON_WIDTH - GAP / 2, buttonY, BUTTON_WIDTH, FIELD_HEIGHT)
            .build()
        addRenderableWidget(acceptButton)
        addRenderableWidget(
            Button.builder(CANCEL) { onClose() }
                .bounds(width / 2 + GAP / 2, buttonY, BUTTON_WIDTH, FIELD_HEIGHT)
                .build(),
        )

        setInitialFocus(nameField)
        validateName()
    }

    /**
     * Checks the typed name and disables Accept while it is unusable.
     *
     * Rejecting up front, with the reason on screen, beats sanitizing silently: a user who types `My Profile`
     * and gets `my_profile` without being told will not understand why the name in the list is not the one
     * they chose.
     */
    private fun validateName() {
        val sanitized = ConfigProfiles.sanitize(nameText)
        nameError = when {
            nameText.isBlank() -> null
            sanitized == null -> ERROR_UNUSABLE
            sanitized != existingName && ConfigProfiles.exists(sanitized) -> ERROR_TAKEN
            sanitized != nameText.trim() -> Component.translatable("hex.profiles.edit.will_rename", sanitized)
            else -> null
        }
        if (::acceptButton.isInitialized) {
            acceptButton.active = sanitized != null && (sanitized == existingName || !ConfigProfiles.exists(sanitized))
        }
    }

    private fun cycleKind() {
        ruleKind = when (ruleKind) {
            null -> AutoSwitchKind.SERVER
            AutoSwitchKind.SERVER -> AutoSwitchKind.SINGLEPLAYER
            AutoSwitchKind.SINGLEPLAYER -> AutoSwitchKind.SKYBLOCK_ISLAND
            AutoSwitchKind.SKYBLOCK_ISLAND -> null
        }
        rulePattern = patternField.value
        rebuildWidgets()
    }

    private fun kindLabel(): Component = when (ruleKind) {
        null -> Component.translatable("hex.profiles.auto.none")
        AutoSwitchKind.SERVER -> Component.translatable("hex.profiles.auto.server")
        AutoSwitchKind.SINGLEPLAYER -> Component.translatable("hex.profiles.auto.singleplayer")
        AutoSwitchKind.SKYBLOCK_ISLAND -> Component.translatable("hex.profiles.auto.island")
    }

    private fun patternHint(): Component = when (ruleKind) {
        AutoSwitchKind.SKYBLOCK_ISLAND -> HINT_ISLAND
        else -> HINT_SERVER
    }

    private fun accept() {
        val name = ConfigProfiles.sanitize(nameText) ?: return
        if (name != existingName && ConfigProfiles.exists(name)) return

        val kind = ruleKind
        val rule = when {
            kind == null -> null
            kind == AutoSwitchKind.SINGLEPLAYER -> AutoSwitchRule(kind, "")
            // A server or island rule with no pattern would match nothing, so it is stored as no rule at all
            // rather than as a rule that silently never fires.
            rulePattern.isBlank() -> null
            else -> AutoSwitchRule(kind, rulePattern.trim().lowercase())
        }

        minecraft.setScreen(parent)
        onAccept(name, descriptionText.trim(), rule)
    }

    override fun extractBackground(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(extractor, mouseX, mouseY, delta)
        extractor.centeredText(font, title, width / 2, TITLE_TOP, TITLE_COLOR)

        // Straight from the positions [init] worked out, so a caption cannot drift onto a widget.
        captions.forEach { extractor.text(font, it.text, it.x, it.y, MUTED_COLOR) }

        nameError?.let {
            extractor.text(font, it, width / 2 - FIELD_WIDTH / 2, errorY, errorColour())
        }
    }

    /**
     * Red for a name that cannot be used, muted for one that merely gets tidied up.
     *
     * "Will be saved as …" is information, not a failure — Accept still works — so colouring it like an error
     * would misrepresent what is happening.
     */
    private fun errorColour(): Int =
        if (acceptButton.active) MUTED_COLOR else ERROR_COLOR

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    private companion object {
        const val FIELD_WIDTH = 220
        const val FIELD_HEIGHT = 20
        const val BUTTON_WIDTH = 100
        const val GAP = 6

        /** Height of a caption line above its field. */
        const val LABEL_HEIGHT = 11

        /** Caption plus the field under it. */
        const val LABELLED_ROW = LABEL_HEIGHT + FIELD_HEIGHT

        /** Space reserved under the name field for the validation message, kept whether or not there is one. */
        const val ERROR_PADDING = 4
        const val ERROR_ROW = ERROR_PADDING + 10

        /** Breathing room between one labelled row and the next. */
        const val ROW_GAP = 10

        /** Between the auto-switch selector and the pattern field it belongs to — tighter, they are one unit. */
        const val PATTERN_GAP = 4

        /** Between the form and the Accept/Cancel row. */
        const val ACTION_GAP = 16

        /** Baseline of the title, and the lowest the form may start. */
        const val TITLE_TOP = 20
        const val TITLE_CLEARANCE = 18

        const val NAME_MAX_LENGTH = 32
        const val DESCRIPTION_MAX_LENGTH = 96
        const val PATTERN_MAX_LENGTH = 64

        const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        const val MUTED_COLOR = 0xFF9A9A9A.toInt()
        const val ERROR_COLOR = 0xFFFF6B6B.toInt()

        val LABEL_NAME: Component = Component.translatable("hex.profiles.edit.name")
        val LABEL_DESCRIPTION: Component = Component.translatable("hex.profiles.edit.description")
        val LABEL_AUTO_SWITCH: Component = Component.translatable("hex.profiles.edit.auto_switch")
        val LABEL_PATTERN: Component = Component.translatable("hex.profiles.edit.pattern")
        val HINT_DESCRIPTION: Component = Component.translatable("hex.profiles.edit.description.hint")
        val HINT_SERVER: Component = Component.translatable("hex.profiles.edit.pattern.server_hint")
        val HINT_ISLAND: Component = Component.translatable("hex.profiles.edit.pattern.island_hint")
        val ERROR_UNUSABLE: Component = Component.translatable("hex.profiles.edit.error.unusable")
        val ERROR_TAKEN: Component = Component.translatable("hex.profiles.edit.error.taken")
        val ACCEPT: Component = Component.translatable("hex.profiles.edit.accept")
        val CANCEL: Component = Component.translatable("gui.cancel")
    }
}
