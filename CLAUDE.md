# Hex — Agent Instructions

Hex is a client-side Fabric utility mod for Hypixel Skyblock (Kotlin, Minecraft 26.1.2, Java 25). It must work
entirely client side — never add server-side logic or a `main` entrypoint.

## After every change: keep docs and changelog in sync

Before finishing any task that changes the mod, do all of the following:

1. **Update the changelog** — add an entry for the change under `## Unreleased` in [CHANGELOG.md](CHANGELOG.md).
   - Follow the SkyHanni-style format documented in [docs/RELEASING.md](docs/RELEASING.md): category (`### New
     Features` / `### Improvements` / `### Fixes` / `### Technical Details` / `### Removed Features`), then a
     `#### Feature Area` heading, then `+` bullets.
   - Write player-facing entries for gameplay changes; put refactors, build, and tooling changes under
     `### Technical Details`.
   - Skip changelog entries only for changes with no effect on the shipped mod or its workflow (e.g. fixing a typo
     in a doc).

2. **Document the feature** — every user-visible feature is described in [docs/FEATURES.md](docs/FEATURES.md). A new
   feature gets its own `##` section there (what it does, how the player enables/configures it, any limitation);
   a change to an existing feature updates that feature's section. Write for a player — implementation notes belong
   in the changelog's `### Technical Details`, not here.

3. **Check the README** — if the change affects anything [README.md](README.md) mentions (installation, dependencies,
   MC/Java version, build instructions, license), update it. [README.md](README.md) carries only a one-line summary
   per feature: add a bullet for a new feature, but keep the details in [docs/FEATURES.md](docs/FEATURES.md).

4. **Check the docs folder** — if the change affects a workflow documented in [docs/](docs/) (e.g. the release
   process in [docs/RELEASING.md](docs/RELEASING.md)), update the affected doc in the same task.

## Project conventions

- `gradle.properties` is the single source of truth for all versions (`mod_version`, `minecraft_version`,
  `loader_version`, …). `fabric.mod.json` gets these expanded at build time via `processResources` — never hardcode
  versions there.
- Mixins are written in Java under `src/main/java/net/trilleo/mixin/` and registered in the `"client"` array of
  `hex.mixins.json` (not `"mixins"` — this mod has no common/server side).
- Mod code is Kotlin under `src/main/kotlin/`, entrypoint is the `client` entrypoint (`ClientModInitializer`).
- Releases are made by tagging `vX.Y.Z` — see [docs/RELEASING.md](docs/RELEASING.md). Never tag or push tags unless
  explicitly asked.
- Commit messages follow the `<tag>: <message>` convention (`Feature:`, `Improvement:`, `Fix:`, `Internal:`,
  `Backend:`, `Update:`) with one granular commit per logical change — see
  [docs/COMMIT_STRUCTURE.md](docs/COMMIT_STRUCTURE.md).
