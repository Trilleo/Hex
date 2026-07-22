package net.trilleo.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.trilleo.util.Notify
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/** What a profile activates on by itself, or null when it is only ever switched to by hand. */
data class AutoSwitchRule(
    var kind: AutoSwitchKind = AutoSwitchKind.SERVER,
    /** Server host for [AutoSwitchKind.SERVER], island name for [AutoSwitchKind.SKYBLOCK_ISLAND], else unused. */
    var pattern: String = "",
)

/** The kinds of context a profile can bind itself to. */
enum class AutoSwitchKind { SERVER, SINGLEPLAYER, SKYBLOCK_ISLAND }

/** One profile's bookkeeping. The settings themselves live in `config/hex/profiles/<name>/`. */
data class ProfileEntry(
    var name: String = "",
    var description: String = "",
    /** Epoch millis, or 0 when unknown — profiles migrated from the old format have no recorded date. */
    var createdAt: Long = 0L,
    var modifiedAt: Long = 0L,
    var autoSwitch: AutoSwitchRule? = null,
)

/**
 * Which profile is active and which ones exist, persisted at `config/hex/profiles.json`.
 *
 * [known] is the pre-metadata shape and is only still here to be migrated: the normalizer copies it into
 * [profiles] once and then empties it, so the next save writes only the current shape.
 */
