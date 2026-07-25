# Keybinds

Hex registers its keys under **Options → Controls → Key Binds**, in their own **Hex** category, so they group together
instead of scattering into Misc.

**Every Hex key ships unbound.** Nothing is taken from you on install, and nothing fires until you have chosen a key for
it. Anything you never bind simply does nothing.

## The keys

| Key | What it does | More |
|---|---|---|
| **Open Hex Config** | Opens the settings menu | [Configuration](Configuration) |
| **Open Hex Keybinds** | Opens the Keybinds screen | [Keybind shortcuts](Keybind-Shortcuts) |
| **Toggle Freecam** | Detach / reattach the camera | [Freecam](Freecam) |
| **Cycle Attack Mode** | Flip Attack/Destroy between Hold and Toggle | [Attack mode switch](Attack-Mode-Switch) |
| **Toggle Swing For Held Item** | Add or remove the held item from the swing list | [Per-item swing](Per-Item-Swing) |
| **Customize Hovered Item** | Open the editor for the item under the cursor | [Item customization](Item-Customization) |
| **Open Reminders** | Opens the reminders list | [Reminders](Reminders) |
| **Dismiss Reminder** | Silence whatever is firing | [Reminders](Reminders) |
| **Snooze Reminder** | Push the firing reminder back | [Reminders](Reminders) |
| **Open Regions** | Opens the regions list | [Regions](Regions) |
| **Region Here** | A region centred on where you stand | [Regions](Regions) |
| **Mark Region Corner** | Set one corner, then the opposite one | [Regions](Regions) |
| **Walk Region** | Start/stop recording the outline you walk | [Regions](Regions) |
| **Toggle Region Preview** | Draw every region on the island in the world | [Regions](Regions) |

## Two different things called "keybinds"

This trips people up, so it is worth stating plainly:

- **Hex's own keys** — the table above. Bound in Minecraft's own Controls screen, one key each, no modifiers.
- **[Keybind shortcuts](Keybind-Shortcuts)** — a Hex *feature*, where a key combo (optionally with Ctrl/Shift/Alt) runs a
  sequence of commands or chat messages. Configured on the Hex Keybinds screen, not in Minecraft's Controls.

Hex also has [control switches](Control-Switches), which let a key combo *rebind* one of Minecraft's own controls
mid-game.

## Choosing keys that work

- **Customize Hovered Item** fires inside Hypixel's menus, where most letters already do something to the menu. Pick a
  key you do not otherwise use in a chest — a function key or a numpad key is a safe choice. It ships unbound for this
  reason.
- **Region Here**, **Mark Region Corner** and **Walk Region** are pressed while moving around the world, so avoid
  anything near WASD that you might catch by accident.
- **Dismiss Reminder** and **Snooze Reminder** are worth binding somewhere reachable if you use reminders at all —
  otherwise the only way to silence one is `/hexa remind dismiss`.

## Keybinds and profiles

Hex's own key bindings live in Minecraft's `options.txt` like every other mod's, not in Hex's config. By default a
[config profile](Config-Profiles) does **not** carry them. Turning on **MC keys** on the Profiles screen makes profiles
carry Minecraft's key bindings too — read that section first, because it carries *every* mod's bindings, not just Hex's.
