# Entity highlight

Pick the entities you care about and they light up — a coloured outline around the mob itself, readable across a crowded
room and through the wall in front of it. A rule can also **announce itself**: the first time a matching entity turns
up, a title appears in the middle of your screen and a sound plays.

Open the list from the **Entity Highlight** tab of `/hexa config`, with `/hexa highlight edit`, or by binding **Open
Entity Highlights** under Options → Controls → **Hex**.

## Making one

The easy way is to point at something.

| Do this                                       | And                                                  |
|-----------------------------------------------|------------------------------------------------------|
| Look at a mob, press **Add this** in the list | Hex reads its name and type and opens the new rule   |
| Look at a mob, run `/hexa highlight add`      | The same, from chat                                  |
| Bind **Highlight What You Look At**           | The same, in one key                                 |
| Press **Add empty**                           | A blank rule, for when you already know what to type |

Your crosshair keeps pointing where it did before you opened the list, so **Add this** works from inside the screen —
open the list, notice the mob you wanted, add it.

If you would rather type a rule out, `/hexa highlight nearby` prints everything within 24 blocks: its type id, and its
name exactly as Hex reads it, with Hypixel's colour codes and invisible padding already stripped. **Those printed
strings are the ones a rule matches against**, so it is the place to copy a name from rather than guessing at one.

## Matching

A rule matches one of two ways, chosen with **Match by**:

| Match by | Compares                                                              |
|----------|-----------------------------------------------------------------------|
| **Name** | Any fragment of the entity's name, ignoring colours and capitals      |
| **Type** | The entity id, such as `minecraft:zombie` — every entity of that kind |

A fragment rather than the whole name, because Hypixel decorates names with levels, health bars and rarity colours that
change from one mob to the next while the middle of the name stays put. A typo in an entity id is reported in the
editor, rather than leaving you with a rule that quietly catches nothing.

### Completing an entity id

The entity id field completes as you type, the way the chat box completes a command.

| Key           | Does                                                     |
|---------------|----------------------------------------------------------|
| **Tab**       | Takes the highlighted suggestion; press again to walk on |
| **↑** / **↓** | Move the highlight through the list                      |
| **Click**     | Takes the one you clicked                                |

Type `zomb` and everything matching drops down. Matches on the part after the colon come first, since nobody types the
namespace — but `spider` still finds `minecraft:cave_spider`, because a match anywhere in the id counts too.

**Leave the field empty and the list is every entity the game knows**, so the whole set is browsable with the arrow keys
without knowing a name to start from. It is read from the game's own registry rather than written down, so it can never
drift from what a rule can actually match, and anything another mod registers is in it.

### Names on Hypixel

**A Hypixel mob's name is not on the mob.** It floats on a separate invisible marker a head above it. Hex follows that
link, so a name rule lights up the **mob**, not the marker — which is what you want, since an invisible marker has no
model to draw an outline around.

