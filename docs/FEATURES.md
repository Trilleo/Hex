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

**Copy to clipboard** copies every Hex setting as text you can send to someone else or keep as a backup.
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

## Auto-update

Hex checks its [GitHub releases](https://github.com/Trilleo/Hex/releases) on startup and, when a newer version is out,
downloads it and applies it automatically the next time you close the game. Run `/hexa update` to check on demand, or
manage it from the **Updates** tab of `/hexa config`. See [Updating](../README.md#updating) for the full details.

---

## Adding a feature to this file

When a new feature lands, add a `##` section for it here, in the same style as the ones above: what it does, how the
player turns it on or configures it, and any limitation worth stating up front. Write for a player, not a developer —
implementation notes belong in the changelog's **Technical Details**. Then add a matching one-line bullet to the
[README](../README.md) feature list.
