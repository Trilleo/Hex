# Colour picker

Every colour in Hex is chosen on one screen. Any setting that holds a colour shows its value as text with a **swatch**
beside it; click the swatch and the picker opens.

That is true across the whole mod — [entity highlights](Entity-Highlight), [chat highlights](Chat-Highlight),
[regions](Regions), [reminders](Reminders), [item customization](Item-Customization) and the [notebook](Notebook) — and
it stays true for anything added later, because a colour setting has no other control to offer.

## Four ways to reach a colour

People open a colour picker knowing different things, so there are four ways in and they all edit the same value:

| Control        | For                                                                                |
|----------------|------------------------------------------------------------------------------------|
| **The square** | Saturation across, brightness down. Hunting for a colour by eye.                   |
| **The bar**    | Hue, from red round to red.                                                        |
| **#RRGGBB**    | A colour copied from somewhere else. The `#` is optional and case does not matter. |
| **R / G / B**  | A colour written down as numbers, `0`–`255` each.                                  |

## Swatches

Three rows, and the last one is the useful one.

### Minecraft colours

Minecraft's sixteen `&0`–`&f` colours, named. Hypixel writes every rarity, every stat and every broadcast in one of
them, so these are the colours a Skyblock player already thinks in — "the same gold the legendary items use" is a click
rather than a look-up.

| Rarity    | Colour       |
|-----------|--------------|
| Common    | White        |
| Uncommon  | Green        |
| Rare      | Blue         |
| Epic      | Dark purple  |
| Legendary | Gold         |
| Mythic    | Light purple |
| Divine    | Aqua         |
| Special   | Red          |

### Presets

Twelve colours Minecraft has no code for, which is the point of having them: orange, amber, lime, mint, teal, sky,
violet, magenta, pink, coral, crimson and slate. All chosen to stay legible against both a dark HUD and a bright sky.

### Recent

The last twelve colours you picked **anywhere in Hex**, newest first. This is the row that makes two features match: a
colour chosen for a region box is one click away when a highlight wants the same one, and the notebook's own palette
shows the same row.

The **×** at the end of the row forgets them all. Recent colours are stored per installation and are **not** part of a
[config profile](Config-Profiles) — they are a record of what you have been doing, not a loadout, so switching profiles
leaves them alone.

## Copy and paste

**Copy** puts the current value on the clipboard. **Paste** reads one back and accepts `#RRGGBB`, `RRGGBB` and
`0xRRGGBB`, which are the three spellings a colour actually arrives in.

## It applies as you drag

The picker behaves like every other settings row in Hex: the colour is written through as you choose it, so a region box
or the reminder panel behind the screen recolours while you are still dragging. **Done** keeps it and adds it to
**Recent**; **Cancel**, Escape, or closing the screen any other way puts the original back.

## Chroma

Where a colour is allowed to move, the picker has a **Chroma** button. [Chroma](Chroma-Text) is not a colour but a
mode — it flows through the rainbow — and it lives in the colour setting rather than beside it because it answers the
same question the colour does. One control, not two that could disagree.

Chroma is available for:

| Where                                    | What flows                       | Speed set on           |
|------------------------------------------|----------------------------------|------------------------|
| [Item customization](Item-Customization) | The item's name                  | Item Customization tab |
| [Chat highlight](Chat-Highlight)         | The highlighted words            | Chat Highlight tab     |
| [Entity highlight](Entity-Highlight)     | The glow outline                 | Entity Highlight tab   |
| [Regions](Regions)                       | The box, cylinder or sphere      | Regions tab            |
| [Reminders](Reminders)                   | Panel background, text and flash | Reminders tab          |
| [Titles](Titles)                         | Both lines of an alert title     | Titles tab             |

A title's own colours are `&` codes rather than a colour setting, so its chroma comes from writing `&z` (the toolbar's
palette has a button for it). The **Titles** tab's two *default* colour rows are ordinary picker rows, and setting one
to chroma sets every title that has not coloured itself flowing.

> **Upgrading from 1.10.3 or earlier?** Chroma used to be a separate on/off row on a chat rule and on an item
> customization. Those rows are gone and the setting moved into the colour; your existing rules and items are converted
> the first time this version loads them, and go on flowing exactly as before.

## None

Settings where "leave it alone" is a real answer have a **None** button as well: an item's **Name colour** and **Dye
colour**, and a note's colour. It clears the colour rather than replacing it with another one — which was previously not
possible at all once a colour had been set.

## Transparency

A few settings carry transparency as well as a colour, written `#AARRGGBB` with the opacity byte first: region colours,
and the reminder panel's background, text and flash. `80` is roughly half transparent, `FF` fully opaque.

There is no transparency slider. Type the value into the hex field, or leave it alone — whatever transparency the
setting had is carried through everything else you change, so dragging a hue never silently makes a translucent panel
solid.

A chroma region or panel takes the stock transparency for that setting, since a flowing colour is a hue and has none of
its own.

## For developers

A colour setting is one call in a feature's `settingsCategory`:

```kotlin
color(
    "glow_color",
    default = "#FFFF55",
    chroma = true,
    get = { MyConfig.settings.glowColor },
    set = { MyConfig.settings.glowColor = it; MyConfig.markDirty() },
)
```

That produces the swatch, the text field, the reset button and this whole screen. There is deliberately no second way to
ask for a colour, so a feature added next year is consistent with this one without trying to be. See
[Adding a feature](Adding-a-Feature).

Set `chroma = true` only where whatever draws the colour re-reads it every frame or every tick — a colour baked into a
component that is then cached cannot animate, and offering chroma there would ship a setting that visibly does nothing.
Where a render path cannot be made to re-read a value, hand it a component that re-renders *itself*: that is how
[titles](Titles) flow, since the game draws a title from a field it re-reads every frame. Set `alpha = true` when the
value carries an opacity byte. `optional` defaults to true exactly when the setting's own default is blank, which is
what a blank default already meant.
