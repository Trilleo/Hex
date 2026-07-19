# Hex - Change Log

## Unreleased

## Version 1.3.0

### New Features

#### Freecam

+ Added a freecam: press its keybind to detach the camera from your player and fly it around freely to look at
  your surroundings — WASD to move, Space/Shift for up/down, the mouse to look, and the scroll wheel to change
  speed. Press it again to return to normal; your character stays put the whole time. The keybind lives under a
  new **Hex** category in Options → Controls (unbound by default), and the **Freecam** tab of `/hexa config`
  lets you enable/disable the feature and pick a base fly speed.

### Improvements

#### Keybinds

+ Hex's keybinds now live under their own **Hex** category in Options → Controls instead of being mixed into
  Misc — the config, keybinds and freecam binds are grouped together.
+ Added a rebindable keybind to open the Hex config menu (Options → Controls → Hex), alongside `/hexa config`.

### Technical Details

#### Freecam

+ Added the project's first mixins (`CameraMixin`, `ClientInputMixin`, `KeyboardInputMixin`,
  `MouseHandlerMixin`). The camera is repositioned right after `Camera#alignWithEntity` — before the cull
  frustum is built — so chunk culling follows the freecam wherever it flies; mouse look and scroll are
  redirected, the move vector is forced to zero, and the key-press record is blanked so the real player never
  moves or sends input to the server.
+ Added a reusable `CycleEntry` multiple-choice control to the config-menu framework (a new `ConfigEntry`
  subtype plus a `cycle(...)` builder), and a dedicated "Hex" `KeyMapping.Category`.

## Version 1.2.0

### New Features

#### Config Menu

+ Added a config menu that gathers Hex's settings in one place, split into categories down the side. Open it with
  `/hexa config`. A button on the menu jumps straight to the Keybinds screen.

#### Auto-Update

+ Hex now keeps itself up to date from its GitHub releases. On startup it checks for a newer version in the
  background and, if one is found, downloads it and tells you to restart — the update is applied automatically the
  next time you close the game.
    + Run `/hexa update` to check on demand at any time.
    + The **Updates** tab of `/hexa config` lets you turn the startup check off, opt in to prerelease builds, and
      check for an update on the spot — no more editing `config/hex/update.json` by hand.

### Technical Details

#### Config Menu

+ Added a reusable config-menu framework: a `Feature` contributes a settings tab by overriding
  `settingsCategory()` and returning a `ConfigCategory.build("id", "Title") { toggle(...); action(...) }`, and the
  `ConfigScreen` collects every enabled feature's category into the sidebar automatically. Entries are a small
  sealed `ConfigEntry` hierarchy (`BooleanEntry`, `ActionEntry`), rendered with vanilla widgets like the keybinds
  screens.

#### Auto-Update

+ Added an update feature that queries the `Trilleo/Hex` GitHub releases API (built-in `java.net.http`, no new
  dependency), compares tags with Fabric's `SemanticVersion`, and stages a verified jar under
  `config/hex/update/`. Because the running jar is file-locked by the JVM, the swap is performed on shutdown by a
  detached OS helper script that copies the new jar into `mods/` and removes the old one once the lock is released.
    + The on-demand check is exposed as `UpdateFeature.checkNow()`, shared by the `/hexa update` command and the
      config menu's "Check for updates now" button. Toggling an update setting in the menu persists it immediately.

## Version 1.1.0

### New Features

#### Keybinds

+ Added keybind shortcuts: bind a key (optionally with Ctrl/Shift/Alt) to run a sequence of commands/chat
  messages. Each action has its own input and its own delay, so you can pace a sequence step by step, and
  command inputs offer the same tab-completion as the chat box. Manage bindings in-game through the Hex Keybinds
  screen, opened via a rebindable keybind in the vanilla Controls menu; press Edit on a binding to arrange its
  actions.

#### Commands

+ Added a `/hexa` command. Run `/hexa keybinds` to open the Keybinds screen.

### Technical Details

#### Core

+ Added a Feature/Module lifecycle with a central registry that wires all client events (tick, chat, world
  join/leave, shutdown) and the `/hexa` command hub once and dispatches to registered features, a generic
  `JsonConfig` config helper, and migrated the keybind feature onto them.

## Version 1.0.0

### New Features

#### Misc

+ Initial release for Minecraft 26.1.2.
