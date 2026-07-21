package net.trilleo.update

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.trilleo.command.Commands
import net.trilleo.config.ConfigCategory
import net.trilleo.feature.Feature
import net.trilleo.util.Notify
import org.slf4j.LoggerFactory

/**
 * Auto-update feature. On startup (when enabled in [UpdateConfig]) it checks the `Trilleo/Hex` GitHub
 * releases off-thread; if a newer release exists it downloads and stages the jar and shows a one-time
 * "restart to apply" notice on the next world join. [UpdateStaging] applies the staged jar on shutdown.
 *
 * `/hexa update` runs the same check on demand. In a dev environment (no swappable `mods/` jar) both
 * paths degrade to a notify-only message with the release link.
 */
object UpdateFeature : Feature {
    override val id: String = "update"

    private val LOGGER = LoggerFactory.getLogger("hex/update")

    /** Set by the background check; surfaced once on the next world join. */
    @Volatile
    private var startupNotice: Component? = null
    private var noticeShown = false

    override fun onInit() {
        UpdateConfig.load()
        if (UpdateConfig.settings.enabled) {
            Thread({ runStartupCheck() }, "Hex-UpdateChecker").apply { isDaemon = true }.start()
        }
    }

    override fun onWorldJoin(client: Minecraft) {
        if (noticeShown) return
        val notice = startupNotice ?: return
        noticeShown = true
        Notify.send(client, notice)
    }

    override fun registerCommands(hex: LiteralArgumentBuilder<FabricClientCommandSource>) {
        hex.then(
            Commands.literal("update").executes { _ ->
                checkNow()
                1
            },
        )
    }

    /** Default values for the settings rows; a renderer offers "reset" against these. */
    private val defaults = UpdateSettings()

    override fun settingsCategory(): ConfigCategory = ConfigCategory.build("updates") {
        toggle(
            "enabled",
            default = defaults.enabled,
            get = { UpdateConfig.settings.enabled },
            set = { UpdateConfig.settings.enabled = it; UpdateConfig.save() },
        )
        toggle(
            "include_prereleases",
            default = defaults.includePrereleases,
            get = { UpdateConfig.settings.includePrereleases },
            set = { UpdateConfig.settings.includePrereleases = it; UpdateConfig.save() },
        )
        action("check_now") { checkNow() }
    }

    /**
     * Run an update check on demand, off-thread, reporting every outcome in chat. Shared by `/hexa update`
     * and the "Check for updates now" button in the config menu.
     */
    fun checkNow() {
        val client = Minecraft.getInstance()
        Notify.chat(client, "Checking for updates…", ChatFormatting.GRAY)
        Thread({ runManualCheck(client) }, "Hex-UpdateCommand").apply { isDaemon = true }.start()
    }

    override fun onShutdown() {
        // Settings are flushed centrally by ConfigRegistry; only the jar swap belongs here. UpdateStaging
        // deliberately stays outside the registry — a half-downloaded update is machine state, not a setting.
        UpdateStaging.applyOnShutdown()
    }

    /** Background startup path: stage silently, then stash a notice for the next world join. */
    private fun runStartupCheck() {
        when (val status = UpdateChecker.check(UpdateConfig.settings.includePrereleases)) {
            is UpdateStatus.Available -> startupNotice = stageAndDescribe(status)
            is UpdateStatus.Failed -> LOGGER.info("Update check skipped: {}", status.reason)
            UpdateStatus.UpToDate -> {}
        }
    }

    /** `/hexa update` path: report every outcome, including up-to-date and failures. */
    private fun runManualCheck(client: Minecraft) {
        when (val status = UpdateChecker.check(UpdateConfig.settings.includePrereleases)) {
            is UpdateStatus.Available -> Notify.send(client, stageAndDescribe(status))
            is UpdateStatus.Failed ->
                Notify.chat(client, "Update check failed: ${status.reason}", ChatFormatting.RED)

            UpdateStatus.UpToDate ->
                Notify.chat(client, "Hex is up to date (v${UpdateChecker.currentVersion()}).", ChatFormatting.GREEN)
        }
    }

    /**
     * Download + stage an available update if it is not already staged, and return the line to show. Shared
     * by the startup and manual paths so both behave identically. Never throws — any failure degrades to
     * the notify-only link.
     */
    private fun stageAndDescribe(status: UpdateStatus.Available): Component {
        val version = status.version
        if (UpdateStaging.hasPendingFor(version)) {
            return Notify.line("Hex v$version is downloaded — restart to apply.", ChatFormatting.AQUA)
        }

        val oldJar = UpdateStaging.currentJar()
            ?: return notifyOnly(status) // dev env: nothing to swap
        val asset = UpdateDownloader.selectAsset(status.release)
            ?: return notifyOnly(status)
        val staged = UpdateDownloader.download(asset)
            ?: return notifyOnly(status)

        UpdateStaging.markPending(version, staged, oldJar)
        return Notify.line("Hex v$version downloaded — restart to apply.", ChatFormatting.AQUA)
    }

    private fun notifyOnly(status: UpdateStatus.Available): Component {
        val url = status.release.htmlUrl ?: "https://github.com/Trilleo/Hex/releases"
        return Notify.line("Hex v${status.version} is available: $url", ChatFormatting.AQUA)
    }

}
