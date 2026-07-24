package net.trilleo.region.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import net.trilleo.config.ConfigCategory
import net.trilleo.config.gui.ConfigEntryList
import net.trilleo.reminder.ReminderConfig
import net.trilleo.reminder.gui.ReminderEditScreen
import net.trilleo.reminder.gui.RemindersScreen
import net.trilleo.reminder.model.ActionKind
import net.trilleo.reminder.model.ConditionKind
import net.trilleo.reminder.model.Reminder
import net.trilleo.reminder.model.ReminderAction
import net.trilleo.reminder.model.Trigger
import net.trilleo.reminder.model.TriggerKind
import net.trilleo.region.RegionAlerts
import net.trilleo.region.RegionConfig
import net.trilleo.region.RegionRenderer
import net.trilleo.region.model.Region
import net.trilleo.region.model.RegionShape
import net.trilleo.util.Notify
import java.util.*

/**
 * Edits one region.
 *
 * Reuses [ConfigEntryList] by building a throwaway [ConfigCategory] whose entries close over this region, in
 * exactly the way [ReminderEditScreen] does — inheriting scrolling, keyboard navigation, per-row reset,
 * inline validation and tooltips for nothing, and keeping the editor looking like the rest of the mod.
 *
 * **The region is previewed in the world for as long as this screen is open.** A menu is drawn over the world,
 * not instead of it, so a box being typed into shape is visible behind the rows — which is what makes editing
 * coordinates by hand tolerable at all.
 *
 * Bounds are presented as a centre and a size rather than as two corners. Both describe the same box, but a
 * player nudging a region thinks "move it two blocks north" and "make it a bit taller", not "raise minX";
 * corners are what the *capture* produces, and this screen is the other half of the job.
 */
