package net.trilleo.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.trilleo.util.Notify
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/** Which profile is active and which ones exist, persisted at `config/hex/profiles.json`. */
data class ProfileSettings(
    var active: String = DEFAULT_NAME,
    var known: MutableList<String> = mutableListOf(DEFAULT_NAME),
) {
    companion object {
        const val DEFAULT_NAME: String = "default"
    }
}

/**
 * Named snapshots of every registered config, so a whole setup can be swapped in one click, and a
 * clipboard format for handing that setup to someone else.
 *
 * The live files stay exactly where they have always been — `config/hex/<name>.json`. A profile is a *copy*
 * under `config/hex/profiles/<profile>/`, never a redirected config root. That is deliberate: it means
 * nothing about the on-disk layout changes for someone who never opens this tab, and an existing install
 * keeps loading byte-identically.
 *
 * Switching captures the profile being left before restoring the one being entered, so edits are never
 * silently dropped. A profile that has no file for some config leaves that config alone rather than
 * resetting it, so a partial profile inherits the rest.
 */
object ConfigProfiles {
    private val LOGGER = LoggerFactory.getLogger("hex/config")

    /** Bumped only if the clipboard shape changes incompatibly; import tolerates anything it recognises. */
    private const val FORMAT_VERSION = 1

    private val store = JsonConfig(
        name = "profiles",
        type = object : TypeToken<ProfileSettings>() {}.type,
        default = { ProfileSettings() },
        normalizer = ::normalize,
    )

    var settings: ProfileSettings = ProfileSettings()
        private set

    /**
     * Repairs GSON's reflection gaps and keeps the bookkeeping self-consistent: the active profile must
     * always be one of the known ones, or the tab would show a selection that cannot be re-selected.
     */
    private fun normalize(value: ProfileSettings) {
        @Suppress("SENSELESS_COMPARISON")
        if (value.known == null) value.known = mutableListOf()
        @Suppress("SENSELESS_COMPARISON")
        if (value.active == null) value.active = ProfileSettings.DEFAULT_NAME
        if (value.known.isEmpty()) value.known.add(ProfileSettings.DEFAULT_NAME)
        if (value.active !in value.known) value.active = value.known.first()
    }

    fun load() {
        settings = store.load()
    }

    /**
     * Guarantees the active profile has a snapshot on disk, seeding it from the current live configs if not.
     *
     * Must run *after* the features have initialised, because that is when their configs register with
     * [ConfigRegistry] — seeding any earlier would snapshot an empty registry.
     *
     * Without this the implicit first-run profile never gets a directory, and since [switchTo] deliberately
     * lets a profile inherit any config it has no file for, switching *to* it would restore nothing at all
     * and silently keep the settings being switched away from.
     */
    fun ensureActiveSnapshot() {
        runCatching {
            val dir = profileDir(settings.active)
            if (Files.isDirectory(dir) && Files.list(dir).use { it.findAny().isPresent }) return@runCatching
            Files.createDirectories(dir)
            ConfigRegistry.snapshotTo(dir)
            LOGGER.info("Seeded profile '{}' from the current settings", settings.active)
        }.onFailure { LOGGER.error("Failed to seed profile '{}'", settings.active, it) }
    }

    private fun save() = store.save(settings)

    /** `config/hex/profiles`. */
    private fun profilesRoot(): Path =
        FabricLoader.getInstance().configDir.resolve("hex").resolve("profiles")

    /** The directory holding [name]'s snapshot. */
    fun profileDir(name: String): Path = profilesRoot().resolve(name)

    /** Profile names, in creation order, always including the active one. */
    fun names(): List<String> = settings.known.toList()

    /** Index of the active profile within [names]. */
    fun activeIndex(): Int = names().indexOf(settings.active).coerceAtLeast(0)

