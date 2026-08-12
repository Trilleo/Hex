# Chat highlight

Pick the words you care about and they stand out in chat — repainted in a colour of your choosing, optionally marked,
optionally announced with a title and a sound, optionally hidden altogether. It is the sibling of
[entity highlight](Entity-Highlight), for the half of Skyblock that arrives as text rather than as a mob.

Open the list from the **Chat Highlight** tab of `/hexa config`, with `/hexa chat edit`, or by binding **Open Chat
Highlights** under Options → Controls → **Hex**.

## Making one

Press **Add** in the list, or run `/hexa chat add`. Either way a blank rule opens for editing, and the only field it
really needs is **Text to find**.

Chat has no crosshair to point at something with, so there is no "add what you are looking at" here. What replaces it is
being shown the answer instead of having to guess at it — in two places:

- **The preview.** The line above the editor's buttons is your rule, applied to a sample message. Colour, style, marks,
  scope and chroma all show up there as you set them, and chroma genuinely flows, so a rule can be judged before a real
  message ever arrives.
- **`/hexa chat test <line>`.** Supply a line yourself and Hex prints it exactly as chat would have shown it, followed
  by which rules claimed it and whether they would have hidden it.

## Matching

**Text to find** is plain text, not a pattern. A message counts when it contains that text anywhere, and by default
capitals are ignored — turn on **Match capitals** when the difference matters, as it does for an all-caps broadcast and
does not for a name someone might type either way.

Plain text rather than a regular expression is a deliberate limit. [Reminders](Reminders) already own the pattern-shaped
job, with the safeguards a player-written pattern needs; a highlight that only ever searches for a literal string cannot
misbehave no matter what is typed into it. If you need capture groups, conditions or a countdown, write a reminder.

A rule with an empty **Text to find** never matches anything, so a half-written rule cannot repaint your whole chat.

### Where a rule applies

| Setting     | Restricts the rule to                                                                     |
|-------------|-------------------------------------------------------------------------------------------|
| **Channel** | One chat: public, party, guild, officer, co-op or private. **Any** listens to all of them |
| **Islands** | One or several islands, comma-separated. Blank means anywhere                             |

**Channel** is what lets "my name" highlight in party chat without firing on every guild message. Hex reads the channel
from the tag Hypixel puts at the front of the line, and it insists on a speaker before it believes one: `Guild > Steve:
hi` is guild chat, while `Guild > Steve joined.` is the server talking *about* the guild and belongs to no channel at
all. Only an **Any** rule sees those server broadcasts — which is most of what is worth catching on Skyblock, and why
**Any** is the default.

Nobody can spoof their way past this by typing `Party > x: hi` in public chat: only the leading tag is read, and in
public chat it arrives behind the speaker's own name.

**Islands** takes a list, unlike [entity highlight](Entity-Highlight)'s single island, because chat follows you around
in a way a mob does not:

```
hub, dwarven mines, crystal hollows
```

Leave it blank and the rule works everywhere, which is the normal case. The list's **All islands** / **This island**
button filters what you are looking at without changing any rule.

## Seeing them

**Paint** decides how much of the message is repainted:

| Paint             | Covers                                    |
|-------------------|-------------------------------------------|
| **The match**     | Only the words that matched               |
| **Whole message** | The entire line, from the first character |

Every occurrence in a message is painted, not just the first.

**Colour** is the rule's own, falling back to the tab's default when left alone. On top of it, any of **Bold**,
**Italic**, **Underline**, **Strikethrough** and **Scrambled** can be turned on. Scrambled draws text as characters that
never settle — it hides what it marks, so it suits censoring a word rather than highlighting one.

### Chroma

**Chroma** flows the highlighted text through the rainbow instead of holding one colour, the same effect
[chroma text](Chroma-Text) gives an item name. It replaces the colour setting entirely, which is why the colour row
disappears while it is on.

**Chroma speed** and **Chroma width** live on the settings tab rather than on each rule, so everything in Hex that flows
flows at one rate. Speed is how long a full trip through the rainbow takes; width is how many characters one rainbow
spans — set it wide and a short word shifts colour as a whole rather than striping.

The colours travel along the text and restart on each wrapped line, so a long message reads as one rainbow per row.

### Marks

**Mark before** and **Mark after** put a little text either side of the match — `»` and `«`, or `[` and `]`, or anything
up to sixteen characters:

```
Someone: is that »a rare drop« over there?
```

A colour is invisible to a colour-blind player and survives no screenshot compression worth the name. A mark does both,
which is the reason this exists rather than being decoration.

