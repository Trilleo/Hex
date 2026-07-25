# Commands

Every Hex command is a subcommand of **`/hexa`**. They are *client* commands: they never reach Hypixel, they work in
singleplayer, and they cannot collide with a server command of the same name — the root is `hexa` rather than `hex`
precisely because `/hex` is a Skyblock command.

Run `/hexa` on its own to see the mod version and which subcommands exist. Running a parent command such as
`/hexa region` with no subcommand prints its own subcommands rather than guessing at one.

## Overview

| Command | What it does |
|---|---|
| `/hexa` | Version and the list of subcommands |
| `/hexa config` | Open the [config menu](Configuration) |
| `/hexa keybinds` | Open the [Keybinds](Keybind-Shortcuts) screen |
| `/hexa hand …` | [Per-item swing](Per-Item-Swing) list |
| `/hexa item …` | [Item customization](Item-Customization) list |
| `/hexa remind …` | [Reminders](Reminders) |
| `/hexa region …` | [Regions](Regions) |
| `/hexa suggest …` | [Command suggestions](Command-Suggestions) |
| `/hexa update` | Check for a [mod update](Updating) now |

## Config and keybinds

```
/hexa config
```

Opens the settings menu. See [Configuration](Configuration) for the other three ways in.

```
/hexa keybinds
```

Opens the Hex Keybinds screen, where [keybind shortcuts](Keybind-Shortcuts) and
[control switches](Control-Switches) are configured.

## Hand

```
/hexa hand swing     — open the per-item swing list
/hexa hand toggle    — add the held item to the list, or remove it if already there
```

`toggle` is the same action as the **Toggle Swing For Held Item** keybind, for when you would rather not spend a key on
it. See [Per-item swing](Per-Item-Swing).

## Item customization

```
/hexa item list      — open the Customized items screen
```

The screen lists everything you have customized, so you can edit or delete an entry without going to find the item
first. See [Item customization](Item-Customization).

## Reminders

```
/hexa remind in <duration> <text>   — a one-off reminder
/hexa remind list                   — what is counting down
/hexa remind dismiss                — silence the one that is firing
/hexa remind snooze                 — push the firing one back
/hexa remind edit                   — open the reminders list
/hexa remind hud                    — open the panel position screen
/hexa remind presets                — open the presets catalogue
```

`<duration>` is a single word: `0`, `45s`, `20m`, `2h30m`, `4d`. `<text>` is the rest of the line, spaces and all:

```
/hexa remind in 5m check the forge
```

A reminder made this way fires once and then removes itself. See [Reminders](Reminders).

## Regions

```
/hexa region here [radius] [name]   — a region centred on where you stand
/hexa region mark                   — set a corner (the freecam's position when it is flying)
/hexa region walk [name]            — start/stop recording the outline you walk
/hexa region cancel                 — abandon the capture in progress
/hexa region list                   — the regions on this island
/hexa region preview                — draw every region in the world
/hexa region edit                   — open the regions list
```

`radius` is in blocks, between `1` and `256`; leave it out and the tab's default radius is used. `name` is the rest of
the line:

```
/hexa region here 20 dragon nest
```

Every capture ends by opening the region for editing. See [Regions](Regions).

## Command suggestions

```
/hexa suggest stats               — a summary of what has been learned
/hexa suggest dashboard           — open the "What Hex has learned" screen
/hexa suggest why <text>          — what it would suggest for <text>, and the full arithmetic
/hexa suggest forget <command>    — forget one command line
/hexa suggest clear confirm       — forget everything (settings are kept)
/hexa suggest pause               — keep the suggestions, record nothing new
/hexa suggest resume              — start learning again
```

`/hexa suggest clear` without `confirm` refuses and tells you what it would have done — it is not undoable. See
[Command suggestions](Command-Suggestions).

## Update

```
/hexa update
```

Checks GitHub for a newer release immediately instead of waiting for the next startup check. See [Updating](Updating).

## Notes

- Command output currently prints in **English** regardless of your Minecraft language; menus and HUD text are
  translated. See [Languages](Languages).
- Command *arguments* that are matched against Hypixel — island names, Skyblock item IDs — are always English, because
  they are compared to what the server says.