    /**
     * Normalises a user-typed profile name into something safe to use as a directory: lowercase, with
     * anything that is not alphanumeric, `-` or `_` collapsed to `_`. Returns null when nothing usable is
     * left, which is what the "create" action treats as a rejected name.
     */
    fun sanitize(raw: String): String? {
        val cleaned = raw.trim().lowercase(Locale.ROOT).map { ch ->
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '_'
        }.joinToString("").trim('_')
        return cleaned.ifEmpty { null }
    }

    /** Writes the live configs into [name]'s snapshot, creating the profile if it is new. */
    fun saveTo(name: String) {
        runCatching {
            ConfigRegistry.flushAll()
            val dir = profileDir(name)
            Files.createDirectories(dir)
            ConfigRegistry.snapshotTo(dir)
            if (name !in settings.known) settings.known.add(name)
            settings.active = name
            save()
        }.onFailure { LOGGER.error("Failed to save profile '{}'", name, it) }
    }

    /**
     * Captures the current profile, then adopts [name]'s snapshot into the live configs and files.
     *
     * Returns false if the profile is unknown. After this the open config screen must be rebuilt: its rows
     * captured their values when they were constructed and would otherwise write stale ones back on Save.
     */
    fun switchTo(name: String): Boolean {
        if (name !in settings.known) return false
        if (name == settings.active) return true
        return runCatching {
            // Capture what we are leaving before overwriting anything.
            ConfigRegistry.flushAll()
            val leaving = profileDir(settings.active)
            Files.createDirectories(leaving)
            ConfigRegistry.snapshotTo(leaving)

            val restored = ConfigRegistry.restoreFrom(profileDir(name))
            if (restored == 0) {
                // Every known profile is seeded on startup and on creation, so an empty one means its
                // directory was removed behind our back. Inheriting silently here would look like the
                // switch did nothing, so say so.
                LOGGER.warn("Profile '{}' has no saved settings; keeping the current ones", name)
            }
            // Restoring adopts values into memory; write them through so the live files match.
            ConfigRegistry.all().forEach { it.markDirty() }
            ConfigRegistry.flushAll()

            settings.active = name
            save()
            true
        }.onFailure { LOGGER.error("Failed to switch to profile '{}'", name, it) }.getOrDefault(false)
    }

    /** Creates [name] from the current live configs. Returns false if it already exists. */
    fun create(name: String): Boolean {
        if (name in settings.known) return false
        saveTo(name)
        return true
    }

    /**
     * Deletes [name] and its snapshot, falling back to another profile if it was active. The last remaining
     * profile cannot be deleted — there would be nothing to fall back to.
     */
    fun delete(name: String): Boolean {
        if (name !in settings.known || settings.known.size <= 1) return false
        return runCatching {
            profileDir(name).toFile().deleteRecursively()
            settings.known.remove(name)
            if (settings.active == name) {
                settings.active = settings.known.first()
                ConfigRegistry.restoreFrom(profileDir(settings.active))
                ConfigRegistry.all().forEach { it.markDirty() }
                ConfigRegistry.flushAll()
            }
            save()
            true
        }.onFailure { LOGGER.error("Failed to delete profile '{}'", name, it) }.getOrDefault(false)
    }

    // ---- clipboard transfer --------------------------------------------------------------------------

    /**
     * Every registered config as one JSON blob:
     * `{ "hex": 1, "modVersion": "…", "configs": { "hand": {…}, "keybinds": [ … ] } }`
     */
    fun exportToString(): String {
        val configs = JsonObject()
        ConfigRegistry.all().forEach { configs.add(it.name, it.exportTree()) }
        val root = JsonObject()
        root.addProperty("hex", FORMAT_VERSION)
        root.addProperty("modVersion", modVersion())
        root.add("configs", configs)
        return JsonConfig.GSON.toJson(root)
    }

