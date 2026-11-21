package net.trilleo.sync

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.command.Commands
import net.trilleo.config.ConfigCategory
import net.trilleo.config.gui.ConfirmActionScreen
import net.trilleo.feature.Feature
import net.trilleo.skyblock.SkyblockProfile
import net.trilleo.util.Notify
import java.time.Duration

/**
 * trilleo.net sync: reads the player's attribute levels and Hunting Box in game and uploads them to their
 * trilleo.net account, per Skyblock profile.
 *
 * The pieces, in the order data flows through them:
 *
 *  - [net.trilleo.skyblock.SkyblockProfile] knows which profile the player is on;
 *  - [ShardReader] reads the Attribute Menu and Hunting Box while they are open;
 *  - [SyncQueue] batches what was read and sends it;
 *  - [TrilleoAccount] holds the token and runs linking;
 *  - [TrilleoApi] makes the calls.
 *
 * Nothing is read until the player links an account: there is nobody to upload it to.
 */
object SyncFeature : Feature {
    override val id: String = "sync"

    override fun onInit() {
        SyncConfig.load()
        TrilleoAccount.load()
    }

    override fun onClientTick(client: Minecraft) {
        if (!SyncConfig.settings.enabled || !TrilleoAccount.linked) return
        ShardReader.tick(client)
    }

    override fun onWorldJoin(client: Minecraft) {
        if (TrilleoAccount.linked) TrilleoAccount.verifyOnce(client)
    }

    override fun onWorldLeave(client: Minecraft) {
        ShardReader.reset()
    }

    override fun registerCommands(hex: LiteralArgumentBuilder<FabricClientCommandSource>) {
        hex.then(
            Commands.literal("sync")
                .executes { ctx -> status(ctx.source); 1 }
                .then(Commands.literal("status").executes { ctx -> status(ctx.source); 1 })
                .then(Commands.literal("link").executes { ctx -> TrilleoAccount.startLink(ctx.source.client); 1 })
                .then(Commands.literal("unlink").executes { ctx -> TrilleoAccount.unlink(ctx.source.client); 1 })
                .then(Commands.literal("now").executes { ctx -> SyncQueue.syncNow(ctx.source.client); 1 }),
        )
    }

    private val defaults = SyncSettings()

    override fun settingsCategory(): ConfigCategory = ConfigCategory.build("sync") {
        val client = Minecraft.getInstance()
        val name = TrilleoAccount.username()
        when {
            TrilleoAccount.linked -> action(
                if (name != null) Component.translatable("hex.config.sync.unlink_named", name)
                else Component.translatable("hex.config.sync.unlink"),
                Component.translatable("hex.config.sync.unlink.tooltip"),
            ) { screen -> client.gui.setScreen(unlinkPrompt(screen)) }

            else -> action(
                Component.translatable(if (TrilleoAccount.linking) "hex.config.sync.show_code" else "hex.config.sync.link"),
                Component.translatable("hex.config.sync.link.tooltip"),
            ) { _ ->
                TrilleoAccount.startLink(client)
                // Back to the game, where the code is shown in chat.
                client.gui.setScreen(null)
            }
        }
        toggle(
            "enabled",
            default = defaults.enabled,
            get = { SyncConfig.settings.enabled },
            set = { SyncConfig.settings.enabled = it; SyncConfig.save() },
        )
        toggle(
            "auto_sync",
            default = defaults.autoSync,
            get = { SyncConfig.settings.autoSync },
            set = { SyncConfig.settings.autoSync = it; SyncConfig.save() },
        )
        toggle(
            "chat_summary",
            default = defaults.chatSummary,
            get = { SyncConfig.settings.chatSummary },
            set = { SyncConfig.settings.chatSummary = it; SyncConfig.save() },
        )
        action("sync_now") { SyncQueue.syncNow(client) }
        text(
            "api_url",
            default = defaults.apiUrl,
            get = { SyncConfig.settings.apiUrl },
            set = { SyncConfig.settings.apiUrl = it.trim(); SyncConfig.save() },
            validate = {
                if (TrilleoApi.normalizeBase(it) == null) Component.translatable("hex.config.sync.api_url.invalid")
                else null
            },
        )
        resetsTo(SyncConfig.handle)
    }

    private fun unlinkPrompt(parent: Screen): Screen = ConfirmActionScreen(
        parent,
        Component.translatable("hex.config.sync.unlink"),
        Component.translatable("hex.sync.unlink.confirm"),
        Component.translatable("hex.sync.unlink.detail"),
        listOf(
            ConfirmActionScreen.Choice(Component.translatable("hex.sync.unlink.yes")) {
                TrilleoAccount.unlink(Minecraft.getInstance())
            },
            ConfirmActionScreen.Choice(Component.translatable("hex.sync.unlink.no"), null),
        ),
    )

    /** `/hexa sync`: who this install is linked to, which profile it would sync, and how the last sync went. */
    private fun status(source: FabricClientCommandSource) {
        val name = TrilleoAccount.username()
        val account = when {
            TrilleoAccount.linked && name != null ->
                Component.translatable("hex.sync.status.linked", name, TrilleoApi.base())

            TrilleoAccount.linked -> Component.translatable("hex.sync.status.linked_unnamed", TrilleoApi.base())
            TrilleoAccount.linking -> Component.translatable("hex.sync.status.linking")
            else -> Component.translatable("hex.sync.status.unlinked", TrilleoAccount.command("/hexa sync link"))
        }
        Commands.feedback(source, Notify.line(account, ChatFormatting.AQUA))

        val unknown = Component.translatable("hex.sync.status.unknown")
        val profileName: Component = SkyblockProfile.name?.let { Component.literal(it) } ?: unknown
        val mode: Component = SkyblockProfile.gameMode
            ?.let { Component.translatable("hex.sync.mode.${it.apiName}") } ?: unknown
        val profileId: Component = SkyblockProfile.id?.let { Component.literal(it) } ?: unknown
        Commands.feedback(
            source,
            Component.translatable("hex.sync.status.profile", profileName, mode, profileId).withStyle(ChatFormatting.GRAY),
        )

        val last = SyncQueue.lastSyncAt
        val lastText: Component = if (last == 0L) Component.translatable("hex.sync.status.never") else {
            val minutes = Duration.ofMillis(System.currentTimeMillis() - last).toMinutes()
            Component.translatable("hex.sync.status.minutes_ago", minutes)
        }
        Commands.feedback(
            source,
            Component.translatable("hex.sync.status.queue", SyncQueue.waiting, lastText).withStyle(ChatFormatting.GRAY),
        )
        if (!SyncConfig.settings.enabled) {
            Commands.feedback(source, Component.translatable("hex.sync.status.disabled").withStyle(ChatFormatting.YELLOW))
        }
    }
}
