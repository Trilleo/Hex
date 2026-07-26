# Config files

Hex keeps everything under **`config/hex/`** in your Minecraft folder, as plain, pretty-printed JSON. You never have to
touch these — the [config menu](Configuration) covers everything — but they are readable, portable, and safe to back up.

## The files

| File                  | Holds                                                                           | In a [profile](Config-Profiles)? |
|-----------------------|---------------------------------------------------------------------------------|----------------------------------|
| `profiles.json`       | Which profiles exist, which is active, their auto-switch rules                  | — (owns the system)              |
| `profiles/<name>/`    | One directory per profile: a copy of the files below                            | —                                |
| `keybinds.json`       | [Keybind shortcuts](Keybind-Shortcuts) and [control switches](Control-Switches) | ✅                               |
| `freecam.json`        | [Freecam](Freecam) settings                                                     | ✅                               |
| `hand.json`           | [Hand display](Hand-Display) settings                                           | ✅                               |
| `swing_items.json`    | The [per-item swing](Per-Item-Swing) list                                       | ✅                               |
| `reminders.json`      | [Reminders](Reminders) and their panel settings                                 | ✅                               |
| `regions.json`        | [Regions](Regions) and their settings                                           | ✅                               |
| `suggest.json`        | [Command suggestions](Command-Suggestions) *settings*                           | ✅                               |
| `notebook.json`       | [Notebook](Notebook) *display settings* — sort, previews, opacity, editor layout | ✅                               |
| `vanilla_keys.json`   | Minecraft's own key bindings, when **MC keys** is on                            | ✅                               |
| `item_custom.json`    | [Item customizations](Item-Customization)                                       | ❌ per installation              |
| `update.json`         | [Auto-updater](Updating) settings                                               | ❌ per installation              |
| `reminder_state.json` | Every reminder's live countdown                                                 | ❌ per installation              |
| `suggest/model.json`  | What command suggestions has learned                                            | ❌ per installation              |
| `notebook/index.json` | A summary of your [notes](Notebook), so the list opens without reading them all | ❌ per installation              |
| `notebook/notes/`     | One `.md` file per note — **the notes themselves**                              | ❌ per installation              |
| `update/`             | A downloaded jar waiting to be applied on exit                                  | ❌ transient                     |

## Why some files stay out of profiles

The distinction is **loadout versus installation**:

- A **loadout** is how you want the mod to behave — panel position, hand scale, which reminders exist. Switching
  profiles should swap all of it.
- An **installation** property describes *you and your machine*, not a setup. Whether Hex updates itself, which of your
  items you have reskinned, how far into a four-day cookie you are, which commands you personally type, and what you
  have written down are all in this group.

Swapping those on a profile switch would be actively wrong: it would silently turn the updater off, hand your command
history and your notes to anyone you shared settings with, and reset a countdown you were relying on. So they stay put.

The same split decides what **Copy to clipboard** contains: profiled files only.

## Editing by hand

- **Close the game first.** Hex writes these files as you play (on a short debounce, and again on shutdown), so an edit
  made while the game is running will be overwritten.
- **A broken file costs you that file, not your client.** If a config cannot be read, Hex logs it and falls back to
  defaults rather than crashing — which also means a JSON syntax error silently resets those settings the next time they
  are saved.
- **Unknown fields are ignored**, and missing ones are repaired to sane values, so a file from a slightly different
  version usually loads fine.

## Backing up and moving between machines

Copy the whole `config/hex/` directory. That carries everything — profiles, regions, reminders and their countdowns,
item customizations, your notes, and what suggestions has learned.

Your notes are the one part of that you can also move a piece at a time: `notebook/notes/` is a directory of ordinary
`.md` files. Copy one across, or drop a Markdown file in from anywhere, and Hex picks it up. See [Notebook](Notebook).

To move only your *settings*, use **Copy to clipboard** on the Profiles screen instead: it is one blob of text, it can
be pasted on any machine running the same Hex version or newer, and it deliberately leaves your personal data behind.

## Resetting

- One setting — the reset button on its row.
- One tab — **Reset tab** in the footer.
- Everything — **Reset all** on the Profiles screen.
- Absolutely everything, including profiles and learned data — delete `config/hex/` with the game closed.

The first three touch live settings only and are undone by **Discard**. The fourth is not undoable.
