# Regions

An island is a big place, and "you are in the Hub" is rarely the thing worth saying. A **region** is an area you draw
yourself — a room, a boss arena, the patch of ground where you always forget something — that announces itself with a
title across the middle of the screen and a sound when you walk into it.

Open the list with **Regions…** in the **Regions** tab of `/hexa config`, with `/hexa region edit`, or with the **Open
Regions** keybind. It shows the regions on the island you are standing on; **All islands** shows the rest. A region you
are currently standing in is marked **here**, so you can check one works without walking out and back in.

## Drawing one

Three ways, and none of them involve typing coordinates.

### Around you

Press **Region Here** (Options → Controls → **Hex**), or run `/hexa region here`. You get a region centred on where you
stand, already named and already alerting.

```
/hexa region here 20 dragon nest
```

sets the radius (1–256 blocks) and the name in one breath. This is the one-keypress way, and it is usually enough.

### Two corners, from the air

Press **Mark Region Corner** once for one corner and again for the opposite one.

> **If the [freecam](Freecam) is flying, the corner lands at the camera, not at you** — so you can fly to the top of a
> room and pin the corner there instead of building a tower to stand on.

With the freecam off it marks your feet. `/hexa region mark` does the same without a keybind.

### Walk the outline

Press **Walk Region**, walk around the edge of the area, and press it again. The region is the box your path fitted
inside, given some height above and below. Good for a shape that no two corners describe.

---

While you are drawing, a panel at the top of the screen says which corner you are on and how big the box is so far, and
the box itself is drawn in the world as it forms. `/hexa region cancel` abandons it.

**Every capture ends by opening the region for editing**, because the one thing Hex cannot guess is what you want it to
say.

## Shapes

A region stores a box, and the **Shape** setting decides how that box is read:

| Shape        | Is                                                 | Good for                                                |
|--------------|----------------------------------------------------|---------------------------------------------------------|
| **Box**      | The box itself                                     | Rooms, platforms, corridors                             |
| **Cylinder** | A circle of the box's width, the box's height tall | "Anywhere near this spot"                               |
| **Sphere**   | A ball inside the box                              | True proximity, when height matters as much as distance |

Switching shape never asks you to draw the region again. A cylinder and a sphere take the largest size that fits
**inside** the box, so a cylinder is round rather than oval — the editor shows the radius you actually get.

## What it says

- **Message** is the title. Turn on **Show as a title** for the big centred text, with its own colour, an optional
  smaller **Subtitle** beneath, and how long it holds before fading.
- **Play a sound** adds one, with the same sound id, pitch and volume that [reminders](Reminders) have. **Test** in the
  editor fires both, so you can judge them without leaving the menu.
- **Announce leaving** fires again on the way out, with its own message if you want a different one.
- **Cooldown** is how long a region stays quiet after firing. It matters more than it sounds: without it, a region
  across a doorway announces itself every time you step through.

Standing exactly on the edge cannot make one stutter either — leaving takes a small **exit margin** beyond the boundary,
set in the tab.

## Seeing them

**Preview** in the regions list — or the **Toggle Region Preview** keybind, or `/hexa region preview` — draws every
region on the island as a real shape in the world, labelled with its name, and **stays on after you close the menu** so
you can walk around and look.

The **Regions** tab decides whether they draw through walls and whether names are shown; each region can have its own
colour.

The region you have open in the editor is **always** drawn, so a box you are typing sizes into changes shape behind the
menu.

## Regions and reminders

A region says its piece the moment you arrive. When you want more than that — a delay, a repeat, conditions, a row on
the reminder panel, a snooze key — press **Add reminder** in the region editor. It creates a [reminder](Reminders) armed
by that region and opens it.

The trigger list also has **Entering a region** and **Leaving a region** for building one by hand, and **In region** /
**Not in region** are available as reminder *conditions*, so a reminder can be limited to one room rather than a whole
island.

**Renaming a region updates every reminder that named it**, so nothing breaks quietly.

## Where they live

Each region records the island it was made on and **only fires there** — coordinates repeat across islands, so a region
without one would go off in places you have never been. A region made **off Skyblock** has no island and matches
anywhere, which is what makes them work in singleplayer.

Regions live in `config/hex/regions.json` and take part in [config profiles](Config-Profiles) and clipboard sharing, so
a set of regions is something you can hand to someone else.

## Commands

```
/hexa region here [radius] [name]
/hexa region mark
/hexa region walk [name]
/hexa region cancel
/hexa region list
/hexa region preview
/hexa region edit
```
