package net.trilleo.config.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.config.ConfigProfiles
import net.trilleo.config.ConfigRegistry
import net.trilleo.config.HexConfigScreens
import net.trilleo.config.ProfileAutoSwitch
import net.trilleo.config.ProfileDirtyTracker

/**
 * The profile manager: every saved setup in one list, with switching, renaming, duplicating and deleting.
 *
 * This replaces the old Profiles tab, which expressed each action as a settings row. That model could not
 * show which profiles existed alongside what they contained, had nowhere to put a confirmation, and made
 * naming a profile a per-keystroke side effect. A screen of its own solves all three.
 *
 * Settings still apply live — nothing here is a form you submit — but a *profile* is only written when you
 * say so. The `*` next to the active profile means the live settings have moved away from what it holds; Save
 * folds them in and Discard throws them away.
 */
class ProfilesScreen(private val parent: Screen?) : Screen(Component.translatable("hex.profiles.title")) {

    private lateinit var list: ProfileList
    private lateinit var saveButton: Button
    private lateinit var discardButton: Button

    override fun init() {
        // The screen may have been open while something else changed the settings, so never trust the
        // cached verdict on the way in.
        ProfileDirtyTracker.refresh()

        list = ProfileList(minecraft, width, height - HEADER_HEIGHT - FOOTER_HEIGHT, HEADER_HEIGHT, this)
        addWidget(list)
        list.refresh()

        layoutFooter()
    }

    private fun layoutFooter() {
        val y = height - FOOTER_HEIGHT + (FOOTER_HEIGHT - BUTTON_HEIGHT) / 2 - ROW_TWO_LIFT
        var x = MARGIN

        saveButton = addRenderableWidget(
            Button.builder(SAVE) { saveActive() }
                .bounds(x, y, WIDE_BUTTON, BUTTON_HEIGHT)
                .tooltip(TIP_SAVE)
                .build(),
        )
        x += WIDE_BUTTON + GAP

        discardButton = addRenderableWidget(
            Button.builder(DISCARD) { discardActive() }
                .bounds(x, y, WIDE_BUTTON, BUTTON_HEIGHT)
                .tooltip(TIP_DISCARD)
                .build(),
        )
        x += WIDE_BUTTON + GAP

        addRenderableWidget(
            Button.builder(NEW) { requestNew() }
                .bounds(x, y, NARROW_BUTTON, BUTTON_HEIGHT)
                .tooltip(TIP_NEW)
                .build(),
        )

        // Second row: transfer and the destructive reset, kept away from the per-profile actions above.
        val y2 = y + BUTTON_HEIGHT + GAP
        var x2 = MARGIN

        addRenderableWidget(
            Button.builder(EXPORT) { export() }
                .bounds(x2, y2, WIDE_BUTTON, BUTTON_HEIGHT)
                .tooltip(TIP_EXPORT)
                .build(),
        )
        x2 += WIDE_BUTTON + GAP

        addRenderableWidget(
            Button.builder(IMPORT) { requestImport() }
                .bounds(x2, y2, WIDE_BUTTON, BUTTON_HEIGHT)
                .tooltip(TIP_IMPORT)
                .build(),
        )
        x2 += WIDE_BUTTON + GAP

        addRenderableWidget(
            Button.builder(if (ConfigProfiles.settings.captureVanillaKeys) KEYS_ON else KEYS_OFF) {
                ConfigProfiles.settings.captureVanillaKeys = !ConfigProfiles.settings.captureVanillaKeys
                ConfigProfiles.saveSettings()
                refresh()
            }.bounds(x2, y2, WIDE_BUTTON, BUTTON_HEIGHT).tooltip(TIP_KEYS).build(),
        )

        addRenderableWidget(
            Button.builder(RESET_ALL) { requestResetAll() }
                .bounds(width - MARGIN - WIDE_BUTTON, y2, WIDE_BUTTON, BUTTON_HEIGHT)
                .tooltip(TIP_RESET_ALL)
                .build(),
        )

        addRenderableWidget(
            Button.builder(Component.translatable("gui.done")) { onClose() }
                .bounds(width - MARGIN - NARROW_BUTTON, y, NARROW_BUTTON, BUTTON_HEIGHT)
                .build(),
        )

        refreshFooterState()
    }

