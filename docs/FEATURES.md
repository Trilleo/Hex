# Features

Every feature Hex ships, and how to use it. This is the reference the README's feature list points at — see the
[change log](../CHANGELOG.md) for what changed in each release.

*Hex is in early development — more features will be added here as they land.*

## Config menu

A single, categorized menu for the mod's settings. Open it with `/hexa config` or a rebindable keybind under
Options → Controls → **Hex**. Each feature adds its own tab down the side, a search box filters settings across all of
them at once, every row has a reset button, and a button links straight to the Keybinds screen. Settings apply as you
change them, so you can drag a slider and watch the result.

**Reset tab** in the footer restores everything on the current tab to its defaults, including that feature's own
on/off switch. It asks first, and it only changes your live settings — your saved profile is untouched, so
**Discard** on the Profiles screen brings them back.

## Config profiles

A profile is a complete set of Hex settings under a name. Open **Profiles…** from the config menu footer to see
them all, each with its description and when it was last saved.

Per profile you can **Switch** to it, copy it (**⧉**), rename or describe it (**✎**), or delete it (**✕**, with a
confirmation; the last remaining profile cannot be deleted). **New** creates one from the settings you have now.

**Saving is explicit.** Changing a setting takes effect immediately but does *not* write it into your profile — a
`*` next to the profile's name means your settings have moved away from what it holds. **Save** folds them in;
**Discard** reloads the profile as it was last saved. Switching to another profile while that `*` is showing asks
whether to save or discard first, so nothing disappears without you choosing it.

> Changed in this release: profiles used to be saved automatically whenever you switched away from one.

### Switching automatically

A profile can activate by itself when you arrive somewhere. Open **✎** on the profile and pick what it activates
on:

- **a server** — matches the address you connected to. `hypixel.net` also matches `mc.hypixel.net`.
- **singleplayer** — any single-player world.
- **a Skyblock island** — matches the island name shown on Hypixel's scoreboard, e.g. `private island` or `hub`.
  This one resolves a few seconds after joining, since the scoreboard is empty at first.

If two profiles claim the same place, the one higher in the list wins. Switching to a profile by hand turns
auto-switching off until you disconnect, so it never overrides a deliberate choice. It is also skipped entirely
while you have unsaved changes — you get a message instead, rather than losing them.

### Minecraft's key bindings

**MC keys** on the Profiles screen makes profiles carry Minecraft's own key bindings too, so a profile is a whole
control setup rather than just the Hex half of one.

It is **off by default**, because Minecraft keeps every mod's bindings in one place and Hex cannot tell them
apart. With it on, a profile saved while another mod was bound to `G` will put that mod back on `G` when the
profile is restored — even if you have since rebound it in that mod's own screen. Leave it off unless you want
that.

Note that with this on, using a control-switch shortcut counts as changing your settings, so it will mark the
profile as having unsaved changes.

### Sharing and backups

**Copy to clipboard** copies every profile-carried Hex setting as text you can send to someone else or keep as a
backup. The **Updates** tab is left out — it is a property of your install, not of a loadout.
**Paste from clipboard** takes one back, either **as a new profile** (keeping your current settings intact) or
**over this profile**. A paste that came from a newer Hex than you are running is refused rather than partly
applied, and one from a different version says so.

**Reset all** restores every Hex setting to its default. Like the per-tab reset, it leaves your saved profiles
alone, so **Discard** undoes it.

## Keybind shortcuts

Bind a key (optionally with Ctrl/Shift/Alt) to run a sequence of commands/chat messages, where each action has its own
delay and the command inputs offer chat-style tab-completion. Configure bindings in-game via the Hex Keybinds screen;
open it with the rebindable keybind under Options → Controls, from the config menu, or by running `/hexa keybinds`.

## Control switch shortcuts

Bind a key combo to cycle one of Minecraft's own controls between two or more keys, without leaving the game to rebind
it. For example, switch **Attack/Destroy** between **Left Button** and **J** so your clicks stop swinging. Mouse buttons
work as well as keyboard keys; each switch is announced in chat, plays a short sound, and is saved to your Minecraft
options. Add one with **Add Switch** on the Hex Keybinds screen.

## Attack mode switch

