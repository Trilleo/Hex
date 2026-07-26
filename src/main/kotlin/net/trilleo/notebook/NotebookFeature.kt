package net.trilleo.notebook

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.trilleo.Hex
import net.trilleo.command.Commands
import net.trilleo.config.ConfigCategory
import net.trilleo.feature.Feature
import net.trilleo.notebook.gui.NoteEditorScreen
import net.trilleo.notebook.gui.NotebookScreen
import net.trilleo.notebook.md.NoteSearch
import net.trilleo.notebook.model.NoteDocument
import net.trilleo.notebook.model.NoteEditorView
import net.trilleo.notebook.model.NoteSort
import java.util.*

/**
 * Notes, written in markdown and kept between sessions.
 *
 * Like [net.trilleo.reminder.ReminderFeature], this leaves [enabled] at `true` and gates on
 * [NotebookConfig.active] instead: [net.trilleo.feature.Features.categories] hides a disabled feature's tab,
 * so wiring the master switch to [enabled] would make it impossible to switch back on from the menu.
 *
 * The master switch turns off the *keybind and the commands*, not the notes. Nothing is deleted and nothing is
 * hidden on disk — a player who switches the notebook off and back on a month later finds everything where
 * they left it.
 */
object NotebookFeature : Feature {
    override val id: String = "notebook"

    private lateinit var openKey: KeyMapping

    override fun onInit() {
        NotebookConfig.load()
        Notebook.load()

        openKey = KeyMapping("key.hex.notebook.open", InputConstants.UNKNOWN.value, Hex.KEY_CATEGORY)
            .also { KeyMappingHelper.registerKeyMapping(it) }
    }

    override fun onClientTick(client: Minecraft) {
        // The debounce runs whether or not the feature is switched on: turning the notebook off must not
        // strand an unwritten edit made a second earlier.
        NotebookStore.tick()

        if (!NotebookConfig.active) return
        while (openKey.consumeClick()) {
            if (client.screen == null) client.setScreen(NotebookScreen(null))
        }
    }

    override fun onWorldLeave(client: Minecraft) {
        // Leaving a world is as close as this gets to closing the book; the game may not be quit for hours.
        NotebookStore.flush()
    }

    override fun onShutdown() {
        NotebookStore.shutdown()
    }

    override fun registerCommands(hex: LiteralArgumentBuilder<FabricClientCommandSource>) {
        hex.then(
            Commands.literal("note")
                // A bare `/hexa note` states its subcommands rather than guessing at one of them.
                .executes { ctx ->
                    Commands.feedback(ctx.source, "/hexa note list — every note you have")
                    Commands.feedback(ctx.source, "/hexa note new <title> — start one")
                    Commands.feedback(ctx.source, "/hexa note open <title> — edit one")
                    Commands.feedback(ctx.source, "/hexa note search <text> — find one")
                    Commands.feedback(ctx.source, "/hexa note export <title> — copy it to the clipboard")
                    Commands.feedback(ctx.source, "/hexa note import — paste one in")
                    1
                }
                // Deferred: opening a screen mid-command is undone when the chat screen that ran it closes.
                .then(
                    Commands.literal("open").then(
                        Commands.argument("title", StringArgumentType.greedyString()).executes { ctx ->
                            val title = StringArgumentType.getString(ctx, "title")
                            val document = Notebook.byTitle(title)
                            if (document == null) {
                                Commands.error(ctx.source, "No note called \"$title\".")
                                0
                            } else {
                                openScreen(ctx.source) { NoteEditorScreen(null, document) }
                            }
                        },
                    )
                )
                .then(
                    Commands.literal("new")
                    .executes { ctx -> newNote(ctx.source, "") }
                    .then(
                        Commands.argument("title", StringArgumentType.greedyString()).executes { ctx ->
                            newNote(ctx.source, StringArgumentType.getString(ctx, "title"))
                        },
                    )
                )
                .then(Commands.literal("list").executes { ctx -> list(ctx.source) })
                .then(
                    Commands.literal("search").then(
                        Commands.argument("query", StringArgumentType.greedyString()).executes { ctx ->
                            search(ctx.source, StringArgumentType.getString(ctx, "query"))
                        },
                    )
                )
                .then(
                    Commands.literal("export").then(
                        Commands.argument("title", StringArgumentType.greedyString()).executes { ctx ->
                            export(ctx.source, StringArgumentType.getString(ctx, "title"))
                        },
                    )
                )
                .then(Commands.literal("import").executes { ctx -> import(ctx.source) }),
        )
    }

