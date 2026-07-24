package net.trilleo.region

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.arguments.DoubleArgumentType
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
import net.trilleo.region.gui.RegionsScreen
import net.trilleo.region.model.Region
import net.trilleo.util.Notify
import java.util.*

/**
 * Named volumes of world space that announce themselves when you walk into them.
 *
 * Like [net.trilleo.reminder.ReminderFeature], this deliberately leaves [enabled] at `true` and gates
 * behaviour on [RegionConfig.active] instead: [net.trilleo.feature.Features.categories] hides a disabled
 * feature's tab, so wiring the master switch to [enabled] would make it impossible to switch back on from the
 * menu.
 *
 * Registered ahead of the reminder feature in [Hex], so a crossing detected this tick reaches
 * [net.trilleo.reminder.ReminderTriggers] in the same one rather than the next.
 */
object RegionFeature : Feature {
    override val id: String = "regions"

    /** Marks a corner — at the freecam if it is flying, otherwise at the player's feet. */
    private lateinit var markKey: KeyMapping

    /** Creates a region around the player in one press. */
    private lateinit var hereKey: KeyMapping

    /** Starts and stops a walk-the-perimeter capture. */
    private lateinit var walkKey: KeyMapping

    /** Shows every region on this island in the world. */
    private lateinit var previewKey: KeyMapping

    private lateinit var openKey: KeyMapping

    override fun onInit() {
        RegionConfig.load()

        markKey = registerKey("key.hex.region.mark")
        hereKey = registerKey("key.hex.region.here")
        walkKey = registerKey("key.hex.region.walk")
        previewKey = registerKey("key.hex.region.preview")
        openKey = registerKey("key.hex.region.open")

        // The capture panel names the keys the player actually bound, and says so differently when they have
        // bound none.
        RegionHud.markKey = markKey
        RegionHud.walkKey = walkKey
    }

    private fun registerKey(translationKey: String): KeyMapping =
        KeyMapping(translationKey, InputConstants.UNKNOWN.value, Hex.KEY_CATEGORY)
            .also { KeyMappingHelper.registerKeyMapping(it) }

    override fun onClientTick(client: Minecraft) {
        // Outside the master switch on purpose, and the only key that is: a player who has switched regions
        // off still has to be able to reach the screen that switches them back on.
        while (openKey.consumeClick()) {
            if (client.screen == null) client.setScreen(RegionsScreen(null))
        }

        if (!RegionConfig.active) return

        while (previewKey.consumeClick()) togglePreview(client)
        while (markKey.consumeClick()) mark(client)
        while (hereKey.consumeClick()) createHere(client, RegionConfig.settings.defaultRadius, null)
        while (walkKey.consumeClick()) toggleWalk(client, null)

        RegionCapture.tickWalk(client)
        RegionTracker.tick(
            client,
            onEnter = { RegionAlerts.enter(client, it) },
            onLeave = { RegionAlerts.leave(client, it) },
        )
        RegionRenderer.tick(client)
    }

    override fun onHudRender(extractor: GuiGraphicsExtractor, delta: DeltaTracker) {
        RegionHud.draw(extractor)
    }

    override fun onWorldJoin(client: Minecraft) {
        RegionTracker.reset()
    }

    override fun onWorldLeave(client: Minecraft) {
        RegionTracker.reset()
        // A draft belongs to coordinates on a server that has just gone away.
        RegionCapture.cancel()
        RegionRenderer.previewAll = false
        RegionRenderer.focused = null
    }

    // ---- capture -----------------------------------------------------------------------------------------

    /**
     * Records a corner, finishing the region once two are in.
     *
     * The first press starts a capture rather than needing one started first: pressing "mark a corner" with
     * nothing in progress can only mean "start here".
     */
    private fun mark(client: Minecraft) {
        val point = RegionCapture.markPoint(client) ?: return
        if (!RegionCapture.addCorner(point)) {
            Notify.chat(client, "Corner set. Mark the opposite one to finish the region.")
            Notify.uiSound(client, 1.4f)
            return
        }

        val region = RegionCapture.finish(newName()) ?: return
        install(client, region)
    }

