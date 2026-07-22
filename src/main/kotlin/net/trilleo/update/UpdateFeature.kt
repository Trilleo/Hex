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

    /** Set by the background check; surfaced once the player exists, then cleared. */
    @Volatile
    private var startupNotice: Component? = null

    override fun onInit() {
        UpdateConfig.load()
        if (!UpdateConfig.settings.enabled) {
            LOGGER.info("Startup update check is off; enable it under /hexa config → Updates")
            return
        }
        Thread({ runStartupCheck() }, "Hex-UpdateChecker").apply { isDaemon = true }.start()
    }

    /**
     * Hands the startup check's result to the player as soon as there is one to hand it to.
     *
     * Delivered from the tick rather than from the world-join event, because the check is asynchronous and
     * its result routinely lands *after* that event has fired — a slow release API, or a jar download over a
     * thin connection, both outlast a join. Joining fires once per connection, so a notice that arrived a
     * moment too late used to be dropped for the rest of the session, which made a check that had run and
     * staged an update look like a feature that had stopped working.
     */
    override fun onClientTick(client: Minecraft) {
        val notice = startupNotice ?: return
        if (client.player == null) return
        startupNotice = null
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
        resetsTo(UpdateConfig.handle)
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

    /**
     * Background startup path: stage silently, then stash a notice for [onClientTick] to deliver.
     *
     * Every outcome is logged. Nothing here reaches the player until they are in a world, so without the log
     * there is no way to tell a check that found nothing from one that never ran at all.
     */
    private fun runStartupCheck() {
        LOGGER.info("Checking for updates (current v{})", UpdateChecker.currentVersion())
        when (val status = UpdateChecker.check(UpdateConfig.settings.includePrereleases)) {
            is UpdateStatus.Available -> {
                LOGGER.info("Hex v{} is available", status.version)
                startupNotice = stageAndDescribe(status)
            }

            is UpdateStatus.Failed -> LOGGER.info("Update check skipped: {}", status.reason)
            UpdateStatus.UpToDate -> LOGGER.info("Hex is up to date")
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
            ?: return notifyOnly(status, "no swappable mods/ jar") // dev env: nothing to swap
        val asset = UpdateDownloader.selectAsset(status.release)
            ?: return notifyOnly(status, "the release has no mod jar attached")
        val staged = UpdateDownloader.download(asset)
            ?: return notifyOnly(status, "the download did not complete")

        UpdateStaging.markPending(version, staged, oldJar)
        LOGGER.info("Staged Hex v{} at {}; it is applied on exit", version, staged)
        return Notify.line("Hex v$version downloaded — restart to apply.", ChatFormatting.AQUA)
    }

    /** The fallback line, plus why staging was skipped — the one thing a bug report needs and never has. */
    private fun notifyOnly(status: UpdateStatus.Available, reason: String): Component {
        LOGGER.info("Not staging Hex v{}: {}", status.version, reason)
        val url = status.release.htmlUrl ?: "https://github.com/Trilleo/Hex/releases"
        return Notify.line("Hex v${status.version} is available: $url", ChatFormatting.AQUA)
    }

}