    private fun refreshFooterState() {
        // Both only mean something when the live settings have actually moved away from the saved profile.
        val dirty = ProfileDirtyTracker.isDirty
        if (::saveButton.isInitialized) saveButton.active = dirty
        if (::discardButton.isInitialized) discardButton.active = dirty
    }

    /** Rebuilds the list and the footer after anything that changes what profiles exist or hold. */
    fun refresh() {
        ProfileDirtyTracker.refresh()
        rebuildWidgets()
    }

    // ---- actions -------------------------------------------------------------------------------------

    private fun saveActive() {
        val name = ConfigProfiles.settings.active
        ConfigProfiles.saveTo(name)
        ConfigProfiles.notify("Saved your settings into profile '$name'")
        refresh()
    }

    private fun discardActive() {
        val name = ConfigProfiles.settings.active
        minecraft.setScreen(
            ConfirmActionScreen(
                parent = this,
                title = Component.translatable("hex.profiles.discard.title"),
                message = Component.translatable("hex.profiles.discard.message", name),
                detail = Component.translatable("hex.profiles.discard.detail"),
                choices = listOf(
                    ConfirmActionScreen.Choice(DISCARD) {
                        if (ConfigProfiles.discardChanges()) {
                            ConfigProfiles.notify("Restored profile '$name'")
                            HexConfigScreens.rebuild()
                        } else {
                            ConfigProfiles.notify("Profile '$name' has nothing saved to restore", error = true)
                        }
                        refresh()
                    },
                    ConfirmActionScreen.Choice(CANCEL, null),
                ),
            ),
        )
    }

    /**
     * Switches to [target], prompting first when the current profile holds unsaved changes.
     *
     * The prompt exists because switching is destructive under the explicit-save model — restoring another
     * profile overwrites the live settings, and there is no undo once it has happened.
     */
    fun requestSwitch(target: String) {
        if (!ProfileDirtyTracker.isDirty) {
            performSwitch(target)
            return
        }
        val leaving = ConfigProfiles.settings.active
        minecraft.setScreen(
            ConfirmActionScreen(
                parent = this,
                title = Component.translatable("hex.profiles.switch_prompt.title"),
                message = Component.translatable("hex.profiles.switch_prompt.message", leaving),
                detail = Component.translatable("hex.profiles.switch_prompt.detail", target),
                choices = listOf(
                    ConfirmActionScreen.Choice(SAVE_AND_SWITCH) {
                        ConfigProfiles.saveTo(leaving)
                        performSwitch(target)
                    },
                    ConfirmActionScreen.Choice(DISCARD_AND_SWITCH) { performSwitch(target) },
                    ConfirmActionScreen.Choice(CANCEL, null),
                ),
            ),
        )
    }

    private fun performSwitch(target: String) {
        if (ConfigProfiles.switchTo(target)) {
            // A hand-picked profile outranks any rule for the rest of the session; auto-switching over it
            // would look like the mod overriding a deliberate choice.
            ProfileAutoSwitch.noteManualSwitch()
            ConfigProfiles.notify("Switched to profile '$target'")
            HexConfigScreens.rebuild()
        } else {
            ConfigProfiles.notify("Could not switch to profile '$target'", error = true)
        }
        refresh()
    }

    private fun requestNew() {
        minecraft.setScreen(
            ProfileEditScreen(
                parent = this,
                title = Component.translatable("hex.profiles.new.title"),
                existingName = null,
                initialName = "",
                initialDescription = "",
                initialRule = null,
            ) { name, description, rule ->
                if (ConfigProfiles.create(name, description)) {
                    ConfigProfiles.entryFor(name)?.autoSwitch = rule
                    ConfigProfiles.saveSettings()
                    ProfileAutoSwitch.noteManualSwitch()
                    ConfigProfiles.notify("Created profile '$name' from your current settings")
                } else {
                    ConfigProfiles.notify("Profile '$name' already exists", error = true)
                }
                refresh()
            },
        )
    }

