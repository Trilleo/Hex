# Hex - Change Log

## Unreleased

### New Features

#### Hand Display

+ Added **per-item swing**. List Skyblock items — by item ID, which covers every copy, or by a single item's UUID —
  and holding one in your main hand hides the swing animation, whatever the Hand tab's own swing switch says. Open
  the list with **Per-item swing…** in the **Hand** tab of `/hexa config` or with `/hexa hand swing`.
+ Added a **Toggle Swing For Held Item** keybind under Options → Controls → **Hex**, which adds the item you are
  holding to the per-item swing list or removes it if it is already there, without opening a menu. Unbound by
  default; `/hexa hand toggle` does the same.

### Improvements

#### Hand Display

+ Reworded the **Enabled** tooltip in the **Hand** tab to name what the switch actually governs, now that per-item
  swing rules keep working while it is off.

### Technical Details

#### Skyblock

+ Added a reusable Skyblock item system under `net.trilleo.skyblock.item`: a reader that pulls an item's
  `ExtraAttributes` ID and UUID out of its custom-data component, a match-rule model that new match kinds can be
  added to without migrating existing config files, and a main-hand cache invalidated on stack identity so the
  per-frame swing hook never deep-copies NBT.

#### Config

+ Added `swing_items.json`, registered with `ConfigRegistry` so the per-item list takes part in config profiles and
  clipboard export. Kept separate from `hand.json` so resetting the **Hand** tab does not clear the item list.

## Version 1.6.0

### New Features

#### Attack Mode Switch

+ Added a **Cycle Attack Mode** keybind under Options → Controls → **Hex**, which flips Minecraft's
  **Attack/Destroy** control between **Hold** and **Toggle** without leaving the game. Each switch is announced
  in chat and plays a short sound — higher for **Toggle**, lower for **Hold** — and is saved to your Minecraft
  options. Unbound by default.

#### Config Profiles

+ Added a dedicated **Profiles** screen, reached from the button in the config menu's footer. Every profile is
  listed with its description and when it was last saved, and each row can be switched to, copied, renamed or
  deleted.
+ Added profile descriptions, so setups with similar names can be told apart.
+ Added automatic profile switching. A profile can be set to activate on a server (`hypixel.net` also matches
  `mc.hypixel.net`), in singleplayer, or on a named Skyblock island, and it is applied when you get there.
+ Added optional capture of Minecraft's own key bindings, so a profile can carry your whole control setup.
  Turn it on with **MC keys** on the Profiles screen — it is off by default.
+ Added **Import as a new profile**, so someone else's settings can be tried without overwriting your own.

#### Config Menu

+ Added a **Reset tab** button that restores the settings on the current tab to their defaults, and a
  **Reset all** button on the Profiles screen for every setting at once. Both confirm first, and neither
  touches your saved profiles — **Discard** brings the settings back.

### Improvements

#### Config Profiles

+ **Changed how profiles are saved.** Profiles used to be written silently whenever you switched away from
  one. Now the active profile is only written when you press **Save**, and a `*` next to its name means your
  settings have moved away from what it holds. Switching with unsaved changes asks whether to save or discard
  them first, and **Discard** reloads the profile as it was last saved.
+ Deleting a profile now asks for confirmation.
+ Pasting settings that came from a newer Hex now says so, instead of silently applying part of them, and a
  paste from a different Hex version notes the mismatch.

### Fixes

#### Config Profiles

+ Fixed typing a new profile name creating a profile per keystroke — naming it `abc` also left behind `a` and
  `ab`. Naming now happens on its own screen and commits once.
+ Fixed saving a profile silently making it the active one.

#### Config Menu

+ Fixed switching profile dropping you back on the first tab and clearing your search.

### Technical Details

#### Config

+ Reworked `ProfileSettings` around a `ProfileEntry` list carrying name, description, timestamps and the
  auto-switch rule. The old name-only `known` list is migrated by the normalizer on first load and then
  cleared, the same way `KeybindConfig` retires its legacy command fields. The new shape is a *added* field
  rather than a changed one on purpose: `JsonConfig.loadFrom` falls back to a fresh default on any parse
  error, so redeclaring `known`'s element type would have silently emptied every existing profile list.
+ Added `ProfileDirtyTracker`, which compares the live configs against the active profile's snapshot by
  value rather than tracking an edited flag — restores mark every config dirty as part of their work, and a
  flag cannot tell "changed" from "changed back". `ConfigRegistry.flushCount` gates how often the comparison
  runs.
+ Added `ConfigHandle.resetToDefault` and `ConfigRegistry.resetAll` on top of the previously unused
  `JsonConfig.defaultValue`, and a `ConfigCategory.reset` hook so a tab declares its reset in one line.
+ Added `VanillaKeysConfig`, which reads the live options in `current` so snapshots and the dirty check see
  the true bindings, and defers applying them to the first client tick because options do not exist while
  configs load.
+ Added `SkyblockLocation`, a best-effort scoreboard-sidebar reader isolated so a Hypixel layout change
  cannot affect anything but auto-switching.
