# Hex - Change Log

## Unreleased

### New Features

#### Language

+ Added a **Simplified Chinese (简体中文)** translation, covering every menu, tooltip, confirmation, keybind name
  and reminder preset the mod ships. Hex follows Minecraft's own language setting, so there is nothing to
  configure — pick a language in Options → Language and the mod follows.
    + Text that is matched against Hypixel rather than read stays in English by design: island names typed into
      a reminder, condition or profile rule are compared to the English scoreboard, item IDs are item IDs, and
      a chat pattern has to match the message as the server sent it. Your own region and reminder names are
      stored as you type them, in any language.
    + Not yet covered: the keybind editor screens and the lines Hex prints into chat, which never went through
      translation keys and so stay English for now.

#### Regions

+ Added **Regions** — areas you draw on an island that announce themselves with a title and a sound when you
  walk into them. An island is a big place, and "you are in the Hub" is rarely the thing worth saying.
    + Three ways to draw one, none of which involve typing coordinates. **Region Here** makes a region around
      where you stand in a single keypress. **Mark Region Corner** sets two opposite corners — and while the
      freecam is flying it marks the *camera*, so the top corner of a room can be pinned from the air instead
      of built up to. **Walk Region** records the outline you walk and takes the box around it.
    + **Box, cylinder or sphere**, switchable at any time without drawing the region again. A region stores one
      box and the shape decides how it is read.
    + A customisable title with its own colour, an optional subtitle and a hold time, a sound with the usual id,
      pitch and volume, and a separate message for leaving.
    + **Preview** draws every region on the island as a real shape in the world, labelled, optionally through
      walls, and stays on after the menu closes so you can walk around and look. The region open in the editor
      is always drawn, so a box changes shape behind the menu as you type sizes into it.
    + A **cooldown** per region, so a region across a doorway does not announce itself every time you step
      through, and an **exit margin** so standing on a boundary cannot make one stutter.
    + Regions record the island they were made on and only fire there, since coordinates repeat across islands.
      One made off Skyblock matches anywhere, so they work in singleplayer too.
    + `/hexa region here [radius] [name]`, `mark`, `walk`, `cancel`, `list`, `preview` and `edit`, and five
      rebindable keys under Options → Controls → **Hex**.

#### Reminders

+ Added **Show as a title** to reminders — any reminder can now put its message across the middle of the screen,
  with its own colour, an optional subtitle and a hold time, instead of or alongside the panel row and the
  sound. Capture groups from a chat trigger fill in the subtitle as well as the message.
+ Added **Entering a region** and **Leaving a region** triggers, and **In region** / **Not in region**
  conditions, so the whole countdown, repeat, condition and snooze machinery can hang off an area you drew.
  **Add reminder** in the region editor builds one in a click. Renaming a region updates every reminder that
  named it.

### Fixes

#### Command Suggestions

+ Fixed the suggestion list drawing on top of Minecraft's own, which is what made it look like entries were
  spilling out of the box: the two lists have different widths and different lengths, so the one underneath
  showed through wherever Hex's was narrower or shorter. Only one list is on screen now, whatever you type.
+ Fixed a suggestion longer than the chat box running off the edge of the screen. Lines that do not fit are
  now shortened with an ellipsis, and the list itself is kept on screen — accepting one still types the whole
  command, ellipsis or not.
+ Fixed the greyed-out inline completion staying on the first suggestion while the highlight moved down the
  list, so **→** took a different command from the one highlighted. It now follows the highlight.
+ The right arrow takes the highlighted suggestion out of the list, not just the inline completion. **Accept
  with** is documented as choosing the key for both, and set to *Right arrow* there had been no way to take a
  row from the list at all short of clicking it.
+ Fixed the **Forget everything** button on the learned-commands screen showing a raw
  `hex.suggest.forget_all.tooltip` line instead of its tooltip.

### Technical Details

+ Vanilla's suggestion popup is now switched off on every refresh rather than once on the transition.
  `ChatScreen.onEdited` calls `setAllowSuggestions(true)` and re-asks on every keystroke *before* Hex is given
  the edit, and a completion that needs no server round trip — a command name, which is most of what gets
  typed — resolves inside that call, so vanilla had rebuilt its list by the time Hex was asked to refresh.
  `showSuggestions` does not consult the flag either, so a Tab reaching vanilla rebuilt the list regardless;
  Hex's popup now consumes Tab whether or not Tab is the accept key. Restoring vanilla's popup still happens
  once on the transition, since that one costs a suggestion request.
+ Overlay rows are measured the way they are drawn — as two segments when part of the line is highlighted as
  already typed — because `Font.width` rounds up to a whole pixel and the sum of two rounded halves can exceed
  the rounded whole, leaving a pixel of text outside the popup's background.
