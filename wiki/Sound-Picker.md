# Sound picker

Every sound in Hex is chosen on one screen, exactly as every colour is chosen on the [colour picker](Colour-Picker).
Any setting that holds a sound shows a **▶** preview button and a button naming the current choice; click the name and
the picker opens.

That is true across the whole mod — [reminders](Reminders), [regions](Regions),
[entity highlights](Entity-Highlight), [chat highlights](Chat-Highlight), [titles](Titles) and the mod's own feedback
clicks — and it stays true for anything added later, because a sound setting has no other control to offer.

## What a sound setting can be

Three things, and the picker is how you say which:

| Choice           | What it does                                                        |
|------------------|---------------------------------------------------------------------|
| **A sound**      | One of the game's registered sounds, at a pitch and volume you set. |
| **A sequence**   | A saved [sound sequence](Sound-Sequences) — several sounds in order. |
| **None**         | Silence. Offered only where making no sound is a real answer.        |

## Finding a sound

The list holds **every sound this client has**, including anything another mod registered. It is read from the game's
own registry rather than from a written-down list, so it cannot fall behind a Minecraft update.

- **The filter button** narrows the list to one kind — `block`, `entity`, `ui`, `music`, `item` and so on — or to your
  own sequences. A modded sound gets a group named after its mod.
- **The search box** matches the part of the id after the colon first, because nobody types `minecraft:`. Searching
  `note_block.pl` finds `minecraft:block.note_block.pling`. A search that matches nothing anywhere still falls back to
  matching inside the id, so `spider` finds `cave_spider` sounds.

## Hearing it

This is the point of the screen. A list of thirty names you cannot hear is the problem, not the solution.

- **▶ on a row** plays that sound without choosing it.
- **Choosing a row** plays it too, so browsing with the mouse is browsing by ear.
- **Pitch** and **Volume** sit at the bottom of the same screen. Nudge one and press ▶ again — setting a pitch and
  hearing the result is one action rather than two rows apart.

Pitch runs `0.5`–`2.0`, which is playback speed: `0.5` is an octave down and `2.0` an octave up. Volume runs `0`–`100%`
and is scaled by **Master volume** on the Sounds tab, and by Minecraft's own volume sliders after that.

When the setting names a **sequence**, pitch and volume multiply every step in it — so `1.5` transposes the whole
phrase rather than flattening it.

## Sequences

**Sequences…** goes straight to the [sound sequence](Sound-Sequences) library, where you can build a new one or edit an
existing one, and comes back here afterwards. Your choice on this screen is kept while you are away.

## Keeping or discarding

**Done** writes the sound, pitch and volume to the setting. **Cancel**, Escape, or closing the screen any other way
leaves the setting exactly as it was.

This differs from the [colour picker](Colour-Picker), which applies as you drag: there is nothing behind a sound picker
that would change as you choose, and this screen can open another one, so it commits at the end instead.

## When something is missing

If a setting names a sound this client does not have — a modded sound after the mod was removed, or a typo in a
hand-edited config — the row says **No sound has that id** underneath. If it names a sequence that has since been
deleted, it says **That sequence no longer exists**.

In both cases the mod falls back to a plain UI click rather than going silent. A missed alert is a far worse failure
than a wrong-sounding one, and the row tells you where to fix it.

## The Sounds tab

The **Sounds** tab of the [config menu](Configuration) holds what is true of sound in general:

| Setting                | Does                                                                            |
|------------------------|---------------------------------------------------------------------------------|
| **Enabled**            | Master switch. Off, alerts still fire and titles still appear — silently.       |
| **Master volume**      | Scales everything Hex plays.                                                    |
| **Sound sequences**    | Opens the [sequence library](Sound-Sequences).                                  |
| **Default tempo**      | The tempo a newly created sequence opens on.                                     |
| **Switched on**        | The click when something is switched on, added, or armed.                       |
| **Switched off**       | The click when something is switched off or removed.                            |
| **Refused**            | The click when a keypress could not do anything.                                |
| **Captured**           | The click when a region, mob or item is captured out of the world.              |
| **Control switched**   | The click when a [control switch](Control-Switches) moves you between bindings.  |

Those five feedback sounds start on exactly the sounds and pitches the mod always made, so nothing changes until you
change it. Each is a full sound setting, so any of them can be a sequence.

The per-feature sound switches are separate and stay where they are: the [Reminders](Reminders) tab's **Sounds** switch
silences reminder alerts only, and the [Titles](Titles) tab's silences title stings only. Neither affects the rest of
the mod.

## See also

- [Sound sequences](Sound-Sequences) — building a sound out of several
- [Colour picker](Colour-Picker) — the same idea, for colour
- [Configuration](Configuration) — the settings menu
- [Config files](Config-Files) — where `sounds.json` lives
