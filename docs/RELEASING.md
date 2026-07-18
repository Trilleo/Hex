# Writing the Changelog & Releasing

This guide covers how to maintain [CHANGELOG.md](../CHANGELOG.md) during development and how to publish a release on
GitHub. The release itself is automated by
[.github/workflows/release.yml](../.github/workflows/release.yml) — pushing a version tag builds the mod, extracts the
matching changelog section, and creates the GitHub Release with the jar attached.

## Changelog format

The changelog follows the [SkyHanni](https://github.com/hannibal002/SkyHanni) style:

```markdown
# Hex - Change Log

## Unreleased

## Version 1.1.0

### New Features

#### Fishing

+ Added Sea Creature Kill Timer.
    + Shows time since the last rare sea creature spawn.

### Improvements

#### GUI

+ Made overlay positions draggable in the edit screen.

### Fixes

#### Fishing

+ Fixed timer resetting when switching islands.
```

Structure, top to bottom:

| Level  | Heading                        | Purpose                                                                               |
|--------|--------------------------------|---------------------------------------------------------------------------------------|
| `##`   | `Unreleased` / `Version X.Y.Z` | One section per release; `Unreleased` collects entries during development             |
| `###`  | Category                       | `New Features`, `Improvements`, `Fixes`, `Technical Details`, `Removed Features`      |
| `####` | Feature area                   | `Fishing`, `Mining`, `Dungeon`, `GUI`, `Misc`, … — free-form, `Misc` is the catch-all |
| `+`    | Entry                          | One change per bullet; indent `+` sub-bullets for details                             |

Rules of thumb:

- Add an entry under `## Unreleased` in the same commit (or PR) as the change itself, so the changelog never lags
  behind.
- Only include categories and feature areas that actually have entries — omit empty ones.
- Write entries for players, not developers: "Added Commission Progress HUD", not
  "Refactored CommissionTracker". Developer-facing changes go under `Technical Details`.
- With outside contributors, append attribution like SkyHanni does:
  `+ Added X. - Name (https://github.com/Trilleo/Hex/pull/123)`

## Versioning

`mod_version` in [gradle.properties](../gradle.properties) is the single source of truth — it flows into the jar
filename and `fabric.mod.json` automatically. Follow semver: **patch** for bugfixes, **minor** for new features,
**major** for breaking changes (e.g. config resets).

## Release walkthrough

Example: releasing version `1.1.0`.

1. **Finalize the changelog** — in `CHANGELOG.md`, rename the `## Unreleased`
   heading to `## Version 1.1.0` and add a fresh empty `## Unreleased` above it:

   ```markdown
   ## Unreleased

   ## Version 1.1.0
   ...entries...
   ```

2. **Bump the version** — in `gradle.properties`:

   ```properties
   mod_version=1.1.0
   ```

3. **Verify the build** (optional but recommended):

   ```
   ./gradlew build
   ```

4. **Commit and tag** — the tag must be the version prefixed with `v`:

   ```
   git commit -am "Release 1.1.0"
   git tag v1.1.0
   ```

5. **Push** — this is the publish step; the release workflow fires on the tag:

   ```
   git push && git push --tags
   ```

6. **Check the result** — the `release` workflow under the repo's *Actions* tab builds the jar and creates the GitHub
   Release. Verify the release page shows the changelog text and has `hex-1.1.0.jar` attached.

## How the automation matches things up

- The workflow strips the `v` from the tag (`v1.1.0` → `1.1.0`) and extracts everything between `## Version 1.1.0` and
  the next `## ` heading in
  `CHANGELOG.md`. That text becomes the release body.
- **If no matching section exists, the workflow fails** — a release cannot ship with an empty changelog. Fix the heading
  (exact match: `## Version 1.1.0`)
  and re-run the workflow, or delete and re-push the tag.
- The mod jar from `build/libs/` is attached; the `-sources` jar is excluded.

> **The in-game auto-updater depends on this asset.** Hex's update feature downloads the release asset whose name
> starts with `hex`, ends in `.jar`, and is not the `-sources` jar (i.e. `hex-<version>.jar`). Keep that jar
> attached to every release with that naming, or clients will fall back to a notify-only "update available" message
> with no automatic download. Prereleases are only offered to users who opt in via `config/hex/update.json`.

## Fixing a botched release

- **Wrong changelog / missing section**: fix `CHANGELOG.md`, commit, then move the tag and re-push it:

  ```
  git tag -f v1.1.0
  git push -f origin v1.1.0
  ```

  Delete the draft/failed release on GitHub first if one was created.

- **Wrong version in the jar**: you tagged before bumping `mod_version`. Bump it, commit, move the tag as above.
