# Hex - Change Log

## Unreleased

### New Features

#### Keybinds

+ Added keybind shortcuts: bind a key (optionally with Ctrl/Shift/Alt) to run a command or a sequence of
  commands/chat messages, with an optional delay between them. Manage bindings in-game through the Hex Keybinds
  screen, opened via a rebindable keybind in the vanilla Controls menu.

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
