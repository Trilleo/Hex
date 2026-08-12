package net.trilleo.chat

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.trilleo.Hex
import net.trilleo.chat.gui.ChatHighlightEditScreen
import net.trilleo.chat.gui.ChatHighlightsScreen
import net.trilleo.chat.model.ChatHighlight
import net.trilleo.command.Commands
import net.trilleo.config.ConfigCategory
import net.trilleo.feature.Feature
import net.trilleo.util.Chroma
import java.util.*

/**
 * Words you choose, picked out of chat in a colour you choose, optionally announced, optionally hidden.
 *
 * Like [net.trilleo.highlight.HighlightFeature], this deliberately leaves [enabled] at `true` and gates behaviour
 * on [ChatHighlightConfig.active] instead: [net.trilleo.feature.Features.categories] hides a disabled feature's
 * tab, so wiring the master switch to [enabled] would make it impossible to switch back on from the menu.
 *
 * The feature itself is thin. [ChatHighlighter] does the matching and the repainting,
 * [net.trilleo.mixin.ChatComponentMixin] animates chroma by way of [ChatChroma], and what lives here is only the
 * wiring: one key, the two chat hooks, the commands and the settings tab.
 */
object ChatHighlightFeature : Feature {
    override val id: String = "chat_highlights"

    /** Opens the rule list. */
    private lateinit var openKey: KeyMapping

    override fun onInit() {
        ChatHighlightConfig.load()

        openKey = KeyMapping("key.hex.chat.open", InputConstants.UNKNOWN.value, Hex.KEY_CATEGORY)
            .also { KeyMappingHelper.registerKeyMapping(it) }
    }

    override fun onClientTick(client: Minecraft) {
        // Outside the master switch on purpose, exactly as the entity highlight's own open key is: a player who
        // has switched chat highlights off still has to be able to reach the screen that switches them back on.
        while (openKey.consumeClick()) {
            if (client.screen == null) client.setScreen(ChatHighlightsScreen(null))
        }
    }

    /**
     * Returns `false` only for a line some rule asks to hide.
     *
     * This feature is registered last of the chat readers for that reason — see the ordering comment in
     * [net.trilleo.Hex] — so a hidden line has already been offered to reminders and to command suggestions
     * before it is dropped.
     */
    override fun onChatReceive(message: Component): Boolean =
        ChatHighlighter.onChatReceive(Minecraft.getInstance(), message)

    override fun onChatModify(message: Component): Component = ChatHighlighter.onChatModify(message)

    override fun onWorldJoin(client: Minecraft) {
        ChatHighlighter.reset()
    }

    override fun onWorldLeave(client: Minecraft) {
        ChatHighlighter.reset()
    }

    // ---- commands ----------------------------------------------------------------------------------------

