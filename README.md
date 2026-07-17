# Hex

![build](https://github.com/Trilleo/Hex/actions/workflows/build.yml/badge.svg)

A client-side utility mod for [Hypixel Skyblock](https://hypixel.net/), built on [Fabric](https://fabricmc.net/) for
Minecraft 26.1.2.

Hex runs entirely on your client — it never needs to be installed on a server.

## Features

- **Keybind shortcuts** — bind a key (optionally with Ctrl/Shift/Alt) to run a sequence of commands/chat
  messages, where each action has its own delay and the command inputs offer chat-style tab-completion. Configure
  bindings in-game via the Hex Keybinds screen; open it with the rebindable keybind under Options → Controls, or
  by running `/hexa keybinds`.

*Hex is in early development — more features will be listed here as they land. See the
[change log](CHANGELOG.md) for what's new in each release.*

## Installation

1. Install the [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 26.1.2.
2. Download the following mods and drop them into your `mods` folder:
    - [Fabric API](https://modrinth.com/mod/fabric-api)
    - [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
    - Hex, from the [releases page](https://github.com/Trilleo/Hex/releases)
3. Launch the game with the Fabric profile.

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
