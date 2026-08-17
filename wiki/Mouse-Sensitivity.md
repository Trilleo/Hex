# Mouse sensitivity

Hold a key, scroll the wheel, and your mouse sensitivity changes while you hold it. **Let go and your own sensitivity is
back** — nothing is saved over it.

A long bow shot wants a slow, steady mouse. Clicking a small NPC in a crowd wants a slower one still. Turning to face
something behind you wants the opposite. Minecraft's answer to all three is Options → Controls → Mouse Settings, which
is not somewhere you go mid-fight.

## Using it

Bind **Hold To Adjust Sensitivity** under Options → Controls → **Hex** (unbound by default), then hold it.

| Input               | Does                                              |
|---------------------|---------------------------------------------------|
| **Hold the key**    | Takes over the sensitivity — and the scroll wheel |
| **Scroll up**       | Faster                                            |
| **Scroll down**     | Slower                                            |
| **Release the key** | Back to your normal sensitivity                   |

Your hotbar does **not** scroll while the key is held, so your slot is where you left it when you let go.

## Settings

The **Sensitivity** tab of `/hexa config`:

| Setting           | Notes                                                                                                             |
|-------------------|-------------------------------------------------------------------------------------------------------------------|
| **Enabled**       | Master switch. With it off, the keybind is inert and the wheel always belongs to the hotbar.                      |
| **Wheel step**    | How much one notch changes the sensitivity, as a percentage of its current value. 10% by default.                 |
| **Snap on press** | Applied the instant the key goes down, before you scroll, as a percentage of your normal sensitivity. 100% = off. |

Both live in `config/hex/sensitivity.json` and travel with a [config profile](Config-Profiles), so a Dungeons setup can
snap harder than a Mining one.

### Why the step is a percentage

A notch is worth a share of where you already are, not a fixed amount. At the default 10%, a step down from 100% lands
on 90% — and a step down from 10% lands on 9%, still a usable move. A fixed step big enough to be useful at the top of
the range would walk you into nothing at the bottom, which is exactly where you were aiming carefully.

### Snap on press

**Snap on press** is the difference between "let me tune this" and "give me precision aim *now*". Set it to 40% and
holding the key drops you straight to 40% of your normal sensitivity, with the wheel there only if you want to
fine-tune. Leave it at 100% and holding the key changes nothing until you scroll.

## What it does and does not touch

- It writes to **Minecraft's own sensitivity**, live, and puts the old value back on release. Your `options.txt` is
  never written, so the number in the vanilla settings screen is the one you chose there.
- The key is only read **in the world, with no screen open**. Your inventory and Hypixel's menus keep the wheel.
- While the [freecam](Freecam) is flying, the wheel stays with the freecam's fly speed.
- Leaving the world, closing the game or switching the feature off mid-hold all hand your sensitivity back first, so it
  can never be left borrowed.

Turning the feature off — by hand, or by switching to a [profile](Config-Profiles) that has it off — while the key is
held restores your sensitivity for the same reason.
