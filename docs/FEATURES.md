# Features

Every feature Hex ships, and how to use it. This is the reference the README's feature list points at — see the
[change log](../CHANGELOG.md) for what changed in each release.

*Hex is in early development — more features will be added here as they land.*

## Config menu

A single, categorized menu for the mod's settings. Open it with `/hexa config`, with the small **□** button next to
**Done** on Minecraft's Options screen, or with a rebindable keybind under Options → Controls → **Hex**. Each feature
adds its own tab down the side, a search box filters settings across all of them at once, every row has a reset button,
and a button links straight to the Keybinds screen. Settings apply as you change them, so you can drag a slider and
watch the result.

The **□** button is on the Options screen wherever you open it from — the pause menu or the title screen. From the title
screen it is the only way in, since the command needs a chat box and the keybind needs a world.

If you have [Mod Menu](https://modrinth.com/mod/modmenu) installed, its settings button on Hex's entry in the mod list
opens the same menu, and closing it takes you back to the mod list. Mod Menu is entirely optional — Hex does not require
it, does not bundle it, and behaves the same whether or not it is there.

**Reset tab** in the footer restores everything on the current tab to its defaults, including that feature's own on/off
switch. It asks first, and it only changes your live settings — your saved profile is untouched, so **Discard** on the
Profiles screen brings them back.

## Colour picker

Every colour in Hex is chosen the same way. Any setting that holds a colour shows the value as text with a **swatch**
beside it; click the swatch and the colour picker opens.

It offers four ways to reach a colour, because people arrive knowing different things:

- The **square** picks saturation and brightness by eye, and the **bar** under it picks the hue.
- The **#RRGGBB** field takes a colour copied from somewhere else. `#` is optional and case does not matter.
- The **R**, **G** and **B** fields take one written down as numbers, `0`–`255` each.
- The **swatches** are the colours worth having a name for.

There are three rows of swatches:

- **Minecraft colours** — the sixteen `&0`–`&f` colours, named. Hypixel writes every rarity, stat and broadcast in one
  of them, so "the same gold the legendary items use" is a click rather than a look-up.
- **Presets** — a dozen colours Minecraft has no code for: orange, amber, lime, mint, teal, sky, violet, magenta, pink,
  coral, crimson and slate.
- **Recent** — the last twelve colours you picked *anywhere in the mod*. This is the row that makes two features match:
  a colour chosen for a region is one click away when a highlight needs the same one. The **×** at the end of the row
  forgets them all.

**Copy** puts the current colour on the clipboard and **Paste** reads one back, accepting `#RRGGBB`, `RRGGBB` and
`0xRRGGBB`.

The picker applies as you drag, exactly as a settings row does, so a region box or the reminder panel behind it
recolours while you choose. **Done** keeps the colour and remembers it; **Cancel**, Escape, or closing the screen any
other way puts the original back.

### Chroma

Where a colour is allowed to move, the picker has a **Chroma** button. Chroma is not a colour but a mode — it flows
through the rainbow — and it lives in the same setting as a colour because it answers the same question. It is available
for:

- item names, and chat highlights ([chroma text](#chroma-text));
- the [entity highlight](#entity-highlight) glow outline;
- [region](#regions) boxes;
- the [reminder](#reminders) panel's background, text and flash colours.

- alert [titles](#titles), both lines.

Each of those tabs has its own **Chroma speed** slider.

### None

Settings where "leave it alone" is a real answer — an item's name colour and dye, a note's colour — also have a **None**
button, which clears the colour rather than replacing it with another one.

### Transparency

A few settings carry transparency as well as a colour, written `#AARRGGBB` with the opacity first: region boxes, and the
reminder panel's colours. There is no transparency slider — type the value in the hex field, or leave it alone and it is
carried through everything else you change. A chroma region or panel takes the stock transparency for that setting,
since a flowing colour has none of its own.

## Config profiles

A profile is a complete set of Hex settings under a name. Open **Profiles…** from the config menu footer to see them
all, each with its description and when it was last saved.

Per profile you can **Switch** to it, copy it (**⧉**), rename or describe it (**✎**), or delete it (**✕**, with a
confirmation; the last remaining profile cannot be deleted). **New** creates one from the settings you have now.

**Saving is explicit.** Changing a setting takes effect immediately but does *not* write it into your profile — a
`*` next to the profile's name means your settings have moved away from what it holds. **Save** folds them in;
**Discard** reloads the profile as it was last saved. Switching to another profile while that `*` is showing asks
whether to save or discard first, so nothing disappears without you choosing it.

> Changed in this release: profiles used to be saved automatically whenever you switched away from one.

### Switching automatically

A profile can activate by itself when you arrive somewhere. Open **✎** on the profile and pick what it activates on:

- **a server** — matches the address you connected to. `hypixel.net` also matches `mc.hypixel.net`.
- **singleplayer** — any single-player world.
- **a Skyblock island** — matches the island you are on, e.g. `private island` or `hub`. Hex asks Hypixel which island
  it is, so this names the whole island rather than the smaller area you are standing in (`village` on the Hub), and
  resolves a moment after joining.

If two profiles claim the same place, the one higher in the list wins. Switching to a profile by hand turns
auto-switching off until you disconnect, so it never overrides a deliberate choice. It is also skipped entirely while
you have unsaved changes — you get a message instead, rather than losing them.

### Minecraft's key bindings

**MC keys** on the Profiles screen makes profiles carry Minecraft's own key bindings too, so a profile is a whole
control setup rather than just the Hex half of one.

It is **off by default**, because Minecraft keeps every mod's bindings in one place and Hex cannot tell them apart. With
it on, a profile saved while another mod was bound to `G` will put that mod back on `G` when the profile is restored —
even if you have since rebound it in that mod's own screen. Leave it off unless you want that.

Note that with this on, using a control-switch shortcut counts as changing your settings, so it will mark the profile as
having unsaved changes.

### Sharing and backups

**Copy to clipboard** copies every profile-carried Hex setting as text you can send to someone else or keep as a backup.
The **Updates** tab is left out — it is a property of your install, not of a loadout. **Paste from clipboard** takes one
back, either **as a new profile** (keeping your current settings intact) or **over this profile**. A paste that came
from a newer Hex than you are running is refused rather than partly applied, and one from a different version says so.

**Reset all** restores every Hex setting to its default. Like the per-tab reset, it leaves your saved profiles alone, so
**Discard** undoes it.

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

## Mouse sensitivity

Hold a keybind and the scroll wheel changes your mouse sensitivity; let go and your normal sensitivity comes back. A
long bow shot, a click on a small NPC and a spin to face something behind you all want a different sensitivity, and the
vanilla answer is three trips into Options → Controls → Mouse Settings.

Bind **Hold To Adjust Sensitivity** under Options → Controls → **Hex** (unbound by default), then hold it and scroll:
up is faster, down is slower. The hotbar does not scroll while the key is held. Nothing is written to your Minecraft
options — the sensitivity you had before the key went down is the one you get back.

The **Sensitivity** tab of `/hexa config` has two numbers:

- **Wheel step** — how much one notch changes the sensitivity, as a percentage of its current value (10% by default).
  Because it is a percentage rather than a fixed amount, a notch is worth the same wherever you are: a step down from
  100% lands on 90%, and a step down from 10% lands on 9% rather than on nothing.
- **Snap on press** — a multiplier applied the instant the key goes down, before you scroll at all, as a percentage of
  your normal sensitivity. At 40% the key is instant precision aim and the wheel only fine-tunes it; at 100% (the
  default) it changes nothing and the wheel does all the work.

The key is only read while you are in the world with no screen open, so the wheel still belongs to your inventory and to
Hypixel's menus. While the [freecam](#freecam) is flying it keeps the wheel for its own fly speed.

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

The list has its own switch and works whether or not the Hand tab's master switch is on, so you can keep it running with
the rest of the hand settings off. It needs Skyblock's own item data, so it does nothing for vanilla items or on other
servers. Resetting the **Hand** tab leaves the list alone; it is stored separately at
`config/hex/swing_items.json` if you would rather edit it by hand.

## Item customization

Skyblock hands you the same sword everyone else has, called the same thing and drawn the same way. Item customization
changes how one particular item looks **on your own client**: what it is called, what colour that name is, whether it
shimmers, and what model or skin it is drawn with.

Nothing here leaves your computer. Your real item is untouched, Hypixel is never told anything, and other players see
the item exactly as it always was — this only changes what your client draws.

**Customizing an item.** Hover it in any menu — your inventory, a chest, the auction house — and press **Customize
Hovered Item**, which you bind under Options → Controls → **Hex**. It ships unbound on purpose, because the key fires
inside Hypixel's own menus where most letters already mean something. You can also open the **Item Customization** tab
of `/hexa config`, press **Customized items…**, and use **Add held item**.

The editor shows the item before and after side by side, with its live name underneath, and offers:

- **Name** — what to call it. Use `&` followed by a colour or format code, such as `&6` for gold or `&l` for bold, and
  `&z` for chroma. Leave it blank to keep Hypixel's own name.
- **Name colour** — colours the whole name, through the [colour picker](#colour-picker). With **Name** blank this
  recolours Hypixel's name, which means dropping the colours it came with — that is the only way a recolour can show at
  all. Press **Chroma** in the picker and the whole name flows through the rainbow instead; press **None** to leave the
  name's own colours alone. See [Chroma text](#chroma-text).
- **Enchant glint** — always, never, or unchanged. Cosmetic only; the item's enchantments are untouched.
- **Item model** — a model to draw instead, such as `minecraft:diamond_sword`. Any model your resource packs provide
  works too.
- **Head texture** — a player-head skin. Paste a texture hash, a `textures.minecraft.net` link, or the base64 value
  copied out of an item. Setting one draws the item as a head unless **Item model** says otherwise.
- **Dye colour** — recolours a dyeable model, such as leather armour. It does nothing on a model that cannot be dyed.

Every field is "blank means leave it alone", so an item you only renamed keeps everything else about its appearance.
Each customization also has its own switch, for turning one off without losing what you set up.

**Only unique items can be customized.** A customization is keyed on the item's UUID, which Hypixel gives to
non-stackable items alone — that is what makes it follow *your* Hyperion rather than every Hyperion in the game. Press
the keybind on a stackable or a vanilla item and Hex says so rather than storing something that could never apply.

**Finding them again.** Slots holding a customized item are marked with a small **✎**, which can be switched off on the
settings tab. The **Customized items** screen — the same one `/hexa item list` opens — lists every entry with the item's
original name, so you can edit or delete one without going to find the item first.

Two things worth knowing:

- **Opening the editor closes the Hypixel menu you were in.** Minecraft tells the server a container is closed the
  moment another screen replaces it, so you lose your place in a chest or an auction page. Nothing else is lost — the
  customization is already saved against the item's UUID.
- **Customizations belong to your installation, not to a config profile.** Switching or pasting a profile leaves them
  alone, since they describe items you own rather than a settings loadout. They are stored at
  `config/hex/item_custom.json` if you would rather edit them by hand.

## Chroma text

Chroma is colour that flows through the rainbow, the same effect other Skyblock mods offer. It is a choice in the
[colour picker](#colour-picker) rather than a setting of its own, so anything that can flow is switched to chroma the
same way any other colour is chosen: open the swatch and press **Chroma**.

Hex can flow [item names](#item-customization), [chat highlights](#chat-highlight), the
[entity highlight](#entity-highlight) glow, [region](#regions) boxes, the [reminder](#reminders) panel and both lines of
an alert [title](#titles).

For text there is a second, finer way in: write **`&z`** in the **Name** field of an item, in a [title](#titles), or in
a [note](#notebook), and chroma starts at that point — `&7Old &zHyperion` leaves the first word grey and flows the
second.
Any colour code, or `&r`, ends it. This is the same code NotEnoughUpdates and SkyHanni use, so a name copied from either
works here unchanged.

Each feature that can flow has its own **Chroma speed** on its tab of `/hexa config`, applying to every chroma value in
that feature at once — one item flowing at a different rate from the item beside it reads as a glitch rather than a
choice:

- **Chroma speed** — how long one full trip through the rainbow takes, from half a second to twenty. Lower is faster.
- **Chroma width** — how many characters one full rainbow spans, on the three tabs that colour *text* (Item
  Customization, Chat Highlight and Titles). Set it low and a short name holds every colour at once; set it high and the
  name drifts through one colour at a time. A glow, a box and a panel are one colour rather than a run of characters, so
  those tabs have no width to set.

The colours move on their own, so a chroma name animates wherever it appears: in a tooltip, on a container slot, and in
the item-name popup above the hotbar. It costs a little more to draw than a plain colour, which is why it is off by
default and set per item, per rule or per region rather than applied to everything.

## Titles

A **title** is the big line of text across the middle of the screen, the way the game announces a boss or a new
advancement. Four features can show one — [reminders](#reminders), [regions](#regions),
[entity highlight](#entity-highlight) and [chat highlight](#chat-highlight) — and all four configure it in the same
place, through one **Title style…** button in their editor.

### The style screen

Every title setting lives on that one screen, so what you learn setting up a region applies unchanged to a chat rule.

**The big line and the subtitle are configured identically.** Each has a colour, chosen through the
[colour picker](#colour-picker), and five switches: bold, italic, underline, strikethrough and obfuscated. Leave the
colour on **None** and the title falls back to the default on the **Titles** tab. Choose **Chroma** and it flows
through the rainbow while it is on screen.

**A line can carry more than one colour.** The text takes the same `&` codes item names and notes do, so
`&fBOSS &c&lINCOMING` is white, then bold red, on one line. `&#FF8800` writes any colour at all, and `&z` starts
[chroma](#chroma-text) part-way through. Codes and the switches above compose: the switches are the line's baseline, and
a code overrides it from where it appears.

**Three timings, not one.** **Fade in**, **Time on screen** and **Fade out** are set separately, from instant up to
thirty seconds of dwell — enough for a warning you want up until you have dealt with it.

**A sound of its own.** Turn on **Play a sound** and give it a sound id, a pitch and a volume, and it plays the moment
the title appears. This is separate from the alert's own **Play a sound** action, so an alert can have both, either, or
neither.

**Presets.** The **Preset** row fills the colours, the switches and the sound in one click — **Info**, **Success**,
**Warning**, **Alert** or **Chroma**. It is a starting point rather than a mode: your text and your timings are left
alone, and the row reads **Custom** again the moment you change anything it wrote.

**Preview** shows the title for real, behind the open menu — same colours, same fades, same sound — so nothing has to be
imagined and nothing has to be tested by walking into a region.

### The Titles tab

`/hexa config` → **Titles** holds what is true of every title at once.

- **Enabled** — master switch. With it off, reminders, regions and highlights still fire; they just say nothing in the
  middle of the screen.
- **Sounds** — master switch for the sound a title plays.
- **Default colour** and **Default subtitle colour** — used by any title that has not chosen one. These are read every
  time a title appears, so changing one restyles every such title at once.
- **Chroma speed** and **Chroma width** — shared by every chroma title, because two alerts flowing at different rates
  read as a glitch rather than a choice.
- **New title fade in / time / fade out** — the timings a *newly created* title starts with. Titles you have already set
  up keep their own, so tuning these never silently retimes an alert you had got right.
- **Preview** — a sample title with nothing but these defaults on it.

Settings are stored in `config/hex/titles.json`. Each title's own style is stored with the alert that owns it, so it
travels with that reminder, region or rule.

## Reminders

Skyblock is full of things that quietly run out — a booster cookie, a potion, a forge slot, an ability cooldown — and
the only warning is a chat line that scrolls away seconds later. Reminders let you say "when *this* happens, tell me
*then*", and shows what is pending on a panel you can put wherever you like.

Open the list with **Reminders…** in the **Reminders** tab of `/hexa config`, or with `/hexa remind edit`. Each reminder
has three parts.

**What starts it.** Every reminder counts down, and the trigger decides when the countdown begins:

- **Timer** — you start it, or it repeats on its own.
- **Chat message** — a line of chat starts it. This is the powerful one: it can watch for "your potion has expired" or
  "this ability is on cooldown for 30s" and start counting from there.
- **Arriving at** / **Leaving island** — you reach or leave a named island, such as `dwarven mines`.
- **Entering** / **Leaving a region** — you walk into or out of an area you drew yourself. See [Regions](#regions).
- **Joining a world** — you log in.
- **Holding an item** — you put a particular Skyblock item in your main hand.

**When it speaks up.** *Remind me after* is the gap between the trigger and the reminder firing — write `0` to fire
straight away, or something like `45s`, `20m`, `2h30m`, `4d`. Turn on **Repeat** for a reminder that starts itself again
each time. **Conditions…** limit where it is allowed to fire, so a reminder that only matters in the Dwarven Mines stays
quiet everywhere else; conditions are checked at the moment it fires, not when it started, so leaving an island silences
its reminders rather than losing them. A condition can test a [region](#regions) as well as an island, so a reminder can
be limited to one room.

**What it does.** Show on the panel, play a sound, show the message as a big centred title, or any combination. With
**Show as a title** on, **Title style…** opens the full [title](#titles) editor — colours, bold and italic on both
lines, a subtitle, the three fade timings and a sound of its own. Press **Test** in the reminder editor to see and hear
the whole thing before committing to it.

Countdowns are real time, not game time. They keep running while you are logged out, so a four-day cookie is still
counting when you come back — and anything that came due while you were away fires once, marked overdue, rather than
firing every interval it missed.

### Chat patterns

A chat trigger matches with a regular expression unless you turn on **Plain text**, which compares it as ordinary text
instead and is the easier choice when you just want to match some words. A pattern is case-sensitive; put `(?i)`
at the start to ignore case.

Anything you capture with `(…)` can be put in the message: `$1` is the first captured part, up to `$9`, and `$0` is the
whole match. So a pattern of `on cooldown for (\d+)s` with the message `Ability ready in $1s` fills in the actual
number. Write `$$` for a literal dollar sign; a `$5` the pattern has no fifth group for is left alone, so prices in your
reminder text survive untouched.

If a pattern turns out to be so expensive to match that it would slow the game down, Hex switches that reminder off and
says which one in chat rather than letting it stutter every time you receive a message.

### The panel

**Panel position…** opens a screen where you drag the panel where you want it, or nudge it with the arrow keys — hold
Shift to move further. It is placed as a fraction of the screen, so it stays put when you change resolution, go
fullscreen, or change your GUI scale, and **Grow from** picks which corner stays anchored as reminders come and go. The
rest of the **Reminders** tab covers scale, colours — background, text and flash, each through the
[colour picker](#colour-picker) and each able to flow with **Chroma** — how many rows to show, and whether to hide the
panel off Skyblock. The panel hides with the rest of the HUD when you press F1.

Bind **Dismiss Reminder** under Options → Controls → **Hex** to silence whatever is flashing without opening anything;
**Snooze Reminder** pushes it back instead, by the amount set in the tab.

### Presets

**Presets…** is a small catalogue of ready-made reminders. Adding one copies it into your list, so you are free to edit
it afterwards. If a later version of Hex ships a corrected version of a preset you have *not* edited, yours is updated
in place — keeping its on/off state and any running countdown. If you *have* edited it, Hex leaves it alone and the
editor offers **Reset to preset** for whenever you want the newer version.

The catalogue is deliberately small: a preset whose chat pattern no longer matches is worse than no preset at all,
because there is no way to tell it apart from a broken feature, so only patterns confirmed against live Hypixel are
included.

### Commands and files

`/hexa remind in 5m check the forge` is the quickest way to set a one-off — it disappears by itself once it has fired.
`/hexa remind list` shows what is counting down, and `/hexa remind edit`, `hud` and `presets` open the three screens.
`/hexa remind dismiss` and `snooze` act on whatever is firing.

Reminders live in `config/hex/reminders.json` and take part in config profiles and clipboard sharing. Their countdowns
are kept separately in `config/hex/reminder_state.json`, which deliberately does *not* travel with a profile — switching
profiles changes which reminders you have without resetting the timers you have running, and sharing your settings does
not hand someone else your countdowns.

## Regions

An island is a big place, and "you are in the Hub" is rarely the thing worth saying. A region is an area you draw
yourself — a room, a boss arena, the patch of ground where you always forget something — that announces itself with a
title across the middle of the screen and a sound when you walk into it.

Open the list with **Regions…** in the **Regions** tab of `/hexa config`, or with `/hexa region edit`. It shows the
regions on the island you are standing on; **All islands** shows the rest. A region you are currently standing in is
marked *here*, so you can check one works without walking out and back in.

### Drawing one

Three ways, and none of them involve typing coordinates.

- **Around you.** Press **Region Here** (Options → Controls → **Hex**), or run `/hexa region here`. You get a region
  centred on where you stand, already named and already alerting. `/hexa region here 20 dragon nest`
  sets the radius and the name in the same breath. This is the one-keypress way, and it is usually enough.
- **Two corners, from the air.** Press **Mark Region Corner** once for one corner and again for the opposite one. **If
  the freecam is flying, the corner lands at the camera, not at you** — so you can fly up to the top of a room and pin
  the corner there instead of building a tower to stand on. With the freecam off it marks your feet. `/hexa region mark`
  does the same without a keybind.
- **Walk the outline.** Press **Walk Region**, walk around the edge of the area, and press it again. The region is the
  box your path fitted inside, given some height above and below. Good for a shape no two corners describe.

While you are drawing, a panel at the top of the screen says which corner you are on and how big the box is so far, and
the box itself is drawn in the world as it forms. `/hexa region cancel` abandons it.

Every capture ends by opening the region for editing, because the one thing Hex cannot guess is what you want it to say.

### Shapes

A region stores a box, and the **Shape** setting decides how that box is read:

- **Box** — the box itself. Rooms, platforms, corridors.
- **Cylinder** — a circle of the box's width, the box's height tall. The natural "anywhere near this spot".
- **Sphere** — a ball inside the box. True proximity, when height matters as much as distance.

Switching shape never asks you to draw the region again. A cylinder and a sphere take the largest size that fits
*inside* the box, so a cylinder is round rather than oval — the editor shows the radius you actually get.

### What it says

**Message** is the title. Turn on **Show as a title** for the big centred text, then **Title style…** for the full
[title](#titles) editor — colours, styles, a subtitle, the fade timings and a sound of its own. **Play a sound** adds a
separate one, with the same sound id, pitch and volume the reminders have — **Test** in the editor fires both so you can
judge them without leaving the menu.

**Announce leaving** fires again on the way out, with its own message if you want a different one.

**Cooldown** is how long a region stays quiet after firing. It matters more than it sounds: without it a region across a
doorway announces itself every time you step through. Standing exactly on the edge cannot make one stutter either —
leaving takes a small **exit margin** beyond the boundary, set in the tab.

### Seeing them

**Preview** in the regions list — or the **Toggle Region Preview** keybind, or `/hexa region preview` — draws every
region on the island as a real shape in the world, labelled with its name, and stays on after you close the menu so you
can walk around and look. The **Regions** tab decides whether they draw through walls and whether names are shown, and
each region can have its own colour — chosen through the [colour picker](#colour-picker), **Chroma** included, at the
speed set on that tab.

The region you have open in the editor is always drawn, so a box you are typing sizes into changes shape behind the
menu.

### Regions and reminders

A region says its piece the moment you arrive. When you want more than that — a delay, a repeat, conditions, a row on
the reminder panel, a snooze key — **Add reminder** in the region editor creates a reminder armed by that region and
opens it. The trigger list also has **Entering a region** and **Leaving a region** for building one by hand, and **In
region** / **Not in region** are available as reminder conditions, so a reminder can be limited to one room rather than
a whole island.

Renaming a region updates every reminder that named it, so nothing breaks quietly.

### Where they live

Each region records the island it was made on, and only fires there — coordinates repeat across islands, so a region
without one would go off in places you have never been. A region made off Skyblock has no island and matches anywhere,
which is what makes them work in singleplayer.

Regions live in `config/hex/regions.json` and take part in config profiles and clipboard sharing, so a set of regions is
something you can hand to someone else.

## Entity highlight

Pick the entities you care about and they light up — a coloured outline drawn around the mob itself, visible across a
crowded room and through the wall in front of it. A rule can also announce itself: the first time a matching entity
turns up, a title appears in the middle of your screen and a sound plays.

Open the list from the **Entity Highlight** tab of `/hexa config`, with `/hexa highlight edit`, or by binding **Open
Entity Highlights** under Options → Controls → **Hex**.

### Making one

The easy way is to point at something. Look at the mob you want, then press **Add this** in the list, run
`/hexa highlight add`, or bind **Highlight What You Look At** to a key. Hex reads the entity's name and type off the
world and opens the new rule for you to give it a colour.

If you would rather type it, **Add empty** makes a blank rule. `/hexa highlight nearby` prints everything within 24
blocks — its type id, and its name exactly as Hex reads it, with Hypixel's colour codes and invisible padding already
stripped out. Those printed strings are the ones a rule matches against, so it is the place to copy a name from rather
than guessing at one.

### Matching

A rule matches one of two ways, chosen with **Match by**:

- **Name** — the entity's name contains what you typed, ignoring colours and capitals. Any fragment will do, which
  matters because Hypixel decorates names with levels, health bars and rarity colours that change from one mob to the
  next while the middle of the name stays put.
- **Type** — the entity is of a given kind, written as an entity id such as `minecraft:zombie`. Every one of them
  matches. A typo here is reported in the editor rather than leaving you with a rule that quietly catches nothing.

The entity id field completes as you type, the way the chat box completes a command. Type `zomb` and a list of
everything matching drops down; **Tab** takes the highlighted one, and **↑** / **↓** walk the list. Leave the field
empty and the list is every entity the game knows, so you can browse the whole set without knowing a name to start from.
The list is read from the game's own registry, so anything another mod adds is in it too.

**On Hypixel, a mob's name is not on the mob.** It floats on a separate invisible marker a head above it. Hex follows
that link, so a name rule lights up the *mob* rather than the marker — which is what you want, since an invisible marker
has nothing to draw an outline around. A hologram that labels nothing still matches on its own name, so signs and
floating text remain findable.

Two more limits are worth knowing. **Range** decides how far away a match still counts; past it the entity neither glows
nor announces itself. **Island** restricts a rule to one place, in case the same name means something different
elsewhere — leave it blank and the rule works everywhere, which is the normal case.

### Seeing them

**Glow colour** is the outline's colour, chosen through the [colour picker](#colour-picker), and every rule can have its
own — **Chroma** included, which walks the outline through the rainbow at the speed set on the **Entity Highlight** tab.
The outline is vanilla's own glowing effect, so it draws through terrain and follows the mob smoothly however fast it
moves.

One thing it cannot do is outline an entity that is invisible — there is no model to draw around. That is exactly why a
name rule targets the mob and not the marker above it.

**Far away, the name is marked instead.** Hypixel does not send a distant mob at all — only the floating name that sits
where it will be — so until you are close enough there is nothing to draw an outline around. A rule that matches one of
those marks the name itself instead, wrapping it in arrows in the rule's colour: `▶ ✦ Lapis Zombie 100/100❤ ◀`. The name
is left exactly as Hypixel wrote it, so its own colours and health bar are still readable. Walk closer, the mob arrives,
and it glows the ordinary way with no arrows — nothing to switch on, and nothing to switch off. This is a **name**
rule's doing; a **type** rule cannot match a mob that has not been sent, since the only thing there is its name.

A marked name is drawn bigger than the game would draw it, because at that range an ordinary name tag is a few pixels
tall. **Marked name size**, on the settings tab, sets how much bigger for every rule at once — `1.00×` is the size the
game draws it, it starts at half again as big, and it goes to `4.00×`. The name grows about the point it hangs from, so
it stays over the spot the mob will appear at rather than climbing away from it as you turn the setting up.

**Show label** floats the rule's name over everything it matches, in the rule's colour, and **Label distance** adds how
far away it is. A label never covers up a name the game was already showing — including a marked one, so a distant mob
shows its own name rather than the rule's.

### Being told

Turn on **Announce new ones** and the rule speaks up the first time it sees each matching entity — a title, a sound, or
both. **Title style…** opens the same [title](#titles) editor reminders and regions use, and the sound has the same id,
pitch and volume controls. **Test** fires it on the spot so you can judge the sound without waiting for a mob.

"New" means an entity Hex has not seen before, not one that has merely come back into range: a mob that wanders behind a
hill and returns stays quiet. Leaving the world forgets everything, so arriving somewhere is always announced.
**Cooldown** keeps a pack that spawns together down to a single announcement rather than one per mob.

### Where they live

Highlights live in `config/hex/highlights.json` and take part in config profiles and clipboard sharing, so a set of
rules for an island is something you can hand to someone else.

The **Scan interval** setting decides how often the world is checked. Raising it costs less and only delays how quickly
a mob that has just spawned starts glowing — nothing already matched flickers, because a match is remembered for as long
as the entity exists.

Note that a glow drawn through terrain shows you where something is before you can see it. That goes a little further
than most of what Hex does, which is why it is opt-in per rule rather than on by default; see the note on Hypixel's
rules in the [README](../README.md).

## Chat highlight

The words you care about, picked out of chat in a colour you choose. A rule watches for a piece of text, and when a
message contains it the matching words are repainted — the sibling of entity highlight, for the half of Skyblock that
arrives as text rather than as a mob.

The rule list opens from the **Chat Highlight** tab of `/hexa config`, from `/hexa chat edit`, or from the **Open Chat
Highlights** keybind, which is unbound by default. **Add** makes a blank rule and opens it; the only field it really
needs is **Text to find**.

**Text to find** is plain text rather than a pattern: a message counts when it contains that text anywhere, and capitals
are ignored unless **Match capitals** is on. Reminders already cover the pattern-shaped job, and keeping this one
literal means no rule anyone writes can misbehave.

**Channel** restricts a rule to public, party, guild, officer, co-op or private chat, or **Any**, which also covers the
server's own broadcasts. A line only counts as a channel's when somebody is actually speaking on it, so `Guild > Steve:
hi` is guild chat while `Guild > Steve joined.` belongs to no channel.

**Islands** restricts a rule to one or several islands, comma-separated — `hub, dwarven mines` — or anywhere when left
blank. A list rather than the single island an entity highlight takes, because chat follows the player around.

**Paint** covers either the words that matched or the whole message. On top of the **Colour** — chosen through the
[colour picker](#colour-picker), where **Chroma** flows the text through the rainbow — come **Bold**, **Italic**,
**Underline**, **Strikethrough** and **Scrambled**. **Mark before** and **Mark after** put text such as `»` and `«`
either side of the match, which stays visible in a screenshot and to a colour-blind player in a way a colour does not.

**Hide the message** drops a matching line from chat. It still counts as a match, so a rule can silence spam and keep
its sound, and a hidden line still reaches reminders and command suggestions.

**Announce matches** fires a title and a sound, with the same message, [title style](#titles), sound and cooldown
settings reminders, regions and entity highlights use. The cooldown matters more here: a chat line has no identity to
remember, so without one a rule watching a busy channel would fire on every message that mentions its word.

The editor previews the rule on a sample line as it is written, chroma and all, and `/hexa chat test <line>` runs a line
of the player's own past every rule and prints what they made of it. Between them they replace the crosshair an entity
rule is made with — chat cannot be pointed at.

Where two rules claim the same words, the first in the list wins them; both still count as having matched. Clicking and
hovering survive a repaint, because the words are painted inside Hypixel's own message rather than the message being
rebuilt from its text. The one consequence is that a rule whose text would span Hypixel's invisible padding does not
match.

Rules live in `config/hex/chathighlights.json` and take part in config profiles and clipboard sharing. Messages already
in the chat log keep whatever styling they arrived with: chat is styled once, on arrival, so switching a rule on changes
the next message rather than the ones above it.

## Notebook

Somewhere to write things down without leaving the game — a dungeon checklist, a mining route, prices you keep
forgetting, what you were in the middle of doing when you logged off. Notes are written in Markdown, kept between
sessions, and stored as ordinary `.md` files you can open in any text editor.

Open it with **Open notebook…** in the **Notebook** tab of `/hexa config`, with `/hexa note`, or by binding **Open
Notebook** under Options → Controls → **Hex**.

### Finding a note

The browser lists every note you have, newest edit first. Down the left is a sidebar of filters — **All notes**,
**Pinned**, then one entry per folder and one per tag you have used. The search box searches everything at once:
titles, folders, tags, and the text of the notes themselves, showing the line it matched under each result.

Folders and tags are never created or deleted. They exist exactly as long as a note uses them, so filing a note
somewhere new makes that folder appear and moving the last note out makes it go away again.

### Writing

**New** creates a note and opens it. There is no save button — what you type is kept, written a couple of seconds after
you stop typing and again when you close the screen.

Notes are Markdown, so headings, bullet and numbered lists, task checkboxes, quotes, tables and code blocks all work,
and a note pasted in from anywhere else already looks right.

You do not have to type any of that syntax by hand. Above the text is a **formatting toolbar** that works the way a word
processor's does: select some text and press **B**, *I*, strikethrough or code; press **H1**, **H2**, a bullet, a
number, a check box or a quote to change the line; drop in a divider or a link. Every button is a toggle — pressing
**B** on text that is already bold takes it off again — and with nothing selected it leaves the cursor where you need to
type. **Ctrl+B**, **Ctrl+I** and **Ctrl+E** do bold, italic and code from the keyboard.

The **&** button opens Minecraft's sixteen colours — the same sixteen the [colour picker](#colour-picker) offers, with
the same names — plus **chroma**, the flowing colour, and a swatch that returns to plain. Under them is a row of the
colours you have picked recently anywhere in Hex, and under that a field that takes **any** colour: type `#RRGGBB` and
press the swatch next to it, and it joins that shared row. Colours are written into the note as `&c` or `&#RRGGBB`
codes, so they survive export and work anywhere in the text.

The palette is a panel here rather than the full colour picker on purpose: leaving the editor for another screen would
lose the cursor and the selection the colour is meant to apply to.

Beside the source is a **live preview**: the note as it reads, updated as you type, with headings at their own size,
real bullets and check boxes, a bar down quotes, code on a slab, and every colour and chroma run in colour. The button
at the right of the toolbar switches between **Source**, **Split** and **Preview**, and remembers your choice.

The preview covers headings, lists, task boxes, quotes, fenced code, dividers, tables, bold, italic, strikethrough,
inline code and links. Tables get aligned columns and a bold header, and honour the `:---:` alignment markers; a cell
too wide for its column wraps onto as many lines as it needs, so nothing you wrote is ever hidden. The **⊞** button
inserts an empty table to fill in. Anything more exotic than that is shown as the plain text you typed; the note is
still valid Markdown either way. Two lines of prose in a row stay two lines rather than being joined into a paragraph,
because that is what pressing Enter means when you are writing a note in a game.

### Reading a note

**View** on a note's row opens it full width with no toolbar and no source pane — the note as it reads, as large as the
screen allows, for following directions or a checklist while you play.

It is not quite read-only: **check boxes still tick**. Clicking a task line writes the `x` into the note's markdown, the
same edit as typing it yourself, so the list is one you can actually use. **Edit** in the footer opens the same note in
the editor. A note written by a newer Hex is shown but cannot be ticked, for the same reason its editor is read-only.

Editing the styled text directly — with clickable item and coordinate chips — is still to come. Notes written now carry
over untouched, because the text is the note either way.

**Details…** covers the rest of a note: its folder, its tags, the colour of the bar down its row, an item id to use as
its icon, and whether it is pinned to the top of the list.

### How it looks

The **Notebook** tab of `/hexa config` has three display settings. **Sort by** sets the order of the list — last edited,
newest, title, or by hand — with pinned notes always at the top. **Show previews** draws the first line of each note
under its title. **Background opacity** sets how solid the notebook and the editor are: turn it down to see the game
through them, up for a flat backdrop behind the text. The outline of the writing area stays visible at any setting, so
you can always tell where the text ends and the world begins.

Note icons are drawn from the item registry, which the game only fills in once a world is loaded. Open the notebook from
the title screen and the rows show no icons; everything else works, and the icons are back the moment you are in a
world.

### How it looks

The **Notebook** tab of `/hexa config` has five display settings. **Sort by** sets the order of the list — last edited,
newest, title, or by hand — with pinned notes always at the top. **Editor layout** is the same choice the editor's own
button makes: source, preview, or both. **Show previews** draws the first line of each note under its title. **Line
spacing** adds space between the lines of a rendered note, in the preview and the reading screen — raise it for prose
you read while playing, lower it to fit a long checklist on one screen; the editing pane uses Minecraft's own fixed line
height and is not affected. **Background opacity** sets how solid the notebook and the editor are: turn it down to see
the game through them, up for a flat backdrop behind the text. The outline of the writing area stays visible at any
setting, so you can always tell where the text ends and the world begins.

Note icons are drawn from the item registry, which the game only fills in once a world is loaded. Open the notebook from
the title screen and the rows show no icons; everything else works, and the icons are back the moment you are in a
world.

### Sharing

**Export** copies a note to your clipboard and **Import** takes whatever is on your clipboard as a new note. An exported
note carries its folder, tags, colour and icon along with its text, in a small header at the top, so a note you send
someone arrives exactly as you filed it. Plain Markdown from anywhere else imports fine too — it just arrives unfiled,
titled after its first heading. Importing always creates a new note and never overwrites one you already have.

### Where they live

Notes live in `config/hex/notebook/notes/`, one `.md` file each, with `config/hex/notebook/index.json` as a summary so
the list opens without reading every file. Because each note carries its own details in its header, that index is
disposable — delete it and the notebook is rebuilt by reading the files. It also means **dropping a `.md` file into
`notes/` imports it**, whatever wrote it.

Notes are **not** part of a config profile and are not included when you copy settings to the clipboard: they are things
you wrote, not a settings loadout, and switching profiles should not swap out what you have written down. Only the
notebook's display settings, in `config/hex/notebook.json`, travel with a profile.

Writes go to a temporary file and are then moved into place, so a crash mid-save leaves either the old version or the
new one and never half of either. A note written by a newer version of Hex than you are running is shown read-only
rather than saved over.

## Command suggestions

Hex watches which commands you run, learns your habits, and offers them back the next time you open chat. Nothing is
sent anywhere — it all lives on your computer — and nothing is ever run for you: it fills in the box, you press Enter.

### The three ways it helps

**The list.** Start typing a command and a ranked list appears above the chat box, ordered by what you actually use
rather than alphabetically. **↑/↓** move through it, **Tab** or **→** takes the highlighted one — whichever you have set
under **Accept with** — **Escape** dismisses it without closing chat, and clicking a row picks it. Each row carries a
small note on the right — *here*, *next*, *often*, *holding* — saying in one word why it made the list. A line too long
for the chat box is shortened with an ellipsis; what gets typed in is always the whole thing.

**Inline completion.** As you type, the rest of the line appears greyed out ahead of the cursor; **Tab** or **→**
accepts it. It follows the list: arrow down to another suggestion and the greyed-out text becomes that one, so what is
highlighted and what is written ahead of the cursor are never two different answers. This is where it beats Hypixel's
own tab-completion, which knows the command `/warp` exists but has no idea that when *you* type `/warp d` you mean
`dungeon_hub`. It only appears when the guess is a confident one — how confident is the **Inline threshold** slider.

**Just a slash.** Type `/` on its own and it offers what you are most likely to want *right now*. That is a real
prediction, not a most-used list: it changes with where you are standing, what you are holding, what you last ran, and
what chat said in the last few seconds. A party invite thirty seconds ago moves `/party accept` to the top; the same
slash in the Dwarven Mines while holding a drill offers something else entirely.

### What it pays attention to

Where you are (island and the patch of ground you are on), what is in your hand and what kind of thing it is, your
hotbar and armour, how full your inventory is, how long you have been online, the last two commands you ran, and whether
chat has just asked you something. It learns which of those actually predict *your* commands and ignores the rest — and
it learns how much to trust each one from which suggestions you pick, so it gets better at being useful to you
specifically rather than to players in general.

**It reads Skyblock's own calendar too:**

- **The season.** A Skyblock year passes in a little over five real days, so autumn comes round often enough to learn
  from — which is what makes the Spooky Festival commands start appearing before you have thought of them. Read off the
  scoreboard.
- **Skyblock time of day.** Not your clock — Skyblock's, which runs a full day every twenty real minutes. What you do
  when it is dark on Skyblock has nothing to do with what you do at night where you live, and both get learned
  separately. It takes the sun/moon marker as the answer when Hypixel shows one, rather than guessing from the hour.
- **The running event.** The strongest signal of the lot. When the Dark Auction starts counting down, `/warp da` is very
  nearly something you have already announced — and after a couple of auctions Hex knows it.

**Where the event comes from.** Hypixel scatters this one, so Hex looks everywhere it is stated: the **player list**'s
`Event:` widget, which names the Skyblock-wide event on every island and how long is left; the **boss bar**, which is
the only place a mining event (`2X POWDER`, `GOBLIN RAID`) shows up; the **scoreboard**, for the island events that
reach it; and **chat**, which shouts an event's start before anything else knows. The player list is the best of the
four, so if you have turned Hypixel's tab-list widgets off, turning them back on makes this noticeably sharper. An event
Hex has never heard of still counts, under whatever name Hypixel used. When two events overlap, the one ending soonest
is the one credited — the mining event with four minutes left, not the festival with three days. One that has not
started yet counts only inside the last ten minutes of its countdown.

Rows in the list say which of these did the work, so a suggestion that turns up during a Dark Auction is labelled *dark
auction*, and one that turns up because it is dark is labelled *night*.

Suggestions the server offers are kept and re-ranked, never thrown away, so a command Hypixel added last week still
appears — just in the right place in the list.

### What it never records

**Message text is never learned.** For `/msg`, `/w`, `/r`, `/pc` and the like, Hex records the command and who you sent
it to, and throws away what you said before anything is written to disk. A command Hex has never heard of records at
most its first word, so this holds even for commands from a mod or a server it knows nothing about. Turn **Learn player
names** off and the names go too.

What it has learned lives in `config/hex/suggest/model.json` and is deliberately **not** part of config profiles:
switching profiles never swaps it, and **Copy to clipboard** never contains it. Nobody gets a copy of your command
history by asking for your settings.

### Seeing and changing what it knows

**What it has learned…** in the **Command Suggestions** tab (or `/hexa suggest dashboard`) lists every command line it
holds, with how often you use it, when you last did, and what it has associated the command with — *island:
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
- **Suggest known commands** — Hex ships knowing the common Skyblock commands so your first session is not blank. Your
  own use overtakes that within a few dozen commands. Switch it off to start from a completely clean slate.

Turn the feature off and the chat box behaves exactly as vanilla again, server tab-completion included.

## Auto-update

Hex checks its [GitHub releases](https://github.com/Trilleo/Hex/releases) on startup and, when a newer version is out,
downloads it and applies it automatically the next time you close the game. Run `/hexa update` to check on demand, or
manage it from the **Updates** tab of `/hexa config`. See [Updating](../README.md#updating) for the full details.

The **Updates** tab belongs to your installation rather than to a profile: switching, saving or pasting a profile never
changes whether Hex updates itself, and the tab is not part of a **Copy to clipboard** blob.

The result of the startup check reaches you in chat once you are in a world — including when a slow download means that
is a while after you joined.

## Language

Hex speaks **English** and **简体中文 (Simplified Chinese)**. There is no setting to change: the mod follows Minecraft's
own language, so picking a language in Options → Language switches Hex's menus, tooltips, keybind names and reminder
presets along with the rest of the game. Anything a translation has not caught up with falls back to English rather than
going blank.

Three kinds of text stay in English on purpose, because they are matched against Hypixel rather than read:

- **Island names** in a reminder's Island field or an **On island** condition, and in a profile's island rule — these
  are compared to what Hypixel reports, which is in English. Type `dwarven mines`, not the translated name.
- **Skyblock item IDs** in the per-item swing list and the **Holding an item** trigger, such as `HYPERION`.
- **Chat patterns** for a chat-triggered reminder, which have to match the message as the server sent it.

Region and reminder *names* are yours — they are stored as you type them, in any language, and the panel and titles
render them as-is.

Two things are still English everywhere, and will be translated in a later release: the **keybind editor**
screens reached from **Edit keybinds…**, and the lines Hex prints into chat, including the `/hexa` command output.

Adding another language means dropping a translated file into the mod; see
[Translations](TRANSLATIONS.md) if you want to contribute one.

---

## Adding a feature to this file

When a new feature lands, add a `##` section for it here, in the same style as the ones above: what it does, how the
player turns it on or configures it, and any limitation worth stating up front. Write for a player, not a developer —
implementation notes belong in the changelog's **Technical Details**. Then add a matching one-line bullet to the
[README](../README.md) feature list.