data class ProfileSettings(
    var active: String = DEFAULT_NAME,
    var known: MutableList<String> = mutableListOf(),
    var profiles: MutableList<ProfileEntry> = mutableListOf(),
    /**
     * Whether profiles also capture Minecraft's own key bindings. Global rather than per-profile: it is a
     * choice about how the profile system behaves, and swapping it with the profile would make switching
     * unpredictable. Off by default — see [net.trilleo.config.VanillaKeysConfig] for why.
     */
    var captureVanillaKeys: Boolean = false,
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
     * Repairs GSON's reflection gaps, migrates the pre-metadata shape, and keeps the bookkeeping
     * self-consistent: the active profile must always be one that exists, or the manager would show a
     * selection that cannot be re-selected.
     *
     * The migration adds a field rather than changing one, and that is deliberate. [JsonConfig.loadFrom]
     * swallows every exception and falls back to a fresh default, so had `known` been redeclared from
     * `List<String>` to `List<ProfileEntry>`, GSON would have thrown on every existing install, the fallback
     * would have quietly handed back an empty list, and every profile name would have vanished while its
     * directory sat orphaned on disk. An absent `profiles` key simply arrives null and is repaired here.
     *
     * Clearing [ProfileSettings.known] afterwards is what stops the migration re-firing, the same way
     * [net.trilleo.keybind.KeybindConfig] retires its legacy command fields once converted.
     */
    private fun normalize(value: ProfileSettings) {
        @Suppress("SENSELESS_COMPARISON")
        if (value.known == null) value.known = mutableListOf()
        @Suppress("SENSELESS_COMPARISON")
        if (value.profiles == null) value.profiles = mutableListOf()
        @Suppress("SENSELESS_COMPARISON")
        if (value.active == null) value.active = ProfileSettings.DEFAULT_NAME

        // Migrate the old name-only list. Timestamps stay 0: a fabricated creation date would read as fact.
        if (value.profiles.isEmpty() && value.known.isNotEmpty()) {
            value.known.forEach { name -> value.profiles.add(ProfileEntry(name = name)) }
            LOGGER.info("Migrated {} profile(s) to the metadata format", value.profiles.size)
        }
        value.known = mutableListOf()

        value.profiles.forEach { entry ->
            @Suppress("SENSELESS_COMPARISON")
            if (entry.name == null) entry.name = ""
            @Suppress("SENSELESS_COMPARISON")
            if (entry.description == null) entry.description = ""
            // GSON maps an unrecognised enum constant to null, so a rule written by a newer Hex degrades to
            // "no rule" here instead of throwing the whole file away. Kotlin cannot see that these fields
            // are nullable in practice, hence the suppression — reflection does not honour the declared type.
            val rule = entry.autoSwitch
            @Suppress("SENSELESS_COMPARISON")
            if (rule != null && (rule.kind == null || rule.pattern == null)) entry.autoSwitch = null
        }
        value.profiles.removeAll { it.name.isBlank() }

        if (value.profiles.isEmpty()) value.profiles.add(ProfileEntry(name = ProfileSettings.DEFAULT_NAME))
        if (value.profiles.none { it.name == value.active }) value.active = value.profiles.first().name
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
        // Establish the baseline once the snapshot is guaranteed to exist. Note this is not unconditionally
        // "clean": if the live files were hand-edited since the last save, the tracker reports that honestly.
        ProfileDirtyTracker.refresh()
    }

    private fun save() = store.save(settings)

    /**
     * Persists the bookkeeping after a caller edits a [ProfileEntry] in place.
     *
     * The manager screen mutates descriptions and auto-switch rules directly on the entry it was handed,
     * which is simpler than routing every field through its own method — but the file still has to be
     * written, and this is that write.
     */
    fun saveSettings() = save()

    /** `config/hex/profiles`. */
    private fun profilesRoot(): Path =
        FabricLoader.getInstance().configDir.resolve("hex").resolve("profiles")

    /** The directory holding [name]'s snapshot. */
    fun profileDir(name: String): Path = profilesRoot().resolve(name)

    /** Profile names, in creation order, always including the active one. */
    fun names(): List<String> = settings.profiles.map { it.name }

    /** Every profile's bookkeeping, in creation order. Auto-switch resolves ties by taking the first match. */
    fun entries(): List<ProfileEntry> = settings.profiles.toList()

    /** [name]'s bookkeeping, or null if there is no such profile. */
    fun entryFor(name: String): ProfileEntry? = settings.profiles.firstOrNull { it.name == name }

    /** The active profile's bookkeeping. Never null — the normalizer guarantees the active profile exists. */
    fun activeEntry(): ProfileEntry = entryFor(settings.active) ?: settings.profiles.first()

    /** Whether [name] is an existing profile. */
    fun exists(name: String): Boolean = settings.profiles.any { it.name == name }

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

    /**
     * Writes the live configs into [name]'s snapshot, creating the profile if it is new.
     *
     * Saving does not change which profile is active — that is a separate decision the caller makes, so that
     * "save my current settings" cannot silently move you somewhere else.
     */
    fun saveTo(name: String) {
        runCatching {
            ConfigRegistry.flushAll()
            val dir = profileDir(name)
            Files.createDirectories(dir)
            ConfigRegistry.snapshotTo(dir)
            val now = System.currentTimeMillis()
            val entry = entryFor(name)
            if (entry == null) {
                settings.profiles.add(ProfileEntry(name = name, createdAt = now, modifiedAt = now))
            } else {
                entry.modifiedAt = now
            }
            save()
            if (name == settings.active) ProfileDirtyTracker.markClean()
        }.onFailure { LOGGER.error("Failed to save profile '{}'", name, it) }
    }

    /**
     * Adopts [name]'s snapshot into the live configs and files, discarding anything unsaved.
     *
     * Returns false if the profile is unknown. After this the open config screen must be rebuilt: its rows
     * captured their values when they were constructed and would otherwise write stale ones back on Save.
     *
     * This deliberately does *not* capture the profile being left. Hex used to snapshot it automatically,
     * which under the explicit-save model would save the very edits a user had just chosen to discard.
     * Keeping unsaved work is now the caller's decision — see [saveTo] and [discardChanges], which the
     * manager screen offers as a prompt before it gets here.
     */
    fun switchTo(name: String): Boolean {
        if (!exists(name)) return false
        if (name == settings.active) return true
        return runCatching {
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
            ProfileDirtyTracker.markClean()
            true
        }.onFailure { LOGGER.error("Failed to switch to profile '{}'", name, it) }.getOrDefault(false)
    }

    /**
     * Throws away unsaved edits by re-reading the active profile's snapshot.
     *
     * Returns false when the snapshot holds nothing to restore, which means its directory was removed behind
     * our back — the live settings are then left exactly as they are rather than being reset to stock.
     */
    fun discardChanges(): Boolean = runCatching {
        val restored = ConfigRegistry.restoreFrom(profileDir(settings.active))
        if (restored == 0) {
            LOGGER.warn("Profile '{}' has no saved settings to restore", settings.active)
            return@runCatching false
        }
        // Restoring adopts values into memory; write them through so the live files match.
        ConfigRegistry.all().forEach { it.markDirty() }
        ConfigRegistry.flushAll()
        ProfileDirtyTracker.markClean()
        true
    }.onFailure { LOGGER.error("Failed to discard changes to '{}'", settings.active, it) }.getOrDefault(false)

    /**
     * Creates [name] from the current live configs and makes it active. Returns false if it already exists.
     *
     * Adopting the new profile is safe without capturing the one being left: the two hold identical settings
     * at this instant, since the new one was just snapshotted from the live values.
     */
    fun create(name: String, description: String = ""): Boolean {
        if (exists(name)) return false
        saveTo(name)
        entryFor(name)?.description = description
        settings.active = name
        save()
        return true
    }

    /**
     * Copies [source]'s snapshot to a new profile [target], leaving the active profile alone.
     *
     * The snapshot directory is copied rather than re-captured from the live configs, because [source] is
     * usually *not* the active profile and its settings therefore are not the live ones.
     */
    fun duplicate(source: String, target: String): Boolean {
        if (!exists(source) || exists(target)) return false
        return runCatching {
            // Duplicating the active profile has to include anything unsaved, or the copy would silently be
            // of the last save rather than of what the user is looking at.
            if (source == settings.active) ConfigRegistry.flushAll()
            val from = profileDir(source)
            val to = profileDir(target)
            Files.createDirectories(to)
            if (source == settings.active) ConfigRegistry.snapshotTo(to) else from.toFile()
                .copyRecursively(to.toFile(), overwrite = true)

            val now = System.currentTimeMillis()
            settings.profiles.add(
                ProfileEntry(
                    name = target,
                    description = entryFor(source)?.description.orEmpty(),
                    createdAt = now,
                    modifiedAt = now,
                    // An auto-switch rule is deliberately not copied: two profiles claiming the same context
                    // would make which one wins depend on list order, which is invisible to the user.
                ),
            )
            save()
            true
        }.onFailure { LOGGER.error("Failed to duplicate '{}' to '{}'", source, target, it) }.getOrDefault(false)
    }

    /** Renames [from] to [to], moving its snapshot. Returns false if [from] is unknown or [to] is taken. */
    fun rename(from: String, to: String): Boolean {
        if (!exists(from) || exists(to)) return false
        return runCatching {
            val source = profileDir(from)
            if (Files.exists(source)) Files.move(source, profileDir(to))
            entryFor(from)?.name = to
            if (settings.active == from) settings.active = to
            save()
            true
        }.onFailure { LOGGER.error("Failed to rename '{}' to '{}'", from, to, it) }.getOrDefault(false)
    }

    /**
     * Deletes [name] and its snapshot, falling back to another profile if it was active. The last remaining
     * profile cannot be deleted — there would be nothing to fall back to.
     */
    fun delete(name: String): Boolean {
        if (!exists(name) || settings.profiles.size <= 1) return false
        return runCatching {
            profileDir(name).toFile().deleteRecursively()
            settings.profiles.removeAll { it.name == name }
            if (settings.active == name) {
                settings.active = settings.profiles.first().name
                ConfigRegistry.restoreFrom(profileDir(settings.active))
                ConfigRegistry.all().forEach { it.markDirty() }
                ConfigRegistry.flushAll()
                ProfileDirtyTracker.markClean()
            }
            save()
            true
        }.onFailure { LOGGER.error("Failed to delete profile '{}'", name, it) }.getOrDefault(false)
    }

    // ---- clipboard transfer --------------------------------------------------------------------------

    /**
     * Every registered config as one JSON blob:
     * `{ "hex": 1, "modVersion": "…", "profile": { … }, "configs": { "hand": {…}, "keybinds": [ … ] } }`
     *
     * The profile's name and description ride along so the recipient can import it as a named profile rather
     * than only over their current one. Its auto-switch rule is left out on purpose — it describes where the
     * *sender* plays, and silently binding someone else's profile to a server would be a surprise.
     */
    fun exportToString(): String {
        val configs = JsonObject()
        ConfigRegistry.all().forEach { configs.add(it.name, it.exportTree()) }

        val active = activeEntry()
        val profile = JsonObject()
        profile.addProperty("name", active.name)
        profile.addProperty("description", active.description)

        val root = JsonObject()
        root.addProperty("hex", FORMAT_VERSION)
        root.addProperty("modVersion", modVersion())
        root.add("profile", profile)
        root.add("configs", configs)
        return JsonConfig.GSON.toJson(root)
    }

    /**
     * The outcome of an import, so the caller can explain what happened rather than just saying "no".
     *
     * A count alone could not distinguish "that was not Hex settings" from "those settings are from a newer
     * Hex than you are running", which are very different things for a user to act on.
     */
    sealed interface ImportResult {
        /** The text was not a Hex export at all — most likely the clipboard held something else entirely. */
        data object NotHex : ImportResult

        /** Written by a Hex whose export format this version cannot read. */
        data class TooNew(val version: Int) : ImportResult

        /** Applied [count] config sections. [fromVersion] is set only when the exporting Hex differed. */
        data class Applied(val count: Int, val fromVersion: String?) : ImportResult
    }

    /**
     * Adopts a blob produced by [exportToString].
     *
     * Unknown keys are ignored and absent ones left untouched, so a blob from a newer version still applies
     * whatever it does share — but a blob whose *format* is newer is refused outright, since its config
     * shapes cannot be trusted to deserialize into this version's classes.
     *
     * Nothing here is allowed to throw: this runs from a GUI click, and the input is whatever happened to be
     * on the clipboard, including something hand-edited into nonsense.
     */
    fun importFromString(text: String): ImportResult = runCatching {
        val root = JsonParser.parseString(text) as? JsonObject ?: return@runCatching ImportResult.NotHex
        val version = root.get("hex")?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asInt }.getOrNull() }
            ?: return@runCatching ImportResult.NotHex
        if (version > FORMAT_VERSION) return@runCatching ImportResult.TooNew(version)
        val configs = root.getAsJsonObject("configs") ?: return@runCatching ImportResult.NotHex

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
        ProfileDirtyTracker.refresh()

        val from = runCatching { root.get("modVersion")?.asString }.getOrNull()
        ImportResult.Applied(applied, from?.takeIf { it != modVersion() })
    }.onFailure { LOGGER.error("Failed to import settings", it) }.getOrDefault(ImportResult.NotHex)

    /** The name recorded in an export, or null when the blob carries none. */
    fun importedProfileName(text: String): String? = runCatching {
        (JsonParser.parseString(text) as? JsonObject)
            ?.getAsJsonObject("profile")?.get("name")?.asString?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun modVersion(): String = FabricLoader.getInstance().getModContainer("hex")
        .map { it.metadata.version.friendlyString }
        .orElse("?")

    /**
     * Says something to the player in chat.
     *
     * Profile actions run from a screen, but their effect is on the whole client rather than on the screen,
     * so the confirmation belongs in chat where it survives the screen closing.
     */
    fun notify(text: String, error: Boolean = false) {
        Notify.chat(
            Minecraft.getInstance(),
            text,
            if (error) ChatFormatting.RED else ChatFormatting.AQUA,
        )
    }
}
