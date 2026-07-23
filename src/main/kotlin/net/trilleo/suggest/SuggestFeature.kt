package net.trilleo.suggest

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.command.Commands
import net.trilleo.config.ConfigCategory
import net.trilleo.config.gui.ConfirmActionScreen
import net.trilleo.feature.Feature
import net.trilleo.suggest.context.ChatCues
import net.trilleo.suggest.context.ContextSnapshot
import net.trilleo.suggest.context.ContextSources
import net.trilleo.suggest.context.SessionMemory
import net.trilleo.suggest.model.CommandCatalog
import net.trilleo.suggest.model.CommandParser
import net.trilleo.suggest.model.ModelStore
import net.trilleo.suggest.model.Ranker
import net.trilleo.suggest.model.SuggestModel
import net.trilleo.suggest.model.Weights
import net.trilleo.suggest.ui.gui.SuggestScreen
import net.trilleo.util.TextClean
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Learns which commands the player runs, where and after what, and offers them back in the chat box.
 *
 * Like [net.trilleo.reminder.ReminderFeature], this leaves [enabled] at `true` and gates behaviour on
 * [SuggestConfig.active] instead: [net.trilleo.feature.Features.categories] hides a disabled feature's tab,
 * so wiring the master switch to [enabled] would make it impossible to switch back on from the menu.
 *
 * **This feature never sends anything.** It writes into the player's own chat box and stops there; every
 * command that leaves the client is one the player pressed Enter on. That line is what keeps it on the
 * display-and-convenience side of the Hypixel rules the README commits to, and it is not a detail that may be
 * relaxed later for convenience.
 */
object SuggestFeature : Feature {
    private val LOGGER = LoggerFactory.getLogger("hex/suggest")

    override val id: String = "suggest"

    override fun onInit() {
        SuggestConfig.load()
        // Before the model, because loading it installs the slot layouts that decide what may be recorded —
        // and a command sent in the seconds before it landed would otherwise be parsed by the built-in
        // fallback rather than by the real rules.
        CommandCatalog.load()
        ModelStore.load()

        // Recording needs no mixin: fabric-message-api fires this for every command the player executes,
        // client commands included, with the leading slash already stripped.
        ClientSendMessageEvents.COMMAND.register { command ->
            guard("record") { record(command) }
        }
    }

    override fun onClientTick(client: Minecraft) {
        // Outside the master switch, and cheap by construction: one integer increment and one countdown.
        // Session history has to keep flowing even while suggestions are off, or switching the feature back
        // on mid-session would leave it predicting from a history with a hole in it.
        SessionMemory.tick()
        ModelStore.tick()
    }

    override fun onWorldJoin(client: Minecraft) {
        SessionMemory.reset()
        ChatCues.reset()
    }

    override fun onWorldLeave(client: Minecraft) {
        SessionMemory.reset()
        ChatCues.reset()
        // The session that just ended is the natural save point, and it is the one moment a hitch cannot be
        // noticed.
        ModelStore.flush()
    }

    /**
     * Always returns `true`. This feature observes chat and never swallows it — the hook is shared with every
     * other feature, so refusing to interfere is the only safe contract.
     */
    override fun onChatReceive(message: Component): Boolean {
        if (SuggestConfig.active) {
            guard("cue") { ChatCues.offer(TextClean.strip(message.string)) }
        }
        return true
    }

    override fun onShutdown() {
        ModelStore.shutdown()
    }

    // ---- recording -------------------------------------------------------------------------------------

