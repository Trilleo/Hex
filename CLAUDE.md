# Hex — Agent Instructions

Hex is a client-side Fabric utility mod for Hypixel Skyblock (Kotlin, Minecraft 26.1.2, Java 25). It must work
entirely client side — never add server-side logic or a `main` entrypoint.

## After every change: keep translations, docs and changelog in sync

Before finishing any task that changes the mod, do all of the following:

1. **Translate every string** — the mod ships one language file per locale in
   `src/main/resources/assets/hex/lang/`, currently `en_us.json` (the source of truth) and `zh_cn.json`
   (Simplified Chinese). **Every file must carry exactly the same key set, in the same order.** Adding,
   renaming or removing a key in one file means doing it in *all* of them in the same task — a key present in
   `en_us.json` but missing elsewhere renders as the raw key id in game.
   - Any user-visible string goes through a translation key and `Component.translatable`, never
     `Component.literal("some English text")`. `Component.literal` is for values that are not language —
     player names, item IDs, numbers, symbols like `✎`.
   - Keep `%s` and `%d` placeholders, their count and their order identical across files.
   - Leave untranslated only what is genuinely not language: the mod name, item IDs (`HYPERION`), key names
     (`Tab`), and text matched against Hypixel's English scoreboard (`private island`, `dwarven mines`).
   - See [docs/TRANSLATIONS.md](docs/TRANSLATIONS.md) for the full rules and the parity check.

2. **Update the changelog** — add an entry for the change under `## Unreleased` in [CHANGELOG.md](CHANGELOG.md).
   - Follow the SkyHanni-style format documented in [docs/RELEASING.md](docs/RELEASING.md): category (`### New
     Features` / `### Improvements` / `### Fixes` / `### Technical Details` / `### Removed Features`), then a
     `#### Feature Area` heading, then `+` bullets.
   - Write player-facing entries for gameplay changes; put refactors, build, and tooling changes under
     `### Technical Details`.
   - Skip changelog entries only for changes with no effect on the shipped mod or its workflow (e.g. fixing a typo
     in a doc).

3. **Document the feature** — every user-visible feature is described in [docs/FEATURES.md](docs/FEATURES.md). A new
   feature gets its own `##` section there (what it does, how the player enables/configures it, any limitation);
   a change to an existing feature updates that feature's section. Write for a player — implementation notes belong
   in the changelog's `### Technical Details`, not here.

4. **Check the README** — if the change affects anything [README.md](README.md) mentions (installation, dependencies,
   MC/Java version, build instructions, license), update it. [README.md](README.md) carries only a one-line summary
   per feature: add a bullet for a new feature, but keep the details in [docs/FEATURES.md](docs/FEATURES.md).

5. **Check the docs folder** — if the change affects a workflow documented in [docs/](docs/) (e.g. the release
   process in [docs/RELEASING.md](docs/RELEASING.md)), update the affected doc in the same task.

## Project conventions

- `gradle.properties` is the single source of truth for all versions (`mod_version`, `minecraft_version`,
  `loader_version`, …). `fabric.mod.json` gets these expanded at build time via `processResources` — never hardcode
  versions there.
- Mixins are written in Java under `src/main/java/net/trilleo/mixin/` and registered in the `"client"` array of
  `hex.mixins.json` (not `"mixins"` — this mod has no common/server side).
- Mod code is Kotlin under `src/main/kotlin/`, entrypoint is the `client` entrypoint (`ClientModInitializer`).
- Language files live in `src/main/resources/assets/hex/lang/`, one per locale, UTF-8 without a BOM, all sharing
  one key set — see [docs/TRANSLATIONS.md](docs/TRANSLATIONS.md).
- Releases are made by tagging `vX.Y.Z` — see [docs/RELEASING.md](docs/RELEASING.md). Never tag or push tags unless
  explicitly asked.
- Commit messages follow the `<tag>: <message>` convention (`Feature:`, `Improvement:`, `Fix:`, `Internal:`,
  `Backend:`, `Update:`) with one granular commit per logical change — see
  [docs/COMMIT_STRUCTURE.md](docs/COMMIT_STRUCTURE.md).
