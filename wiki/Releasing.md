# Releasing

How the changelog is maintained during development, and how a release is published. The release itself is automated:
**pushing a `vX.Y.Z` tag** builds the mod, extracts the matching changelog section, and creates the GitHub Release with
the jar attached.

## Changelog format

`CHANGELOG.md` follows the [SkyHanni](https://github.com/hannibal002/SkyHanni) style:

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

| Level | Heading | Purpose |
|---|---|---|
| `##` | `Unreleased` / `Version X.Y.Z` | One section per release; `Unreleased` collects entries during development |
| `###` | Category | `New Features`, `Improvements`, `Fixes`, `Technical Details`, `Removed Features` |
| `####` | Feature area | `Fishing`, `Mining`, `Dungeon`, `GUI`, `Misc`, … — free-form; `Misc` is the catch-all |
| `+` | Entry | One change per bullet; indent `+` sub-bullets for details |

Rules of thumb:

- Add the entry under `## Unreleased` **in the same commit or PR as the change**, so the changelog never lags behind.
- Only include categories and feature areas that actually have entries.
- Write for players, not developers: *"Added Commission Progress HUD"*, not *"Refactored CommissionTracker"*.
  Developer-facing changes go under **Technical Details**.
- With outside contributors, append attribution:
  `+ Added X. - Name (https://github.com/Trilleo/Hex/pull/123)`

## Versioning

`mod_version` in `gradle.properties` is the single source of truth — it flows into the jar filename and
`fabric.mod.json` automatically. Follow semver:

- **patch** — bugfixes
- **minor** — new features
- **major** — breaking changes (e.g. a config reset)

## Walkthrough — releasing 1.1.0

1. **Finalize the changelog.** Rename `## Unreleased` to `## Version 1.1.0` and add a fresh empty `## Unreleased` above
   it.

2. **Bump the version** in `gradle.properties`:

   ```properties
   mod_version=1.1.0
   ```

3. **Verify the build** (recommended):

   ```bash
   ./gradlew build
   ```

4. **Commit and tag** — the tag is the version prefixed with `v`:

   ```bash
   git commit -am "Update: Mod version 1.1.0 release"
   ```

   ```bash
   git tag v1.1.0
   ```

5. **Push** — this is the publish step; the release workflow fires on the tag:

   ```bash
   git push && git push --tags
   ```

6. **Check the result.** The `release` workflow under *Actions* builds the jar and creates the GitHub Release. Verify
   the page shows the changelog text and has `hex-1.1.0.jar` attached.

> **Never tag or push tags unless you have been explicitly asked to.** Pushing a tag publishes a release.

## How the automation matches things up

- The workflow strips the `v` (`v1.1.0` → `1.1.0`) and extracts everything between `## Version 1.1.0` and the next
  `## ` heading in `CHANGELOG.md`. That text becomes the release body.
- **If no matching section exists, the workflow fails** — a release cannot ship with an empty changelog. Fix the heading
  (exact match: `## Version 1.1.0`) and move the tag.
- The mod jar from `build/libs/` is attached; the `-sources` jar is excluded.

> **The in-game auto-updater depends on that asset.** Hex downloads the release asset whose name starts with `hex`,
> ends in `.jar`, and is not the `-sources` jar. Keep that jar attached to every release with that naming, or clients
> fall back to a notify-only "update available" message with no automatic download. Prereleases are only offered to
> users who opt in via `config/hex/update.json`.

## Fixing a botched release

**Wrong changelog / missing section** — fix `CHANGELOG.md`, commit, then move the tag and re-push it (delete the
draft/failed release on GitHub first if one was created):

```bash
git tag -f v1.1.0 && git push -f origin v1.1.0
```

**Wrong version in the jar** — you tagged before bumping `mod_version`. Bump it, commit, move the tag as above.
