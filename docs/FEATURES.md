# Features

Every feature Hex ships, and how to use it. This is the reference the README's feature list points at — see the
[change log](../CHANGELOG.md) for what changed in each release.

*Hex is in early development — more features will be added here as they land.*

## Config menu

A single, categorized menu for the mod's settings. Open it with `/hexa config` or a rebindable keybind under
Options → Controls → **Hex**. Each feature adds its own tab down the side, a search box filters settings across all of
them at once, every row has a reset button, and a button links straight to the Keybinds screen. Settings apply as you
change them, so you can drag a slider and watch the result.

## Config profiles & sharing

Keep whole named setups side by side and switch between them from the **Profiles** tab; the settings you are leaving are
saved into their own profile first. The same tab copies every setting to the clipboard as text you can send to someone
else or keep as a backup, and pastes one back.

## Keybind shortcuts

Bind a key (optionally with Ctrl/Shift/Alt) to run a sequence of commands/chat messages, where each action has its own
delay and the command inputs offer chat-style tab-completion. Configure bindings in-game via the Hex Keybinds screen;
open it with the rebindable keybind under Options → Controls, from the config menu, or by running `/hexa keybinds`.

## Control switch shortcuts

Bind a key combo to cycle one of Minecraft's own controls between two or more keys, without leaving the game to rebind
it. For example, switch **Attack/Destroy** between **Left Button** and **J** so your clicks stop swinging. Mouse buttons
work as well as keyboard keys; each switch is announced in chat, plays a short sound, and is saved to your Minecraft
options. Add one with **Add Switch** on the Hex Keybinds screen.

## Freecam

Press a keybind to detach the camera from your player and fly it around freely to observe your surroundings (WASD to
move, Space/Shift for up/down, the mouse to look, and the scroll wheel to change speed); press it again to return. Your
character stays in place. Bind it under Options → Controls → **Hex** and tune it in the **Freecam** tab of
`/hexa config`.

## Hand display

Reposition your held item in first person and change how it swings. The **Hand** tab of `/hexa config` has sliders for
the main hand's position, scale and rotation, a swing-speed multiplier, and a switch to hide the swing animation
entirely. Everything is cosmetic: your attack cooldown, mining speed and reach are untouched.

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