A marker that labels nothing — a hologram, a sign, floating text — keeps its own name and matches normally, so those
stay findable. That also covers a mob too far away to have been sent yet, which is nothing *but* its name: see
[far away, the name is marked instead](#far-away-the-name-is-marked-instead). And an entity that names *itself*, as a
name-tagged mob in singleplayer does, is always taken at its word over a marker that happens to float above it.

## Where a rule applies

**Range** decides how far away a match still counts. Past it the entity neither glows nor announces itself.

**Island** restricts a rule to one place, as it appears on the scoreboard, for when the same name means something
different elsewhere. Leave it blank and the rule works everywhere, which is the normal case — unlike a
[region](Regions), whose island is close to mandatory because coordinates repeat and names do not.

The list's **All islands** / **This island** button filters what you are looking at without changing any rule.

## Seeing them

**Glow colour** is the outline's colour, and every rule has its own. It is vanilla's own glowing effect, so it draws
through terrain and follows the mob smoothly however fast it moves.

One thing it cannot do is outline an entity that is **invisible** — there is no model to draw around. That is exactly
why a name rule targets the mob rather than the marker above it.

### Far away, the name is marked instead

Hypixel does not send a distant mob at all. Only its floating name is there, hanging where the mob will be, which is why
a rule could fire its notification while nothing lit up until you walked over.

A rule that matches one of those now marks **the name itself**, wrapping it in arrows in the rule's colour:

```
▶ ✦ Lapis Zombie 100/100❤ ◀
```

The name is left exactly as Hypixel wrote it, so its colours and health bar stay readable. Walk closer, the mob arrives,
and it glows the ordinary way with no arrows — there is nothing to switch on, and nothing to switch off.

This is something a **name** rule does. A **type** rule cannot match a mob that has not been sent, because the only
thing there is its name.

### Labels

**Show label** floats the rule's name over everything it matches, in the rule's colour, and **Label distance** adds how
far away it is. A label never covers up a name the game was already showing — including a marked one, so a distant mob
keeps showing its own name rather than the rule's.

Each row in the list shows a live count of how many entities its rule is matching right now, so you can tell a working
rule from a mistyped one without hunting the island for the mob you wrote it for.

## Notifications

Turn on **Announce new ones** and the rule speaks up the first time it sees each matching entity.

| Setting             | Notes                                                                 |
|---------------------|-----------------------------------------------------------------------|
| **Message**         | The title's text. Blank uses the rule's name                          |
| **Show as a title** | Subtitle, colour and how long it holds                                |
| **Play a sound**    | Any sound id, with pitch and volume. A bad id is reported as you type |
| **Cooldown**        | How long the rule stays quiet afterwards                              |

**Test** fires the actions on the spot, so a sound and a title can be judged without waiting for a mob to spawn.

"New" means an entity Hex has not seen before, not one that has merely come back into range: a mob that wanders behind a
hill and returns stays quiet. Leaving the world forgets everything, so arriving somewhere is always announced. The
cooldown keeps a pack that spawns together down to a single announcement rather than one per mob.

These are the same actions [reminders](Reminders) and [regions](Regions) use, so they behave identically and the
reminder tab's master sound switch covers them too.

## Settings

The **Entity Highlight** tab of `/hexa config`:

| Setting            | Notes                                                                                                   |
|--------------------|---------------------------------------------------------------------------------------------------------|
| **Enabled**        | Master switch. With it off nothing glows and no rule announces anything, but your rules are kept        |
| **Highlights…**    | Opens the rule list                                                                                     |
| **Scan interval**  | How often the world is checked, in ticks. Higher costs less and only delays a mob that has just spawned |
| **Default colour** | The glow colour a rule uses when it names none of its own                                               |
| **Skyblock only**  | Ignore every rule unless the scoreboard looks like Skyblock's                                           |

Raising the scan interval makes nothing flicker: a match is remembered for as long as the entity exists, so it only
changes how quickly something newly spawned is picked up.

## Commands

```
/hexa highlight add [name]   — a rule for the entity under your crosshair, then opens it
/hexa highlight nearby       — the type ids and names of everything within 24 blocks
/hexa highlight list         — every rule, and how many entities each is matching now
/hexa highlight edit         — opens the rule list
```

`name` is the rest of the line and only sets the new rule's label; what it matches is read off the entity. Command
output is English whatever your language is set to — see the note in [Commands](Commands).

## Keybinds

| Key                            | Does                                                          |
|--------------------------------|---------------------------------------------------------------|
| **Open Entity Highlights**     | Opens the rule list. Works even with the feature switched off |
| **Highlight What You Look At** | A rule for the entity under your crosshair, then opens it     |

Both unbound by default. Bind them under Options → Controls → **Hex**.

## Where they live

Highlights live in `config/hex/highlights.json` and take part in [config profiles](Config-Profiles) and clipboard
sharing, so a set of rules for an island is something you can hand to someone else.

## Hypixel rules

A glow drawn through terrain shows you where something is before you can see it, which goes a little further than most
of what Hex does. It is opt-in per rule rather than on by default for that reason. Hex is designed to comply with the
[Hypixel Server Rules](https://hypixel.net/rules), but as with everything here, use at your own risk — see
[Home](Home).

## Related

- [Regions](Regions) announce a **place** you walked into; this announces a **thing** that turned up. Both deliver their
  alert through the same title and sound settings.
- [Reminders](Reminders) — if you want a countdown, conditions or a snooze key rather than an immediate alert.
- [Commands](Commands) — every command Hex registers.
- [Keybinds](Keybinds) — every key Hex registers.
- [Config files](Config-Files) — where `highlights.json` sits, and whether it travels with a profile.
