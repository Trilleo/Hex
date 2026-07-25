# Command suggestions

Hex watches which commands you run, learns your habits, and offers them back the next time you open chat.

> **Nothing is sent anywhere** — it all lives on your computer — and **nothing is ever run for you**: it fills in the
> box, you press Enter.

## The three ways it helps

### The list

Start typing a command and a ranked list appears above the chat box, ordered by what you actually use rather than
alphabetically.

| Key              | Does                                                               |
|------------------|--------------------------------------------------------------------|
| **↑ / ↓**        | Move through the list                                              |
| **Tab** or **→** | Take the highlighted one (whichever you set under **Accept with**) |
| **Escape**       | Dismiss the list without closing chat                              |
| **Click**        | Pick that row                                                      |

Each row carries a small note on the right — *here*, *next*, *often*, *holding* — saying in one word why it made the
list. A line too long for the chat box is shortened with an ellipsis; what gets typed in is always the whole thing.

### Inline completion

As you type, the rest of the line appears greyed out ahead of the cursor; **Tab** or **→** accepts it.

It follows the list: arrow down to another suggestion and the greyed-out text becomes that one, so what is highlighted
and what is written ahead of the cursor are never two different answers.

This is where it beats Hypixel's own tab-completion, which knows the command `/warp` exists but has no idea that when
*you* type `/warp d` you mean `dungeon_hub`. It only appears when the guess is a confident one — how confident is the
**Inline threshold** slider.

### Just a slash

Type **`/`** on its own and it offers what you are most likely to want *right now*.

That is a real prediction, not a most-used list: it changes with where you are standing, what you are holding, what you
last ran, and what chat said in the last few seconds. A party invite thirty seconds ago moves `/party accept` to the
top; the same slash in the Dwarven Mines while holding a drill offers something else entirely.

## What it pays attention to

Where you are (island, and the patch of ground you are on), what is in your hand and what kind of thing it is, your
hotbar and armour, how full your inventory is, how long you have been online, the last two commands you ran, and whether
chat has just asked you something.

It learns **which of those actually predict *your* commands** and ignores the rest — and it learns how much to trust
each one from which suggestions you pick, so it gets better at being useful to you specifically rather than to players
in general.

### It reads Skyblock's own calendar too

- **The season.** A Skyblock year passes in a little over five real days, so autumn comes round often enough to learn
  from — which is what makes the Spooky Festival commands start appearing before you have thought of them. Read off the
  scoreboard.
- **Skyblock time of day.** Not your clock — Skyblock's, which runs a full day every twenty real minutes. What you do
  when it is dark on Skyblock has nothing to do with what you do at night where you live, and both are learned
  separately. It takes the sun/moon marker as the answer when Hypixel shows one, rather than guessing from the hour.
- **The running event.** The strongest signal of the lot. When the Dark Auction starts counting down, `/warp da` is very
  nearly something you have already announced — and after a couple of auctions Hex knows it.

### Where the event comes from

Hypixel scatters this one, so Hex looks everywhere it is stated:

| Source                            | Gives                                                                                |
|-----------------------------------|--------------------------------------------------------------------------------------|
| **Player list** (`Event:` widget) | The Skyblock-wide event on every island, and how long is left — the best of the four |
| **Boss bar**                      | The only place a mining event (`2X POWDER`, `GOBLIN RAID`) shows up                  |
| **Scoreboard**                    | The island events that reach it                                                      |
| **Chat**                          | Shouts an event's start before anything else knows                                   |

If you have turned Hypixel's tab-list widgets off, **turning them back on makes this noticeably sharper.**

An event Hex has never heard of still counts, under whatever name Hypixel used. When two events overlap, the one ending
soonest is credited — the mining event with four minutes left, not the festival with three days. One that has not
started yet counts only inside the last ten minutes of its countdown.

Rows in the list say which signal did the work, so a suggestion that turns up during a Dark Auction is labelled *dark
auction*, and one that turns up because it is dark is labelled *night*.

Suggestions the **server** offers are kept and re-ranked, never thrown away, so a command Hypixel added last week still
appears — just in the right place in the list.

## What it never records

> **Message text is never learned.**

For `/msg`, `/w`, `/r`, `/pc` and the like, Hex records the command and who you sent it to, and **throws away what you
said before anything is written to disk**. A command Hex has never heard of records at most its first word, so this
holds even for commands from a mod or a server it knows nothing about. Turn **Learn player names** off and the names go
too.

What it has learned lives in `config/hex/suggest/model.json` and is deliberately **not** part of
[config profiles](Config-Profiles): switching profiles never swaps it, and **Copy to clipboard** never contains it.
Nobody gets a copy of your command history by asking for your settings.

## Seeing and changing what it knows

**What it has learned…** in the **Command Suggestions** tab (or `/hexa suggest dashboard`) lists every command line it
holds, with how often you use it, when you last did, and what it has associated the command with — *island: dwarven
mines*, *holding: TITANIUM_DRILL*. Anything wrong can be fixed on the spot:

| Control               | Does                                                                               |
|-----------------------|------------------------------------------------------------------------------------|
| **☆**                | Pin a command to the top of every list it appears in                               |
| **○**                 | Block one — never suggested, never recorded again                                  |
| **✕**                | Forget it                                                                          |
| **?**                 | Show the full arithmetic: every signal, what it was worth, how much it counted for |
| **Forget everything** | Wipe the lot. Your settings are kept.                                              |

`/hexa suggest why <text>` prints the same arithmetic in chat, for the situation you are actually in.

## Settings

All in the **Command Suggestions** tab of `/hexa config`:

| Setting                    | Notes                                                                                                                                                                                            |
|----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Enabled**                | Plus a switch for each of the three surfaces separately                                                                                                                                          |
| **Keep learning**          | Pause to keep the suggestions you have while recording nothing new. Also `/hexa suggest pause`                                                                                                   |
| **List length**            | How many rows the list shows                                                                                                                                                                     |
| **Inline threshold**       | How confident a guess must be before it is written ahead of your cursor                                                                                                                          |
| **Adapts**                 | How fast old habits fade. *Quickly* suits play that changes week to week; *slowly* suits a settled routine                                                                                       |
| **Accept with**            | Tab, the right arrow, or either                                                                                                                                                                  |
| **Suggest known commands** | Hex ships knowing the common Skyblock commands so your first session is not blank. Your own use overtakes that within a few dozen commands; switch it off to start from a completely clean slate |
| **Learn player names**     | Off means the recipient of a `/msg` is not recorded either                                                                                                                                       |

Turn the feature off and the chat box behaves **exactly as vanilla** again, server tab-completion included.

## Commands

```
/hexa suggest stats             — a summary of what has been learned
/hexa suggest dashboard         — open the dashboard
/hexa suggest why <text>        — what it would suggest, and why
/hexa suggest forget <command>  — forget one command line
/hexa suggest clear confirm     — forget everything (settings are kept)
/hexa suggest pause | resume    — stop or start learning
```