    /**
     * Folds one sent command into the model.
     *
     * The order here is load-bearing. The context snapshot is taken *before*
     * [SessionMemory.note], so `prev1` names the command before this one rather than this one — get that
     * backwards and every transition the model learns is a self-loop. And the session history is updated even
     * when learning is paused, because pausing means "stop writing to the file", not "stop knowing what I just
     * did".
     */
    private fun record(command: String) {
        if (!SuggestConfig.active) return
        val parsed = CommandParser.parse(
            command,
            blocklist = SuggestConfig.blocklist,
            learnPlayerNames = SuggestConfig.learnPlayerNames,
        ) ?: return

        if (SuggestConfig.learning) {
            val context = ContextSources.snapshot(Minecraft.getInstance())
            SuggestModel.record(parsed, context, System.currentTimeMillis())
            ModelStore.markDirty()
        }
        // Before the session history moves on, while the chat screen that showed the list is still alive: a
        // command typed out in full while a list was on screen is a rejection of everything else on it, and
        // that is a training example as good as a click.
        SuggestSession.onCommandSent(parsed.key)
        SessionMemory.note(parsed.key)
    }

    /**
     * Runs [block], swallowing anything it throws.
     *
     * Both callers sit on hooks shared with the rest of the mod — `ClientSendMessageEvents.COMMAND` and the
     * chat receive event — where a throw would break sending commands or receiving chat for *every* feature,
     * not just this one. The same reasoning [net.trilleo.reminder.ChatMatcher] documents at length: a
     * suggestion that fails to appear is a disappointment, and chat that stops working is a broken game.
     */
    private inline fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            LOGGER.error("Command suggestions failed while handling {}", what, e)
        }
    }

    // ---- commands --------------------------------------------------------------------------------------

    override fun registerCommands(hex: LiteralArgumentBuilder<FabricClientCommandSource>) {
        hex.then(
            Commands.literal("suggest")
                .executes { ctx ->
                    Commands.feedback(ctx.source, "/hexa suggest stats — what has been learned")
                    Commands.feedback(ctx.source, "/hexa suggest why <text> — what it would suggest, and why")
                    Commands.feedback(ctx.source, "/hexa suggest forget <command> — forget one command")
                    Commands.feedback(ctx.source, "/hexa suggest clear confirm — forget everything")
                    Commands.feedback(ctx.source, "/hexa suggest pause | resume — stop or start learning")
                    Commands.feedback(ctx.source, "/hexa suggest dashboard — inspect what has been learned")
                    1
                }
                .then(Commands.literal("stats").executes { ctx -> stats(ctx.source) })
                .then(
                    Commands.literal("why").then(
                        Commands.argument("text", StringArgumentType.greedyString()).executes { ctx ->
                            why(ctx.source, StringArgumentType.getString(ctx, "text"))
                        },
                    ),
                )
                .then(
                    Commands.literal("forget").then(
                        Commands.argument("command", StringArgumentType.greedyString()).executes { ctx ->
                            forget(ctx.source, StringArgumentType.getString(ctx, "command"))
                        },
                    ),
                )
                .then(
                    Commands.literal("clear")
                        .executes { ctx ->
                            Commands.error(
                                ctx.source,
                                "This forgets every command Hex has learned. Run /hexa suggest clear confirm to go ahead.",
                            )
                            0
                        }
                        .then(Commands.literal("confirm").executes { ctx -> clear(ctx.source) }),
                )
                .then(Commands.literal("pause").executes { ctx -> setLearning(ctx.source, false) })
                .then(Commands.literal("resume").executes { ctx -> setLearning(ctx.source, true) })
                // Deferred: opening a screen mid-command is undone when the chat screen that ran it closes.
                .then(
                    Commands.literal("dashboard").executes { ctx ->
                        val client = ctx.source.client
                        client.execute { client.setScreen(SuggestScreen(null)) }
                        1
                    },
                ),
        )
    }

    private fun stats(source: FabricClientCommandSource): Int {
        val summary = SuggestModel.summary(System.currentTimeMillis())
        if (summary.keys == 0) {
            Commands.feedback(
                source,
                "Nothing learned yet — run a few commands and check back. " +
                    "${CommandCatalog.size} command(s) are suggested from the bundled catalogue meanwhile.",
            )
            return 1
        }
        Commands.feedback(
            source,
            "${summary.keys} command line(s) across ${summary.names} command(s), " +
                "${String.format(Locale.ROOT, "%.1f", summary.observations)} weighted uses, " +
                "${summary.trainingSteps} training step(s).",
        )
        summary.top.forEach { (key, weight) ->
            Commands.feedback(source, "  /$key — ${String.format(Locale.ROOT, "%.1f", weight)}")
        }
        if (!SuggestConfig.learning) Commands.feedback(source, "Learning is paused.")
        return 1
    }

    /**
     * Prints what the ranker would suggest for [raw], and the arithmetic behind the leader.
     *
     * This exists as a command rather than only as a screen because it is the tool for the question "why on
     * earth did it offer me that", and that question is always asked *in* chat, about something that just
     * happened, with the context still live. Opening a menu to ask it would change the answer.
     */
    private fun why(source: FabricClientCommandSource, raw: String): Int {
        val typed = raw.trim().removePrefix("/")
        val now = System.currentTimeMillis()
        val context = ContextSources.snapshot(source.client)
        val ranked = Ranker.rank(typed, context, limit = SuggestConfig.settings.rows, now = now)

        if (ranked.isEmpty()) {
            Commands.feedback(source, "Nothing to suggest for \"/$typed\" yet.")
            return 1
        }

        Commands.feedback(
            source,
            "Context: $context".take(MAX_LINE),
        )
        ranked.forEachIndexed { index, candidate ->
            Commands.feedback(
                source,
                "${index + 1}. /${candidate.key} — ${String.format(Locale.ROOT, "%.2f", candidate.score)}",
            )
        }

        val best = Ranker.explain(ranked.first(), ranked, context, now)
        Commands.feedback(
            source,
            "Why /${best.key} (${String.format(Locale.ROOT, "%.0f%%", best.probability * 100)} confident):",
        )
        best.terms.filter { kotlin.math.abs(it.contribution) > 0.005 }.forEach { term ->
            Commands.feedback(
                source,
                "  ${term.name}: ${String.format(Locale.ROOT, "%+.2f", term.contribution)}" +
                    " (${String.format(Locale.ROOT, "%.2f", term.value)} × ${String.format(Locale.ROOT, "%.2f", term.weight)})",
            )
        }
        best.routine?.let { Commands.feedback(source, "  routine: $it") }
        best.context.take(TOP_CONTEXT).forEach { (feature, value) ->
            val label = ContextSnapshot.LABELS[feature] ?: feature
            Commands.feedback(source, "  · $label: ${String.format(Locale.ROOT, "%+.2f", value)}")
        }
        return 1
    }

    private fun forget(source: FabricClientCommandSource, raw: String): Int {
        val wanted = raw.trim().removePrefix("/").lowercase(Locale.ROOT)
        if (wanted.isEmpty()) {
            Commands.error(source, "Name a command to forget.")
            return 0
        }
        // The exact line first, then the command and everything under it — so `forget warp dungeon_hub`
        // removes one destination and `forget warp` removes the lot, which is what each phrasing means.
        val removed = if (SuggestModel.forget(wanted)) 1 else SuggestModel.forgetCommand(wanted)
        if (removed == 0) {
            Commands.error(source, "Nothing learned about /$wanted.")
            return 0
        }
        ModelStore.saveNow()
        Commands.feedback(source, "Forgot $removed entr${if (removed == 1) "y" else "ies"} for /$wanted.")
        return 1
    }

    private fun clear(source: FabricClientCommandSource): Int {
        SuggestModel.clear()
        // Explicit rather than incidental: replacing the model object already makes Weights re-read and find
        // nothing, but a wipe that left a trained blend behind would be a wipe in name only.
        Weights.reset()
        ModelStore.saveNow()
        Commands.feedback(source, "Forgot every learned command. Your settings are untouched.")
        return 1
    }

    private fun setLearning(source: FabricClientCommandSource, learning: Boolean): Int {
        SuggestConfig.settings.learning = learning
        SuggestConfig.save()
        Commands.feedback(
            source,
            if (learning) "Learning resumed." else "Learning paused — suggestions still work, nothing new is recorded.",
        )
        return 1
    }

    override fun settingsCategory(): ConfigCategory = ConfigCategory.build("suggest") {
        toggle(
            "enabled",
            default = true,
            get = { SuggestConfig.active },
            set = { SuggestConfig.settings.enabled = it; SuggestConfig.save() },
        )
        toggle(
            "learning",
            default = true,
            get = { SuggestConfig.learning },
            set = { SuggestConfig.settings.learning = it; SuggestConfig.save() },
        )

        action("dashboard") { screen -> Minecraft.getInstance().setScreen(SuggestScreen(screen)) }

        toggle(
            "popup",
            default = true,
            get = { SuggestConfig.popup },
            set = { SuggestConfig.settings.popup = it; SuggestConfig.save() },
        )
        toggle(
            "ghost_text",
            default = true,
            get = { SuggestConfig.ghostText },
            set = { SuggestConfig.settings.ghostText = it; SuggestConfig.save() },
        )
        toggle(
            "next_command",
            default = true,
            get = { SuggestConfig.nextCommand },
            set = { SuggestConfig.settings.nextCommand = it; SuggestConfig.save() },
        )

        slider(
            "rows",
            min = SuggestConfig.ROWS_MIN.toDouble(),
            max = SuggestConfig.ROWS_MAX.toDouble(),
            step = 1.0,
            default = SuggestConfig.DEFAULT_ROWS.toDouble(),
            get = { SuggestConfig.settings.rows.toDouble() },
            set = { SuggestConfig.settings.rows = it.toInt(); SuggestConfig.markDirty() },
            format = { it.toInt().toString() },
        )
        slider(
            "confidence",
            min = 0.0,
            max = 1.0,
            step = 0.05,
            default = SuggestConfig.DEFAULT_CONFIDENCE,
            get = { SuggestConfig.settings.confidence },
            set = { SuggestConfig.settings.confidence = it; SuggestConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.0f%%", it * 100) },
        )

        enum(
            "adaptation",
            default = Adaptation.NORMAL,
            get = { SuggestConfig.settings.adaptation },
            set = { SuggestConfig.settings.adaptation = it; SuggestConfig.save() },
        )
        enum(
            "accept_key",
            default = AcceptKey.BOTH,
            get = { SuggestConfig.settings.acceptKey },
            set = { SuggestConfig.settings.acceptKey = it; SuggestConfig.save() },
        )

        toggle(
            "catalogue_priors",
            default = true,
            get = { SuggestConfig.cataloguePriors },
            set = { SuggestConfig.settings.cataloguePriors = it; SuggestConfig.save() },
        )
        toggle(
            "learn_player_names",
            default = true,
            get = { SuggestConfig.learnPlayerNames },
            set = { SuggestConfig.settings.learnPlayerNames = it; SuggestConfig.save() },
        )

        // Separate from the tab's reset button, and necessarily so: the reset below restores these settings,
        // which live in a config profile, while this wipes the learned model, which deliberately does not.
        // One button doing both would mean switching profiles could silently erase what has been learned.
        action("forget_all") { screen -> Minecraft.getInstance().setScreen(wipePrompt(screen)) }

        resetsTo(SuggestConfig.handle)
    }

    /** The "forget everything" confirmation, shared by the settings tab and the dashboard's own button. */
    fun wipePrompt(parent: Screen?): Screen = ConfirmActionScreen(
        parent,
        Component.translatable("hex.suggest.forget_all"),
        Component.translatable("hex.suggest.forget_all.confirm"),
        Component.translatable("hex.suggest.forget_all.detail"),
        listOf(
            ConfirmActionScreen.Choice(Component.translatable("hex.suggest.forget_all.yes")) {
                SuggestModel.clear()
                Weights.reset()
                ModelStore.saveNow()
            },
            ConfirmActionScreen.Choice(Component.translatable("hex.suggest.forget_all.no"), null),
        ),
    )

    /** Chat wraps rather than truncates, and a full context snapshot is longer than anyone wants to read. */
    private const val MAX_LINE = 220

    /** How many context features the `why` command names before it stops being an explanation. */
    private const val TOP_CONTEXT = 4
}