    fun requestEdit(name: String) {
        val entry = ConfigProfiles.entryFor(name) ?: return
        minecraft.setScreen(
            ProfileEditScreen(
                parent = this,
                title = Component.translatable("hex.profiles.edit.title"),
                existingName = name,
                initialName = name,
                initialDescription = entry.description,
                initialRule = entry.autoSwitch,
            ) { newName, description, rule ->
                if (newName != name && !ConfigProfiles.rename(name, newName)) {
                    ConfigProfiles.notify("Could not rename '$name' to '$newName'", error = true)
                } else {
                    ConfigProfiles.entryFor(newName)?.apply {
                        this.description = description
                        this.autoSwitch = rule
                    }
                    ConfigProfiles.saveSettings()
                }
                refresh()
            },
        )
    }

    fun requestDuplicate(name: String) {
        minecraft.setScreen(
            ProfileEditScreen(
                parent = this,
                title = Component.translatable("hex.profiles.duplicate.title"),
                existingName = null,
                initialName = uniqueCopyName(name),
                initialDescription = ConfigProfiles.entryFor(name)?.description.orEmpty(),
                initialRule = null,
            ) { target, description, rule ->
                if (ConfigProfiles.duplicate(name, target)) {
                    ConfigProfiles.entryFor(target)?.apply {
                        this.description = description
                        this.autoSwitch = rule
                    }
                    ConfigProfiles.saveSettings()
                    ConfigProfiles.notify("Copied '$name' to '$target'")
                } else {
                    ConfigProfiles.notify("Could not copy '$name' to '$target'", error = true)
                }
                refresh()
            },
        )
    }

    /** `x_copy`, then `x_copy_2`, … so the suggested name is free without the user having to discover that. */
    private fun uniqueCopyName(source: String): String {
        val base = "${source}_copy"
        if (!ConfigProfiles.exists(base)) return base
        var n = 2
        while (ConfigProfiles.exists("${base}_$n")) n++
        return "${base}_$n"
    }

    fun requestDelete(name: String) {
        minecraft.setScreen(
            ConfirmActionScreen(
                parent = this,
                title = Component.translatable("hex.profiles.delete.title"),
                message = Component.translatable("hex.profiles.delete.message", name),
                detail = Component.translatable("hex.profiles.delete.detail"),
                choices = listOf(
                    ConfirmActionScreen.Choice(DELETE) {
                        val wasActive = name == ConfigProfiles.settings.active
                        if (ConfigProfiles.delete(name)) {
                            ConfigProfiles.notify("Deleted profile '$name'")
                            if (wasActive) HexConfigScreens.rebuild()
                        } else {
                            ConfigProfiles.notify("Cannot delete the only remaining profile", error = true)
                        }
                        refresh()
                    },
                    ConfirmActionScreen.Choice(CANCEL, null),
                ),
            ),
        )
    }

    private fun export() {
        Minecraft.getInstance().keyboardHandler.setClipboard(ConfigProfiles.exportToString())
        ConfigProfiles.notify("Copied every Hex setting to the clipboard")
    }

    /**
     * Offers the two things a pasted export can mean: adopt it here, or keep it as a profile of its own.
     *
     * Importing over the current profile used to be the only option, which made trying out someone else's
     * setup a destructive act.
     */
    private fun requestImport() {
        val text = Minecraft.getInstance().keyboardHandler.clipboard
        val suggested = ConfigProfiles.importedProfileName(text)?.let { ConfigProfiles.sanitize(it) }

        minecraft.setScreen(
            ConfirmActionScreen(
                parent = this,
                title = Component.translatable("hex.profiles.import.title"),
                message = Component.translatable("hex.profiles.import.message"),
                detail = Component.translatable("hex.profiles.import.detail"),
                choices = listOf(
                    ConfirmActionScreen.Choice(IMPORT_AS_NEW) { importAsNew(text, suggested) },
                    ConfirmActionScreen.Choice(IMPORT_HERE) { applyImport(text) },
                    ConfirmActionScreen.Choice(CANCEL, null),
                ),
            ),
        )
    }

