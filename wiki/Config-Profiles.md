# Config profiles

A **profile** is a complete set of Hex settings under a name. One for Dungeons with the reminder panel front and centre,
one for Mining with a different hand position, one bare setup for when you are just standing in the Hub — and a way to
move between them without redoing anything.

Open **Profiles…** from the [config menu](Configuration) footer. Each profile shows its description and when it was last
saved.

## Managing profiles

| Control    | What it does                                                         |
|------------|----------------------------------------------------------------------|
| **Switch** | Make this profile the active one                                     |
| **⧉**      | Copy it                                                              |
| **✎**     | Rename it, describe it, set what it auto-switches on                 |
| **✕**     | Delete it (asks first; the last remaining profile cannot be deleted) |
| **New**    | Create a profile from the settings you have right now                |

## Saving is explicit

This is the one rule worth internalising:

> Changing a setting takes effect immediately, but does **not** write it into your profile.

A **`*`** next to the profile's name means your live settings have moved away from what the profile holds.

- **Save** folds your current settings into the profile.
- **Discard** reloads the profile as it was last saved, throwing away the changes.

Switching to another profile while that `*` is showing **asks first** — save or discard — so nothing disappears without
you choosing it. Nothing is auto-saved behind your back.

## Switching automatically

A profile can activate by itself when you arrive somewhere. Open **✎** on the profile and pick what it activates on:

- **A server** — matches the address you connected to. `hypixel.net` also matches `mc.hypixel.net`.
- **Singleplayer** — any single-player world.
- **A Skyblock island** — matches the island you are on, e.g. `private island` or `hub`.

Hex asks Hypixel which island you are on rather than reading the scoreboard, so an island rule names the **whole
island** (`hub`), not the smaller area you happen to be standing in (`village`). It resolves a moment after you join, so
a switch lands a beat after the world loads.

Three things shape how this behaves:

- **Order wins.** If two profiles claim the same place, the one higher in the list is used.
- **A manual switch turns auto-switching off** until you disconnect, so it never overrides a deliberate choice.
- **Unsaved changes block it entirely.** You get a message instead of a switch — losing an unsaved setup to a warp would
  be worse than not switching.

Island names are typed in **English**, because they are compared to what Hypixel reports. See [Languages](Languages).

## Minecraft's key bindings

**MC keys** on the Profiles screen makes profiles carry **Minecraft's own key bindings** as well as Hex's settings, so a
profile becomes a whole control setup rather than just the Hex half of one.

It is **off by default, and that default is the right one for most people.** Minecraft keeps every mod's bindings in one
place and Hex cannot tell them apart. With **MC keys** on, a profile saved while another mod was bound to `G` will put
that mod back on `G` when the profile is restored — even if you have since rebound it inside that mod's own screen.

Also note: with this on, using a [control switch](Control-Switches) counts as changing your settings, so it marks the
profile as having unsaved changes.

## Sharing and backups

- **Copy to clipboard** copies every profile-carried Hex setting as text. Send it to someone, or paste it into a file as
  a backup.
- **Paste from clipboard** takes one back, either **as a new profile** (your current settings stay intact) or **over
  this profile**.

Version handling is strict on purpose: a blob from a **newer** Hex than you are running is **refused** rather than
partly applied, and one from a different version says so before it lands.

What is **not** in a copied blob:

- The **Updates** tab — a property of your install, not a loadout.
- **[Item customizations](Item-Customization)** — they describe items you own.
- **What [command suggestions](Command-Suggestions) has learned** — nobody gets your command history by asking for your
  settings.
- **[Reminder](Reminders) countdowns** — the reminders travel, the running timers do not.

## Reset all

**Reset all** restores every Hex setting to its default. Like a per-tab reset it leaves your saved profiles alone, so
**Discard** undoes it.

## Where they live

Your **live** settings stay where they always were, at `config/hex/<name>.json`. A profile is a *copy* of those files
under `config/hex/profiles/<profile>/`, and `config/hex/profiles.json` records which profiles exist and which is active.
Nothing about the on-disk layout changes for someone who never opens this screen.

A profile that has no file for some config leaves that config alone rather than resetting it, so a partial profile — one
pasted from an older version, say — inherits the rest of your settings instead of blanking them. See
[Config files](Config-Files).