+ Region previews are drawn with `net.minecraft.gizmos`, the game's own world-space shape system, collected
  through the public `Minecraft.collectPerTickGizmos()`. The mod gains a world preview without a renderer, a
  mixin, or a render pipeline of its own.
+ Regions and reminders share one action model and one runner: a region holds the same `ReminderAction` list a
  reminder does and fires through the same `ReminderActions.run`, so there is a single implementation of
  "turn an action into a title or a sound" rather than one per feature.
+ Regions are stored separately in `config/hex/regions.json`, registered with the config registry, so they join
  config profiles and clipboard sharing while the Regions tab's reset button leaves a hand-drawn set alone.
+ Language files are now a set rather than a single file, and [docs/TRANSLATIONS.md](docs/TRANSLATIONS.md)
  states the invariant that holds them together: every locale carries the same key set, in the same order, with
  matching `%s` placeholders. It ships the parity check that proves it, since a key missing from one file
  renders as its raw id and is invisible to anyone testing in English.

## Version 1.8.0

### New Features

#### Command Suggestions

+ Added **Command Suggestions** — Hex learns which commands you run, where you run them and what you run them
  after, and offers them back in the chat box. Everything is learned and stored on your own machine; nothing is
  ever sent anywhere, and nothing is ever run for you.
    + A ranked list above the chat box, ordered by what you actually use rather than alphabetically. Arrow keys
      move, Tab accepts, Escape dismisses, and each row carries a one-word note on why it is there.
    + Inline completion greys out the rest of the line as you type — including arguments Hypixel's own
      tab-completion can never offer, like the warp you always mean by `/warp d`. It only appears when the guess
      is confident, and how confident is a slider.
    + Typing just `/` offers what you are most likely to want *right now*, from where you are standing, what you
      are holding, what you just ran, and what chat said in the last few seconds.
    + It reads Skyblock's own calendar as well — the season, whether it is night on Skyblock rather than where you
      live, and whichever event the scoreboard is counting down. A Dark Auction timer on screen is very nearly you
      announcing `/warp da`, and after a couple of them Hex has worked that out.
    + Suggestions the server offers are folded in and re-ranked rather than replaced, so a command added to
      Hypixel yesterday still appears — just in the right place.
    + Ships knowing the common Skyblock commands so the first session is not blank. Your own use overtakes that
      within a few dozen commands, and it can be switched off entirely.
+ Added a screen showing everything that has been learned, reached from the **Command Suggestions** tab or
  `/hexa suggest dashboard`. Every line shows how often you use it and what Hex has associated it with, and can
  be pinned to the top, blocked, or forgotten. **?** on any row shows the full arithmetic behind that
  suggestion — every signal, its value, and how much it counted for.
+ Added `/hexa suggest` — `stats`, `why <text>`, `forget <command>`, `clear confirm`, `pause`, `resume`, and
  `dashboard`.

#### Privacy

+ Command suggestions never record message text. `/msg`, `/w`, `/r`, `/pc` and the rest are learned as the
  command and the recipient, and the message itself is discarded before anything is written; a command Hex does
  not recognise learns at most its first word. Player names can be left out too, with one toggle.
+ What has been learned lives outside the config-profile system on purpose, so switching profiles cannot swap
  it and **Copy to clipboard** cannot share it.

### Fixes

#### Config Profiles

+ Fixed the Skyblock island freezing for the rest of the session once you switched profile by hand. The sidebar was
  polled from inside the auto-switch check, which returns early on a manual switch — so nothing that reads the
  sidebar was updated again, and a reminder conditional on an island stopped firing after arriving there. The
  sidebar is now read centrally, independently of whether auto-switching is still looking.

#### Auto Update

+ Fixed Hex never checking for updates on startup, however the **Updates** tab was set. Its settings were captured
  in config profiles, so switching profiles restored — and wrote back to disk — whatever the snapshot happened to
  hold, silently turning the startup check off for good. `/hexa update` was unaffected, which is why the manual
  check kept working. The **Updates** tab is now a property of your installation: profiles no longer capture,
  restore or share it.
+ Fixed the result of the startup check being lost when it arrived after you had already joined a world — a slow
  connection or a large download could outlast the join, and the message was then dropped for the rest of the
  session even though the update had been downloaded. It is now delivered whenever you next have a world open.

### Technical Details

#### Command Suggestions

+ The ranker is a hybrid: recency-decayed counts, naive Bayes over seventeen categorical context features, and
  an order-2 Markov chain each produce one signal, and a nine-parameter online logistic model learns how much
  to trust each of them for this player. It trains by softmax cross-entropy over the list that was actually on
  screen whenever something is taken from it — accepted from the popup, taken as an inline completion, or typed
  out in full while it was showing. Weights are pulled towards the shipped defaults each step, so the stock
  behaviour is an attractor rather than only a starting point, and one strange week cannot run away with it.
