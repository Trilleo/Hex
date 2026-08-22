package net.trilleo.highlight

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.trilleo.Hex
import net.trilleo.command.Commands
import net.trilleo.config.ConfigCategory
import net.trilleo.feature.Feature
import net.trilleo.highlight.gui.HighlightsScreen
import net.trilleo.highlight.model.Highlight
import net.trilleo.sound.SoundPlayer
import net.trilleo.sound.SoundSlot
import net.trilleo.util.Chroma
import net.trilleo.util.Notify
import java.util.*

/**
 * Entities you choose, lit up in a colour you choose, announcing themselves when a new one turns up.
 *
 * Like [net.trilleo.region.RegionFeature], this deliberately leaves [enabled] at `true` and gates behaviour on
 * [HighlightConfig.active] instead: [net.trilleo.feature.Features.categories] hides a disabled feature's tab,
 * so wiring the master switch to [enabled] would make it impossible to switch back on from the menu.
 *
 * The feature itself is thin. [HighlightTracker] does the work on a tick,
 * [net.trilleo.mixin.EntityRenderDispatcherMixin] does the drawing by way of [HighlightLookup], and what lives
 * here is only the wiring: two keys, the tick call, the commands and the settings tab.
 */
object HighlightFeature : Feature {
    override val id: String = "highlights"

    /** Opens the rule list. */
    private lateinit var openKey: KeyMapping

    /** Creates a rule from whatever the crosshair is on, and opens it. */
    private lateinit var addKey: KeyMapping

    override fun onInit() {
        HighlightConfig.load()

        openKey = registerKey("key.hex.highlight.open")
        addKey = registerKey("key.hex.highlight.add")
    }

    private fun registerKey(translationKey: String): KeyMapping =
        KeyMapping(translationKey, InputConstants.UNKNOWN.value, Hex.KEY_CATEGORY)
            .also { KeyMappingHelper.registerKeyMapping(it) }

    override fun onClientTick(client: Minecraft) {
        // Outside the master switch on purpose, and the only key that is: a player who has switched highlights
        // off still has to be able to reach the screen that switches them back on.
        while (openKey.consumeClick()) {
            if (client.screen == null) client.setScreen(HighlightsScreen(null))
        }

        if (!HighlightConfig.active) return

        while (addKey.consumeClick()) addFromCrosshair(client)

        HighlightTracker.tick(client) { highlight, _ -> HighlightAlerts.found(client, highlight) }
    }

    override fun onWorldJoin(client: Minecraft) {
        HighlightTracker.reset()
    }

    override fun onWorldLeave(client: Minecraft) {
        HighlightTracker.reset()
    }

    // ---- capture -----------------------------------------------------------------------------------------

    /** Creates a rule from the entity under the crosshair, reports it, and opens it for a colour and a message. */
    private fun addFromCrosshair(client: Minecraft, name: String? = null) {
        val highlight = HighlightCapture.fromCrosshair(client)
        if (highlight == null) {
            // Translated, unlike the `/hexa` feedback below it: this line is what a *keybind* produces, and a
            // key that answers in English on a Chinese client is a different thing from a command that does.
            Notify.chat(client, Component.translatable("hex.highlights.no_target"))
            return
        }
        name?.let { highlight.name = HighlightConfig.uniqueName(it) }
        install(client, highlight)
    }

    private fun install(client: Minecraft, highlight: Highlight) {
        HighlightConfig.settings.highlights.add(highlight)
        HighlightConfig.normalizeNow()
        HighlightConfig.save()

        // The name and the summary are the player's own words and a formatted line, so they stay literal and
        // only the sentence around them is translated.
        Notify.chat(
            client,
            Component.translatable("hex.highlights.added", highlight.name, highlight.summary()),
        )
        SoundPlayer.feedback(client, SoundSlot.CAPTURED)
        // Deferred: a screen opened from inside the tick would be replaced the moment anything else sets one.
        client.execute {
            client.setScreen(net.trilleo.highlight.gui.HighlightEditScreen(null, highlight))
        }
    }

    // ---- commands ----------------------------------------------------------------------------------------

    override fun registerCommands(hex: LiteralArgumentBuilder<FabricClientCommandSource>) {
        hex.then(
            Commands.literal("highlight")
                // A bare `/hexa highlight` states its subcommands rather than guessing at one of them.
                .executes { ctx ->
                    Commands.feedback(ctx.source, "/hexa highlight add [name] — a rule for what you are looking at")
                    Commands.feedback(ctx.source, "/hexa highlight nearby — the names and types around you")
                    Commands.feedback(ctx.source, "/hexa highlight list, edit")
                    1
                }
                .then(
                    Commands.literal("add")
                        .executes { ctx -> add(ctx.source, null) }
                        .then(
                            Commands.argument("name", StringArgumentType.greedyString()).executes { ctx ->
                                add(ctx.source, StringArgumentType.getString(ctx, "name"))
                            },
                        ),
                )
                .then(Commands.literal("list").executes { ctx -> list(ctx.source) })
                .then(Commands.literal("nearby").executes { ctx -> nearby(ctx.source) })
                // Deferred: opening a screen mid-command is undone when the chat screen that ran it closes.
                .then(
                    Commands.literal("edit").executes { ctx ->
                        val client = ctx.source.client
                        client.execute { client.setScreen(HighlightsScreen(null)) }
                        1
                    },
                ),
        )
    }

