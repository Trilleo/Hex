# Chroma text

Chroma is text whose colour flows through the rainbow — the same effect other Skyblock mods offer. Hex uses it in
[item names](Item-Customization).

## Two ways to switch it on

They do the same thing, at different granularity:

- **Chroma name** in the item's editor — the whole name flows.
- **`&z`** in the **Name** field — chroma starts at that point, so you can flow only part of a name. Any colour code, or
  `&r`, ends it.

```
&7Old &zHyperion
```

leaves the first word grey and flows the second.

`&z` is the same code **NotEnoughUpdates** and **SkyHanni** use, so a name copied from either works here unchanged.

## Any colour, not just the sixteen

`&#RRGGBB` sets a colour Minecraft has no code for — `&#FF8800 Sunset` is orange, and it ends the same way any colour
does, at the next code or `&r`. It works wherever `&` codes do: item names, [note](Notebook) text, note titles. The
notebook's colour palette writes it for you.

## Settings

Two settings on the **Item Customization** tab of `/hexa config`. Both apply to **every chroma name at once** — one item
flowing at a different rate from the item beside it reads as a glitch rather than a choice.

| Setting          | Range            | Effect                                                                                                                                        |
|------------------|------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| **Chroma speed** | 0.5 – 20 seconds | How long one full trip through the rainbow takes. Lower is faster.                                                                            |
| **Chroma width** | —                | How many characters one full rainbow spans. Low: a short name holds every colour at once. High: the name drifts through one colour at a time. |

## Where it shows

The colours move on their own, so a chroma name animates **wherever it appears**: in a tooltip, on a container slot, and
in the item-name popup above the hotbar.

It costs a little more to draw than a plain name, which is why it is **off by default and set per item** rather than
applied to everything.
