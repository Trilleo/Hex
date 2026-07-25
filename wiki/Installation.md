# Installation

Hex is a **client-side** Fabric mod. You install it into your own Minecraft; nothing goes on a server, and other players
do not need it.

## Requirements

| Requirement                                                               | Version                                      |
|---------------------------------------------------------------------------|----------------------------------------------|
| Minecraft                                                                 | **26.1.2**                                   |
| Java                                                                      | **25** or newer                              |
| Fabric Loader                                                             | **0.19.3** or newer                          |
| [Fabric API](https://modrinth.com/mod/fabric-api)                         | Built for 26.1.2 (`0.155.0+26.1.2` or newer) |
| [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) | `1.13.13+kotlin.2.4.10` or newer             |

[Mod Menu](https://modrinth.com/mod/modmenu) is **optional**. With it installed, its settings button on Hex's entry in
the mod list opens Hex's config menu. Without it, Hex behaves identically — it is neither bundled nor required.

## Steps

1. **Install Fabric Loader** for Minecraft 26.1.2 with the [Fabric installer](https://fabricmc.net/use/installer/).
2. **Download the dependencies** — Fabric API and Fabric Language Kotlin, both for 26.1.2.
3. **Download Hex** from the [releases page](https://github.com/Trilleo/Hex/releases). Take the file named
   `hex-<version>.jar`; ignore the `-sources` jar, which is for developers.
4. **Drop all three jars into your `mods` folder.**
    - Windows: `%APPDATA%\.minecraft\mods`
    - macOS: `~/Library/Application Support/minecraft/mods`
    - Linux: `~/.minecraft/mods`
5. **Launch the game** with the Fabric profile.

## Checking it worked

Join a world or server and run:

```
/hexa
```

Hex replies with its version and the list of available subcommands. If the command does not exist, the mod did not
load — see [Troubleshooting](Troubleshooting).

You can also open the settings menu without joining anything: from the title screen, press **Options** and click the
small **□** button next to **Done**.

## Where Hex keeps its files

Everything lives under `config/hex/` in your Minecraft folder — see [Config files](Config-Files) for what each file
holds. Deleting that folder resets Hex to defaults; it never touches anything outside it, apart from Minecraft's own
`options.txt` when you change a control through [control switches](Control-Switches) or the
[attack mode switch](Attack-Mode-Switch).

## Updating and uninstalling

Hex updates itself — see [Updating](Updating). To uninstall, delete `hex-<version>.jar` from `mods`. Your settings stay
in `config/hex/` unless you delete that too, so reinstalling later picks up where you left off.
