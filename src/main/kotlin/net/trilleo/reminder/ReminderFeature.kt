package net.trilleo.reminder

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.DeltaTracker
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.trilleo.Hex
import net.trilleo.command.Commands
import net.trilleo.config.ConfigCategory
import net.trilleo.feature.Feature
import net.trilleo.reminder.gui.RemindersScreen
import net.trilleo.reminder.hud.*
import net.trilleo.reminder.model.Reminder
import net.trilleo.reminder.model.ReminderAction
import net.trilleo.reminder.model.Trigger
import net.trilleo.reminder.preset.PresetSync
import net.trilleo.reminder.preset.ReminderPresets
import net.trilleo.reminder.preset.gui.PresetsScreen
import net.trilleo.util.Duration
import net.trilleo.util.TextClean
import java.util.*

/**
 * Timers, chat triggers and location alerts, shown on a movable HUD panel.
 *
 * Like [net.trilleo.hand.HandFeature], this deliberately leaves [enabled] at `true` and gates behaviour on
 * [ReminderConfig.active] instead: [net.trilleo.feature.Features.categories] hides a disabled feature's tab,
 * so wiring the master switch to [enabled] would make it impossible to switch back on from the menu.
 */
object ReminderFeature : Feature {
    override val id: String = "reminders"

    /** Silences whatever is flashing. The most important of the three — a flash and a repeating sound need
     * stopping without opening a menu. */
    private lateinit var dismissKey: KeyMapping

    private lateinit var snoozeKey: KeyMapping

    private lateinit var openKey: KeyMapping

    override fun onInit() {
        ReminderConfig.load()
        ReminderPresets.load()
        // After both, so it can compare what is installed against what ships.
        PresetSync.run()
        ReminderEngine.onLoad()

        dismissKey = registerKey("key.hex.reminder.dismiss")
        snoozeKey = registerKey("key.hex.reminder.snooze")
        openKey = registerKey("key.hex.reminder.open")
    }

    private fun registerKey(translationKey: String): KeyMapping =
        KeyMapping(translationKey, InputConstants.UNKNOWN.value, Hex.KEY_CATEGORY)
            .also { KeyMappingHelper.registerKeyMapping(it) }

    override fun onClientTick(client: Minecraft) {
        // The keys are handled outside the master switch on purpose: a player who has just switched reminders
        // off should still be able to silence the one that is already flashing.
        while (dismissKey.consumeClick()) {
            ReminderEngine.topmostFiring()?.let { ReminderEngine.dismiss(it) }
        }
        while (snoozeKey.consumeClick()) {
            ReminderEngine.topmostFiring()?.let {
                ReminderEngine.snooze(it, ReminderConfig.settings.defaultSnoozeMinutes)
            }
        }
        while (openKey.consumeClick()) {
            if (client.screen == null) client.setScreen(RemindersScreen(null))
        }

        if (!ReminderConfig.active) return
        ReminderEngine.tick(client)
    }

