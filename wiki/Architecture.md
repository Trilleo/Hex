# Architecture

How Hex is put together, for anyone reading or extending the code.

Two rules shape everything below:

1. **Client only.** There is no common or server side. `fabric.mod.json` declares `"environment": "client"` and a single
   `client` entrypoint; `hex.mixins.json` has an empty `"mixins"` array and lists everything under `"client"`. Never add
   a `main` entrypoint or server-side logic.
2. **Features do not touch the plumbing.** Event wiring, command roots, config persistence and the settings menu are all
   owned centrally. A feature declares what it wants; it never registers a Fabric callback itself.

## The entrypoint

`net.trilleo.Hex` (`src/main/kotlin/net/trilleo/Hex.kt`) is the whole entrypoint:

```kotlin
Features.register(KeybindsFeature)
Features.register(FreecamFeature)
…
Features.bootstrap()
```

Adding a feature is **one `register` line**. Registration order matters where features interact — `RegionFeature` is
registered ahead of `ReminderFeature` so a region crossing detected this tick is drained by the reminder dispatch in the
*same* tick, and a region-armed reminder starts counting without a tick of lag.

`Hex.KEY_CATEGORY` is the shared **Hex** keybind category, registered once here so every feature's keys group together
under Options → Controls instead of scattering into Misc.

## Features

`net.trilleo.feature.Feature` is the module interface. Every hook is a no-op by default, so a feature overrides only
what it needs:

| Member                          | Purpose                                                                                 |
|---------------------------------|-----------------------------------------------------------------------------------------|
| `id`                            | Stable identifier; also the `/hexa <id>` subcommand namespace                           |
| `enabled`                       | When false, the feature receives **no** dispatch — the single toggle point for a module |
| `onInit()`                      | Once at startup, in registration order: register keymappings, load config               |
| `registerCommands(hex)`         | Add subcommands under the shared `/hexa` root                                           |
| `settingsCategory()`            | Contribute a tab to the config menu, or `null`                                          |
| `onClientTick(client)`          | Every client tick                                                                       |
| `onHudRender(extractor, delta)` | Draw the feature's HUD overlay, once per frame                                          |
| `onWorldJoin` / `onWorldLeave`  | Connection lifecycle                                                                    |
| `onChatReceive(message)`        | Return `false` to swallow the message                                                   |
| `onShutdown()`                  | Flush unsaved config                                                                    |

Features are Kotlin `object`s, one package each: `attack`, `freecam`, `hand`, `itemcustom`, `keybind`, `region`,
`reminder`, `suggest`, `update`.

## The event hub

`net.trilleo.feature.Features` wires each underlying Fabric callback **exactly once** and fans it out. This is the only
place event wiring lives.

Ordering inside the tick is deliberate and documented in the source:

1. `ConfigRegistry.tick()` — debounced writes, **outside** the enabled check, so a disabled feature's pending write
   still lands.
2. `VanillaKeysConfig.tick()` — bindings read from a profile at startup wait for the options to exist.
3. `Sidebar` → `TabList` → `SkyblockEvents` → `IslandResolver` — the shared Skyblock readers, before anything that reads
   them.
4. `ProfileAutoSwitch.tick()`
5. `HeldItem.tick()` — one cache, several consumers.
6. Feature dispatch.

Two details worth knowing:

- **HUD overlays are attached before the vanilla chat element**, not added first or last. Attaching relative to a
  vanilla element inherits its render condition, which means **F1 hides Hex's overlays** without a single feature
  checking for it. The element is registered once and gated at dispatch, never re-registered — a feature toggling
  `enabled` at runtime must not be able to move itself in the draw order.
- **Chat goes through `IslandResolver` first**, so it can swallow the reply to its own `/locraw` request before any
  reminder pattern — or the chat log — sees the raw JSON.

## Commands

Everything is nested under one root, **`/hexa`** — the bare `hex` name is a Skyblock command. `Features` owns the root
and `config` subcommand; each feature contributes its own subtree through `registerCommands`.

`net.trilleo.command.Commands` is thin sugar over Fabric's client command API (`literal`, `argument`, `feedback`,
`error`), plus a `registerStandalone` escape hatch that should stay unused.

Bare parent commands print their subcommands rather than guessing at one.

## Configuration

Three layers:

