# Keybind shortcuts

A keybind shortcut binds a key — optionally with **Ctrl**, **Shift** or **Alt** — to a **sequence** of commands or chat
messages, each with its own delay. One key for "warp, then party chat that you have arrived"; one key for the three
commands you always run after a dungeon.

This is a Hex feature with its own editor. It is not the same thing as [Hex's own keys](Keybinds) in Minecraft's
Controls screen.

## Opening the editor

Any of:

- `/hexa keybinds`
- The **Open Hex Keybinds** key, bound under Options → Controls → **Hex** (unbound by default)
- The link from the [config menu](Configuration)

## Building a shortcut

1. **Add a keybind** and press the key combination you want. Modifiers are part of the combo, so `Ctrl+G` and `G` are
   different shortcuts and can coexist.
2. **Add actions.** Each action is a line to send — a command such as `/warp dungeon_hub`, or a plain chat message.
3. **Give each action a delay**, so the sequence paces itself instead of firing everything in one tick. Hypixel drops
   commands sent too quickly; the delay is what makes a multi-command shortcut actually work.

The command inputs offer **chat-style tab-completion**, so you can write a command in the editor the same way you would
write it in chat.

## What it does not do

- **It sends what you told it to, when you press the key.** Nothing runs on a timer, on a trigger, or while you are
  away — this is a macro key, not automation. If you want something to happen *in response* to an event, that is what
  [Reminders](Reminders) are for, and they tell you rather than acting for you.
- **It does not chain into itself.** A shortcut sends chat lines; it cannot press another shortcut's key.

## Storage

Shortcuts live in `config/hex/keybinds.json` and travel with a [config profile](Config-Profiles), so a profile can carry
a different set of shortcuts for a different activity.

## See also

- [Control switches](Control-Switches) — the other thing on this screen: rebinding one of *Minecraft's* controls
  mid-game.
- [Keybinds](Keybinds) — the keys Hex registers with Minecraft.