    private fun createHere(client: Minecraft, radius: Double, name: String?) {
        val player = client.player ?: return
        RegionCapture.cancel()
        val region = RegionCapture.around(name ?: newName(), player.position())
        // A radius given on the command line applies to this region only, without changing the default.
        region.setAround(region.center(), radius, RegionConfig.settings.defaultHeight)
        install(client, region)
    }

    private fun toggleWalk(client: Minecraft, name: String?) {
        if (RegionCapture.mode == CaptureMode.WALK) {
            val region = RegionCapture.finish(name ?: newName())
            if (region == null) {
                Notify.chat(client, "Nothing was recorded.")
                return
            }
            install(client, region)
            return
        }
        val player = client.player ?: return
        RegionCapture.beginWalk(player.position())
        Notify.chat(client, "Recording — walk the outline, then press the key again to finish.")
        Notify.uiSound(client, 1.4f)
    }

    /** Adds a freshly captured region, reports it, and opens it for a name and a message. */
    private fun install(client: Minecraft, region: Region) {
        RegionConfig.settings.regions.add(region)
        RegionConfig.normalizeNow()
        RegionConfig.save()

        Notify.chat(client, "Added region \"${region.name}\" — ${region.summary()}.")
        Notify.uiSound(client, 1.8f)
        // Deferred: a screen opened from inside the tick would be replaced the moment anything else sets one.
        client.execute { client.setScreen(net.trilleo.region.gui.RegionEditScreen(null, region)) }
    }

    /** A distinct default name, so two quick captures do not both land on "region". */
    private fun newName(): String =
        RegionConfig.uniqueName(Component.translatable("hex.regions.new_name").string)

    private fun togglePreview(client: Minecraft) {
        RegionRenderer.previewAll = !RegionRenderer.previewAll
        val state = if (RegionRenderer.previewAll) "on" else "off"
        Notify.chat(client, "Region preview $state.")
    }

    // ---- commands ----------------------------------------------------------------------------------------

    override fun registerCommands(hex: LiteralArgumentBuilder<FabricClientCommandSource>) {
        hex.then(
            Commands.literal("region")
                // A bare `/hexa region` states its subcommands rather than guessing at one of them.
                .executes { ctx ->
                    Commands.feedback(ctx.source, "/hexa region here [radius] [name] — a region around you")
                    Commands.feedback(ctx.source, "/hexa region mark — set a corner (uses the freecam when flying)")
                    Commands.feedback(ctx.source, "/hexa region walk [name] — record the outline you walk")
                    Commands.feedback(ctx.source, "/hexa region list, preview, cancel, edit")
                    1
                }
                .then(
                    Commands.literal("here")
                        .executes { ctx -> here(ctx.source, RegionConfig.settings.defaultRadius, null) }
                        .then(
                            Commands.argument("radius", DoubleArgumentType.doubleArg(1.0, 256.0))
                                .executes { ctx ->
                                    here(ctx.source, DoubleArgumentType.getDouble(ctx, "radius"), null)
                                }
                                .then(
                                    Commands.argument("name", StringArgumentType.greedyString())
                                        .executes { ctx ->
                                            here(
                                                ctx.source,
                                                DoubleArgumentType.getDouble(ctx, "radius"),
                                                StringArgumentType.getString(ctx, "name"),
                                            )
                                        },
                                ),
                        ),
                )
                .then(
                    Commands.literal("mark").executes { ctx ->
                        mark(ctx.source.client)
                        1
                    },
                )
                .then(
                    Commands.literal("walk")
                        .executes { ctx -> toggleWalk(ctx.source.client, null); 1 }
                        .then(
                            Commands.argument("name", StringArgumentType.greedyString()).executes { ctx ->
                                toggleWalk(ctx.source.client, StringArgumentType.getString(ctx, "name"))
                                1
                            },
                        ),
                )
                .then(
                    Commands.literal("cancel").executes { ctx ->
                        if (RegionCapture.active) {
                            RegionCapture.cancel()
                            Commands.feedback(ctx.source, "Capture cancelled.")
                        } else {
                            Commands.error(ctx.source, "Nothing is being captured.")
                        }
                        1
                    },
                )
                .then(Commands.literal("list").executes { ctx -> list(ctx.source) })
                .then(
                    Commands.literal("preview").executes { ctx ->
                        togglePreview(ctx.source.client)
                        1
                    },
                )
                // Deferred: opening a screen mid-command is undone when the chat screen that ran it closes.
                .then(
                    Commands.literal("edit").executes { ctx ->
                        val client = ctx.source.client
                        client.execute { client.setScreen(RegionsScreen(null)) }
                        1
                    },
                ),
        )
    }

