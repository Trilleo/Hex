# Titles

A **title** is the big line of text across the middle of the screen — the way the game announces a boss, or a new
advancement.

Four features in Hex can show one: [reminders](Reminders), [regions](Regions), [entity highlight](Entity-Highlight) and
[chat highlight](Chat-Highlight). All four configure it **in the same place**, through one **Title style…** button in
their editor, so what you learn setting up a region applies unchanged to a chat rule.

> **Upgrading from 1.11.1 or earlier?** Each of those four used to carry its own three settings — a subtitle, a colour
> and a duration. Everything you had set is converted the first time this version loads it, and your titles go on
> looking exactly as they did.

## Opening the editor

In any of the four editors, turn on **Show as a title**, then press **Title style…**.

## The two lines

The big line and the smaller line beneath it are configured **identically**. A subtitle is not a lesser thing than a
title: each has a colour and the same five switches.

| Setting                                  | Notes                                                                            |
|------------------------------------------|----------------------------------------------------------------------------------|
| **Colour** / **Subtitle colour**         | Through the [colour picker](Colour-Picker). **None** uses the default on the Titles tab; **Chroma** flows |
| **Bold**                                 | Thicker text                                                                     |
| **Italic**                               | Slanted text                                                                     |
| **Underline**                            | A line under it                                                                  |
| **Strikethrough**                        | A line through it                                                                | 
| **Obfuscated**                           | Scrambles the letters, the way an enchantment table reads                        |
| **Subtitle**                             | The smaller line's text. Leave blank for no subtitle                             |

The **big line's text** is not set here — it comes from whatever fires the title: a reminder's message, a region's
message, a highlight rule's. That way a reminder that puts a captured number in its message puts it in the title too.

## Many colours on one line

The text takes the same `&` codes [item names](Item-Customization) and [notes](Notebook) do, so one line can carry
several colours and styles:

```
&fBOSS &c&lINCOMING
```

is white, then bold red. Beyond Minecraft's sixteen codes:

- **`&#RRGGBB`** — any colour at all, e.g. `&#FF8800`.
- **`&z`** — [chroma](Chroma-Text) from that point on, the same code NotEnoughUpdates and SkyHanni use.
- **`&r`** — back to the line's own colour and styles.

Codes and the switches above **compose**. The switches are the line's baseline, and a code overrides it from where it
appears — so `&l` mid-sentence adds bolding to a line that did not ask for it, rather than being overruled by it.

## How long it lasts

Three separate timings, rather than the one Hex used to offer.

| Setting            | Range           | Effect                                                     |
|--------------------|-----------------|-------------------------------------------------------------|
| **Fade in**        | 0 – 5 seconds   | How long the title takes to appear. Zero is instant         |
| **Time on screen** | 0.5 – 30 seconds| How long it holds at full brightness, once it has faded in  |
| **Fade out**       | 0 – 5 seconds   | How long it takes to disappear again                        |

Thirty seconds of dwell is deliberate: "hold this until I have dealt with it" is a real thing to want from a boss
warning.

## Its own sound

Turn on **Play a sound** for a sound played the moment the title appears, with a **Sound** id, a **Pitch** and a
**Volume**. A bad id is reported as you type it.

**This is separate from the alert's own Play a sound action**, which exists so an alert can beep *without* showing
anything. An alert can have both, either, or neither.

## Presets

The **Preset** row fills the colours, the switches and the sound in one click.

| Preset      | Look                                    | Sound                       |
|-------------|-----------------------------------------|-----------------------------|
| **Info**    | Aqua, grey subtitle                     | A soft chime                |
| **Success** | Green and bold                          | The level-up                |
| **Warning** | Gold and bold, yellow subtitle          | A high note                 |
| **Alert**   | Red and bold, pale red subtitle         | An anvil landing            |
| **Chroma**  | Both lines flowing, bold                | The level-up                |

A preset is a **starting point, not a mode**. Your text and your timings are left alone — they are the two things you
have already decided by the time you reach for a preset — and the row reads **Custom** again the moment you change
anything it wrote. Nothing stores which preset you used.

## Preview

**Preview** shows the title for real, behind the open menu: same colours, same fades, same sound, chroma and all. It is
not a mock-up drawn inside the list, which is the one thing that could be wrong about it.

## The Titles tab

`/hexa config` → **Titles** holds what is true of every title at once.

| Setting                                                | Effect                                                                              |
|--------------------------------------------------------|--------------------------------------------------------------------------------------|
| **Enabled**                                            | Master switch. With it off, alerts still fire — they just say nothing on screen      |
| **Sounds**                                             | Master switch for the sound a title plays                                           |
| **Default colour** / **Default subtitle colour**       | Used by any title that has not chosen one                                           |
| **Chroma speed**                                       | How long one full trip through the rainbow takes. Lower is faster                   |
| **Chroma width**                                       | How many characters one full rainbow spans                                          |
| **New title fade in** / **time** / **fade out**        | The timings a *newly created* title starts with                                     |
| **Preview**                                            | A sample title with nothing but these defaults on it                                |

Two of those rows behave differently, and the difference is the point:

- The **default colours** are read **every time a title appears**. Change one and every title that never chose a colour
  of its own is restyled at once.
- The **new title** timings are **seeds**: copied into a title when it is created, and never read again. A title's
  dwell time is part of that title, so changing a seed leaves the ones you have already tuned alone.

## Where the settings live

The Titles tab is stored in `config/hex/titles.json` — see [Config files](Config-Files). Each individual title's style
travels with the alert that owns it, in `reminders.json`, `regions.json`, `highlights.json` or `chat_highlights.json`,
so duplicating a reminder duplicates its title and sharing a profile shares both.

## See also

- [Colour picker](Colour-Picker) — how every colour in Hex is chosen.
- [Chroma text](Chroma-Text) — flowing colour, and the `&z` code.
- [Reminders](Reminders), [Regions](Regions), [Entity highlight](Entity-Highlight),
  [Chat highlight](Chat-Highlight) — the four features that show titles.
