# Hex

**Hex** is a client-side utility mod for [Hypixel Skyblock](https://hypixel.net/), built on
[Fabric](https://fabricmc.net/) for Minecraft **26.1.2**.

Everything Hex does happens on your own machine. It never needs to be installed on a server, it never sends your data
anywhere, and it has no server-side half — there is nothing to install for your friends, your guild, or Hypixel.

> **New here?** [Installation](Installation) takes about two minutes, and everything after that is reachable from
> `/hexa config`.

## What Hex does

| Feature                                    | In one line                                                             |
|--------------------------------------------|-------------------------------------------------------------------------|
| [Config menu](Configuration)               | One categorized settings menu, opened four different ways               |
| [Colour picker](Colour-Picker)             | One picker for every colour in the mod, with shared recent colours      |
| [Sound picker](Sound-Picker)               | One picker for every sound in the mod, browsable and previewable        |
| [Sound sequences](Sound-Sequences)         | Build multi-note alert sounds on a timeline, then use them anywhere     |
| [Config profiles](Config-Profiles)         | Named setups you switch by hand or automatically per server or island   |
| [Keybind shortcuts](Keybind-Shortcuts)     | A key combo runs a delayed sequence of commands or chat messages        |
| [Control switches](Control-Switches)       | Cycle one of Minecraft's own controls between two or more keys, in-game |
| [Attack mode switch](Attack-Mode-Switch)   | Flip Attack/Destroy between hold and toggle with a keypress             |
| [Freecam](Freecam)                         | Detach the camera and fly it around while your character stays put      |
| [Mouse sensitivity](Mouse-Sensitivity)     | Hold a key to slow the mouse, and stick the view to round angles        |
| [Hand display](Hand-Display)               | Reposition and restyle your held item in first person, cosmetically     |
| [Per-item swing](Per-Item-Swing)           | Hide the swing animation only while holding chosen Skyblock items       |
| [Item customization](Item-Customization)   | Rename, recolour, reskin or de-glint one specific item, client-side     |
| [Chroma text](Chroma-Text)                 | Flowing rainbow colour, chosen wherever a colour is                     |
| [Titles](Titles)                           | One editor for every alert title: colours, styles, timings and a sound  |
| [Reminders](Reminders)                     | Timers, chat triggers and location alerts on a movable HUD panel        |
| [Regions](Regions)                         | Areas you draw that announce themselves when you walk in                |
| [Entity highlight](Entity-Highlight)       | Mobs you pick, lit up in your colour, announcing themselves when new    |
| [Chat highlight](Chat-Highlight)           | Words you pick, repainted in chat, optionally announced or hidden       |
| [Notebook](Notebook)                       | Markdown notes with folders, tags and search, kept between sessions     |
| [Command suggestions](Command-Suggestions) | Learns the commands you use and completes them, ranked by context       |
| [Auto-update](Updating)                    | Hex downloads new releases itself and applies them on exit              |
| [Languages](Languages)                     | English and 简体中文, following Minecraft's own language setting        |

## Quick reference

- **[Commands](Commands)** — every `/hexa` subcommand.
- **[Keybinds](Keybinds)** — every key Hex registers, and what it does.
- **[Config files](Config-Files)** — what lives in `config/hex/`, and what travels with a profile.
- **[FAQ](FAQ)** and **[Troubleshooting](Troubleshooting)** — when something is not behaving.

## For developers

- **[Building from source](Building-from-Source)** — Java 25, one Gradle command.
- **[Architecture](Architecture)** — how features, config, and events fit together.
- **[Adding a feature](Adding-a-Feature)** — the checklist for a change that ships.
- **[Translating](Translating)** — the language-file parity rule and how to add a locale.
- **[Releasing](Releasing)** — changelog format and the tag-driven release workflow.
- **[Contributing](Contributing)** — commit convention and what a good PR looks like.

## Hypixel rules

Hex is designed to comply with the [Hypixel Server Rules](https://hypixel.net/rules) on allowed modifications. Features
are limited to displaying information the game already gives you and quality-of-life improvements — no automation, no
unfair advantages, nothing that plays for you. That said, use at your own risk: no third-party mod is officially
endorsed by Hypixel.

## Status and licence

Hex is in **early development** — features land regularly, and
the [changelog](https://github.com/Trilleo/Hex/blob/master/CHANGELOG.md)
is the record of what changed when. The mod is released into the public domain under
[CC0 1.0](https://github.com/Trilleo/Hex/blob/master/LICENSE).
