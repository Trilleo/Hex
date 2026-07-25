# Languages

Hex speaks **English** and **简体中文 (Simplified Chinese)**.

**There is no setting to change.** The mod follows Minecraft's own language, so picking a language in **Options →
Language** switches Hex's menus, tooltips, keybind names and reminder presets along with the rest of the game. Anything
a translation has not caught up with falls back to English rather than going blank.

## What stays in English on purpose

Three kinds of text are **matched against Hypixel** rather than read by you, so translating them would produce something
that cannot match anything:

| Where                                                                                              | Example                  | Why                                                |
|----------------------------------------------------------------------------------------------------|--------------------------|----------------------------------------------------|
| **Island names** — a reminder's Island field, an **On island** condition, a profile's island rule  | `dwarven mines`          | Compared to what Hypixel reports, which is English |
| **Skyblock item IDs** — the [per-item swing](Per-Item-Swing) list, the **Holding an item** trigger | `HYPERION`               | Hypixel's own identifiers                          |
| **Chat patterns** — a chat-triggered [reminder](Reminders)                                         | `on cooldown for (\d+)s` | Must match the message as the server sent it       |

Region and reminder **names** are yours: they are stored exactly as you type them, in any language, and the panel and
titles render them as-is.

## Still English everywhere

Two things are not yet translated and will be in a later release:

- The **keybind editor** screens reached from **Edit keybinds…**.
- The lines Hex prints into **chat**, including all `/hexa` command output.

## Contributing a translation

Adding another language means dropping a translated file into the mod — no code change, no registration step. See
[Translating](Translating) for the rules (every locale carries the same key set, in the same order) and the parity check
you run before opening a pull request.