    /**
     * Adopts a blob produced by [exportToString], returning how many configs it applied, or null when the
     * text is not a Hex export at all.
     *
     * Unknown keys are ignored and absent ones left untouched, so a blob from a newer version still applies
     * whatever it does share. Nothing here is allowed to throw: this runs from a GUI click, and the input is
     * whatever happened to be on the clipboard.
     */
    fun importFromString(text: String): Int? = runCatching {
        val root = JsonParser.parseString(text) as? JsonObject ?: return@runCatching null
        if (!root.has("hex")) return@runCatching null
        val configs = root.getAsJsonObject("configs") ?: return@runCatching null

        var applied = 0
        for ((name, element) in configs.entrySet()) {
            val handle = ConfigRegistry.byName(name)
            if (handle == null) {
                LOGGER.warn("Ignoring unknown config '{}' in imported settings", name)
                continue
            }
            if (handle.importTree(element)) applied++ else LOGGER.warn("Could not apply imported '{}'", name)
        }
        ConfigRegistry.flushAll()
        applied
    }.onFailure { LOGGER.error("Failed to import settings from clipboard", it) }.getOrNull()

    private fun modVersion(): String = FabricLoader.getInstance().getModContainer("hex")
        .map { it.metadata.version.friendlyString }
        .orElse("?")

    /** Display name for a profile row; the active one is marked so the selector reads unambiguously. */
    fun displayName(name: String): Component =
        if (name == settings.active) Component.literal("$name *") else Component.literal(name)

    // ---- settings tab --------------------------------------------------------------------------------

    /**
     * The Profiles tab.
     *
     * Not a [net.trilleo.feature.Feature]: it has no lifecycle, no keybind and no commands, and the config
     * menu is already owned centrally alongside `/hexa config`. [ClothConfigFactory] appends it after the
     * feature tabs.
     *
     * Switching and creating are ordinary settings rows, so they commit on Save like everything else.
     * Exporting, importing and deleting are buttons, because they are commands rather than values — they
     * take effect the moment they are pressed.
     */
    fun category(): ConfigCategory = ConfigCategory.build("profiles") {
        val profiles = names()

        cycle(
            label = Component.translatable("hex.config.profiles.active"),
            tooltip = Component.translatable("hex.config.profiles.active.tooltip"),
            options = profiles.map { displayName(it) },
            default = activeIndex(),
            get = { activeIndex() },
            set = { index ->
                profiles.getOrNull(index)?.let { target ->
                    if (switchTo(target)) {
                        notify("Switched to profile '$target'")
                        HexConfigScreens.rebuild()
                    }
                }
            },
        )

        text(
            "new_name",
            default = "",
            get = { "" },
            set = { raw ->
                if (raw.isNotBlank()) {
                    val name = sanitize(raw)
                    when {
                        name == null -> notify("'$raw' is not a usable profile name", error = true)
                        !create(name) -> notify("Profile '$name' already exists", error = true)
                        else -> {
                            notify("Created profile '$name' from the current settings")
                            HexConfigScreens.rebuild()
                        }
                    }
                }
            },
        )

        action("save_current") {
            saveTo(settings.active)
            notify("Saved current settings into profile '${settings.active}'")
        }

        action("delete_current") {
            val target = settings.active
            if (delete(target)) {
                notify("Deleted profile '$target'")
                HexConfigScreens.rebuild()
            } else {
                notify("Cannot delete the only remaining profile", error = true)
            }
        }

        action("export") {
            Minecraft.getInstance().keyboardHandler.setClipboard(exportToString())
            notify("Copied all Hex settings to the clipboard")
        }

        action("import") {
            val applied = importFromString(Minecraft.getInstance().keyboardHandler.clipboard)
            if (applied == null) {
                notify("The clipboard does not contain Hex settings", error = true)
            } else {
                notify("Imported $applied config section(s) from the clipboard")
                HexConfigScreens.rebuild()
            }
        }
    }

    private fun notify(text: String, error: Boolean = false) {
        Notify.chat(
            Minecraft.getInstance(),
            text,
            if (error) ChatFormatting.RED else ChatFormatting.AQUA,
        )
    }
}
