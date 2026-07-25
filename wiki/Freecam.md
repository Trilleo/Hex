# Freecam

Detach the camera from your player and fly it around to look at things — the top of a room, the far side of an arena,
your own build from outside. **Your character stays exactly where it was.**

## Using it

Bind **Toggle Freecam** under Options → Controls → **Hex** (unbound by default), then press it. Press it again to snap
back into your body.

While the camera is detached:

| Input             | Does                         |
|-------------------|------------------------------|
| **WASD**          | Move the camera horizontally |
| **Space / Shift** | Up / down                    |
| **Mouse**         | Look around                  |
| **Scroll wheel**  | Fine-tune the fly speed      |

## Settings

The **Freecam** tab of `/hexa config`:

| Setting       | Notes                                                                                                       |
|---------------|-------------------------------------------------------------------------------------------------------------|
| **Enabled**   | Master switch. With it off, the keybind is inert.                                                           |
| **Fly speed** | Base speed: **Slow**, **Normal**, **Fast** or **Turbo**. The scroll wheel scales around whichever you pick. |

Turning the feature off — by hand, or by switching to a [profile](Config-Profiles) that has it off — while the camera is
detached returns you to your body first, so you are never stranded outside it.

## What it is and is not

The freecam moves **your view**, nothing else. Your player does not move, does not act, and is as visible and as
vulnerable as it was before you pressed the key. It shows you what your client already knows — nothing is revealed that
the server has not already sent.

## It doubles as a region tool

While the freecam is flying, the [regions](Regions) **Mark Region Corner** key places the corner **at the camera**
rather than at your feet. Fly to the top corner of a room and pin it there instead of building a tower to stand on. With
the freecam off, the same key marks your feet as usual.