+ `ConfigProfiles.switchTo` no longer captures the profile being left; `importFromString` returns a typed
  `ImportResult` instead of a nullable count.
+ `HexConfigScreens.rebuild` now reuses the open screen via `rebuildWidgets` rather than constructing a new
  one, which is what preserves the selected tab and search text.

## Version 1.5.1

### Fixes

#### Config

+ Fixed most of `/hexa config` being empty. Every tab except **Keybinds** showed no settings at all, and
  switching tabs did nothing — the **Hand**, **Freecam**, **Updates** and **Profiles** tabs are all usable
  again.

### Technical Details

#### Config

+ Fixed the crash behind the empty tabs: `ResettableRow` attached its tooltip from an `init` block that called
  down into the subclass, which runs before the subclass's widgets exist, so every row carrying a value threw
  an NPE while being built. Rows now attach their own tooltip where the widget is constructed. Only
  action-button rows were unaffected, which is why the Keybinds tab alone kept working.
+ Removed stale references to the Cloth Config classes deleted in 1.5.0 from the config KDoc, including
  descriptions of a Save/Cancel step the menu no longer has.

#### Documentation

+ Moved the full feature descriptions out of the README into a dedicated [docs/FEATURES.md](docs/FEATURES.md).
  The README now carries a one-line summary per feature and links to the new doc, which is where each feature's
  usage and configuration is documented from now on.

## Version 1.5.0

### New Features

#### Hand

+ Added main hand display settings: the new **Hand** tab of `/hexa config` moves your held item around in first
  person with **Position X/Y/Z** sliders, resizes it with **Scale**, and turns it with **Rotation X/Y/Z**. A
  **Reset to defaults** button puts everything back.
+ Added a **Swing speed** slider to make the swing animation play faster or slower, and a **Disable swing
  animation** switch to hide it completely. Both are purely visual — your attack cooldown and mining speed are
  unchanged, and other players still see you swing normally. Note that swing speed also applies to your own
  model in third person, while disabling the animation affects first person only.

#### Config

+ Added config profiles: the new **Profiles** tab of `/hexa config` keeps whole named setups side by side.
  Switch between them from the **Active profile** picker — the settings you are leaving are saved into their
  own profile first, so nothing is lost. Create one from your current settings by typing a name into **New
  profile name**, and remove one with **Delete this profile**.
+ Added settings sharing: **Copy settings to clipboard** puts every Hex setting into a block of text you can
  paste to someone else or keep as a backup, and **Paste settings from clipboard** loads one back. Settings
  the pasted text does not mention are left alone, and text that is not a Hex export is reported in chat
  rather than doing anything.

### Improvements

#### Config

+ Rebuilt the `/hexa config` menu. It gains a search box that filters settings across every tab at once, a
  properly scrolling list in place of the old page arrows, and a reset button on each row that lights up only
  when that setting differs from its default.
+ Settings still apply the moment you change them, so you can drag a slider and watch the result rather than
  hunting for a save button.
+ Every setting label and tooltip is now translatable rather than hardcoded English, so the menu can be
  translated. Tooltips are picked up automatically from the language file.
+ Hex still needs nothing but Fabric API and Fabric Language Kotlin — the new menu is built in, so there is no
  extra config library to install.

### Fixes

#### Config

+ Dragging a slider no longer rewrites the config file on every frame of the drag. Changes are now batched
  and written about a second after you stop, and still flushed immediately when you close the menu, switch
  profile or quit the game.

### Technical Details

#### Config

+ Added a slider control to the config menu (`SliderEntry`), so settings can now take a number over a range
  instead of only a toggle or a fixed list of choices.
+ Split the config system into a backend-agnostic settings model (`ConfigCategory` / `ConfigEntry`) and a
  renderer. Features describe their settings in the mod's own vocabulary and never touch GUI code, so the
  entire menu can be rewritten without a single feature changing.
+ Rewrote the menu as `HexConfigScreen` on vanilla's `ContainerObjectSelectionList`, which supplies scrolling,
  the scrollbar and keyboard navigation. Rows draw through `extractContent` and the screen chrome through
  `extractBackground`, matching this Minecraft build's extractor render pipeline.
+ Grew the entry model to eight row types — boolean, slider, cycle, enum, action, text, colour and keybind —
  so features stop hand-rolling a sub-screen every time they need something other than a toggle.
+ Slider values are projected onto an integer notch grid and rounded to the precision the step implies, which
  keeps `0.01`-step sliders from drifting into values like `0.30000000000000004`.
+ Added `ConfigRegistry` and `ConfigHandle`, which own every user-facing config generically — debounced
  writes, a central flush at shutdown, profile snapshot/restore, and clipboard export/import. `UpdateStaging`
  deliberately stays out of the registry: a half-downloaded jar is machine state, not a setting.
+ Config entries now carry their default value, which drives the per-row reset button and removes the need for
  each feature to hand-roll a reset action.

## Version 1.4.0

### New Features

#### Keybinds

