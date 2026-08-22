# Sound sequences

A sequence is a sound made out of several of the game's sounds, with an order and a delay: four insistent notes, a
rising chime, a two-note coin flick. Anywhere Hex can play a sound it can play a sequence instead — so a
[reminder](Reminders), a [region](Regions), an [entity highlight](Entity-Highlight), a
[chat highlight](Chat-Highlight), a [title](Titles), or one of the mod's own feedback clicks can be a phrase rather
than a beep.

## Getting there

- **Config menu → Sounds → Sound sequences**, or
- the **Sequences…** button in the [sound picker](Sound-Picker), which comes straight back afterwards.

**Add** builds one from scratch and opens it. **Presets** offers five ready-made ones. Each row in the library shows
the sequence's name, the `@id` that names it, and how many sounds it holds and how long it runs.

## Choosing a sequence

Once a sequence exists, it appears in the [sound picker](Sound-Picker) under the **Sequences** filter, alongside every
sound in the game. Pick it the same way you would pick a single sound.

In a config file it is written as `@` followed by its id — `"@alarm"`, `"@boss-warning"` — in the same field that would
otherwise hold `minecraft:block.note_block.pling`. That is why the row shows the id: it is what you type if you edit a
config by hand.

> **A sequence's id never changes.** Its **name** can be rewritten whenever you like, but the id it was given when it
> was created is what every setting pointing at it uses, so it stays fixed. Renaming a sequence never breaks an alert.

## The editor

The editor is a timeline. A ruler runs along the top, eight tracks sit under it, and each sound in the sequence is a
**clip** placed where its time puts it.

| Action                | Does                                                                              |
|-----------------------|-----------------------------------------------------------------------------------|
| Drag a clip sideways  | Change when it plays                                                              |
| Drag a clip up / down | Move it to another track                                                          |
| Drag on empty space   | Select every clip in the box                                                      |
| Ctrl+click a clip     | Add it to, or remove it from, the selection                                       |
| Drag on the ruler     | Move the playhead                                                                 |
| Scroll                | Move along the timeline                                                           |
| Ctrl+scroll           | Zoom, about the pointer                                                           |

Everything selected moves together, so a whole phrase can be shifted or retimed in one drag.

**Tracks are only a way of arranging the picture.** Two sounds at the same moment play together whichever tracks they
are on. They exist because a drone under a melody is unreadable when both are on one row.

**A clip's fill height is its volume**, so a quiet sound reads as a short block without having to select it.

## The grid

The ruler is marked in **seconds**, because that is what the times are, and ruled in **beats** at a tempo you set —
which is what makes anything rhythmic land where you want it.

**Snap** chooses how finely clips land while you drag:

| Snap    | At 120 BPM |
|---------|------------|
| Off     | anywhere   |
| 1/4     | 500 ms     |
| 1/8     | 250 ms     |
| 1/16    | 125 ms     |

Hold **Alt** while dragging to ignore snapping for that drag.

Changing the **tempo** re-rules the grid and never moves a sound you have already placed. Times are stored as absolute
milliseconds, not as beats, so the grid is a guide rather than a container.

## Notes instead of numbers

Note block sounds are tuned instruments, so when a step uses one — `block.note_block.pling`, `.bell`, `.bass`, and the
rest — its pitch is set as a **note**: F#3 up to F#5, the twenty-five notes a note block actually has. The clip labels
itself with the note, so a melody is readable on the timeline.

F#4 is the sound as recorded. Every other sound in the game is a recording rather than an instrument, and keeps a plain
pitch number from `0.5` to `2.0`.

## Keyboard

| Key         | Does                                              |
|-------------|---------------------------------------------------|
| Space       | Play / stop                                       |
| Delete      | Remove the selection                              |
| Ctrl+D      | Duplicate the selection one grid step later       |
| Ctrl+A      | Select everything                                 |
| Ctrl+Z      | Undo                                              |
| ← →         | Nudge the selection by one grid step              |
| ↑ ↓         | Move the selection between tracks                 |
| Escape      | Close the editor                                  |

Undo goes back thirty-two steps and covers every edit, including drags.

## The panel on the right

With **one clip selected** it shows that step:

| Setting            | Does                                                                  |
|--------------------|-----------------------------------------------------------------------|
| **Sound**          | Opens the [sound picker](Sound-Picker).                               |
| **Note** / **Pitch** | A note for note block sounds, a number for everything else.         |
| **Volume**         | How loud this step is.                                                |
| **Time**           | When it plays, in seconds from the start.                             |
| **Track**          | Which row it sits on.                                                 |

With **nothing selected**, or **several**, it shows the sequence itself:

| Setting     | Does                                                       |
|-------------|------------------------------------------------------------|
| **Name**    | What the library and the picker call it.                   |
| **Tempo**   | The grid, in beats per minute.                             |
| **Loop**    | Play the whole sequence more than once per trigger.        |
| **Times**   | How many passes, `1`–`8`. Only shown when Loop is on.      |

> A step cannot name another sequence. A sequence containing itself would play forever, so the picker inside the editor
> offers sounds only.

## Presets

Five sequences ship with the mod, under **Presets**. Adding one copies it into your library, so you can edit it freely
afterwards.

| Preset         | What it is                                                             |
|----------------|------------------------------------------------------------------------|
| **Alarm**      | Four insistent notes. Hard to miss.                                    |
| **Chime**      | A soft rising triad, for something you want noticed rather than shouted.|
| **Fanfare**    | A short rising run with a bass note under it.                          |
| **Countdown**  | Three ticks and a chime, one every half second.                        |
| **Coin**       | Two quick high notes.                                                  |

As with [reminder presets](Reminders), a later version of the mod can improve a preset you have **not** edited without
touching one you have — and it never changes the id, so an alert pointing at a preset keeps working across updates.

## Limits

| Limit                        | Value      |
|------------------------------|------------|
| Sounds in one sequence       | 128        |
| Length of a sequence         | 60 seconds |
| Tracks                       | 8          |
| Tempo                        | 40–300 BPM |
| Loop passes                  | 8          |
| Sequences playing at once    | 4          |

These are generous for an alert and exist so a hand-edited config cannot exhaust the game's sound engine. A sequence
past a limit is trimmed when it loads rather than refused.

## Where they are stored

`config/hex/sounds.json`, alongside the feedback sounds and the sound settings. Sequences travel with
[config profiles](Config-Profiles), so a profile carries its own alert sounds. See [Config files](Config-Files).

## See also

- [Sound picker](Sound-Picker) — choosing a sound or a sequence
- [Reminders](Reminders), [Regions](Regions), [Entity highlight](Entity-Highlight),
  [Chat highlight](Chat-Highlight), [Titles](Titles) — everything that can play one
- [Config files](Config-Files) — the shape of `sounds.json`
