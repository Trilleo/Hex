# Attack mode switch

Minecraft's **Attack/Destroy** control has two modes: **Hold** (keep the button down to keep attacking) and **Toggle**
(press once to start, again to stop). Which one you want depends on what you are doing — hold to break a long line of
blocks, toggle for a sustained fight — and changing it normally means pausing and walking through Options → Controls.

This feature is a key that flips between them where you stand.

## Using it

Bind **Cycle Attack Mode** under Options → Controls → **Hex**. It ships unbound; while it is unbound the feature does
nothing at all.

Press it and:

- **Attack/Destroy** flips between **Hold** and **Toggle**.
- The new mode is **announced in chat**.
- A short sound plays — **higher for Toggle, lower for Hold** — so you can tell which mode you landed on without reading
  chat.
- You are always left **not attacking**, even if the button was latched down at the time. No stuck swinging.

## It drives the vanilla setting

This is not a Hex-private mode. It changes Minecraft's own **Attack/Destroy** mode setting, so the change appears in the
vanilla Controls screen and is saved into your Minecraft options like any other control preference.

## Related

- [Control switches](Control-Switches) change **which key** a control is bound to; this changes **how that key
  behaves**. They compose: you can have one key that moves Attack/Destroy off the mouse, and another that flips it
  between hold and toggle.
- [Keybinds](Keybinds) — every key Hex registers.
