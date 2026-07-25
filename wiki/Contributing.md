# Contributing

Hex is public domain under [CC0 1.0](https://github.com/Trilleo/Hex/blob/master/LICENSE). Contributions are welcome —
bug reports, translations, features.

## Reporting a bug

Open an issue at [Trilleo/Hex](https://github.com/Trilleo/Hex/issues) with:

- Your **Hex version** — `/hexa` prints it.
- Minecraft, Fabric Loader, Fabric API and Fabric Language Kotlin versions.
- **`logs/latest.log`** — the whole file.
- What you did, what happened, what you expected.

[Troubleshooting](Troubleshooting) covers the common causes; it is worth a look first.

## Suggesting a feature

Say what you want to *know* or *do*, not just the UI you imagine. Hex is limited to displaying information the game
already gives you and quality-of-life improvements — nothing that automates play or gives an unfair advantage, in line
with the [Hypixel rules](https://hypixel.net/rules). A suggestion that needs the mod to act for you will be declined
however well it is argued.

## Making a change

1. Read [Building from source](Building-from-Source) and [Architecture](Architecture).
2. Work through the checklist in [Adding a feature](Adding-a-Feature) — translations, changelog, `docs/FEATURES.md`,
   README, and this wiki. A change that skips them is not finished.
3. `./gradlew build` — which runs the unit tests too — and run the client at least once. Most of Hex can only be
   verified by playing it; the tests cover the handful of pieces that are pure logic.

## Commit messages

Every commit message follows:

```
<tag>: <message>
```

| Tag             | Usage                                             | Example                                          |
|-----------------|---------------------------------------------------|--------------------------------------------------|
| **Feature**     | Brand-new functionality                           | `Feature: Add Sea Creature Kill Timer`           |
| **Fix**         | Bugs, crashes, logic errors                       | `Fix: Resolve timer reset on island switch`      |
| **Improvement** | Refining existing code, UX or performance         | `Improvement: Add sound feedback to overlay`     |
| **Internal**    | Documentation, comments, repo maintenance         | `Internal: Update COMMIT_STRUCTURE instructions` |
| **Backend**     | Build system, dependency or configuration updates | `Backend: Update Gradle to 9.6.1`                |
| **Update**      | Mod version changes                               | `Update: Mod version 1.9.2 release`              |

Practices:

- **Present tense** — "Add feature", not "Added feature".
- **Be specific** — `Fix: Handle missing bobber entity in timer`, not `Fix: bug`.
- **No trailing period.**
- **One granular commit per logical change.** A feature, its translations and its changelog entry belong together; two
  unrelated fixes do not.

Tags map onto changelog categories:

| Tag                | Changelog category      |
|--------------------|-------------------------|
| Feature            | `### New Features`      |
| Improvement        | `### Improvements`      |
| Fix                | `### Fixes`             |
| Backend / Internal | `### Technical Details` |
| Update             | Usually no entry        |

## Pull requests

- Target `master`.
- CI runs `./gradlew build` on every push and PR — it should be green.
- Include the changelog entry in the PR itself; maintainers should not have to write it for you.
- Attribution follows the SkyHanni style in the changelog:
  `+ Added X. - Name (https://github.com/Trilleo/Hex/pull/123)`

## Translations

The most valuable low-friction contribution. See [Translating](Translating) — copy `en_us.json`, translate the values,
keep the keys and their order identical, and run the parity check before opening the PR.

## Things that are not accepted

- **Server-side code or a `main` entrypoint.** Hex is client-only by design.
- **Hardcoded versions in `fabric.mod.json`.** `gradle.properties` is the single source of truth.
- **User-visible English inside `Component.literal`.** It is invisible to every locale but English.
- **Anything that plays the game for you.**