+ Every learned count decays lazily rather than on a sweep: a counter stores when its weight was last correct
  and every read discounts from there, so the model forgets at a configurable half-life with no periodic pass
  over it at any size. Naive-Bayes terms are in pointwise-mutual-information form and shrunk by how much
  evidence stands behind them, which is what keeps a command typed once from becoming that island's most
  confident suggestion.
+ Candidate selection is retrieve-then-rank: a cheap pass over every key on match quality and raw frequency,
  then the full seventeen-feature scoring over the surviving forty. Context is snapshotted once when chat opens
  rather than per keystroke — nothing it reads can change while the chat screen is up, and the hotbar and
  armour signatures cost a tag copy per stack.
+ `ChatScreenMixin` holds no logic; every injection is a one-line delegation to `SuggestSession`, which catches
  everything and disables the feature for the rest of the session on the first exception, so a failure in
  suggestions can never break chat. `CommandSuggestionsAccessor` reads vanilla's in-flight completion request
  so the server's own suggestions can be re-ranked instead of discarded while Hex's popup is up; vanilla's
  popup is suppressed with `setAllowSuggestions(false)` and handed back on the transition, once, rather than
  per keystroke.
+ The model is written to `config/hex/suggest/model.json` outside `ConfigRegistry` — debounced, on a daemon
  thread, and moved into place from a temporary file so a crash mid-write cannot truncate it. Pruning happens
  at save time, when the structure is being walked anyway.

#### Skyblock

+ The scoreboard sidebar is now read once, centrally, by a new `Sidebar` object, with `SkyblockLocation` and the
  new `SkyblockCalendar` as views over the lines it extracts. There are two interpretations of the same lines
  now, and rebuilding every entry's string twice a second to answer two questions instead of one would be waste;
  the extraction is the expensive part and the interpretation is a regular expression.
+ `SkyblockCalendar` reads the Skyblock date, the Skyblock clock and the named event. The date and time lines get
  strict anchored patterns, because those formats are stable and a strict pattern that fails to match is the right
  way to read a stable format; events get a substring vocabulary instead, extensible from the bundled catalogue,
  because event lines carry timers and suffixes that vary per event and per Hypixel update. The sun/moon glyph is
  preferred over the hour for the day/night split — it is Hypixel stating the answer rather than Hex inferring it.

#### Config

+ `ConfigHandle` takes a `global` flag, and `ConfigRegistry.profiled()` returns everything without it. Snapshot,
  restore, clipboard export/import and the unsaved-changes comparison all run over the profiled set; flush, tick
  and reset still run over every config. Snapshots written before a config became global keep their now-ignored
  file, so nothing has to migrate on disk and an older Hex reads the same directory unchanged.

#### Auto Update

+ The startup check logs every outcome — the version it compared, whether one was found, whether it staged, and
  why it fell back to a link when it did not. Previously only a failed check logged anything, so a check that ran
  and a check that never started were indistinguishable.

## Version 1.7.2

### Fixes

#### Hand Display

+ Fixed **Toggle Swing For Held Item** replying **"That is not a Skyblock item"** for every item, including real
  Skyblock ones. Per-item swing rules could not be added by keybind or by the editor's add button, and existing
  rules never matched, so listed items still swung.

### Technical Details

#### Skyblock Items

+ Hypixel serves the component item format natively, and its `id` / `uuid` now sit at the root of
  `minecraft:custom_data` rather than nested inside an `ExtraAttributes` compound. `SkyblockItem` read only the
  nested compound, so every item looked vanilla. It now reads either layout, matching Skyblocker's `ItemUtils`.
  `SkyblockItem.extraAttributes` is renamed to `attributes` to stop implying the old key.

## Version 1.7.1

### Fixes

#### Auto Update

+ Fixed a console window flashing up with **"The batch file cannot be found"** on Windows after quitting the game
  with an update downloaded. The update itself was applied correctly; only the message was wrong. The updater now
  runs without a window at all.

## Version 1.7.0

### New Features

#### Hand Display

+ Added **per-item swing**. List Skyblock items — by item ID, which covers every copy, or by a single item's UUID —
  and holding one in your main hand hides the swing animation, whatever the Hand tab's own swing switch says. Open
  the list with **Per-item swing…** in the **Hand** tab of `/hexa config` or with `/hexa hand swing`.
+ Added a **Toggle Swing For Held Item** keybind under Options → Controls → **Hex**, which adds the item you are
  holding to the per-item swing list or removes it if it is already there, without opening a menu. Unbound by
  default; `/hexa hand toggle` does the same.

#### Reminders

+ Added **Reminders**, a new feature that tells you when something is about to run out. Every reminder counts down;
  what starts the countdown is up to you — a timer, a chat message, arriving at or leaving an island, joining a
  world, or holding a particular Skyblock item. Edit them with **Reminders…** in the new **Reminders** tab of
  `/hexa config`, or with `/hexa remind edit`.
