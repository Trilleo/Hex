# Chroma text

Chroma is colour that flows through the rainbow — the same effect other Skyblock mods offer.

It is **a choice in the [colour picker](Colour-Picker)**, not a setting of its own. Anything in Hex that can flow is
switched to chroma the way any other colour is chosen: click the swatch beside the setting and press **Chroma**.

## Where it works

| Where                                    | What flows                       | Speed set on           |
|------------------------------------------|----------------------------------|------------------------|
| [Item customization](Item-Customization) | The item's name                  | Item Customization tab |
| [Chat highlight](Chat-Highlight)         | The highlighted words            | Chat Highlight tab     |
| [Entity highlight](Entity-Highlight)     | The glow outline                 | Entity Highlight tab   |
| [Regions](Regions)                       | The box, cylinder or sphere      | Regions tab            |
| [Reminders](Reminders)                   | Panel background, text and flash | Reminders tab          |

Alert **titles** cannot flow: Minecraft draws a title once from a fixed component, so there is nothing to animate it.

> **Upgrading from 1.10.3 or earlier?** Chroma used to be a separate on/off row beside the colour on a chat rule and on
> an item customization. That row is gone and the setting moved into the colour itself. Existing rules and items are
> converted the first time this version loads them and go on flowing exactly as before.

## `&z` — flowing part of some text

For text there is a second, finer way in. Write **`&z`** in an item's **Name** field, or in a [note](Notebook), and
chroma starts at that point:

```
&7Old &zHyperion
```

leaves the first word grey and flows the second. Any colour code, or `&r`, ends it.

`&z` is the same code **NotEnoughUpdates** and **SkyHanni** use, so a name copied from either works here unchanged.

## Any colour, not just the sixteen

`&#RRGGBB` sets a colour Minecraft has no code for — `&#FF8800 Sunset` is orange, and it ends the same way any colour
does, at the next code or `&r`. It works wherever `&` codes do: item names, [note](Notebook) text, note titles. The
notebook's colour palette writes it for you, and the [colour picker](Colour-Picker) is where you find the digits.

## Settings

Each feature that can flow has its own settings on its own tab of `/hexa config`, and each applies to every chroma value
in that feature at once — one item flowing at a different rate from the item beside it reads as a glitch rather than a
choice.

| Setting          | Range            | Effect                                                                                                                                       |
|------------------|------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| **Chroma speed** | 0.5 – 20 seconds | How long one full trip through the rainbow takes. Lower is faster. Present on all five tabs.                                                  |
| **Chroma width** | 2 – 80           | How many characters one full rainbow spans. Low: a short name holds every colour at once. High: it drifts through one colour at a time.       |

**Chroma width** exists only on the two tabs that colour *text* — Item Customization and Chat Highlight. A glow, a box
and a panel are a single colour rather than a run of characters, so there is nothing for a rainbow to spread along and
those tabs simply change colour over time.

## Where it shows

The colours move on their own, so a chroma value animates wherever it appears: an item name in a tooltip, on a container
slot, and in the item-name popup above the hotbar; a glow on the mob as it moves; a region box you can walk around.

A chroma region or panel takes the stock transparency for that setting, since a flowing colour is a hue and carries no
transparency of its own.

It costs a little more to draw than a plain colour, which is why it is off by default and set per item, per rule or per
region rather than applied to everything.