    private fun add(source: FabricClientCommandSource, name: String?): Int {
        val client = source.client
        if (client.player == null) {
            Commands.error(source, "You have to be in a world.")
            return 0
        }
        addFromCrosshair(client, name)
        return 1
    }

    private fun list(source: FabricClientCommandSource): Int {
        val highlights = HighlightConfig.settings.highlights
        if (highlights.isEmpty()) {
            Commands.feedback(source, "No highlights yet — make one with /hexa highlight add.")
            return 1
        }
        highlights.forEach { highlight ->
            val count = HighlightTracker.countFor(highlight)
            val state = when {
                !highlight.enabled -> "off"
                count > 0 -> "matching $count"
                else -> "idle"
            }
            Commands.feedback(source, "${highlight.name} — ${highlight.summary()} — $state")
        }
        return 1
    }

    /**
     * Prints what is around the player, as the strings a rule matches against.
     *
     * The discovery tool the rest of the feature depends on. Hypixel's mob names carry colour codes, levels and
     * health bars that are invisible to a player and very visible to a rule, so guessing at one is hopeless;
     * this prints the name exactly as [HighlightTracker] resolved and folded it, which is the string to paste.
     */
    private fun nearby(source: FabricClientCommandSource): Int {
        val client = source.client
        val level = client.level
        val player = client.player
        if (level == null || player == null) {
            Commands.error(source, "You have to be in a world.")
            return 0
        }

        val origin = player.position()
        val found = level.entitiesForRendering()
            .asSequence()
            .filter { it !== player && !it.isRemoved }
            .filter { it.distanceToSqr(origin) <= NEARBY_RANGE * NEARBY_RANGE }
            .sortedBy { it.distanceToSqr(origin) }
            .take(NEARBY_LIMIT)
            .toList()

        if (found.isEmpty()) {
            Commands.feedback(source, "Nothing within $NEARBY_RANGE blocks.")
            return 1
        }

        found.forEach { entity ->
            val tag = HighlightTracker.tagOf(level, entity)
            val distance = String.format(Locale.ROOT, "%.0fm", kotlin.math.sqrt(entity.distanceToSqr(origin)))
            val name = tag?.let { "\"$it\"" } ?: "(no name)"
            Commands.feedback(source, "$distance  ${HighlightTracker.typeIdOf(entity)}  $name")
        }
        return 1
    }

    /** How far `nearby` looks. Enough to cover a room, short enough that the list stays readable. */
    private const val NEARBY_RANGE = 24.0

    /** How many lines `nearby` prints. A wall of chat is no more useful than none. */
    private const val NEARBY_LIMIT = 20

    // ---- settings ----------------------------------------------------------------------------------------

    private val defaults = HighlightSettings()

    override fun settingsCategory(): ConfigCategory = ConfigCategory.build("highlights") {
        toggle(
            "enabled",
            default = true,
            get = { HighlightConfig.active },
            set = {
                HighlightConfig.settings.enabled = it
                HighlightConfig.save()
                // Switching the feature off must take the glow off the entities that have it right now, rather
                // than leaving them lit until something else happens to rescan.
                if (!it) HighlightTracker.reset()
            },
        )

        action("edit_list") { screen -> Minecraft.getInstance().setScreen(HighlightsScreen(screen)) }

        slider(
            "scan_interval",
            min = HighlightConfig.SCAN_INTERVAL_MIN.toDouble(),
            max = HighlightConfig.SCAN_INTERVAL_MAX.toDouble(),
            step = 1.0,
            default = defaults.scanIntervalTicks.toDouble(),
            get = { HighlightConfig.settings.scanIntervalTicks.toDouble() },
            set = { HighlightConfig.settings.scanIntervalTicks = it.toInt(); HighlightConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.0f ticks", it) },
        )
        color(
            "default_color",
            default = defaults.defaultColor,
            chroma = true,
            get = { HighlightConfig.settings.defaultColor },
            set = { HighlightConfig.settings.defaultColor = it; HighlightConfig.markDirty() },
        )
        // Shared by every chroma rule — see HighlightSettings.chromaSeconds. markDirty, not save: a slider
        // fires its setter on every frame of a drag.
        slider(
            "chroma_speed",
            min = Chroma.SECONDS_MIN,
            max = Chroma.SECONDS_MAX,
            step = Chroma.SECONDS_STEP,
            default = defaults.chromaSeconds,
            get = { HighlightConfig.settings.chromaSeconds },
            set = { HighlightConfig.settings.chromaSeconds = it; HighlightConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.1fs", it) },
        )
        slider(
            "name_tag_scale",
            min = HighlightConfig.NAME_TAG_SCALE_MIN,
            max = HighlightConfig.NAME_TAG_SCALE_MAX,
            step = 0.25,
            default = defaults.nameTagScale,
            get = { HighlightConfig.settings.nameTagScale },
            set = { HighlightConfig.settings.nameTagScale = it; HighlightConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.2f×", it) },
        )
        toggle(
            "skyblock_only",
            default = defaults.skyblockOnly,
            get = { HighlightConfig.settings.skyblockOnly },
            set = { HighlightConfig.settings.skyblockOnly = it; HighlightConfig.save() },
        )

        resetsTo(HighlightConfig.handle)
    }
}
