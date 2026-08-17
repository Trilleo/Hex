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

## You write a title, you do not configure one

The screen is a **live editor**, the same shape the [notebook](Notebook) uses:

```
┌──────────────────────────────────────────────┐
│  B  I  U  S  K   │  &            [ Preview ] │   the toolbar
├──────────────────────────────────────────────┤
│                                              │
│              BOSS INCOMING                   │   the title, as it will look
│            get to the platform               │
├──────────────────────────────────────────────┤
│  Title     │ &c&lBOSS INCOMING             │ │   what you type
│  Subtitle  │ &7get to the platform         │ │
├──────────────────────────────────────────────┤
│  Preset · Fade in · Time on screen · …       │   the numbers
└──────────────────────────────────────────────┘
```

**The preview is the real thing**, not a mock-up: it is built by the same code that builds the title when the alert
fires, and redrawn every frame — so colours, styles and flowing [chroma](Chroma-Text) all look here exactly as they will
in play.

## The toolbar

Each button writes a `&` code **at the cursor**, in whichever of the two boxes you were last typing in. A code applies
from where you put it onward, so one line can carry several colours.

| Button | Writes | Effect                                                      |
|--------|--------|-------------------------------------------------------------|
| **B**  | `&l`   | Bold                                                        |
| **I**  | `&o`   | Italic                                                      |
| **U**  | `&n`   | Underline                                                   |
| **S**  | `&m`   | Strikethrough                                               |
| **K**  | `&k`   | Obfuscated — scrambles the letters, as an enchantment table |
| **&**  | —      | Opens the colour palette                                    |

The palette holds Minecraft's sixteen colours, **chroma**, a **reset** back to plain, the colours you have picked
recently *anywhere in the mod*, and a `#RRGGBB` field for any other colour. It opens over the rows below rather than
over the boxes, so you can watch the preview change as you click.

You can type the codes by hand instead — the boxes are only text. `&#FF8800` writes any colour, `&z` starts
[chroma](Chroma-Text), and `&r` goes back to plain.

```
&fBOSS &c&lINCOMING
```

is white, then bold red.

## The big line usually needs no words

The alert already has a message — a reminder's, a region's, a rule's — and the title shows it. So a title line of
**nothing but codes styles that message** rather than replacing it:

| You type    | You get                          |
|-------------|----------------------------------|
| *(nothing)* | The alert's message, plain       |
| `&c&l`      | The alert's message, in bold red |
| `&c&lBOSS`  | The word **BOSS**, in bold red   |

The Title box shows the alert's own message as its hint, so which of those you have written is visible before you leave
the screen.

The **Subtitle** box has no such rule: nothing else offers a second line, so whatever you type there is what appears,
and leaving it blank means no subtitle at all.

Both boxes understand `$0`–`$9` [capture groups](Reminders) on a chat-triggered reminder, exactly as its message does.

## How long it lasts

Three separate timings, rather than the one Hex used to offer.

| Setting            | Range            | Effect                                                     |
|--------------------|------------------|------------------------------------------------------------|
| **Fade in**        | 0 – 5 seconds    | How long the title takes to appear. Zero is instant        |
| **Time on screen** | 0.5 – 30 seconds | How long it holds at full brightness, once it has faded in |
| **Fade out**       | 0 – 5 seconds    | How long it takes to disappear again                       |

Thirty seconds of dwell is deliberate: "hold this until I have dealt with it" is a real thing to want from a boss
warning.

## Its own sound

Turn on **Play a sound** for a sound played the moment the title appears, with a **Sound** id, a **Pitch** and a
**Volume**. A bad id is reported as you type it.

**This is separate from the alert's own Play a sound action**, which exists so an alert can beep *without* showing
anything. An alert can have both, either, or neither.

## Presets

The **Preset** row writes a ready-made set of codes at the front of both lines and sets a sound, in one click.

| Preset      | Title  | Subtitle | Sound            |
|-------------|--------|----------|------------------|
| **Info**    | `&b`   | `&7`     | A soft chime     |
| **Success** | `&a&l` | `&7`     | The level-up     |
| **Warning** | `&6&l` | `&e`     | A high note      |
| **Alert**   | `&c&l` | `&7`     | An anvil landing |
| **Chroma**  | `&z&l` | `&z`     | The level-up     |

A preset is a **starting point, not a mode**. The codes it wrote are ordinary text you can then edit; only the *leading*
codes are replaced, so a `&e` you put mid-line to colour one word survives; your words and your timings are untouched;
and the row reads **Custom** again once the leading codes no longer match. Nothing stores which preset you used.

## Preview

**Preview** fires the title for real, behind the open menu — same colours, same fades, same sound. The live preview at
the top shows you the *look*; this button is how you judge the *timing*.

## The Titles tab

`/hexa config` → **Titles** holds what is true of every title at once.

| Setting                                          | Effect                                                                          |
|--------------------------------------------------|---------------------------------------------------------------------------------|
| **Enabled**                                      | Master switch. With it off, alerts still fire — they just say nothing on screen |
| **Sounds**                                       | Master switch for the sound a title plays                                       |
| **Default colour** / **Default subtitle colour** | The colour a line starts from when its own codes do not set one                 |
| **Chroma speed**                                 | How long one full trip through the rainbow takes. Lower is faster               |
| **Chroma width**                                 | How many characters one full rainbow spans                                      |
| **New title fade in** / **time** / **fade out**  | The timings a *newly created* title starts with                                 |
| **Preview**                                      | A sample title with nothing but these defaults on it                            |

The two default colours are chosen through the [colour picker](Colour-Picker), chroma included — set one to chroma and
every title that has not coloured itself will flow.

Two of those rows behave differently, and the difference is the point:

- The **default colours** are read **every time a title appears**. Change one and every title that never set its own is
  restyled at once.
- The **new title** timings are **seeds**: copied into a title when it is created, and never read again. A title's dwell
  time is part of that title, so changing a seed leaves the ones you have already tuned alone.

## Where the settings live

The Titles tab is stored in `config/hex/titles.json` — see [Config files](Config-Files). Each individual title's two
lines travel with the alert that owns it, in `reminders.json`, `regions.json`, `highlights.json` or
`chathighlights.json`, so duplicating a reminder duplicates its title and sharing a profile shares both.

## See also

- [Chroma text](Chroma-Text) — flowing colour, the `&z` code, and `&#RRGGBB`.
- [Colour picker](Colour-Picker) — how the Titles tab's default colours are chosen.
- [Reminders](Reminders), [Regions](Regions), [Entity highlight](Entity-Highlight),
  [Chat highlight](Chat-Highlight) — the four features that show titles.