+ Added **chat triggers**, which match a line of chat as a regular expression (or as plain text) and can start a
  countdown from it. Anything captured with `(…)` can be inserted into the reminder's message with `$1` to `$9`, so
  a pattern reading a cooldown out of a chat line can put the actual number on screen.
+ Added **conditions**, checked at the moment a reminder would fire rather than when it started, so a reminder can be
  limited to one island and stays quiet elsewhere without losing its countdown.
+ Added a movable **reminder panel** showing everything that is counting down, with a live countdown per row and a
  flash when one fires. Drag it into place with **Panel position…**, or nudge it with the arrow keys. It is anchored
  as a fraction of the screen, so it survives a resolution, fullscreen or GUI-scale change, and it hides with the
  rest of the HUD on F1.
+ Added per-reminder **sounds**, with a chosen sound event, pitch and volume, and a **Test** button to hear one
  before committing to it.
+ Added a **preset catalogue**. Adding a preset copies it, so it can be edited freely; an unedited copy is updated in
  place when a later version ships a correction, keeping its on/off state and running countdown, while an edited one
  is left alone and offered **Reset to preset**.
+ Added `/hexa remind in <duration> <text>` for a one-off reminder that deletes itself once it has fired, plus
  `list`, `edit`, `hud`, `presets`, `dismiss` and `snooze`.
+ Added **Dismiss Reminder**, **Snooze Reminder** and **Open Reminders** keybinds under Options → Controls → **Hex**,
  unbound by default. Dismiss and snooze work even with the feature switched off, so a reminder already on screen can
  always be silenced.

### Improvements

#### Hand Display

+ Reworded the **Enabled** tooltip in the **Hand** tab to name what the switch actually governs, now that per-item
  swing rules keep working while it is off.

#### Config Menu

+ The settings list can now keep its scroll position when its rows are rebuilt, so the reminder editor no longer
  jumps to the top when a choice changes which fields apply.

### Technical Details

#### Skyblock

+ Added a reusable Skyblock item system under `net.trilleo.skyblock.item`: a reader that pulls an item's
  `ExtraAttributes` ID and UUID out of its custom-data component, a match-rule model that new match kinds can be
  added to without migrating existing config files, and a main-hand cache invalidated on stack identity so the
  per-frame swing hook never deep-copies NBT.

#### Config

+ Added `swing_items.json`, registered with `ConfigRegistry` so the per-item list takes part in config profiles and
  clipboard export. Kept separate from `hand.json` so resetting the **Hand** tab does not clear the item list.

#### Feature Framework

+ Added a `Feature.onHudRender` hook, dispatched from a single HUD element registered in `Features.bootstrap`.
  It is attached before the vanilla chat element rather than added first or last, so it inherits vanilla's own
  render condition and mod overlays hide with F1 without any feature checking for it.
+ Moved the main-hand item cache's tick and reset out of `HandFeature` and into `Features`, next to
  `ProfileAutoSwitch.tick` and outside the per-feature enabled check. It has more than one consumer now, so a
  shared cache no longer depends on one feature's master switch being on.
+ Extracted Hypixel text cleaning into `util.TextClean` and added `util.Duration` for parsing and formatting
  human durations such as `2h30m`.
+ Generalised `Notify.uiSound` to play any registered sound with a pitch and volume, resolving it by id and
  falling back to the standard UI click. The existing pitch-only call sites are unchanged.
+ Extracted hex colour parsing into `util.HexColor`, shared by the config menu's colour rows and the reminder panel.

#### Reminders

+ Reminder definitions live in `reminders.json`, registered with `ConfigRegistry` so they join config profiles and
  clipboard export. Live countdowns are kept apart in `reminder_state.json`, deliberately unregistered: a running
  timer is machine state, and capturing it in a profile would reset every countdown on a profile switch and hand a
  friend your timers when sharing settings.
+ Countdowns are stored as absolute wall-clock instants rather than tick counts, so they survive a relog, keep
  running while the game is closed, and do not drift with server lag. A deadline missed while away fires once, marked
  overdue, rather than replaying every interval in between.
+ User-written chat patterns are guarded three ways, since they run inside the shared chat event where a throw would
  break chat for the whole mod and a hang would lock the client: the subject is capped at 256 characters, matching is
  bounded by a read budget enforced through a counting `CharSequence` (which caps backtracking without a watchdog
  thread), and the whole evaluation is wrapped so nothing escapes. A pattern that exhausts its budget disables its
  reminder and says which one. Compiled patterns are cached, and a bad one is diagnosed once and never retried.
+ The trigger, condition and action models follow the existing `ItemRule` shape — an enum kind over one generic
  string payload — so a new kind is an appended constant and one branch, with no config migration and no failure when
  an older build reads a newer file.

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