    private fun here(source: FabricClientCommandSource, radius: Double, name: String?): Int {
        val client = source.client
        if (client.player == null) {
            Commands.error(source, "You have to be in a world.")
            return 0
        }
        createHere(client, radius, name)
        return 1
    }

    private fun list(source: FabricClientCommandSource): Int {
        val regions = RegionConfig.settings.regions
        if (regions.isEmpty()) {
            Commands.feedback(source, "No regions yet — make one with /hexa region here.")
            return 1
        }
        val insideIds = RegionTracker.insideIds()
        regions.forEach { region ->
            val state = when {
                !region.enabled -> "off"
                region.id in insideIds -> "you are here"
                else -> "idle"
            }
            Commands.feedback(source, "${region.name} — ${region.summary()} — $state")
        }
        return 1
    }

    // ---- settings ----------------------------------------------------------------------------------------

    private val defaults = RegionSettings()

    override fun settingsCategory(): ConfigCategory = ConfigCategory.build("regions") {
        toggle(
            "enabled",
            default = true,
            get = { RegionConfig.active },
            set = {
                RegionConfig.settings.enabled = it
                RegionConfig.save()
                // Switching the feature off mid-capture would strand the draft: its keys stop being read, so
                // the hint panel would sit there naming keys that no longer do anything.
                if (!it) {
                    RegionCapture.cancel()
                    RegionRenderer.previewAll = false
                }
            },
        )

        action("edit_list") { screen -> Minecraft.getInstance().setScreen(RegionsScreen(screen)) }

        slider(
            "default_radius",
            min = 1.0,
            max = 64.0,
            step = 1.0,
            default = defaults.defaultRadius,
            get = { RegionConfig.settings.defaultRadius },
            set = { RegionConfig.settings.defaultRadius = it; RegionConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.0f blocks", it) },
        )
        slider(
            "default_height",
            min = 1.0,
            max = 64.0,
            step = 1.0,
            default = defaults.defaultHeight,
            get = { RegionConfig.settings.defaultHeight },
            set = { RegionConfig.settings.defaultHeight = it; RegionConfig.markDirty() },
            format = { String.format(Locale.ROOT, "±%.0f blocks", it) },
        )
        slider(
            "exit_margin",
            min = 0.0,
            max = 4.0,
            step = 0.25,
            default = defaults.exitMargin,
            get = { RegionConfig.settings.exitMargin },
            set = { RegionConfig.settings.exitMargin = it; RegionConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.2f blocks", it) },
        )

        toggle(
            "preview_see_through",
            default = defaults.previewSeeThrough,
            get = { RegionConfig.settings.previewSeeThrough },
            set = { RegionConfig.settings.previewSeeThrough = it; RegionConfig.save() },
        )
        toggle(
            "preview_names",
            default = defaults.previewNames,
            get = { RegionConfig.settings.previewNames },
            set = { RegionConfig.settings.previewNames = it; RegionConfig.save() },
        )
        color(
            "preview_color",
            default = defaults.previewColor,
            alpha = true,
            get = { RegionConfig.settings.previewColor },
            set = { RegionConfig.settings.previewColor = it; RegionConfig.markDirty() },
        )
        color(
            "draft_color",
            default = defaults.draftColor,
            alpha = true,
            get = { RegionConfig.settings.draftColor },
            set = { RegionConfig.settings.draftColor = it; RegionConfig.markDirty() },
        )

        toggle(
            "skyblock_only",
            default = defaults.skyblockOnly,
            get = { RegionConfig.settings.skyblockOnly },
            set = { RegionConfig.settings.skyblockOnly = it; RegionConfig.save() },
        )

        resetsTo(RegionConfig.handle)
    }
}
