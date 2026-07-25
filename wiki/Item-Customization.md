# Item customization

Skyblock hands you the same sword everyone else has, called the same thing and drawn the same way. Item customization
changes how **one particular item** looks **on your own client**: what it is called, what colour that name is, whether
it shimmers, and what model or skin it is drawn with.

> **Nothing here leaves your computer.** Your real item is untouched, Hypixel is never told anything, and other players
> see the item exactly as it always was. This only changes what your client draws.

## Customizing an item

**The quick way.** Hover the item in any menu — your inventory, a chest, the auction house — and press **Customize
Hovered Item**, which you bind under Options → Controls → **Hex**.

It ships **unbound on purpose**: the key fires inside Hypixel's own menus, where most letters already mean something.
Pick a key you never press in a chest.

**The other way.** Open the **Item Customization** tab of `/hexa config` → **Customized items…** → **Add held item**.
`/hexa item list` opens the same screen.

## The editor

It shows the item **before and after side by side**, with its live name underneath, and offers:

| Field | What it does |
|---|---|
| **Name** | What to call it. `&` + a colour or format code works — `&6` gold, `&l` bold, `&z` [chroma](Chroma-Text). Blank keeps Hypixel's name. |
| **Name colour** | Colours the whole name. |
| **Chroma name** | The whole name flows through the rainbow — see [Chroma text](Chroma-Text). |
| **Enchant glint** | Always, never, or unchanged. Cosmetic only; enchantments are untouched. |
| **Item model** | A model to draw instead, e.g. `minecraft:diamond_sword`. Models from your resource packs work too. |
| **Head texture** | A player-head skin: a texture hash, a `textures.minecraft.net` link, or the base64 value copied from an item. |
| **Dye colour** | Recolours a dyeable model such as leather armour. Does nothing on a model that cannot be dyed. |

**Every field is "blank means leave it alone"**, so an item you only renamed keeps everything else about its appearance.
Each customization also has its own switch, for turning one off without losing what you set up.

> **Name colour with a blank Name** recolours *Hypixel's* name — which means dropping the colours it came with. That is
> the only way a recolour can show at all.

## Only unique items can be customized

A customization is keyed on the item's **UUID**, which Hypixel gives to **non-stackable** items alone. That is exactly
what makes it follow *your* Hyperion rather than every Hyperion in the game.

Press the keybind on a stackable or a vanilla item and Hex says so, rather than storing something that could never
apply.

## Finding them again

- Slots holding a customized item are marked with a small **✎** in any menu. This can be switched off on the settings
  tab.
- The **Customized items** screen (`/hexa item list`) lists every entry **with the item's original name**, so you can
  edit or delete one without going to find the item first.

## Two things worth knowing

- **Opening the editor closes the Hypixel menu you were in.** Minecraft tells the server a container is closed the
  moment another screen replaces it, so you lose your place in a chest or on an auction page. Nothing else is lost — the
  customization is already saved against the item's UUID.
- **Customizations belong to your installation, not to a [config profile](Config-Profiles).** Switching or pasting a
  profile leaves them alone, since they describe items you own rather than a settings loadout. They are also left out of
  **Copy to clipboard**.

## Storage

`config/hex/item_custom.json`. Chroma speed and width live on the same settings tab and apply to
[every chroma name at once](Chroma-Text).
