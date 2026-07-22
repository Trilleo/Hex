# Hex

![build](https://github.com/Trilleo/Hex/actions/workflows/build.yml/badge.svg)

A client-side utility mod for [Hypixel Skyblock](https://hypixel.net/), built on [Fabric](https://fabricmc.net/) for
Minecraft 26.1.2.

Hex runs entirely on your client — it never needs to be installed on a server.

## Features

- **Config menu** — one categorized settings menu, opened with `/hexa config` or a keybind.
- **Config profiles** — named setups you can switch between by hand or automatically per server, plus sharing via the clipboard.
- **Keybind shortcuts** — bind a key combo to a delayed sequence of commands or chat messages.
- **Control switch shortcuts** — cycle one of Minecraft's own controls between two or more keys in-game.
- **Freecam** — detach the camera and fly it around while your character stays put.
- **Hand display** — reposition and restyle your held item in first person, cosmetically.
- **Auto-update** — Hex downloads new [releases](https://github.com/Trilleo/Hex/releases) itself and applies them on
  exit. See [Updating](#updating).

*Hex is in early development — more features will land over time. See [Features](docs/FEATURES.md) for what each one
does and how to configure it, and the [change log](CHANGELOG.md) for what's new in each release.*

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

- [Features](docs/FEATURES.md)
- [Change log](CHANGELOG.md)
- [Writing the changelog & releasing](docs/RELEASING.md)
- [Commit structure](docs/COMMIT_STRUCTURE.md)

## License

Hex is released into the public domain under [CC0 1.0](LICENSE).