    private fun importAsNew(text: String, suggested: String?) {
        minecraft.setScreen(
            ProfileEditScreen(
                parent = this,
                title = Component.translatable("hex.profiles.import.new_title"),
                existingName = null,
                initialName = suggested ?: "imported",
                initialDescription = "",
                initialRule = null,
            ) { name, description, rule ->
                // Snapshot the current profile first: importing replaces the live settings, and they are
                // about to become the new profile's contents rather than this one's.
                val leaving = ConfigProfiles.settings.active
                ConfigProfiles.saveTo(leaving)
                if (applyImport(text)) {
                    if (ConfigProfiles.create(name, description)) {
                        ConfigProfiles.entryFor(name)?.autoSwitch = rule
                        ConfigProfiles.saveSettings()
                        ProfileAutoSwitch.noteManualSwitch()
                    } else {
                        ConfigProfiles.notify("Profile '$name' already exists", error = true)
                    }
                } else {
                    // Nothing was applied, so put the settings back the way they were.
                    ConfigProfiles.discardChanges()
                }
                refresh()
            },
        )
    }

    /** Applies a clipboard blob, reporting what happened. Returns whether anything was adopted. */
    private fun applyImport(text: String): Boolean {
        val applied = when (val result = ConfigProfiles.importFromString(text)) {
            is ConfigProfiles.ImportResult.NotHex -> {
                ConfigProfiles.notify("The clipboard does not contain Hex settings", error = true)
                false
            }

            is ConfigProfiles.ImportResult.TooNew -> {
                ConfigProfiles.notify(
                    "Those settings were exported by a newer Hex (format v${result.version}) — update Hex to import them",
                    error = true,
                )
                false
            }

            is ConfigProfiles.ImportResult.Applied -> {
                ConfigProfiles.notify("Imported ${result.count} settings section(s)")
                result.fromVersion?.let {
                    ConfigProfiles.notify(
                        "They came from Hex v$it and you are on v${ConfigProfiles.modVersion()}; some settings may not apply",
                    )
                }
                result.count > 0
            }
        }
        if (applied) HexConfigScreens.rebuild()
        refresh()
        return applied
    }

    private fun requestResetAll() {
        minecraft.setScreen(
            ConfirmActionScreen(
                parent = this,
                title = Component.translatable("hex.profiles.reset_all.title"),
                message = Component.translatable("hex.profiles.reset_all.message"),
                detail = Component.translatable("hex.profiles.reset_all.detail"),
                choices = listOf(
                    ConfirmActionScreen.Choice(RESET_ALL) {
                        ConfigRegistry.resetAll()
                        ConfigRegistry.flushAll()
                        ConfigProfiles.notify("Reset every Hex setting to its default")
                        HexConfigScreens.rebuild()
                        refresh()
                    },
                    ConfirmActionScreen.Choice(CANCEL, null),
                ),
            ),
        )
    }

    // ---- rendering -----------------------------------------------------------------------------------