| Layer             | Does                                                                                                                                                                                                                                                              |
|-------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `JsonConfig<T>`   | One `<name>.json`, round-tripped with GSON. Errors never propagate: a failed load degrades to defaults, a failed save is logged and swallowed. A `normalizer` hook repairs GSON's reflection gaps (an absent field arrives as the JVM default, e.g. a null list). |
| `ConfigHandle<T>` | Debounced writes, reload, reset, and the `global` flag.                                                                                                                                                                                                           |
| `ConfigRegistry`  | The set of user-facing configs, so they can be flushed, reloaded, snapshotted and exported as a group without any caller knowing which features exist.                                                                                                            |

**Registration is opt-in.** Only *settings* join the registry; transient machine state that happens to use `JsonConfig`
— `UpdateStaging`'s record of a downloaded jar, for instance — stays out, because it is not something a user would want
captured in a profile or pasted to a friend.

`global = true` marks a config that belongs to the **installation** rather than to a loadout — `update.json` and
`item_custom.json`. Globals are excluded from profile snapshots and clipboard exports. This is not hypothetical
tidiness: the updater's "check on startup" toggle used to be captured, so a profile snapshotted while it was off turned
it back off on every switch, with nothing in the menu to explain why.

`ConfigProfiles` keeps live files exactly where they always were (`config/hex/<name>.json`) and stores a profile as a
*copy* under `config/hex/profiles/<profile>/` — never a redirected config root, so nothing about the on-disk layout
changes for someone who never opens the Profiles screen.

## The settings menu

`ConfigCategory.build(id) { … }` is a small DSL producing `ConfigEntry` rows — `toggle`, `slider`, `text`, `color`,
`action`, plus cycle/enum and keybind entries. Labels are **translation keys derived from the ids**, so call sites never
repeat a prefix:

```
hex.config.category.<id>          the tab title
hex.config.<id>.<key>             an entry's label
hex.config.<id>.<key>.tooltip     its tooltip, if the lang file defines one
```

A tooltip exists exactly when the language file defines one — adding help text is a lang-file edit with no code change.

The GUI itself is **in-house**: no Cloth Config, no Mod Menu backend. `ModMenuIntegration` is a soft integration only,
compiled against a `compileOnly` dependency and loaded lazily by Fabric, so nothing resolves those types unless Mod Menu
is installed.

## Shared Skyblock readers

`net.trilleo.skyblock` is state that belongs to no single feature, ticked centrally so it stays live regardless of which
features are enabled:

| Object             | Reads                                                                                                     |
|--------------------|-----------------------------------------------------------------------------------------------------------|
| `Sidebar`          | The scoreboard: area, Skyblock date, some events                                                          |
| `TabList`          | The player list, including the `Event:` widget                                                            |
| `SkyblockEvents`   | Merges event claims from the sidebar, tab list, boss bar and chat                                         |
| `IslandResolver`   | Asks Hypixel via `/locraw` which **island** this is — the scoreboard line is the sub-area, not the island |
| `SkyblockLocation` | The resolved current location                                                                             |
| `SkyblockCalendar` | Season and Skyblock time of day                                                                           |
| `item.HeldItem`    | Cached main-hand Skyblock item, with `ItemRule` / `SkyblockItem`                                          |

All of them are reset on disconnect, so an item, island or date from one server cannot go on matching into the next.

## Mixins

Java, under `src/main/java/net/trilleo/mixin/`, registered in the **`"client"`** array of `hex.mixins.json`.
`net.trilleo.duck` holds duck interfaces for state added to vanilla classes.

Roughly: `Camera`/`ClientInput`/`KeyboardInput`/`MouseHandler` for the freecam, `ItemInHandRenderer` for the hand,
`ItemStack`/`ItemModelResolver` for item customization, `ChatScreen` + `CommandSuggestionsAccessor` for command
suggestions, `OptionsScreen` for the **□** button, and accessors where vanilla state is otherwise private.

Keep mixins minimal — prefer a Fabric API hook where one exists. Item customization uses Fabric's screen API rather than
a mixin on `AbstractContainerScreen` for exactly this reason: it gives both the key hook and the draw hook for any
screen and leaves vanilla input handling alone.

## Conventions

- `gradle.properties` is the **single source of truth** for versions; `fabric.mod.json` gets them expanded at build
  time.
- Mod code is Kotlin; mixins are Java.
- Every user-visible string goes through a translation key — see [Translating](Translating).
- Commit messages follow `<tag>: <message>` — see [Contributing](Contributing).

## Next

- [Adding a feature](Adding-a-Feature)
- [Building from source](Building-from-Source)
