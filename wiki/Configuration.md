# Configuration

Everything Hex can be told to do lives in one menu. There is no scattering of settings across screens, and no separate
"advanced" config file you are expected to edit — though [the files are there](Config-Files) if you prefer them.

## Opening the menu

Four ways in, all equivalent:

- **`/hexa config`** in chat.
- **The □ button** next to **Done** on Minecraft's Options screen.
- **A keybind** — bind *Open Hex Config* under Options → Controls → **Hex**. It ships unbound.
- **Mod Menu's settings button**, if you have [Mod Menu](https://modrinth.com/mod/modmenu) installed. Closing the menu
  takes you back to the mod list.

The **□** button is on the Options screen wherever you open it from — the pause menu *or the title screen*. From the
title screen it is the only way in, since the command needs a chat box and the keybind needs a world.

## How the menu is laid out

- **Tabs down the side**, one per feature: Keybinds, Freecam, Hand, Item Customization, Titles, Reminders, Regions,
  Entity Highlight, Command Suggestions, Updates. Each feature contributes its own tab, so the sidebar reflects what the mod
  actually has.
- **A search box** filters settings across *all* tabs at once, so you can find a setting without knowing which feature
  owns it.
- **A reset button on every row**, restoring that one setting to its default.
- **A link to the Keybinds screen** for [keybind shortcuts](Keybind-Shortcuts) and [control switches](Control-Switches).

Settings apply **as you change them** — drag a slider and watch the result behind the menu. There is no Apply button and
no need to reopen anything.

## Resetting

- **Reset tab** (footer) restores everything on the current tab to its defaults, including that feature's own on/off
  switch. It asks first.
- **Reset all** (on the [Profiles](Config-Profiles) screen) restores every Hex setting to its default.

Both touch **live settings only**. Your saved profile is not rewritten, so **Discard** on the Profiles screen brings
everything back. A mis-clicked reset is never the end of a setup.

A few lists are deliberately not caught by a tab reset, because they are data rather than settings — the
[per-item swing list](Per-Item-Swing) survives a **Hand** tab reset, for instance.

## What each tab covers

| Tab                     | Covers                                                                                                         |
|-------------------------|----------------------------------------------------------------------------------------------------------------|
| **Keybinds**            | Master switch for [keybind shortcuts](Keybind-Shortcuts), and the way into their editor                        |
| **Freecam**             | Movement speed, sensitivity and behaviour of the [freecam](Freecam)                                            |
| **Sensitivity**         | Wheel step and snap-on-press for the [sensitivity hold](Mouse-Sensitivity)                                     |
| **Hand**                | First-person [hand position, scale, rotation and swing](Hand-Display), plus the per-item swing list            |
| **Item Customization**  | The [customized items](Item-Customization) list, the ✎ slot marker, and [chroma](Chroma-Text) speed and width |
| **Titles**              | Masters, fallback colours and chroma for every alert [title](Titles); the timings a new one starts with       |
| **Reminders**           | The [reminder](Reminders) panel's position, scale, colours and row count; chroma speed; snooze length          |
| **Regions**             | [Region](Regions) drawing, names, through-walls, colours and chroma speed, default radius, exit margin         |
| **Entity Highlight**    | The [highlight rules](Entity-Highlight) list, scan interval, default glow colour, chroma speed                 |
| **Chat Highlight**      | The [chat highlight rules](Chat-Highlight) list, default colour, and its [chroma](Chroma-Text) speed and width |
| **Command Suggestions** | Everything about [command suggestions](Command-Suggestions), and the dashboard                                 |
| **Updates**             | The [auto-updater](Updating) — per installation, never part of a profile                                       |

## Profiles

Whole settings setups can be saved, named, switched, shared and auto-applied per server or island. See
[Config profiles](Config-Profiles).

## Editing by hand

Settings are plain JSON under `config/hex/` — see [Config files](Config-Files) for the map. Hex writes those files as
you play, so edit them with the game closed, and note that a corrupt or unreadable file is silently replaced with
defaults rather than crashing your client.