    /**
     * The chrome: header and footer panels, dividers and the title.
     *
     * Drawn in the background pass rather than [extractRenderState] for the same reason as
     * [HexConfigScreen] — that pass only walks the registered renderables, so panels drawn there would paint
     * over the footer buttons.
     */
    override fun extractBackground(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(extractor, mouseX, mouseY, delta)

        extractor.fill(0, 0, width, HEADER_HEIGHT, PANEL_COLOR)
        extractor.fill(0, height - FOOTER_HEIGHT, width, height, PANEL_COLOR)
        extractor.horizontalLine(0, width, HEADER_HEIGHT - 1, DIVIDER_COLOR)
        extractor.horizontalLine(0, width, height - FOOTER_HEIGHT, DIVIDER_COLOR)

        extractor.text(font, title, MARGIN, MARGIN + 8, TITLE_COLOR)

        val status = if (ProfileDirtyTracker.isDirty) {
            Component.translatable("hex.profiles.status.unsaved", ConfigProfiles.settings.active)
        } else {
            Component.translatable("hex.profiles.status.saved", ConfigProfiles.settings.active)
        }
        val colour = if (ProfileDirtyTracker.isDirty) UNSAVED_COLOR else MUTED_COLOR
        extractor.text(font, status, width - MARGIN - font.width(status), MARGIN + 8, colour)
    }

    override fun extractRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        // Registered with addWidget rather than addRenderableWidget — it scissors and scrolls itself — so it
        // is drawn explicitly here, beneath the footer widgets.
        list.extractWidgetRenderState(extractor, mouseX, mouseY, delta)
        super.extractRenderState(extractor, mouseX, mouseY, delta)
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun removed() {
        // Profile bookkeeping is written as it changes; this only forces out any batched settings write.
        ConfigRegistry.flushAll()
    }

    private companion object {
        const val HEADER_HEIGHT = 32
        const val FOOTER_HEIGHT = 58
        const val MARGIN = 6
        const val GAP = 4
        const val BUTTON_HEIGHT = 20
        const val WIDE_BUTTON = 108
        const val NARROW_BUTTON = 60
        const val ROW_TWO_LIFT = 12

        const val PANEL_COLOR = 0xC0101010.toInt()
        const val DIVIDER_COLOR = 0x60FFFFFF
        const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        const val MUTED_COLOR = 0xFF9A9A9A.toInt()
        const val UNSAVED_COLOR = 0xFFE8C547.toInt()

        val SAVE: Component = Component.translatable("hex.profiles.save")
        val DISCARD: Component = Component.translatable("hex.profiles.discard")
        val NEW: Component = Component.translatable("hex.profiles.new")
        val EXPORT: Component = Component.translatable("hex.profiles.export")
        val IMPORT: Component = Component.translatable("hex.profiles.import")
        val RESET_ALL: Component = Component.translatable("hex.profiles.reset_all")
        val DELETE: Component = Component.translatable("hex.profiles.delete")
        val CANCEL: Component = Component.translatable("gui.cancel")
        val KEYS_ON: Component = Component.translatable("hex.profiles.capture_keys.on")
        val KEYS_OFF: Component = Component.translatable("hex.profiles.capture_keys.off")
        val SAVE_AND_SWITCH: Component = Component.translatable("hex.profiles.switch_prompt.save")
        val DISCARD_AND_SWITCH: Component = Component.translatable("hex.profiles.switch_prompt.discard")
        val IMPORT_AS_NEW: Component = Component.translatable("hex.profiles.import.as_new")
        val IMPORT_HERE: Component = Component.translatable("hex.profiles.import.here")

        val TIP_SAVE: Tooltip = Tooltip.create(Component.translatable("hex.profiles.save.tooltip"))
        val TIP_DISCARD: Tooltip = Tooltip.create(Component.translatable("hex.profiles.discard.tooltip"))
        val TIP_NEW: Tooltip = Tooltip.create(Component.translatable("hex.profiles.new.tooltip"))
        val TIP_EXPORT: Tooltip = Tooltip.create(Component.translatable("hex.profiles.export.tooltip"))
        val TIP_IMPORT: Tooltip = Tooltip.create(Component.translatable("hex.profiles.import.tooltip"))
        val TIP_RESET_ALL: Tooltip = Tooltip.create(Component.translatable("hex.profiles.reset_all.tooltip"))
        val TIP_KEYS: Tooltip = Tooltip.create(Component.translatable("hex.profiles.capture_keys.tooltip"))
    }
}