### Clicking and hovering still work

Hex repaints the words *inside* Hypixel's own message rather than rebuilding the message from its text, so everything
else about the line survives: a party invite you can click, an item you can hover, a name you can shift-click. That is
worth knowing because it is the part a highlight could easily have broken.

The one limit is the reverse case: Hypixel pads its lines with invisible characters, and a rule whose text would span
one of them does not match. Highlight a word rather than a whole decorated phrase and this never comes up.

## Hiding a message

**Hide the message** drops a matching line from chat entirely — a blacklist, in the same place as the paint.

The pairing is the point: hiding and announcing are not opposites. A rule can silence a firehose of spam and still play
its sound for the one line you were watching for. A hidden message also still reaches the rest of the mod, so a
[reminder](Reminders) armed on the same text still fires and [command suggestions](Command-Suggestions) still learn from
it. Hiding it from your chat window does not hide it from Hex.

## Notifications

Turn on **Announce matches** and the rule speaks up when a message matches.

| Setting             | Notes                                                                 |
|---------------------|-----------------------------------------------------------------------|
| **Message**         | The title's text. Blank uses the rule's name                          |
| **Show as a title** | Subtitle, colour and how long it holds                                |
| **Play a sound**    | Any sound id, with pitch and volume. A bad id is reported as you type |
| **Cooldown**        | How long the rule stays quiet afterwards                              |

**Test** fires the actions on the spot, ignoring the cooldown, so a sound and a title can be judged without waiting for
a message.

**The cooldown matters more here than it does for a mob.** An entity is announced once because Hex remembers which
entities it has already seen; a chat line has no such identity, so without a cooldown a rule watching a busy channel
would fire on every single message that mentions its word. Ten seconds is the starting point.

These are the same actions [reminders](Reminders), [regions](Regions) and [entity highlight](Entity-Highlight) use, so
they behave identically and the reminder tab's master sound switch covers them too.

## When two rules want the same words

Rules are tried in list order, and **the first one wins any words two rules both claim**. That makes the order
meaningful: put a specific rule above a broad one and it keeps its colour on the overlap.

The loser is dropped from that stretch rather than trimmed, because half a word in one colour and half in another is not
what either rule was asking for. Both rules still count as having matched, so both can still announce themselves and
either can hide the line.

## Settings

The **Chat Highlight** tab of `/hexa config`:

| Setting            | Notes                                                                                          |
|--------------------|------------------------------------------------------------------------------------------------|
| **Enabled**        | Master switch. With it off nothing is repainted, hidden or announced, but your rules are kept  |
| **Highlights…**    | Opens the rule list                                                                            |
| **Default colour** | The colour a rule uses when it names none of its own                                           |
| **Chroma speed**   | How long one full trip through the rainbow takes. Lower is faster. Shared by every chroma rule |
| **Chroma width**   | How many characters one rainbow spans                                                          |
| **Skyblock only**  | Ignore every rule unless the scoreboard looks like Skyblock's                                  |

Messages already in your chat log keep whatever they were given when they arrived — chat is styled once, on arrival, and
cannot be repainted afterwards. Switching a rule on or off changes the next message, not the ones above it.

## Commands

```
/hexa chat add [name]    — a new rule, opened for editing
/hexa chat list          — every rule, what it catches, and whether it is on
/hexa chat test <line>   — run a line of your own past your rules
/hexa chat edit          — opens the rule list
```

`name` is the rest of the line and only sets the new rule's label; what it looks for is typed into the editor. Command
output is English whatever your language is set to — see the note in [Commands](Commands).

## Keybinds

| Key                      | Does                                                          |
|--------------------------|---------------------------------------------------------------|
| **Open Chat Highlights** | Opens the rule list. Works even with the feature switched off |

Unbound by default. Bind it under Options → Controls → **Hex**.

## Where they live

Chat highlights live in `config/hex/chathighlights.json` and take part in [config profiles](Config-Profiles) and
clipboard sharing, so a set of rules is something you can hand to someone else.

## Related

- [Entity highlight](Entity-Highlight) marks a **thing in the world**; this marks **words in chat**. Same shape of rule,
  same title and sound settings.
- [Reminders](Reminders) — if you want a pattern, capture groups, conditions or a countdown rather than a repaint.
- [Chroma text](Chroma-Text) — the same flowing colour, on item names.
- [Commands](Commands) — every command Hex registers.
- [Keybinds](Keybinds) — every key Hex registers.
- [Config files](Config-Files) — where `chathighlights.json` sits, and whether it travels with a profile.
