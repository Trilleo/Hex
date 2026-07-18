# Hex - Change Log

## Unreleased

### New Features

#### Auto-Update

+ Hex now keeps itself up to date from its GitHub releases. On startup it checks for a newer version in the
  background and, if one is found, downloads it and tells you to restart — the update is applied automatically the
  next time you close the game.
    + Run `/hexa update` to check on demand at any time.
    + Edit `config/hex/update.json` to turn the startup check off (`enabled`) or to also receive prerelease builds
      (`includePrereleases`).

### Technical Details

#### Auto-Update

+ Added an update feature that queries the `Trilleo/Hex` GitHub releases API (built-in `java.net.http`, no new
  dependency), compares tags with Fabric's `SemanticVersion`, and stages a verified jar under
  `config/hex/update/`. Because the running jar is file-locked by the JVM, the swap is performed on shutdown by a
  detached OS helper script that copies the new jar into `mods/` and removes the old one once the lock is released.

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
