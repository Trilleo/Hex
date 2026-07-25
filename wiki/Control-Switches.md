# Control switches

A control switch binds a key combo to **cycle one of Minecraft's own controls between two or more keys**, without
leaving the game to rebind it.

The example that motivated the feature: switch **Attack/Destroy** between **Left Button** and **J**, so your clicks stop
swinging while you are clicking through a Hypixel menu — then switch it back with the same combo.

## Setting one up

1. Open the Hex Keybinds screen: `/hexa keybinds`, the **Open Hex Keybinds** key, or the link from the
   [config menu](Configuration).
2. Press **Add Switch**.
3. Pick the Minecraft control to cycle — anything in the vanilla controls list.
4. Add the keys to cycle between. Two is the common case; more is allowed and cycles in order.
5. Choose the key combo (with optional Ctrl/Shift/Alt) that performs the cycle.

## What happens when you press it

- The control moves to the next key in the list.
- The switch is **announced in chat** and plays a short sound, so you always know which state you are in without opening
  Options.
- The change is written to **Minecraft's own options**, so it shows up in the vanilla Controls screen and survives a
  restart.

**Mouse buttons work as well as keyboard keys**, which is the whole point for Attack/Destroy.

## Interaction with profiles

Because a switch writes into Minecraft's options rather than Hex's, it only interacts with
[config profiles](Config-Profiles) when **MC keys** is turned on. With that on, using a control switch counts as
changing your settings and will mark the active profile as having unsaved changes.

## Related

- [Attack mode switch](Attack-Mode-Switch) — a dedicated key for flipping Attack/Destroy between **Hold** and
  **Toggle**, which is a different axis from *which key* it is bound to.
- [Keybind shortcuts](Keybind-Shortcuts) — the other feature on the same screen.

The switches themselves live in `config/hex/keybinds.json`.