Press a keybind to flip **Attack/Destroy** between **Hold** and **Toggle**, so you can switch mid-session instead of
opening Options → Controls — hold to break a long line of blocks, toggle for a sustained fight. Each switch is announced
in chat and plays a short sound, higher for **Toggle** and lower for **Hold**, so you can tell which mode you landed on
without reading chat.

This drives Minecraft's own **Attack/Destroy** mode setting, so the change shows up in the vanilla Controls screen and
is saved to your Minecraft options. Switching always leaves you not attacking, even if the button was latched down at
the time. Bind it under Options → Controls → **Hex**; while it is unbound the feature does nothing.

## Freecam

Press a keybind to detach the camera from your player and fly it around freely to observe your surroundings (WASD to
move, Space/Shift for up/down, the mouse to look, and the scroll wheel to change speed); press it again to return. Your
character stays in place. Bind it under Options → Controls → **Hex** and tune it in the **Freecam** tab of
`/hexa config`.

## Hand display

Reposition your held item in first person and change how it swings. The **Hand** tab of `/hexa config` has sliders for
the main hand's position, scale and rotation, a swing-speed multiplier, and a switch to hide the swing animation
entirely. Everything is cosmetic: your attack cooldown, mining speed and reach are untouched.

To hide the swing for only certain items rather than all of them, see [Per-item swing](#per-item-swing).

## Per-item swing

Some items look better swinging and some do not, and the Hand tab's swing switch is all or nothing. Per-item swing is
the exception list: while you hold a listed Skyblock item in your main hand, the swing animation is hidden. Hold
anything else and your normal hand settings apply again.

Open the list with **Per-item swing…** in the **Hand** tab of `/hexa config`, or with `/hexa hand swing`. Each entry
matches one of two ways:

- **Item ID** — a Skyblock item ID such as `HYPERION`, matching every copy of that item, including one you buy later.
- **UUID** — one specific item, so a second Hyperion is unaffected. Only unique (non-stackable) items have one.

The quickest way to add something is to hold it and press **Add held item**, which picks the right kind for you and
fills in the item's name. Faster still, bind **Toggle Swing For Held Item** under Options → Controls → **Hex**: pressing
it adds whatever you are holding, or removes it if it is already listed, and says which in chat. `/hexa hand toggle`
does the same thing without a keybind.

The list has its own switch and works whether or not the Hand tab's master switch is on, so you can keep it running
with the rest of the hand settings off. It needs Skyblock's own item data, so it does nothing for vanilla items or on
other servers. Resetting the **Hand** tab leaves the list alone; it is stored separately at
`config/hex/swing_items.json` if you would rather edit it by hand.

## Reminders

Skyblock is full of things that quietly run out — a booster cookie, a potion, a forge slot, an ability cooldown — and
the only warning is a chat line that scrolls away seconds later. Reminders let you say "when *this* happens, tell me
*then*", and shows what is pending on a panel you can put wherever you like.

Open the list with **Reminders…** in the **Reminders** tab of `/hexa config`, or with `/hexa remind edit`. Each
reminder has three parts.

**What starts it.** Every reminder counts down, and the trigger decides when the countdown begins:

- **Timer** — you start it, or it repeats on its own.
- **Chat message** — a line of chat starts it. This is the powerful one: it can watch for "your potion has expired" or
  "this ability is on cooldown for 30s" and start counting from there.
- **Arriving at** / **Leaving island** — you reach or leave a named island, such as `dwarven mines`.
- **Entering** / **Leaving a region** — you walk into or out of an area you drew yourself. See [Regions](#regions).
- **Joining a world** — you log in.
- **Holding an item** — you put a particular Skyblock item in your main hand.

**When it speaks up.** *Remind me after* is the gap between the trigger and the reminder firing — write `0` to fire
straight away, or something like `45s`, `20m`, `2h30m`, `4d`. Turn on **Repeat** for a reminder that starts itself
again each time. **Conditions…** limit where it is allowed to fire, so a reminder that only matters in the Dwarven
Mines stays quiet everywhere else; conditions are checked at the moment it fires, not when it started, so leaving an
island silences its reminders rather than losing them. A condition can test a [region](#regions) as well as an
island, so a reminder can be limited to one room.

**What it does.** Show on the panel, play a sound, show the message as a big centred title, or any combination.
The title has its own colour, an optional smaller subtitle, and a time it holds before fading. Press **Test** in
the editor to see and hear it before committing to it.

Countdowns are real time, not game time. They keep running while you are logged out, so a four-day cookie is still
counting when you come back — and anything that came due while you were away fires once, marked overdue, rather than
firing every interval it missed.

### Chat patterns

A chat trigger matches with a regular expression unless you turn on **Plain text**, which compares it as ordinary
text instead and is the easier choice when you just want to match some words. A pattern is case-sensitive; put `(?i)`
at the start to ignore case.

Anything you capture with `(…)` can be put in the message: `$1` is the first captured part, up to `$9`, and `$0` is
the whole match. So a pattern of `on cooldown for (\d+)s` with the message `Ability ready in $1s` fills in the actual
number. Write `$$` for a literal dollar sign; a `$5` the pattern has no fifth group for is left alone, so prices in
your reminder text survive untouched.

If a pattern turns out to be so expensive to match that it would slow the game down, Hex switches that reminder off
and says which one in chat rather than letting it stutter every time you receive a message.

### The panel

**Panel position…** opens a screen where you drag the panel where you want it, or nudge it with the arrow keys —
hold Shift to move further. It is placed as a fraction of the screen, so it stays put when you change resolution, go
fullscreen, or change your GUI scale, and **Grow from** picks which corner stays anchored as reminders come and go.
The rest of the **Reminders** tab covers scale, colours, how many rows to show, and whether to hide the panel off
Skyblock. The panel hides with the rest of the HUD when you press F1.

Bind **Dismiss Reminder** under Options → Controls → **Hex** to silence whatever is flashing without opening
anything; **Snooze Reminder** pushes it back instead, by the amount set in the tab.

### Presets

**Presets…** is a small catalogue of ready-made reminders. Adding one copies it into your list, so you are free to
edit it afterwards. If a later version of Hex ships a corrected version of a preset you have *not* edited, yours is
updated in place — keeping its on/off state and any running countdown. If you *have* edited it, Hex leaves it alone
and the editor offers **Reset to preset** for whenever you want the newer version.

The catalogue is deliberately small: a preset whose chat pattern no longer matches is worse than no preset at all,
because there is no way to tell it apart from a broken feature, so only patterns confirmed against live Hypixel are
included.

### Commands and files

`/hexa remind in 5m check the forge` is the quickest way to set a one-off — it disappears by itself once it has
fired. `/hexa remind list` shows what is counting down, and `/hexa remind edit`, `hud` and `presets` open the three
screens. `/hexa remind dismiss` and `snooze` act on whatever is firing.

Reminders live in `config/hex/reminders.json` and take part in config profiles and clipboard sharing. Their
countdowns are kept separately in `config/hex/reminder_state.json`, which deliberately does *not* travel with a
profile — switching profiles changes which reminders you have without resetting the timers you have running, and
sharing your settings does not hand someone else your countdowns.

## Regions

An island is a big place, and "you are in the Hub" is rarely the thing worth saying. A region is an area you
draw yourself — a room, a boss arena, the patch of ground where you always forget something — that announces
itself with a title across the middle of the screen and a sound when you walk into it.

Open the list with **Regions…** in the **Regions** tab of `/hexa config`, or with `/hexa region edit`. It shows
the regions on the island you are standing on; **All islands** shows the rest. A region you are currently
standing in is marked *here*, so you can check one works without walking out and back in.

### Drawing one

Three ways, and none of them involve typing coordinates.

- **Around you.** Press **Region Here** (Options → Controls → **Hex**), or run `/hexa region here`. You get a
  region centred on where you stand, already named and already alerting. `/hexa region here 20 dragon nest`
  sets the radius and the name in the same breath. This is the one-keypress way, and it is usually enough.
- **Two corners, from the air.** Press **Mark Region Corner** once for one corner and again for the opposite
  one. **If the freecam is flying, the corner lands at the camera, not at you** — so you can fly up to the top
  of a room and pin the corner there instead of building a tower to stand on. With the freecam off it marks
  your feet. `/hexa region mark` does the same without a keybind.
- **Walk the outline.** Press **Walk Region**, walk around the edge of the area, and press it again. The region
  is the box your path fitted inside, given some height above and below. Good for a shape no two corners
  describe.

While you are drawing, a panel at the top of the screen says which corner you are on and how big the box is so
far, and the box itself is drawn in the world as it forms. `/hexa region cancel` abandons it.

Every capture ends by opening the region for editing, because the one thing Hex cannot guess is what you want
it to say.

### Shapes

A region stores a box, and the **Shape** setting decides how that box is read:

- **Box** — the box itself. Rooms, platforms, corridors.
- **Cylinder** — a circle of the box's width, the box's height tall. The natural "anywhere near this spot".
- **Sphere** — a ball inside the box. True proximity, when height matters as much as distance.

Switching shape never asks you to draw the region again. A cylinder and a sphere take the largest size that
fits *inside* the box, so a cylinder is round rather than oval — the editor shows the radius you actually get.

### What it says

**Message** is the title. Turn on **Show as a title** for the big centred text, with its own colour, an
optional smaller **Subtitle** beneath, and how long it holds before fading. **Play a sound** adds one, with the
same sound id, pitch and volume the reminders have — **Test** in the editor fires both so you can judge them
without leaving the menu.

**Announce leaving** fires again on the way out, with its own message if you want a different one.

**Cooldown** is how long a region stays quiet after firing. It matters more than it sounds: without it a region
across a doorway announces itself every time you step through. Standing exactly on the edge cannot make one
stutter either — leaving takes a small **exit margin** beyond the boundary, set in the tab.

### Seeing them

**Preview** in the regions list — or the **Toggle Region Preview** keybind, or `/hexa region preview` — draws
every region on the island as a real shape in the world, labelled with its name, and stays on after you close
the menu so you can walk around and look. The **Regions** tab decides whether they draw through walls and
whether names are shown, and each region can have its own colour.

The region you have open in the editor is always drawn, so a box you are typing sizes into changes shape behind
the menu.

### Regions and reminders

A region says its piece the moment you arrive. When you want more than that — a delay, a repeat, conditions, a
row on the reminder panel, a snooze key — **Add reminder** in the region editor creates a reminder armed by
that region and opens it. The trigger list also has **Entering a region** and **Leaving a region** for building
one by hand, and **In region** / **Not in region** are available as reminder conditions, so a reminder can be
limited to one room rather than a whole island.

Renaming a region updates every reminder that named it, so nothing breaks quietly.

### Where they live

Each region records the island it was made on, and only fires there — coordinates repeat across islands, so a
region without one would go off in places you have never been. A region made off Skyblock has no island and
matches anywhere, which is what makes them work in singleplayer.

Regions live in `config/hex/regions.json` and take part in config profiles and clipboard sharing, so a set of
regions is something you can hand to someone else.

## Command suggestions

Hex watches which commands you run, learns your habits, and offers them back the next time you open chat. Nothing is
sent anywhere — it all lives on your computer — and nothing is ever run for you: it fills in the box, you press
Enter.

### The three ways it helps

**The list.** Start typing a command and a ranked list appears above the chat box, ordered by what you actually use
rather than alphabetically. **↑/↓** move through it, **Tab** takes the highlighted one, **Escape** dismisses it
without closing chat, and clicking a row picks it. Each row carries a small note on the right — *here*, *next*,
*often*, *holding* — saying in one word why it made the list.

**Inline completion.** As you type, the rest of the line appears greyed out ahead of the cursor; **Tab** or **→**
accepts it. This is where it beats Hypixel's own tab-completion, which knows the command `/warp` exists but has no
idea that when *you* type `/warp d` you mean `dungeon_hub`. It only appears when the guess is a confident one — how
confident is the **Inline threshold** slider.

**Just a slash.** Type `/` on its own and it offers what you are most likely to want *right now*. That is a real
prediction, not a most-used list: it changes with where you are standing, what you are holding, what you last ran,
and what chat said in the last few seconds. A party invite thirty seconds ago moves `/party accept` to the top; the
same slash in the Dwarven Mines while holding a drill offers something else entirely.

### What it pays attention to

Where you are (island and the patch of ground you are on), what is in your hand and what kind of thing it is, your
hotbar and armour, how full your inventory is, how long you have been online, the last two commands you ran, and
whether chat has just asked you something. It learns which of those actually predict *your* commands and ignores the
rest — and it learns how much to trust each one from which suggestions you pick, so it gets better at being useful to
you specifically rather than to players in general.

**It reads Skyblock's own calendar too**, straight off the scoreboard:

- **The season.** A Skyblock year passes in about two and a half real days, so autumn comes round often enough to
  learn from — which is what makes the Spooky Festival commands start appearing before you have thought of them.
- **Skyblock time of day.** Not your clock — Skyblock's, which runs a full day every twenty real minutes. What you do
  when it is dark on Skyblock has nothing to do with what you do at night where you live, and both get learned
  separately. It takes the sun/moon marker as the answer when Hypixel shows one, rather than guessing from the hour.
- **The running event.** The strongest signal of the lot. When the sidebar starts counting down the Dark Auction,
  `/warp da` is very nearly something you have already announced — and after a couple of auctions Hex knows it.

Rows in the list say which of these did the work, so a suggestion that turns up during a Dark Auction is labelled
*dark auction*, and one that turns up because it is dark is labelled *night*.

Suggestions the server offers are kept and re-ranked, never thrown away, so a command Hypixel added last week still
appears — just in the right place in the list.

### What it never records

**Message text is never learned.** For `/msg`, `/w`, `/r`, `/pc` and the like, Hex records the command and who you
sent it to, and throws away what you said before anything is written to disk. A command Hex has never heard of
records at most its first word, so this holds even for commands from a mod or a server it knows nothing about. Turn
**Learn player names** off and the names go too.

What it has learned lives in `config/hex/suggest/model.json` and is deliberately **not** part of config profiles:
switching profiles never swaps it, and **Copy to clipboard** never contains it. Nobody gets a copy of your command
history by asking for your settings.

### Seeing and changing what it knows

**What it has learned…** in the **Command Suggestions** tab (or `/hexa suggest dashboard`) lists every command line
it holds, with how often you use it, when you last did, and what it has associated the command with — *island:
dwarven mines*, *holding: TITANIUM_DRILL*. Anything wrong can be fixed on the spot:

- **☆** pins a command to the top of every list it appears in.
- **○** blocks one, so it is never suggested and never recorded again.
- **✕** forgets it.
- **?** shows the full arithmetic — every signal, what it was worth, and how much it counted for. `/hexa suggest why
  <text>` prints the same thing in chat, for the situation you are actually in.
- **Forget everything** wipes the lot. Your settings are kept.

### Settings

All in the **Command Suggestions** tab of `/hexa config`:

- **Enabled**, and a switch for each of the three surfaces separately.
- **Keep learning** — pause to keep the suggestions you have while recording nothing new. Also `/hexa suggest pause`.
- **List length** and **Inline threshold**.
- **Adapts** — how fast old habits fade. *Quickly* suits play that changes week to week; *slowly* suits a settled
  routine.
- **Accept with** — Tab, the right arrow, or either.
- **Suggest known commands** — Hex ships knowing the common Skyblock commands so your first session is not blank.
  Your own use overtakes that within a few dozen commands. Switch it off to start from a completely clean slate.

Turn the feature off and the chat box behaves exactly as vanilla again, server tab-completion included.

## Auto-update

Hex checks its [GitHub releases](https://github.com/Trilleo/Hex/releases) on startup and, when a newer version is out,
downloads it and applies it automatically the next time you close the game. Run `/hexa update` to check on demand, or
manage it from the **Updates** tab of `/hexa config`. See [Updating](../README.md#updating) for the full details.

The **Updates** tab belongs to your installation rather than to a profile: switching, saving or pasting a profile
never changes whether Hex updates itself, and the tab is not part of a **Copy to clipboard** blob.

The result of the startup check reaches you in chat once you are in a world — including when a slow download means
that is a while after you joined.

---

## Adding a feature to this file

When a new feature lands, add a `##` section for it here, in the same style as the ones above: what it does, how the
player turns it on or configures it, and any limitation worth stating up front. Write for a player, not a developer —
implementation notes belong in the changelog's **Technical Details**. Then add a matching one-line bullet to the
[README](../README.md) feature list.
