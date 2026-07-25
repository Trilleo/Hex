# Hand display

Reposition your held item in first person and change how it swings. Useful when a big sword model covers half the
screen, or when you simply want your hand somewhere else.

**Everything here is cosmetic.** Your attack cooldown, mining speed and reach are untouched — only what is drawn
changes.

## Settings

The **Hand** tab of `/hexa config`:

| Setting | What it changes |
|---|---|
| **Enabled** | Master switch for the hand settings |
| **Position** | Where the main-hand item sits, on each axis |
| **Scale** | How large it is drawn |
| **Rotation** | Its angle |
| **Swing speed** | A multiplier on the swing animation's speed |
| **Hide swing** | Removes the swing animation entirely |
| **Per-item swing…** | Opens the [per-item swing](Per-Item-Swing) list |

Settings apply live, so you can drag a slider and watch your hand move behind the menu.

## Hiding the swing for only some items

**Hide swing** is all or nothing. To hide the animation only while holding particular Skyblock items — and keep it for
everything else — use [per-item swing](Per-Item-Swing). That list works whether or not this tab's master switch is on.

## Storage

`config/hex/hand.json`, and it travels with a [config profile](Config-Profiles) — so a Mining profile can hold the hand
somewhere different from a Dungeons one. The per-item swing list is stored separately in `swing_items.json` and is
**not** cleared by a **Reset tab** on the Hand tab.
