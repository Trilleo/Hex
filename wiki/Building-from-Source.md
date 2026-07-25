# Building from source

## Requirements

- **JDK 25** (the project compiles and targets Java 25)
- Git
- Nothing else — the Gradle wrapper fetches its own Gradle, Loom, Minecraft and the mappings

## Build

```bash
git clone https://github.com/Trilleo/Hex.git
```

```bash
./gradlew build
```

The mod jar lands in `build/libs/hex-<version>.jar`, alongside a `-sources` jar. The first build downloads and remaps
Minecraft, so it takes a while; later builds are fast.

On Windows use `gradlew.bat` in cmd/PowerShell, or `./gradlew` in Git Bash.

## Run a development client

```bash
./gradlew runClient
```

The dev client's game directory is `run/`, which is gitignored — so its `config/hex/` is separate from your real
Minecraft install and you can experiment freely.

## Project layout

```
src/main/kotlin/net/trilleo/     mod code, one package per feature
src/main/java/net/trilleo/mixin/ mixins (Java), registered in hex.mixins.json
src/main/java/net/trilleo/duck/  duck interfaces for mixin-added state
src/main/resources/
  fabric.mod.json                mod metadata; versions are expanded at build time
  hex.mixins.json                mixin config — the "client" array
  assets/hex/lang/               one language file per locale
  assets/hex/reminders/          shipped reminder presets
  assets/hex/suggest/            the shipped command catalogue
docs/                            contributor documentation
```

## Versions live in one place

**`gradle.properties` is the single source of truth** for every version — `mod_version`, `minecraft_version`,
`loader_version`, `fabric_api_version`, `fabric_kotlin_version`, `loom_version`, `modmenu_version`.

`fabric.mod.json` receives `version`, `minecraft_version` and `loader_version` through Gradle's `processResources`, so
**never hardcode a version there**.

Current values:

| Property                | Value                        |
|-------------------------|------------------------------|
| `minecraft_version`     | 26.1.2                       |
| `loader_version`        | 0.19.3                       |
| `fabric_api_version`    | 0.155.0+26.1.2               |
| `fabric_kotlin_version` | 1.13.13+kotlin.2.4.10        |
| `modmenu_version`       | 18.0.0-beta.1 (compile-only) |

Mod Menu is `compileOnly`: it is never bundled, never listed in `depends`, and the only class that touches its API is
the entrypoint — which Fabric loads lazily, so nothing resolves those types unless Mod Menu is actually installed.

## Continuous integration

`.github/workflows/build.yml` runs `./gradlew build` on **every push and pull request** with JDK 25, validates the
Gradle wrapper, and uploads `build/libs/` as an artifact. A red build blocks nothing automatically, but a PR is not
ready while it is failing.

`.github/workflows/release.yml` fires on a `v*` tag — see [Releasing](Releasing).

## IDE notes

IntelliJ IDEA is the usual choice: open the folder, let it import the Gradle project, and use the generated **Minecraft
Client** run configuration. The Gradle **configuration cache is deliberately disabled** in
`gradle.properties` because IDEA is not yet fully compatible with it.

## Next

- [Architecture](Architecture) — how the pieces fit together
- [Adding a feature](Adding-a-Feature) — the checklist for a change that ships
- [Contributing](Contributing) — commits and pull requests