class RegionEditScreen(
    private val parent: RegionsScreen?,
    private val region: Region,
) : Screen(Component.translatable("hex.regions.edit.title")) {

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
        var x = width / 2 - (BUTTON_WIDTH * 4 + GAP * 3) / 2

        addRenderableWidget(
            Button.builder(Component.translatable("hex.regions.edit.recapture")) { recapture() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.regions.edit.test")) { test() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.regions.edit.add_reminder")) { addReminder() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )
        x += BUTTON_WIDTH + GAP

        addRenderableWidget(
            Button.builder(Component.translatable("hex.regions.edit.done")) { onClose() }
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
        )

        // Shown in the world behind this screen until it closes.
        RegionRenderer.focused = region

        rebuild(preserveScroll = false)
    }

    /** Rebuilds the rows, since which fields apply depends on the shape and the chosen actions. */
    private fun rebuild(preserveScroll: Boolean = true) {
        val category = buildCategory()
        list?.show(listOf(category.title to category.entries), preserveScroll)
        summary?.message = summaryText()
    }

    private fun touch() {
        RegionConfig.markDirty()
        summary?.message = summaryText()
    }

    private fun summaryText(): Component = Component.literal(region.summary())

    private fun buildCategory(): ConfigCategory = ConfigCategory.build("region_edit") {
        toggle(
            "enabled",
            default = true,
            get = { region.enabled },
            set = { region.enabled = it; RegionConfig.save() },
        )
        text(
            "name",
            default = "",
            get = { region.name },
            set = { rename(it) },
            validate = { typed ->
                if (typed.isBlank()) Component.translatable("hex.regions.edit.name.blank") else null
            },
        )
        text(
            "island",
            default = "",
            get = { region.island },
            set = { region.island = it.trim().lowercase(Locale.ROOT); touch() },
        )
        enum(
            "shape",
            default = RegionShape.BOX,
            get = { region.shape },
            set = {
                region.shape = it
                touch()
                // The radius shown in the summary depends on the shape, and so does which rows make sense.
                rebuild()
            },
        )

        text(
            "text",
            default = "",
            get = { region.text },
            set = { region.text = it; touch() },
        )
        toggle(
            "notify_on_leave",
            default = false,
            get = { region.notifyOnLeave },
            set = { region.notifyOnLeave = it; touch(); rebuild() },
        )
        if (region.notifyOnLeave) {
            text(
                "leave_text",
                default = "",
                get = { region.leaveText },
                set = { region.leaveText = it; touch() },
            )
        }

        // Actions are switches rather than a list, for the same reason the reminder editor's are: there are
        // only two kinds a region can carry, and a list editor for a two-item set is more machinery than the
        // thing it edits.
        toggle(
            "action_title",
            default = true,
            get = { region.actions.any { it.kind == ActionKind.TITLE } },
            set = { setAction(ActionKind.TITLE, it); rebuild() },
        )
        actionOf(ActionKind.TITLE)?.let { title ->
            text(
                "title_subtitle",
                default = "",
                get = { title.subtitle },
                set = { title.subtitle = it; touch() },
            )
            color(
                "title_color",
                default = "#FFFFFF",
                get = { title.titleColor.ifBlank { "#FFFFFF" } },
                set = { title.titleColor = it; touch() },
            )
            slider(
                "title_seconds",
                min = ReminderAction.TITLE_SECONDS_MIN,
                max = ReminderAction.TITLE_SECONDS_MAX,
                step = 0.5,
                default = ReminderAction.DEFAULT_TITLE_SECONDS,
                get = { title.titleSeconds },
                set = { title.titleSeconds = it; touch() },
                format = { String.format(Locale.ROOT, "%.1fs", it) },
            )
        }

        toggle(
            "action_sound",
            default = false,
            get = { region.actions.any { it.kind == ActionKind.SOUND } },
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
                        Component.translatable("hex.regions.edit.sound.unknown")
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

        slider(
            "cooldown",
            min = Region.COOLDOWN_MIN,
            max = 300.0,
            step = 5.0,
            default = Region.DEFAULT_COOLDOWN_SECONDS,
            get = { region.cooldownSeconds },
            set = { region.cooldownSeconds = it; touch() },
            format = { String.format(Locale.ROOT, "%.0fs", it) },
        )
        color(
            "color",
            default = RegionConfig.settings.previewColor,
            alpha = true,
            get = { region.color.ifBlank { RegionConfig.settings.previewColor } },
            set = { region.color = it; touch() },
        )

        // ---- bounds ------------------------------------------------------------------------------------

        number("center_x", { region.center().x }, { moveTo(it, region.center().y, region.center().z) })
        number("center_y", { region.center().y }, { moveTo(region.center().x, it, region.center().z) })
        number("center_z", { region.center().z }, { moveTo(region.center().x, region.center().y, it) })
        number("size_x", { region.sizeX() }, { resizeTo(it, region.sizeY(), region.sizeZ()) })
        number("size_y", { region.sizeY() }, { resizeTo(region.sizeX(), it, region.sizeZ()) })
        number("size_z", { region.sizeZ() }, { resizeTo(region.sizeX(), region.sizeY(), it) })
    }

    /**
     * A coordinate field: free text that has to parse as a number.
     *
     * Not a slider, because the useful range spans a whole island and a slider precise to one block over two
     * thousand of them would be unusable. Not an int field either — a region captured from a flying camera has
     * fractional bounds, and rounding them on every edit would creep.
     */
    private fun ConfigCategory.Builder.number(key: String, get: () -> Double, set: (Double) -> Unit) {
        text(
            key,
            default = "0",
            get = { String.format(Locale.ROOT, "%.1f", get()) },
            set = { typed -> typed.trim().toDoubleOrNull()?.takeIf { it.isFinite() }?.let(set) },
            validate = { typed ->
                val parsed = typed.trim().toDoubleOrNull()
                if (parsed == null || !parsed.isFinite()) {
                    Component.translatable("hex.regions.edit.number.invalid")
                } else {
                    null
                }
            },
        )
    }

    /** Keeps the size and moves the centre. */
    private fun moveTo(x: Double, y: Double, z: Double) {
        val sx = region.sizeX() / 2
        val sy = region.sizeY() / 2
        val sz = region.sizeZ() / 2
        region.setCorners(Vec3(x - sx, y - sy, z - sz), Vec3(x + sx, y + sy, z + sz))
        touch()
    }

    /**
     * Keeps the centre and changes the size, so growing a region does not also slide it.
     *
     * Sizes are clamped to the same bounds the normalizer enforces on load. Without that, typing a size of
     * `0` — which happens on the way to typing `05`, since the field applies every keystroke — would leave a
     * region nothing can be inside until the next restart quietly repaired it.
     */
    private fun resizeTo(x: Double, y: Double, z: Double) {
        val center = region.center()
        val sx = x.coerceIn(Region.MIN_EXTENT, Region.MAX_EXTENT) / 2
        val sy = y.coerceIn(Region.MIN_EXTENT, Region.MAX_EXTENT) / 2
        val sz = z.coerceIn(Region.MIN_EXTENT, Region.MAX_EXTENT) / 2
        region.setCorners(
            Vec3(center.x - sx, center.y - sy, center.z - sz),
            Vec3(center.x + sx, center.y + sy, center.z + sz),
        )
        touch()
    }

    /**
     * Renames the region and repoints anything that referred to it by the old name.
     *
     * A reminder's `REGION_ENTER` trigger names a region rather than holding its id, which keeps
     * `reminders.json` readable — the cost of that choice is exactly this, and paying it here is what stops a
     * rename silently breaking a reminder the player set up weeks ago.
     */
    private fun rename(wanted: String) {
        val old = region.name
        val new = RegionConfig.uniqueName(wanted, except = region)
        if (new == old) return

        region.name = new
        var repointed = 0
        ReminderConfig.settings.reminders.forEach { reminder ->
            val trigger = reminder.trigger
            val isRegionTrigger =
                trigger.kind == TriggerKind.REGION_ENTER || trigger.kind == TriggerKind.REGION_LEAVE
            if (isRegionTrigger && trigger.value == old) {
                trigger.value = new
                repointed++
            }
            reminder.conditions.forEach { condition ->
                // Only the region-shaped conditions: an ON_ISLAND condition naming an island that happens to
                // share this region's name is about somewhere else entirely, and repointing it would break a
                // reminder that had nothing to do with the rename.
                val isRegionCondition =
                    condition.kind == ConditionKind.IN_REGION || condition.kind == ConditionKind.NOT_IN_REGION
                if (isRegionCondition && condition.value == old) {
                    condition.value = new
                    repointed++
                }
            }
        }
        // Marked rather than written: the name field's setter fires on every keystroke, so saving here would
        // be one file write per character typed.
        if (repointed > 0) ReminderConfig.markDirty()
        touch()
    }

    private fun setAction(kind: ActionKind, on: Boolean) {
        if (on) {
            if (region.actions.none { it.kind == kind }) {
                region.actions.add(ReminderAction().apply { this.kind = kind })
            }
        } else {
            region.actions.removeAll { it.kind == kind }
            // Never leave a region that fires and does nothing — it reads exactly like a broken one.
            if (region.actions.isEmpty()) {
                region.actions.add(ReminderAction().apply { this.kind = ActionKind.TITLE })
            }
        }
        touch()
    }

    private fun actionOf(kind: ActionKind): ReminderAction? = region.actions.firstOrNull { it.kind == kind }

    /** Re-centres the region on the player without changing its size or shape. */
    private fun recapture() {
        val player = minecraft.player ?: return
        val pos = player.position()
        moveTo(pos.x, pos.y, pos.z)
        rebuild()
    }

    /** Fires the region's actions now, so a sound and a title can be judged before committing to them. */
    private fun test() {
        RegionAlerts.test(Minecraft.getInstance(), region)
    }

    /**
     * Creates a reminder armed by this region and opens it.
     *
     * The bridge between the two halves of the feature. A region's own alert is immediate; a reminder gives it
     * a countdown, conditions, the panel and a snooze key — and setting one up by hand would mean leaving this
     * screen, finding the reminder editor, choosing the right trigger and retyping the region's name.
     */
    private fun addReminder() {
        val reminder = Reminder().apply {
            id = UUID.randomUUID().toString()
            name = region.name
            text = region.text.ifBlank { region.name }
            trigger = Trigger().apply {
                kind = TriggerKind.REGION_ENTER
                value = region.name
                seconds = 0.0
            }
            actions = mutableListOf(ReminderAction())
        }
        ReminderConfig.settings.reminders.add(reminder)
        ReminderConfig.normalizeNow()
        ReminderConfig.save()

        // Opened through the reminder list, so Done walks back here rather than dumping the player into the
        // world from a screen they reached three levels deep.
        minecraft.setScreen(ReminderEditScreen(RemindersScreen(this), reminder))
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun removed() {
        RegionRenderer.focused = null
        RegionConfig.save()
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