+ Added control switch shortcuts: bind a key combo to cycle one of Minecraft's own controls between two or
  more keys. For example, bind `Alt + /` to switch **Attack/Destroy** between **Left Button** and **J**, so
  you can stop your clicks from swinging without leaving the game to rebind anything. Add one with the
  **Add Switch** button on the Hex Keybinds screen, then pick the control and the keys to cycle through —
  mouse buttons work as well as keyboard keys. Each switch shows the new binding in chat, plays a short
  confirmation sound, and is saved to your Minecraft options just like a manual rebind, so it survives a
  restart.

### Technical Details

#### Keybinds

+ Control switches reuse the existing keybind entry list and its per-tick combo detection; entries carry a
  `type` discriminator, and configs written before this release load as command shortcuts unchanged.
+ Added a shared `Notify` helper for prefixed chat lines and UI sounds, replacing the update checker's
  private copies — the mod previously had no sound playback at all.

## Version 1.3.0

### New Features

#### Freecam

+ Added a freecam: press its keybind to detach the camera from your player and fly it around freely to look at
  your surroundings — WASD to move, Space/Shift for up/down, the mouse to look, and the scroll wheel to change
  speed. Press it again to return to normal; your character stays put the whole time. The keybind lives under a
  new **Hex** category in Options → Controls (unbound by default), and the **Freecam** tab of `/hexa config`
  lets you enable/disable the feature and pick a base fly speed.

### Improvements

#### Keybinds

+ Hex's keybinds now live under their own **Hex** category in Options → Controls instead of being mixed into
  Misc — the config, keybinds and freecam binds are grouped together.
+ Added a rebindable keybind to open the Hex config menu (Options → Controls → Hex), alongside `/hexa config`.

### Technical Details

#### Freecam

+ Added the project's first mixins (`CameraMixin`, `ClientInputMixin`, `KeyboardInputMixin`,
  `MouseHandlerMixin`). The camera is repositioned right after `Camera#alignWithEntity` — before the cull
  frustum is built — so chunk culling follows the freecam wherever it flies; mouse look and scroll are
  redirected, the move vector is forced to zero, and the key-press record is blanked so the real player never
  moves or sends input to the server.
+ Added a reusable `CycleEntry` multiple-choice control to the config-menu framework (a new `ConfigEntry`
  subtype plus a `cycle(...)` builder), and a dedicated "Hex" `KeyMapping.Category`.

## Version 1.2.0

### New Features

#### Config Menu

+ Added a config menu that gathers Hex's settings in one place, split into categories down the side. Open it with
  `/hexa config`. A button on the menu jumps straight to the Keybinds screen.

#### Auto-Update

+ Hex now keeps itself up to date from its GitHub releases. On startup it checks for a newer version in the
  background and, if one is found, downloads it and tells you to restart — the update is applied automatically the
  next time you close the game.
    + Run `/hexa update` to check on demand at any time.
    + The **Updates** tab of `/hexa config` lets you turn the startup check off, opt in to prerelease builds, and
      check for an update on the spot — no more editing `config/hex/update.json` by hand.

### Technical Details

#### Config Menu

+ Added a reusable config-menu framework: a `Feature` contributes a settings tab by overriding
  `settingsCategory()` and returning a `ConfigCategory.build("id", "Title") { toggle(...); action(...) }`, and the
  `ConfigScreen` collects every enabled feature's category into the sidebar automatically. Entries are a small
  sealed `ConfigEntry` hierarchy (`BooleanEntry`, `ActionEntry`), rendered with vanilla widgets like the keybinds
  screens.

#### Auto-Update

+ Added an update feature that queries the `Trilleo/Hex` GitHub releases API (built-in `java.net.http`, no new
  dependency), compares tags with Fabric's `SemanticVersion`, and stages a verified jar under
  `config/hex/update/`. Because the running jar is file-locked by the JVM, the swap is performed on shutdown by a
  detached OS helper script that copies the new jar into `mods/` and removes the old one once the lock is released.
    + The on-demand check is exposed as `UpdateFeature.checkNow()`, shared by the `/hexa update` command and the
      config menu's "Check for updates now" button. Toggling an update setting in the menu persists it immediately.

## Version 1.1.0

### New Features

#### Keybinds

+ Added keybind shortcuts: bind a key (optionally with Ctrl/Shift/Alt) to run a sequence of commands/chat
  messages. Each action has its own input and its own delay, so you can pace a sequence step by step, and
  command inputs offer the same tab-completion as the chat box. Manage bindings in-game through the Hex Keybinds
  screen, opened via a rebindable keybind in the vanilla Controls menu; press Edit on a binding to arrange its
  actions.

#### Commands

+ Added a `/hexa` command. Run `/hexa keybinds` to open the Keybinds screen.

### Technical Details

#### Core

+ Added a Feature/Module lifecycle with a central registry that wires all client events (tick, chat, world
  join/leave, shutdown) and the `/hexa` command hub once and dispatches to registered features, a generic
  `JsonConfig` config helper, and migrated the keybind feature onto them.

## Version 1.0.0

### New Features

#### Misc

+ Initial release for Minecraft 26.1.2.