    override fun registerCommands(hex: LiteralArgumentBuilder<FabricClientCommandSource>) {
        hex.then(
            Commands.literal("chat")
                // A bare `/hexa chat` states its subcommands rather than guessing at one of them.
                .executes { ctx ->
                    Commands.feedback(ctx.source, "/hexa chat add [name] — a new rule, opened for editing")
                    Commands.feedback(ctx.source, "/hexa chat test <line> — run a line past your rules")
                    Commands.feedback(ctx.source, "/hexa chat list, edit")
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
                .then(
                    Commands.literal("test").then(
                        Commands.argument("line", StringArgumentType.greedyString()).executes { ctx ->
                            test(ctx.source, StringArgumentType.getString(ctx, "line"))
                        },
                    ),
                )
                // Deferred: opening a screen mid-command is undone when the chat screen that ran it closes.
                .then(
                    Commands.literal("edit").executes { ctx ->
                        val client = ctx.source.client
                        client.execute { client.setScreen(ChatHighlightsScreen(null)) }
                        1
                    },
                ),
        )
    }

    private fun add(source: FabricClientCommandSource, name: String?): Int {
        val client = source.client
        val rule = ChatHighlight().apply {
            id = UUID.randomUUID().toString()
            this.name = ChatHighlightConfig.uniqueName(
                name ?: Component.translatable("hex.chat_highlights.new_name").string,
            )
        }
        ChatHighlightConfig.settings.highlights.add(rule)
        ChatHighlightConfig.normalizeNow()
        ChatHighlightConfig.save()
        // Straight into the editor. A rule with no text matches nothing, so leaving the player in chat with a
        // "created it" message would only mean telling them to go and open it themselves.
        client.execute { client.setScreen(ChatHighlightEditScreen(null, rule)) }
        return 1
    }

    private fun list(source: FabricClientCommandSource): Int {
        val rules = ChatHighlightConfig.settings.highlights
        if (rules.isEmpty()) {
            Commands.feedback(source, "No chat highlights yet — make one with /hexa chat add.")
            return 1
        }
        rules.forEach { rule ->
            val state = if (rule.enabled) "on" else "off"
            Commands.feedback(
                source,
                Component.literal("${rule.name} — ").append(rule.summary()).append(" — $state"),
            )
        }
        return 1
    }

    /**
     * Runs [line] past the rules and prints what they made of it.
     *
     * The discovery tool this feature needs, and the counterpart of `/hexa highlight nearby`. Chat has no
     * crosshair to point at something with, and waiting for a real message to arrive is a slow way to find out
     * that a colour is unreadable or that a rule was scoped to the wrong island — so the line can be supplied by
     * hand instead. What it prints is the genuine article: the same [ChatHighlighter] pass, painted the same way,
     * chroma and all.
     */
    private fun test(source: FabricClientCommandSource, line: String): Int {
        val outcome = ChatHighlighter.dryRun(line)
        Commands.feedback(source, outcome.styled)
        if (outcome.isEmpty) {
            Commands.feedback(source, "No rule matched that line.")
            return 1
        }
        val names = outcome.matched.joinToString(", ") { it.name }
        val hidden = if (outcome.hide) " (this line would be hidden)" else ""
        Commands.feedback(source, "Matched: $names$hidden")
        return 1
    }

    // ---- settings ----------------------------------------------------------------------------------------

    private val defaults = ChatHighlightSettings()

    override fun settingsCategory(): ConfigCategory = ConfigCategory.build("chat_highlights") {
        toggle(
            "enabled",
            default = true,
            get = { ChatHighlightConfig.active },
            set = {
                ChatHighlightConfig.settings.enabled = it
                ChatHighlightConfig.save()
                // Messages already in the log keep the styling they were given — they were built once and cannot
                // be rebuilt — so the reset is about the stash and the cooldowns, not about the chat window.
                if (!it) ChatHighlighter.reset()
            },
        )

        action("edit_list") { screen -> Minecraft.getInstance().setScreen(ChatHighlightsScreen(screen)) }

        color(
            "default_color",
            default = defaults.defaultColor,
            get = { ChatHighlightConfig.settings.defaultColor },
            set = { ChatHighlightConfig.settings.defaultColor = it; ChatHighlightConfig.markDirty() },
        )
        slider(
            "chroma_speed",
            min = Chroma.SECONDS_MIN,
            max = Chroma.SECONDS_MAX,
            step = Chroma.SECONDS_STEP,
            default = defaults.chromaSeconds,
            get = { ChatHighlightConfig.settings.chromaSeconds },
            set = { ChatHighlightConfig.settings.chromaSeconds = it; ChatHighlightConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.1fs", it) },
        )
        slider(
            "chroma_width",
            min = Chroma.WIDTH_MIN,
            max = Chroma.WIDTH_MAX,
            step = Chroma.WIDTH_STEP,
            default = defaults.chromaWidth,
            get = { ChatHighlightConfig.settings.chromaWidth },
            set = { ChatHighlightConfig.settings.chromaWidth = it; ChatHighlightConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.0f", it) },
        )
        toggle(
            "skyblock_only",
            default = defaults.skyblockOnly,
            get = { ChatHighlightConfig.settings.skyblockOnly },
            set = { ChatHighlightConfig.settings.skyblockOnly = it; ChatHighlightConfig.save() },
        )

        resetsTo(ChatHighlightConfig.handle)
    }
}
