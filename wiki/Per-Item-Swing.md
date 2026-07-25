# Per-item swing

Some items look better swinging and some do not. The [Hand](Hand-Display) tab's **Hide swing** switch is all or nothing;
per-item swing is the exception list.

> While you hold a listed Skyblock item in your main hand, the swing animation is hidden. Hold anything else and your
> normal hand settings apply again.

## Opening the list

- **Per-item swing…** in the **Hand** tab of `/hexa config`
- `/hexa hand swing`

## Adding items

Three ways, easiest first:

1. **The keybind.** Bind **Toggle Swing For Held Item** under Options → Controls → **Hex**. Pressing it adds whatever
   you are holding — or removes it if it is already listed — and says which in chat. This is the one to use.
2. **`/hexa hand toggle`** — the same action without spending a key on it.
3. **Add held item** in the list screen, which picks the right match type for you and fills in the item's name.

## The two ways an entry matches

| Match | Matches | Use when |
|---|---|---|
| **Item ID** | Every copy of that Skyblock item, e.g. `HYPERION` — including one you buy later | You never want that *kind* of item to swing |
| **UUID** | One specific item; a second Hyperion is unaffected | You have two of something and want them to differ |

Only **unique (non-stackable)** items have a UUID, since that is what Hypixel assigns them to.

Item IDs are always written in **English capitals** as Hypixel defines them — they are not translated. See
[Languages](Languages).

## Independent of the Hand master switch

The list has its own switch and works whether or not the **Hand** tab's master switch is on, so you can keep per-item
swing running with the rest of the hand settings off.

## Limitations

- **Skyblock only.** It reads Skyblock's own item data, so it does nothing for vanilla items or on other servers.
- **Main hand only.**

## Storage

`config/hex/swing_items.json`. It travels with a [config profile](Config-Profiles), and it is **not** cleared by a
**Reset tab** on the Hand tab — resetting your hand position should not throw away a list you built up.
