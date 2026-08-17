# Mouse sensitivity

Hold a key, scroll the wheel, and your mouse sensitivity changes while you hold it. **Let go and your own sensitivity is
back** — nothing is saved over it. While you hold it, the view also **sticks to round angles** it comes near, so lining
up on a wall or the horizon lands exactly instead of nearly.

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

A small panel appears under your crosshair while you hold, showing where the wheel has taken you as a percentage of your
normal sensitivity. Turn it off with **Show readout** if you would rather have a clean screen.

## Sticky angles

Yaw is a decimal. "Facing south" is exactly 180.000, and there is no holding your hand still enough to land on it —
which is a problem, because almost everything worth lining up on is on one of those angles. So while the key is held,
the view is drawn towards the nearest round angle it is *already close to*, and settles on it exactly.

**It never redirects you.** The pull only exists near an angle, it fades to nothing at the edge of its reach, and a turn
faster than it carries straight on through. At the default settings a deliberate turn passes an angle without noticing
it; slow down to place a shot and the angle takes over.

By default the view sticks **every 45°** on both axes — the four block faces and the diagonals between them for yaw,
and level, straight up, straight down and the halfway looks for pitch.

| Setting                       | Notes                                                                                                                                    |
|-------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| **Sticky angles**             | Master switch for the whole magnet. Off leaves the sensitivity hold exactly as it was.                                                    |
| **Yaw angles**                | Which left/right angles catch: every 90°, 45°, 30° or 15°, or **Off** for none but your own.                                              |
| **Pitch angles**              | The same up/down. Every 90° is level, straight up and straight down.                                                                      |
| **Reach**                     | How close you have to come, in degrees, before an angle pulls at all. 6° by default. Longer is stickier.                                  |
| **Pull strength**             | How hard it pulls once you are inside the reach. 50% by default.                                                                          |
| **Stickier as you slow down** | Grows the reach and the pull as the wheel takes you further below your normal sensitivity. On by default.                                 |
| **Custom angles…**            | Opens the editor below.                                                                                                                  |

When the view has settled on an angle, the readout under the crosshair says which one — `Yaw 180°`, `Pitch 0°`. That
line is how you tell "I am close to south" from "I am on south".

### Why a pull and not a snap

A snap would put you on the angle the moment you entered its reach and hold you there until you fought your way out,
which is two jumps you did not ask for. The pull is a field instead: strongest just inside the reach, zero at the edge,
and zero again once you are on the angle. Nothing jumps, and the only thing it costs you is that arriving takes a
fraction of a second.

The consequence worth knowing is that it is **speed** that breaks you free, not distance. Flick past an angle and you
will not feel it at all.

### Custom angles

The intervals cover everything the world is built square to, which is most of it — but not the wall in a dungeon that
runs off true, or the direction one particular NPC stands in. **Custom angles…** in the Sensitivity tab opens a list you
can add those to.

| Button            | Does                                                            |
|-------------------|-----------------------------------------------------------------|
| **Add**           | An empty row to type an angle into                              |
| **Capture yaw**   | Adds the direction you are facing *right now* as a yaw angle    |
| **Capture pitch** | Adds how far up or down you are looking right now as a pitch    |

Capture is the way in. You look at the thing, open the menu, and press the button — the world is still behind the screen
and you have not turned, so the angle you came to record is the one you are still holding.

Each row has a switch, a **Yaw**/**Pitch** button, the number, and delete. Angles are written in Minecraft's own units —
the ones F3 shows — so yaw runs −180 to 180 with 0 south and −90 east, and pitch runs −90 (straight up) to 90 (straight
down). Rows are folded into those ranges when you leave the screen, and the ones that name a direction say which
("south", "level", "straight up") beside the number.

Switching a row off keeps it in the list without letting it catch, which is how you find out which of several angles is
the one getting in your way. A file holds at most 32.

## Settings

The **Sensitivity** tab of `/hexa config`:

| Setting           | Notes                                                                                                             |
|-------------------|-------------------------------------------------------------------------------------------------------------------|
| **Enabled**       | Master switch. With it off, the keybind is inert and the wheel always belongs to the hotbar.                      |
| **Wheel step**    | How much one notch changes the sensitivity, as a percentage of its current value. 10% by default.                 |
| **Snap on press** | Applied the instant the key goes down, before you scroll, as a percentage of your normal sensitivity. 100% = off. |
| **Show readout**  | The panel under the crosshair while the key is held.                                                              |

Plus the [sticky-angle settings](#sticky-angles) above. All of it lives in `config/hex/sensitivity.json` and travels
with a [config profile](Config-Profiles), so a Dungeons setup can snap harder — and stick to different angles — than a
Mining one.

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
- Sticky angles are **only live while the key is held**. Let go and your aim is vanilla's, untouched, in every respect.
- The key is only read **in the world, with no screen open**. Your inventory and Hypixel's menus keep the wheel.
- While the [freecam](Freecam) is flying, the wheel stays with the freecam's fly speed and nothing sticks — the freecam
  moves the camera, not you.
- Leaving the world, closing the game or switching the feature off mid-hold all hand your sensitivity back first, so it
  can never be left borrowed.

Turning the feature off — by hand, or by switching to a [profile](Config-Profiles) that has it off — while the key is
held restores your sensitivity for the same reason.
