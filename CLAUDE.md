# Hex — Agent Instructions

Hex is a client-side Fabric utility mod for Hypixel Skyblock (Kotlin, Minecraft 26.1.2, Java 25). It must work entirely
client side — never add server-side logic or a `main` entrypoint.

## After every change: keep translations, docs, wiki and changelog in sync

Before finishing any task that changes the mod, do all of the following:

1. **Translate every string** — the mod ships one language file per locale in
   `src/main/resources/assets/hex/lang/`, currently `en_us.json` (the source of truth) and `zh_cn.json`
   (Simplified Chinese). **Every file must carry exactly the same key set, in the same order.** Adding, renaming or
   removing a key in one file means doing it in *all* of them in the same task — a key present in
   `en_us.json` but missing elsewhere renders as the raw key id in game.
    - Any user-visible string goes through a translation key and `Component.translatable`, never
      `Component.literal("some English text")`. `Component.literal` is for values that are not language — player names,
      item IDs, numbers, symbols like `✎`.
    - Keep `%s` and `%d` placeholders, their count and their order identical across files.
    - Leave untranslated only what is genuinely not language: the mod name, item IDs (`HYPERION`), key names (`Tab`),
      and text matched against Hypixel's English scoreboard (`private island`, `dwarven mines`).
    - See [docs/TRANSLATIONS.md](docs/TRANSLATIONS.md) for the full rules and the parity check.

2. **Update the changelog** — add an entry for the change under `## Unreleased` in [CHANGELOG.md](CHANGELOG.md).
    - Follow the SkyHanni-style format documented in [docs/RELEASING.md](docs/RELEASING.md): category (`### New
     Features` / `### Improvements` / `### Fixes` / `### Technical Details` / `### Removed Features`), then a
      `#### Feature Area` heading, then `+` bullets.
    - Write player-facing entries for gameplay changes; put refactors, build, and tooling changes under
      `### Technical Details`.
    - Skip changelog entries only for changes with no effect on the shipped mod or its workflow (e.g. fixing a typo in a
      doc).

3. **Document the feature** — every user-visible feature is described in [docs/FEATURES.md](docs/FEATURES.md). A new
   feature gets its own `##` section there (what it does, how the player enables/configures it, any limitation); a
   change to an existing feature updates that feature's section. Write for a player — implementation notes belong in the
   changelog's `### Technical Details`, not here.

4. **Update the wiki** — the GitHub wiki is published from [wiki/](wiki/) in this repository, and it is the page a
   player finds from a search engine, so it must never lag behind the mod.
    - A **new feature** gets its own `wiki/<Feature-Name>.md`, plus a line in [wiki/_Sidebar.md](wiki/_Sidebar.md) and a
      row in the feature table of [wiki/Home.md](wiki/Home.md).
    - A **change to an existing feature** updates that feature's page, in the same task as the code — the same rule
      [docs/FEATURES.md](docs/FEATURES.md) already carries.
    - A change to commands, keybinds, or config files also touches [wiki/Commands.md](wiki/Commands.md),
      [wiki/Keybinds.md](wiki/Keybinds.md) or [wiki/Config-Files.md](wiki/Config-Files.md) — those pages are exhaustive
      lists, so a missing entry is a visible gap.
    - A version bump in `gradle.properties` touches [wiki/Installation.md](wiki/Installation.md) and
      [wiki/Building-from-Source.md](wiki/Building-from-Source.md), which state the supported versions.
    - Page names are titles: renaming a file breaks every link to it. Links between pages are relative and
      extension-less (`[Regions](Regions)`); links into this repository are absolute GitHub URLs. See
      [docs/WIKI.md](docs/WIKI.md) for the conventions and how pages are published.

5. **Check the README** — if the change affects anything [README.md](README.md) mentions (installation, dependencies,
   MC/Java version, build instructions, license), update it. [README.md](README.md) carries only a one-line summary per
   feature: add a bullet for a new feature, but keep the details in [docs/FEATURES.md](docs/FEATURES.md).

6. **Check the docs folder** — if the change affects a workflow documented in [docs/](docs/) (e.g. the release process
   in [docs/RELEASING.md](docs/RELEASING.md)), update the affected doc in the same task.

## Project conventions

- **Every colour goes through the universal colour picker.** A feature asks for a colour with
  `ConfigCategory.Builder.color(...)` and gets the whole picker — palettes, the shared recent colours, hex and RGB
  entry, chroma — for free. Never hand-roll a colour control, a hex-only text field, or a second picker: there is
  deliberately one way in, so a feature added later is consistent without trying to be. See
  [wiki/Colour-Picker.md](wiki/Colour-Picker.md).
    - **Chroma is a colour, not a flag beside one.** It is stored in the colour field as `"chroma"` — see
      `net.trilleo.color.ColorValue`, which also defines `""` as "no colour of its own". Never add a separate chroma
      toggle.
    - Pass `chroma = true` only where whatever draws the colour re-reads it every frame or every tick. A colour baked
      into a cached component cannot animate, and offering chroma there ships a setting that visibly does nothing. Pass
      `alpha = true` when the value carries an opacity byte.
    - Read stored values back with `ColorValue.resolve`, which folds all three cases into one packed ARGB and never
      fails.
- **Every sound goes through the universal sound picker.** A feature asks for a sound with
  `ConfigCategory.Builder.sound(...)` and gets the whole picker — browsing the registry by group, search, click-to-hear,
  pitch and volume, and the saved sequences — for free. Never hand-roll a sound field, a bare id text box, or a second
  pair of pitch/volume sliders beside it. See [wiki/Sound-Picker.md](wiki/Sound-Picker.md).
    - **`net.trilleo.sound.SoundPlayer` is the only thing in the mod that makes a noise.** Never construct a
      `SimpleSoundInstance` anywhere else, and never add a second audio path — that is what `Notify` losing its
      `uiSound` overloads was for.
    - **A sequence is a value, not a field.** A sound setting holds one string, and `"@my-sequence"` is a third thing it
      can say alongside a sound id and `""` — see `net.trilleo.sound.SoundValue`, which mirrors `ColorValue`. Never add
      a parallel "sequence" field beside a sound one.
    - Pass `optional = true` only where a blank value genuinely survives the owning model's normalizer.
      `ReminderAction.normalize` rewrites a blank sound back to its default, so `optional = false` there.
- `gradle.properties` is the single source of truth for all versions (`mod_version`, `minecraft_version`,
  `loader_version`, …). `fabric.mod.json` gets these expanded at build time via `processResources` — never hardcode
  versions there.
- Mixins are written in Java under `src/main/java/net/trilleo/mixin/` and registered in the `"client"` array of
  `hex.mixins.json` (not `"mixins"` — this mod has no common/server side).
- Mod code is Kotlin under `src/main/kotlin/`, entrypoint is the `client` entrypoint (`ClientModInitializer`).
- Language files live in `src/main/resources/assets/hex/lang/`, one per locale, UTF-8 without a BOM, all sharing one key
  set — see [docs/TRANSLATIONS.md](docs/TRANSLATIONS.md).
- Releases are made by tagging `vX.Y.Z` — see [docs/RELEASING.md](docs/RELEASING.md). Never tag or push tags unless
  explicitly asked.
- Commit messages follow the `<tag>: <message>` convention (`Feature:`, `Improvement:`, `Fix:`, `Internal:`,
  `Backend:`, `Update:`) with one granular commit per logical change — see
  [docs/COMMIT_STRUCTURE.md](docs/COMMIT_STRUCTURE.md).