    override fun onHudRender(extractor: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!ReminderConfig.active) return
        ReminderHudRenderer.draw(extractor)
    }

    override fun onWorldJoin(client: Minecraft) {
        if (!ReminderConfig.active) return
        ReminderEngine.onWorldJoin()
    }

    override fun onWorldLeave(client: Minecraft) {
        ReminderEngine.onWorldLeave()
    }

    /**
     * Always returns `true`. Reminders observe chat; they never swallow it — and this hook is shared with
     * every other feature, so refusing to interfere is the only safe contract. See [ChatMatcher] for the
     * defences that keep a user-written pattern from throwing or hanging in here.
     */
    override fun onChatReceive(message: Component): Boolean {
        if (ReminderConfig.active) {
            ReminderEngine.onChat(Minecraft.getInstance(), TextClean.strip(message.string))
        }
        return true
    }

    override fun onShutdown() {
        ReminderState.flush()
    }

    override fun registerCommands(hex: LiteralArgumentBuilder<FabricClientCommandSource>) {
        hex.then(
            Commands.literal("remind")
                // A bare `/hexa remind` states its subcommands rather than guessing at one of them.
                .executes { ctx ->
                    Commands.feedback(ctx.source, "/hexa remind in <duration> <text> — a one-off reminder")
                    Commands.feedback(ctx.source, "/hexa remind list — what is counting down")
                    Commands.feedback(ctx.source, "/hexa remind dismiss — silence the one that is firing")
                    1
                }
                .then(
                    Commands.literal("in").then(
                        Commands.argument("duration", StringArgumentType.word()).then(
                            Commands.argument("text", StringArgumentType.greedyString()).executes { ctx ->
                                val duration = StringArgumentType.getString(ctx, "duration")
                                val text = StringArgumentType.getString(ctx, "text")
                                remindIn(ctx.source, duration, text)
                            },
                        ),
                    ),
                )
                .then(Commands.literal("list").executes { ctx -> list(ctx.source) })
                .then(
                    Commands.literal("dismiss").executes { ctx ->
                        val firing = ReminderEngine.topmostFiring()
                        if (firing == null) {
                            Commands.error(ctx.source, "Nothing is firing.")
                        } else {
                            ReminderEngine.dismiss(firing)
                            Commands.feedback(ctx.source, "Dismissed \"${firing.name}\".")
                        }
                        1
                    },
                )
                .then(
                    Commands.literal("snooze").executes { ctx ->
                        val firing = ReminderEngine.topmostFiring()
                        if (firing == null) {
                            Commands.error(ctx.source, "Nothing is firing.")
                        } else {
                            val minutes = ReminderConfig.settings.defaultSnoozeMinutes
                            ReminderEngine.snooze(firing, minutes)
                            Commands.feedback(ctx.source, "Snoozed \"${firing.name}\" for $minutes min.")
                        }
                        1
                    },
                )
                // Deferred: opening a screen mid-command is undone when the chat screen that ran it closes.
                .then(Commands.literal("edit").executes { ctx -> openScreen(ctx.source) { RemindersScreen(null) } })
                .then(Commands.literal("hud").executes { ctx -> openScreen(ctx.source) { ReminderHudScreen(null) } })
                .then(Commands.literal("presets").executes { ctx -> openScreen(ctx.source) { PresetsScreen(null) } }),
        )
    }

    private fun openScreen(
        source: FabricClientCommandSource,
        screen: () -> net.minecraft.client.gui.screens.Screen
    ): Int {
        val client = source.client
        client.execute { client.setScreen(screen()) }
        return 1
    }

    /** Creates a throwaway one-shot reminder. Deleted by the engine once it has fired. */
    private fun remindIn(source: FabricClientCommandSource, duration: String, text: String): Int {
        val ms = Duration.parse(duration)
        if (ms == null) {
            Commands.error(source, "\"$duration\" is not a duration — try 30s, 5m, 2h30m.")
            return 0
        }
        if (ms > Duration.MAX_MS) {
            Commands.error(source, "That is longer than the seven-day maximum.")
            return 0
        }

        val reminder = Reminder().apply {
            id = UUID.randomUUID().toString()
            name = text.take(32)
            this.text = text
            ephemeral = true
            trigger = Trigger().apply { seconds = ms / 1000.0 }
            actions = mutableListOf(
                ReminderAction(),
                ReminderAction().apply { kind = net.trilleo.reminder.model.ActionKind.SOUND })
        }
        ReminderConfig.settings.reminders.add(reminder)
        ReminderConfig.markDirty()
        ReminderEngine.arm(reminder, System.currentTimeMillis(), null)
        ReminderHudModel.invalidate()

        Commands.feedback(source, "Reminding you in ${Duration.format(ms)}: $text")
        return 1
    }

    private fun list(source: FabricClientCommandSource): Int {
        val reminders = ReminderConfig.settings.reminders
        if (reminders.isEmpty()) {
            Commands.feedback(source, "No reminders yet — add one with /hexa remind in 5m <text>.")
            return 1
        }
        val now = System.currentTimeMillis()
        reminders.forEach { reminder ->
            val state = ReminderState.of(reminder.id)
            val status = when (state.phase) {
                Phase.ARMED, Phase.SNOOZED -> Duration.format((state.firesAtEpochMs - now).coerceAtLeast(0L))
                Phase.PAUSED -> "paused, ${Duration.format(state.remainingMs)} left"
                Phase.FIRING -> "firing"
                Phase.IDLE -> if (reminder.enabled) "idle" else "off"
            }
            Commands.feedback(source, "${reminder.name} — $status")
        }
        return 1
    }

    override fun settingsCategory(): ConfigCategory = ConfigCategory.build("reminders") {
        toggle(
            "enabled",
            default = true,
            get = { ReminderConfig.active },
            set = { ReminderConfig.settings.enabled = it; ReminderConfig.save() },
        )

        action("edit_list") { screen -> Minecraft.getInstance().setScreen(RemindersScreen(screen)) }
        action("presets") { screen -> Minecraft.getInstance().setScreen(PresetsScreen(screen)) }
        action("hud_position") { screen -> Minecraft.getInstance().setScreen(ReminderHudScreen(screen)) }

        enum(
            "hud_corner",
            default = HudCorner.TOP_LEFT,
            get = { ReminderConfig.hud.corner },
            set = { ReminderConfig.hud.corner = it; ReminderConfig.save() },
        )
        enum(
            "sort",
            default = HudSort.SOONEST_FIRST,
            get = { ReminderConfig.hud.sort },
            // The sort is not part of the model's change signature, so the panel has to be told explicitly.
            set = { ReminderConfig.hud.sort = it; ReminderHudModel.invalidate(); ReminderConfig.save() },
        )
        slider(
            "hud_scale",
            min = HudSettings.SCALE_MIN,
            max = HudSettings.SCALE_MAX,
            step = HudSettings.SCALE_STEP,
            default = HudSettings().scale,
            get = { ReminderConfig.hud.scale },
            set = { ReminderConfig.hud.scale = it; ReminderConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.2fx", it) },
        )
        slider(
            "max_rows",
            min = HudSettings.MAX_ROWS_MIN.toDouble(),
            max = HudSettings.MAX_ROWS_MAX.toDouble(),
            step = 1.0,
            default = HudSettings().maxRows.toDouble(),
            get = { ReminderConfig.hud.maxRows.toDouble() },
            set = {
                ReminderConfig.hud.maxRows = it.toInt(); ReminderHudModel.invalidate(); ReminderConfig.markDirty()
            },
            format = { it.toInt().toString() },
        )

        toggle(
            "background",
            default = true,
            get = { ReminderConfig.hud.background },
            set = { ReminderConfig.hud.background = it; ReminderConfig.save() },
        )
        color(
            "background_color",
            default = HudSettings().backgroundColor,
            alpha = true,
            get = { ReminderConfig.hud.backgroundColor },
            set = { ReminderConfig.hud.backgroundColor = it; ReminderConfig.markDirty() },
        )
        color(
            "text_color",
            default = HudSettings().textColor,
            alpha = true,
            get = { ReminderConfig.hud.textColor },
            set = { ReminderConfig.hud.textColor = it; ReminderConfig.markDirty() },
        )
        color(
            "flash_color",
            default = HudSettings().flashColor,
            alpha = true,
            get = { ReminderConfig.hud.flashColor },
            set = { ReminderConfig.hud.flashColor = it; ReminderConfig.markDirty() },
        )

        toggle(
            "skyblock_only",
            default = true,
            get = { ReminderConfig.hud.skyblockOnly },
            set = { ReminderConfig.hud.skyblockOnly = it; ReminderConfig.save() },
        )
        toggle(
            "show_when_empty",
            default = false,
            get = { ReminderConfig.hud.showWhenEmpty },
            set = { ReminderConfig.hud.showWhenEmpty = it; ReminderConfig.save() },
        )

        toggle(
            "sound_enabled",
            default = true,
            get = { ReminderConfig.settings.soundEnabled },
            set = { ReminderConfig.settings.soundEnabled = it; ReminderConfig.save() },
        )
        slider(
            "flash_seconds",
            min = 0.0,
            max = 30.0,
            step = 0.5,
            default = ReminderSettings().flashSeconds,
            get = { ReminderConfig.settings.flashSeconds },
            set = { ReminderConfig.settings.flashSeconds = it; ReminderConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.1fs", it) },
        )
        slider(
            "snooze_minutes",
            min = 0.5,
            max = 60.0,
            step = 0.5,
            default = ReminderSettings().defaultSnoozeMinutes,
            get = { ReminderConfig.settings.defaultSnoozeMinutes },
            set = { ReminderConfig.settings.defaultSnoozeMinutes = it; ReminderConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.1f min", it) },
        )

        resetsTo(ReminderConfig.handle)
    }
}
