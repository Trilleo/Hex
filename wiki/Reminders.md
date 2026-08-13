# Reminders

Skyblock is full of things that quietly run out — a booster cookie, a potion, a forge slot, an ability cooldown — and
the only warning is a chat line that scrolls away seconds later.

Reminders let you say *"when **this** happens, tell me **then**"*, and show what is pending on a panel you can put
wherever you like.

Open the list with **Reminders…** in the **Reminders** tab of `/hexa config`, with `/hexa remind edit`, or with the
**Open Reminders** keybind.

## The three parts of a reminder

### 1. What starts it

Every reminder counts down; the trigger decides when the countdown begins.

| Trigger                              | Starts when                                             |
|--------------------------------------|---------------------------------------------------------|
| **Timer**                            | You start it, or it repeats on its own                  |
| **Chat message**                     | A line of chat matches your pattern                     |
| **Arriving at** / **Leaving island** | You reach or leave a named island, e.g. `dwarven mines` |
| **Entering** / **Leaving a region**  | You cross the boundary of a [region](Regions) you drew  |
| **Joining a world**                  | You log in                                              |
| **Holding an item**                  | You put a particular Skyblock item in your main hand    |

**Chat message is the powerful one.** It can watch for "your potion has expired" or "this ability is on cooldown for
30s" and start counting from there — see [Chat patterns](#chat-patterns).

### 2. When it speaks up

- **Remind me after** — the gap between the trigger and the reminder firing. Write `0` to fire straight away, or
  `45s`, `20m`, `2h30m`, `4d`.
- **Repeat** — the reminder starts itself again each time it fires.
- **Conditions…** — limit where it is allowed to fire, so a reminder that only matters in the Dwarven Mines stays quiet
  everywhere else. Conditions are checked **at the moment it fires**, not when it started, so leaving an island
  *silences* its reminders rather than losing them. A condition can test a [region](Regions) as well as an island, so a
  reminder can be limited to one room.

### 3. What it does

Show on the panel, play a sound, show the message as a big centred title, or any combination.

With **Show as a title** on, **Title style…** opens the full [title editor](Titles) — colours and styles on both
lines, a subtitle, the three fade timings and a sound of the title's own. It is the same editor
[regions](Regions), [entity highlight](Entity-Highlight) and [chat highlight](Chat-Highlight) use.

**Test** in the editor fires it immediately, so you can see and hear it before committing to it.

## Countdowns are real time

They keep running while you are logged out, so a four-day cookie is still counting when you come back. Anything that
came due while you were away fires **once**, marked overdue, rather than firing every interval it missed.

## Chat patterns

A chat trigger matches with a **regular expression** unless you turn on **Plain text**, which compares it as ordinary
text — the easier choice when you just want to match some words.

- Patterns are **case-sensitive**. Put `(?i)` at the start to ignore case.
- Anything you capture with `(…)` can be used in the message: **`$1`** … **`$9`** for the captured parts, **`$0`** for
  the whole match.

```
Pattern:  on cooldown for (\d+)s
Message:  Ability ready in $1s
```

- Write **`$$`** for a literal dollar sign. A `$5` that the pattern has no fifth group for is left alone, so prices in
  your reminder text survive untouched.
- If a pattern turns out to be so expensive to match that it would slow the game down, Hex **switches that reminder off
  and says which one in chat**, rather than letting it stutter on every message you receive.

Patterns are matched against the message **as Hypixel sent it** — so they are written in English regardless of your
Minecraft language. See [Languages](Languages).

## The panel

**Panel position…** opens a screen where you drag the panel where you want it, or nudge it with the arrow keys — hold
**Shift** to move further.

- It is placed as a **fraction of the screen**, so it stays put when you change resolution, go fullscreen, or change
  your GUI scale.
- **Grow from** picks which corner stays anchored as reminders come and go.
- The rest of the **Reminders** tab covers scale, colours, how many rows to show, and whether to hide the panel off
  Skyblock. The background, text and flash colours each go through the [colour picker](Colour-Picker) and each can be
  set to **Chroma**, at the speed set by **Chroma speed** on the same tab.
- The panel **hides with the rest of the HUD when you press F1**.

Two keys, both bound under Options → Controls → **Hex**:

- **Dismiss Reminder** — silence whatever is flashing, without opening anything.
- **Snooze Reminder** — push it back instead, by the amount set in the tab.

## Presets

**Presets…** is a small catalogue of ready-made reminders. Adding one **copies** it into your list, so you are free to
edit it afterwards.

- If a later version of Hex ships a corrected version of a preset you have **not** edited, yours is updated in place —
  keeping its on/off state and any running countdown.
- If you **have** edited it, Hex leaves it alone, and the editor offers **Reset to preset** for whenever you want the
  newer version.

The catalogue is deliberately small. A preset whose chat pattern no longer matches is worse than no preset at all,
because there is no way to tell it apart from a broken feature — so only patterns confirmed against live Hypixel are
included.

## Commands

```
/hexa remind in 5m check the forge   — a one-off; it removes itself once fired
/hexa remind list                    — what is counting down
/hexa remind dismiss                 — silence the one that is firing
/hexa remind snooze                  — push it back
/hexa remind edit | hud | presets    — open the three screens
```

## Storage

| File                             | Holds                                  | Travels with a [profile](Config-Profiles)? |
|----------------------------------|----------------------------------------|--------------------------------------------|
| `config/hex/reminders.json`      | The reminders and the panel's settings | ✅                                         |
| `config/hex/reminder_state.json` | Every reminder's live countdown        | ❌                                         |

The split is deliberate: switching profiles changes *which reminders you have* without resetting the timers you have
running, and sharing your settings does not hand someone else your countdowns.

## Related

- [Regions](Regions) — draw an area, then arm a reminder with it. **Add reminder** in a region's editor creates one
  already armed by that region.