    private fun openScreen(source: FabricClientCommandSource, screen: () -> Screen): Int {
        val client = source.client
        client.execute { client.setScreen(screen()) }
        return 1
    }

    private fun newNote(source: FabricClientCommandSource, title: String): Int {
        val document = Notebook.create(title)
        return openScreen(source) { NoteEditorScreen(null, document) }
    }

    private fun list(source: FabricClientCommandSource): Int {
        val documents = Notebook.sorted(NotebookConfig.sort)
        if (documents.isEmpty()) {
            Commands.feedback(source, "No notes yet — start one with /hexa note new <title>.")
            return 1
        }
        documents.forEach { Commands.feedback(source, describe(it)) }
        return 1
    }

    private fun search(source: FabricClientCommandSource, query: String): Int {
        val hits = NoteSearch.search(Notebook.all(), query)
        if (hits.isEmpty()) {
            Commands.feedback(source, "Nothing matches \"$query\".")
            return 1
        }
        hits.forEach { hit ->
            Commands.feedback(source, "${describe(hit.document)} — ${hit.snippet}")
        }
        return 1
    }

    private fun export(source: FabricClientCommandSource, title: String): Int {
        val document = Notebook.byTitle(title)
        if (document == null) {
            Commands.error(source, "No note called \"$title\".")
            return 0
        }
        val client = source.client
        client.execute { client.keyboardHandler.setClipboard(NoteShare.export(document)) }
        Commands.feedback(source, "Copied \"${document.meta.title}\" to the clipboard.")
        return 1
    }

    private fun import(source: FabricClientCommandSource): Int {
        val client = source.client
        // The clipboard is read on the client thread, where the keyboard handler lives.
        client.execute {
            when (val result = NoteShare.import(client.keyboardHandler.clipboard)) {
                is NoteShare.ImportResult.Added ->
                    Commands.feedback(source, "Added \"${result.document.meta.title}\".")

                is NoteShare.ImportResult.TooNew ->
                    Commands.error(source, "That note was written by a newer Hex (format ${result.version}).")

                NoteShare.ImportResult.NotANote ->
                    Commands.error(source, "The clipboard is empty.")
            }
        }
        return 1
    }

    /** One line for a note in chat: its title, its folder, and whether it is pinned. */
    private fun describe(document: NoteDocument): String {
        val pin = if (document.meta.pinned) "★ " else ""
        val folder = document.meta.folder.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()
        return "$pin${document.meta.title}$folder"
    }

    override fun settingsCategory(): ConfigCategory = ConfigCategory.build("notebook") {
        toggle(
            "enabled",
            default = true,
            get = { NotebookConfig.active },
            set = { NotebookConfig.settings.enabled = it; NotebookConfig.save() },
        )

        action("open") { screen -> Minecraft.getInstance().setScreen(NotebookScreen(screen)) }

        enum(
            "sort",
            default = NoteSort.MODIFIED,
            get = { NotebookConfig.settings.sort },
            set = { NotebookConfig.settings.sort = it; NotebookConfig.save() },
        )
        enum(
            "editor_view",
            default = NoteEditorView.SPLIT,
            get = { NotebookConfig.editorView },
            set = { NotebookConfig.settings.editorView = it; NotebookConfig.save() },
        )
        toggle(
            "show_snippets",
            default = true,
            get = { NotebookConfig.settings.showSnippets },
            set = { NotebookConfig.settings.showSnippets = it; NotebookConfig.save() },
        )
        // markDirty, not save: a slider's setter fires every frame of a drag, and the panels are read per
        // frame, so dragging the handle with the notebook open behind the menu fades it live.
        slider(
            "background_opacity",
            min = NotebookConfig.OPACITY_MIN,
            max = NotebookConfig.OPACITY_MAX,
            step = NotebookConfig.OPACITY_STEP,
            default = NotebookConfig.OPACITY_DEFAULT,
            get = { NotebookConfig.backgroundOpacity },
            set = { NotebookConfig.settings.backgroundOpacity = it; NotebookConfig.markDirty() },
            format = { String.format(Locale.ROOT, "%.0f%%", it * 100) },
        )

        resetsTo(NotebookConfig.handle)
    }
}
