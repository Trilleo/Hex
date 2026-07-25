# Troubleshooting

Work top to bottom: most problems are one of the first three.

## Hex does not load at all

`/hexa` does not exist, and there is no **□** button on the Options screen.

1. **Check the versions.** Hex needs **Minecraft 26.1.2**, **Java 25**, and **Fabric Loader 0.19.3+**. A jar built for a
   different Minecraft version will not load.
2. **Check the dependencies.** Both [Fabric API](https://modrinth.com/mod/fabric-api) and
   [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) must be in `mods`, both built for 26.1.2.
   Missing Kotlin is the single most common cause — Hex is written in Kotlin and cannot start without it.
3. **Check you are launching the Fabric profile**, not vanilla or Forge.
4. **Read `logs/latest.log`.** Fabric says plainly which dependency is missing or which version it wanted.

## The mod loaded but a feature does nothing

- **Is its key bound?** Every Hex keybind ships unbound. Options → Controls → **Hex**. See [Keybinds](Keybinds).
- **Is the feature's master switch on?** Each tab of `/hexa config` has one.
- **Does the feature need Skyblock?** [Per-item swing](Per-Item-Swing), [item customization](Item-Customization) and
  island triggers read Skyblock's own data and do nothing elsewhere.

## Text shows as `hex.config.something` instead of words

A translation key with no value — a bug. Please [report it](https://github.com/Trilleo/Hex/issues) with the key and your
language; as a workaround, switching Minecraft to English shows the intended text.

## My settings keep reverting

You are almost certainly hitting the **explicit save** rule: settings apply immediately but are not written into your
profile until you press **Save**. If you press **Discard**, or switch profiles and choose "discard", your live settings
go back to what the profile holds. See [Config profiles](Config-Profiles).

If settings revert **on restart** instead, check `logs/latest.log` for a config write error — a read-only or
permission-denied `config/hex/` directory is logged and swallowed rather than crashing.

## A profile does not auto-switch

Auto-switching is skipped when:

- You have **unsaved changes** (a `*` next to the profile name).
- You **switched by hand** this session — that turns it off until you disconnect.
- A profile **higher in the list** claims the same place.

Island rules also resolve a moment *after* you join, so the switch lands a beat late by design. Island names must be
typed in **English** (`dwarven mines`), because they are compared to what Hypixel reports.

## A chat-triggered reminder never fires

1. **Test the pattern.** Turn on **Plain text** if you meant to match words rather than write a regex.
2. **Case matters.** Put `(?i)` at the start of the pattern to ignore case.
3. **Match what the server actually sends**, in English, including any prefix.
4. **Check its conditions.** They are evaluated when it *fires*, not when it started — an **On island** condition
   silences the reminder if you have left.
5. **Was it switched off automatically?** A pattern expensive enough to slow the game down is disabled, and Hex says
   which one in chat.

## A reminder fired late, or several fired at once after I logged in

Countdowns are **real time** and keep running while you are logged out. Anything that came due while you were away fires
**once**, marked overdue, rather than firing every interval it missed. This is intended.

## A region does not trigger

- **Islands are recorded per region.** A region made on one island only fires there. `/hexa region list` shows the ones
  for where you are standing; **All islands** shows the rest.
- **Cooldown.** A region stays quiet for its cooldown after firing.
- **Exit margin.** Leaving takes a small distance beyond the boundary, so standing on the edge does not stutter.
- **Use Preview** (`/hexa region preview`) to see the real shape in the world — a cylinder or sphere is the largest that
  fits *inside* the box, which is sometimes smaller than expected.

## Command suggestions are wrong or unwanted

- **`?`** in the dashboard (`/hexa suggest dashboard`) shows exactly why a suggestion was ranked where it was, and
  `/hexa suggest why <text>` prints it for the situation you are in now.
- **○** blocks a command from ever being suggested or recorded again; **✕** forgets one; **☆** pins one.
- **Suggest known commands** off starts you from a clean slate with no shipped defaults.
- Turn the feature off and chat behaves exactly as vanilla, server tab-completion included.

## Suggestions are weak during events

Hex reads the running event from the **player list** first. If you have turned Hypixel's tab-list widgets off, turning
them back on makes event-based suggestions noticeably sharper.

## Auto-update never applies

The downloaded jar waits in `config/hex/update/` and is swapped in when you **close** Minecraft. It will not apply if:

- The game was force-killed rather than closed normally.
- The `mods` folder is read-only, or a launcher holds the jar open.

Move the jar from `config/hex/update/` into `mods` yourself and delete the old one. See [Updating](Updating).

## The item customization key does nothing in a Hypixel menu

Pick a different key. **Customize Hovered Item** fires inside Hypixel's own menus, where most letters already do
something to the menu — a function key or numpad key is a safer choice. Also check the item is **non-stackable**;
stackable items have no UUID and cannot be customized.

## Something looks wrong after switching profiles

Switching restores every profiled config. If **MC keys** is on, it also restores **Minecraft's own key bindings** —
including other mods' — which is why that option is off by default. Turn it off and re-bind if a different mod's keys
moved unexpectedly. See [Config profiles](Config-Profiles).

## Starting over

- One tab — **Reset tab** in the config footer.
- All settings — **Reset all** on the Profiles screen.
- Both leave your saved profiles alone; **Discard** brings them back.
- Everything, permanently — close the game and delete `config/hex/`.

## Reporting a bug

Open an issue at [Trilleo/Hex](https://github.com/Trilleo/Hex/issues) with:

- Your **Hex version** — `/hexa` prints it.
- Your Minecraft, Fabric Loader, Fabric API and Fabric Language Kotlin versions.
- **`logs/latest.log`** — the whole file, not just the part that looks relevant.
- What you did, what happened, and what you expected instead.
