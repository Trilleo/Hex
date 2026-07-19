# Hex

![build](https://github.com/Trilleo/Hex/actions/workflows/build.yml/badge.svg)

A client-side utility mod for [Hypixel Skyblock](https://hypixel.net/), built on [Fabric](https://fabricmc.net/) for
Minecraft 26.1.2.

Hex runs entirely on your client — it never needs to be installed on a server.

## Features

- **Config menu** — a single, categorized menu for the mod's settings. Open it with `/hexa config` or a rebindable
  keybind under Options → Controls → **Hex**; each feature adds its own tab down the side, and a button links
  straight to the Keybinds screen.
- **Keybind shortcuts** — bind a key (optionally with Ctrl/Shift/Alt) to run a sequence of commands/chat messages, where
  each action has its own delay and the command inputs offer chat-style tab-completion. Configure bindings in-game via
  the Hex Keybinds screen; open it with the rebindable keybind under Options → Controls, from the config menu, or by
  running `/hexa keybinds`.
- **Freecam** — press a keybind to detach the camera from your player and fly it around freely to observe your
  surroundings (WASD to move, Space/Shift for up/down, the mouse to look, and the scroll wheel to change speed);
  press it again to return. Your character stays in place. Bind it under Options → Controls → **Hex** and tune it
  in the **Freecam** tab of `/hexa config`.
- **Auto-update** — Hex checks its [GitHub releases](https://github.com/Trilleo/Hex/releases) on startup and, when a
  newer version is out, downloads it and applies it automatically the next time you close the game. Run
  `/hexa update` to check on demand, or manage it from the **Updates** tab of `/hexa config`. See [Updating](#updating).

*Hex is in early development — more features will be listed here as they land. See the
[change log](CHANGELOG.md) for what's new in each release.*

## Installation

1. Install the [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 26.1.2.
2. Download the following mods and drop them into your `mods` folder:
    - [Fabric API](https://modrinth.com/mod/fabric-api)
    - [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
    - Hex, from the [releases page](https://github.com/Trilleo/Hex/releases)
3. Launch the game with the Fabric profile.

## Updating

Hex updates itself from its [GitHub releases](https://github.com/Trilleo/Hex/releases):

- On startup it checks for a newer release in the background. If one exists, it downloads the new jar and shows a chat
  notice; the swap into your `mods` folder happens automatically when you next close Minecraft.
- Run `/hexa update` to check immediately.
- Manage it from the **Updates** tab of `/hexa config`: disable the startup check, opt in to prerelease builds, or check
  for an update on the spot. (These are persisted to `config/hex/update.json` if you prefer to edit them by hand.)

If the automatic swap ever fails, the downloaded jar is left in `config/hex/update/` — drop it into your `mods`
folder (and delete the old one) to update manually.

## Hypixel rules

Hex is designed to comply with the [Hypixel Server Rules](https://hypixel.net/rules) on allowed modifications. Features
are limited to displaying information the game already gives you and quality-of-life improvements — no automation, no
unfair advantages. That said, use at your own risk: no third-party mod is officially endorsed by Hypixel.

## Building from source

Requires Java 25. Clone the repository and run:

```
./gradlew build
```

The mod jar is written to `build/libs/`.

To launch a development client:

```
./gradlew runClient
```

## Documentation

- [Change log](CHANGELOG.md)
- [Writing the changelog & releasing](docs/RELEASING.md)
- [Commit structure](docs/COMMIT_STRUCTURE.md)

## License

Hex is released into the public domain under [CC0 1.0](LICENSE).
