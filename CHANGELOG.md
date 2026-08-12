# Hex - Change Log

## Unreleased

### Improvements

#### Entity Highlight

+ A rule now marks a **distant mob's name tag**, rather than doing nothing until you are close enough to see the mob.
  Hypixel sends a far-away mob as its floating name and nothing else, so there is no body to draw an outline around;
  Hex wraps that name in arrows in the rule's colour instead — `▶ ✦ Lapis Zombie 100/100❤ ◀` — leaving Hypixel's own
  colours and health bar readable. Walk closer, the mob arrives, and it glows the ordinary way.
    + Nothing to switch on: any existing name rule starts doing this. A **type** rule cannot, since a mob that has not
      been sent has no type to match on.
+ Added **Marked name size** to the Entity Highlight tab: how much bigger a marked name tag is drawn, from `1.00×` to
  `4.00×`, half again as big to start with. At the range a mob is name-only, a tag at its ordinary size is a few pixels
  tall.
    + It grows the name about the point it hangs from, so the tag stays over the spot the mob will appear at however
      large it is drawn, and it follows the slider as you drag it rather than waiting for the next scan.

### Technical Details

#### Entity Highlight

+ `HighlightTracker.Match` now carries the marked-up name tag and whether the glow can draw at all, both decided at
  scan time; `HighlightLookup` still does nothing per frame but read the table.
+ The glow colour is no longer written for a marker armor stand. `ArmorStandRenderer` returns no render type for one,
  so the write drew nothing while still switching the whole entity outline pass on for that frame.
+ Added `EntityRendererMixin`, which scales a marked name tag in `submitNameDisplay`. The scale is applied between a
  move to the tag's anchor and back, so vanilla's own anchoring translation is not multiplied by it; the tracker
  publishes the name tags it wrote as an identity set, which is how the render path recognises one without a field
  added to every `EntityRenderState` in the game.

## Version 1.10.1

### New Features

#### Entity Highlight

+ Added **entity highlight**: a list of rules saying which entities to light up, in what colour, and whether finding a
  new one should announce itself. Open it from the **Entity Highlight** tab of `/hexa config`, from
  `/hexa highlight edit`, or with the **Open Entity Highlights** keybind.
+ A rule matches either on the **name** — any fragment of it, ignoring colours and capitals — or on the **entity type**,
  written as an id such as `minecraft:zombie`.
    + On Hypixel a mob's name floats on a separate invisible marker above it, so a name rule follows that link and
      lights up the **mob**, not the marker. A hologram that labels nothing still matches on its own name.
    + An unknown entity id is reported in the editor, rather than leaving a rule that quietly catches nothing.
+ Each rule carries its own **glow colour**, a **range** past which it stops applying, and an optional **island** so the
  same name can mean different things in different places.
+ **Announce new ones** fires a title and a sound the first time each matching entity is seen, with the same colour,
  subtitle, duration, pitch and volume controls reminders and regions already have. A **cooldown** keeps a pack that
  spawns together down to one announcement.
    + "New" means an entity that has not been seen before, not one that has come back into range — a mob that wanders
      out of sight and returns stays quiet. Leaving the world forgets everything, so arriving somewhere is announced.
+ **Show label** floats the rule's name over everything it matches, in the rule's colour, optionally with the distance.
  It never covers up a name the game was already showing.
+ Added **Add this** and the **Highlight What You Look At** keybind, which read the entity under your crosshair and
  build a rule from it — Hypixel's mob names are not something anyone can spell from memory.
+ Added `/hexa highlight nearby`, which prints the type id and the resolved name of everything within 24 blocks. Those
  are the exact strings a rule matches against, so it is somewhere to copy a name from rather than guess at one.
+ Added `/hexa highlight add`, `list` and `edit`, and the **Open Entity Highlights** and **Highlight What You Look At**
  keybinds, both unbound by default.
+ The entity id field **completes as you type**, the way the chat box completes a command: a list drops down, **Tab**
  takes the highlighted entry and pressing it again walks on, the arrow keys move through it, and a click takes the one
  you clicked.
    + Matches on the part after the colon come first, since nobody types the namespace — but `spider` still finds
      `minecraft:cave_spider`, because a match anywhere in the id counts too.
    + An empty field offers every entity the game knows, so the whole set is browsable without knowing a name to start
      from. The list comes from the game's own registry, so it covers anything another mod registers.

#### Config Menu

+ Settings text fields can now offer completions. Only the entity id field uses this today; every other text field is
  unchanged.

### Technical Details

#### Entity Highlight

+ The glow is vanilla's own entity outline, coloured per entity by writing `EntityRenderState.outlineColor` from a mixin
  at the return of `EntityRenderDispatcher.extractEntity`. That is the only point where both halves work: the colour has
  to be in place before `LevelRenderer` checks `appearsGlowing()` to decide whether the outline pass runs at all, and
  vanilla's own extraction overwrites the field partway through. No render pass, no shader and no per-frame geometry
  belongs to this feature.
+ The floating label reuses vanilla's name-tag renderer by writing `nameTag` and `nameTagAttachment` on the same render
  state. Both are written together because vanilla only fills the attachment inside the branch where it decided to show
  a name, leaves it stale otherwise, and dereferences it without a null check.
+ Matching, name resolution and distance work all happen on a tick in `HighlightTracker` and publish an immutable table
  keyed by entity id. The render hook does one map lookup per entity and early-returns on a single volatile read when
  nothing is highlighted.
+ Name-tag resolution queries for a mob beneath a marker only for markers whose text some rule already wants, which on a
  crowded island turns a query per name tag into a query per match.
+ Highlights reuse `ReminderAction` and `ReminderActions.run` rather than growing a parallel alert pipeline, so
  reminders, regions and highlights all deliver a title and a sound through one implementation.

#### Config Menu

+ `TextEntry` gained an optional `suggestions` provider returning the field's whole vocabulary; filtering and ranking
  live in one place (`Suggestions`) rather than at each call site, and the entry model stays free of GUI types.
+ The completion popup is drawn by `ConfigEntryList` after its rows rather than by the row that owns it. A row draws
  inside the list's scissor, so anything it put below itself would be clipped and then painted over by the next row. No
  host screen changed.
+ The popup deliberately does not claim Escape: `Screen.keyPressed` answers it before any child sees it, so a
  dismiss-on-Escape would have been code that never runs.

## Version 1.10.0

### New Features

#### Notebook

+ Added a **notebook**: somewhere to write things down without leaving the game. Notes are written in **Markdown**, kept
  between sessions, and reachable from the new **Notebook** tab of `/hexa config`, from `/hexa note`, or from the new
  **Open Notebook** keybind (unbound by default, under **Hex** in Minecraft's controls).
+ The browser lists every note with its colour, icon, title and opening line, and has a sidebar that filters by **All
  notes**, **Pinned**, or any folder or tag you have used. Folders and tags are never created or deleted — they exist
  exactly as long as a note uses them.
+ Added **search** across everything at once: titles, folders, tags and the text of the notes themselves, showing the
  line each note matched underneath it.
+ Each note has a title, a folder, any number of tags, a colour, an item icon, and a pin that keeps it at the top of the
  list. The list can be sorted by last edited, newest, title, or by hand.
+ Added **export** and **import** through the clipboard. An exported note carries its folder, tags, colour and icon
  along with its text, so a note you send someone arrives exactly as you filed it. Plain Markdown from anywhere else
  imports too — it simply arrives unfiled, titled after its first heading. Importing never overwrites an existing note.
+ Notes are stored as ordinary `.md` files, one per note, under `config/hex/notebook/notes/`. You can open one in a text
  editor, and **dropping a Markdown file into that directory imports it**, whatever wrote it.
+ Notes are kept per installation rather than per config profile, and are left out of the clipboard settings blob:
  they are things you wrote, not a settings loadout, so switching profiles never swaps them. Only the notebook's display
  settings, in `config/hex/notebook.json`, travel with a profile.
+ `/hexa note` gained `list`, `new`, `open`, `search`, `export` and `import`. A note is looked up by title — exactly
  first, then ignoring case, then by prefix — so `/hexa note open mining` finds "Mining routes".
+ Added a **formatting toolbar** to the note editor, working the way a word processor's does: select text and press
  **B**, *I*, strikethrough or code; press **H1**, **H2**, bullet, numbered, check box or quote to change the whole
  line; insert a divider or a link. Every button is a toggle, the selection survives the press so two styles can be
  applied in a row, and **Ctrl+B**, **Ctrl+I** and **Ctrl+E** do the three commonest from the keyboard.
+ Added a **colour palette** to the editor — Minecraft's sixteen colours, **chroma**, and a swatch back to plain. It
  writes `&c` codes into the note, so colours survive an export and read the same as a customized item name does.
+ Added a **live preview** beside the source, updated as you type: headings at their own size, real bullets and check
  boxes, a bar down quotes, code on a slab, dividers as lines, and every colour and chroma run in colour. The toolbar's
  right-hand button switches between **Source**, **Split** and **Preview**, and the choice is remembered as the new
  **Editor layout** setting.
+ Added a **View** button to every note in the browser, opening the note full width with nothing to edit — and **check
  boxes you can still tick**. Clicking a task line writes the `x` into the note itself, so a checklist is usable while
  you play instead of being a picture of a checklist.
+ **Tables are now drawn** in the preview and the reading screen: aligned columns, a bold header on its own row, and the
  `:---:` markers respected. A cell too wide for its column **wraps onto as many lines as it needs** — a table never
  hides part of what you wrote — and cell text sits in the middle of its cell, so a short cell beside a wrapped one
  still lines up with it. The toolbar's **⊞** button drops in an empty one.
+ The colour palette now takes **any colour, not just Minecraft's sixteen**: type `#RRGGBB` in the palette's field and
  press the swatch beside it. It writes a `&#RRGGBB` code — the spelling other Skyblock mods and server plugins already
  use — which works anywhere a colour code does, [chroma text](https://github.com/Trilleo/Hex/wiki/Chroma-Text)
  included, so item names can use it too.
+ Added a **Line spacing** setting to the **Notebook** tab: extra space between the lines of a rendered note, in both
  the editor's preview and the reading screen. The stock setting is roomier than before, and it goes from lines touching
  to double-spaced. The editing pane keeps Minecraft's own line height, which is fixed.
+ Added a **Background opacity** setting to the **Notebook** tab: how solid the browser and the editor are, from
  see-through to a flat backdrop. The writing area is no longer a slab of black — it uses the same setting, and keeps
  its outline at every value so you can still tell where it ends.

### Technical Details

#### Notebook

+ The note's **markdown source is canonical**, not a parsed tree. The block model the visual editor will work on is a
  projection of that string, which is what makes the round trip total: anything the parser cannot yet model still
  survives, because the text it came from is what is on disk.
+ Each note file carries its metadata in a `---hex` front-matter header, serialized as a `NoteMeta` through the shared
  GSON instance — no second schema. That is what makes a `.md` file a *complete* note, makes `index.json` a disposable
  cache that is rebuilt from the files when it is missing or damaged, and makes export nothing more than the file's own
  text. The header is fenced with `---hex` rather than `---` so a horizontal rule in the body cannot truncate a note.
+ `NotebookStore` follows the `ModelStore` discipline: debounced (two seconds, longer than a settings write and far
  shorter than the suggestion model's), dispatched on one daemon thread, written atomically, and version-guarded — an
  `index.json` from a newer Hex is read around rather than overwritten, and a note file from a newer Hex is shown
  read-only.
+ Extracted `AtomicWrite` from `ModelStore` — temp sibling plus `ATOMIC_MOVE` with a plain-replace fallback — so the two
  things in the mod that must never lose a file share one implementation.
+ Added a **JUnit 5 test source set** under `src/test/kotlin`, the first tests in this repository, covering the pure
  logic that touches no Minecraft runtime: the front-matter round trip and `NoteMeta`'s repair of GSON's reflection
  gaps. Run with `./gradlew test`.
+ Fixed a crash when the notebook was opened from the title screen. Item components are bound when a world's data packs
  load, so building the row icon's `ItemStack` before that threw `Components not bound yet` out of a screen's extract
  pass. `NoteIcon` now caches the item's `Holder` rather than a stack, checks `areComponentsBound()`, and reports no
  icon instead — which also fixes a stack cached under one world's components being drawn under another's.
+ Added `NoteBlock` and `NoteInline`, the notebook's own markdown model: a *line* parser rather than a conforming one,
  because the preview sits beside the source and must never disagree with the line the caret is on. `NoteInline` carries
  the legacy colour state across emphasis runs itself, so `&c**red bold**` comes out red *and* bold even though the
  markers split the line into runs `Chroma` never sees as one string.
+ Added `NotePreview`, a scrolling `AbstractScrollArea` that lays out wrapped rows once per edit and only redraws
  between times — except for chroma, which rebuilds on a 50 ms timer rather than per frame so a long note stays cheap.
+ Added `MultiLineEditBoxAccessor` and `MultilineTextFieldAccessor`. The toolbar needs the *selection*, and
  `MultiLineEditBox` re-exports only `getValue`/`setValue`, a whole-document swap that would move the caret to the end
  of the note on every button press. `getSelected()` is public but returns a protected nested record no caller can name,
  so the two cursor fields are read directly instead. Edits go through select-then-`insertText`, the same path a paste
  takes, which is why the value listener still saves the note with nothing extra.
+ Added `NoteBlocksTest` — eleven cases over the block parser's genuinely ambiguous edges, where a rule and a bullet
  start with the same character and a task and a bullet with the same two.
+ Added `NoteTasks`, the one part of the reading screen that changes a note: a pure string function that flips the box
  on a given line and touches nothing else on it — not the indent, the bullet character or the spacing. Eight tests
  cover it, because it rewrites text the player wrote.
+ Table cells are wrapped to their column and the row takes the height of its tallest cell, so the grid's rules and
  column separators are drawn per laid-out *line* rather than per row — which is also what lets a wrapped table scroll
  and clip like everything else in the pane. Each cell is rendered once and both its column's width and its wrapping
  come from that same component: measuring the plain string instead is wrong by exactly the styling, and a bold header
  cell measured plain overflows its column by a character, so `Floor` wraps to `Floo` / `r`.
+ `NoteBlock` gained tables, which are the one construct that cannot be one block per line, and a source line number on
  list items, which is how a click on a rendered check box finds the character to change. A line with no pipe ends a
  table rather than joining it as a one-cell row — the one place the parser knowingly differs from GFM, and it differs
  the way a note behaves.
+ `Chroma` learned `&#RRGGBB`, so every consumer of it — note text, note titles, item names, the HUD — gained full RGB
  at once rather than the notebook growing a private colour syntax.
+ The editor's panes stop short of the footer by the height of `MultiLineEditBox`'s character counter, which the widget
  draws just below itself with no say in the matter — it was landing under the Done button.
+ The notebook's surfaces moved into a `NotebookTheme`, read per frame from `NotebookConfig.backgroundOpacity`, and the
  editor draws the text area's background itself — `MultiLineEditBox`'s own sprite is flat black with no say in how
  solid it is.

## Version 1.9.3

### Technical Details

#### Documentation

+ Added the source of the GitHub wiki under `wiki/`: a landing page, a page per feature, installation, updating,
  commands, keybinds, config files, an FAQ and troubleshooting, plus developer pages on building, architecture, adding a
  feature, translating, releasing and contributing.
+ Added [docs/WIKI.md](docs/WIKI.md), documenting the wiki's page conventions, how the pages are published to the wiki
  repository, and what has to move with a feature change.
+ Added keeping the wiki in sync to the after-every-change checklist in `CLAUDE.md`, so a new feature ships with its
  page, its sidebar entry and its row on the wiki's landing page.

## Version 1.9.2

### New Features

#### Item customization

+ Added **item customization**: change how one specific Skyblock item looks on your own client. Hover it in any menu and
  press the new **Customize Hovered Item** keybind (unbound by default, under **Hex** in Minecraft's controls) to give
  it a new name, a new name colour, a forced enchant glint, a different item model, a player-head skin, or a dye colour.
  Everything is client-side — nothing is sent to Hypixel, your real item is untouched, and other players see it exactly
  as it was.
+ Customizations are keyed on the item's **UUID**, so they follow one particular item rather than every copy of it.
  Stackable items have no UUID and cannot be customized; pressing the keybind on one says so instead of quietly doing
  nothing.
+ Added a **Customized items** screen, reachable from the new **Item Customization** tab of `/hexa config` and from
  `/hexa item list`, listing everything you have customized so you can edit or remove an entry with the item nowhere in
  reach. It can also customize whatever is in your main hand.
+ Slots holding a customized item are marked with a small **✎** in any menu, so a customization is never invisible. This
  can be switched off on the settings tab.
+ Customizations are stored per installation rather than per config profile: switching or pasting a profile leaves them
  alone, since they describe items you own rather than a settings loadout. They live in `config/hex/item_custom.json`.

#### Chroma text

+ Added **chroma** — text that flows through the rainbow, as other Skyblock mods have it — and made item names able to
  use it. Turn on **Chroma name** in an item's editor for the whole name, or write **`&z`** in the **Name** field to
  start it part-way through, so `&7Old &zHyperion` leaves the first word grey. `&z` is the code NotEnoughUpdates and
  SkyHanni use, so a name copied from either works unchanged; any colour code or `&r` ends it.
+ Added **Chroma speed** and **Chroma width** to the **Item Customization** tab, controlling how long one trip through
  the rainbow takes and how many characters it spans. Both apply to every chroma name at once — one item flowing at a
  different rate from the one beside it reads as a glitch rather than a choice.
+ Chroma names animate everywhere a name appears: tooltips, container slots, and the item-name popup above the hotbar.

### Technical Details

#### Item customization

+ Added three mixins: `ItemStackMixin` (a customized name at the return of `getHoverName`, the one choke point every
  tooltip, slot label and hotbar popup reads), `ItemModelResolverMixin` (a substituted stack at the head of
  `appendItemLayers`, the single funnel every drawn item passes through — swapping the argument redirects the model
  lookup and everything the model reads off the stack, so model, head skin, dye and glint all follow from one hook), and
  `AbstractContainerScreenAccessor` (the protected `hoveredSlot`, `leftPos` and `topPos` fields).
+ The hovered-slot keybind and the slot marker are wired through Fabric's `fabric-screen-api-v1` rather than a fourth
  mixin, which leaves vanilla's own input handling untouched.
+ Resolution is cached on the `ItemStack` itself through a duck interface. A stack's Skyblock UUID is read from NBT at
  most once per instance for the life of that instance — `CustomData.copyTag()` deep-copies the whole tag, and these
  hooks run for every drawn item every frame — while the resolved name and render stack are stamped with a config
  generation counter that any edit bumps, so the editor's preview updates live without tracking which items an edit
  touched.
+ `Notify.chat` and `Commands.feedback` gained `Component` overloads, so new player-facing text can come out of the
  language files rather than being written in English at the call site.

#### Chroma text

+ Added `net.trilleo.util.Chroma`, a feature-independent helper: the hue-over-time maths, a frame token for caches, and
  a parser turning `&`/`§`-coded text into a real component tree. Building a tree rather than leaving `§` codes in a
  literal is what lets chroma give every character its own colour, and it also fixes inline codes and a base colour
  being uncombinable — a `§` code inside a literal takes over the rest of the line regardless of the component's style.
+ The per-stack name cache gained a second stamp. The config generation cannot expire a chroma name, whose colours
  change while the config sits still, so a cached name now also records the `Chroma` frame it was built for — or
  `Chroma.STATIC` for a name that does not animate and must not be rebuilt every frame. A single hover asks for a name
  several times over, so even an animating name is resolved once per frame rather than once per ask.

## Version 1.9.1

### New Features

#### Config menu

+ Added a small **□** button beside **Done** on Minecraft's own Options screen that opens Hex's config menu. Until now
  the menu could only be reached by typing `/hexa config` or by binding the keybind that ships unbound, so a fresh
  install had no obvious way in. The button is on the Options screen wherever it is opened from, including the title
  screen — where neither the command nor the keybind works.
+ Added **Mod Menu** support: if you have Mod Menu installed, the settings button on Hex's entry in its mod list now
  opens the config menu, and closing it returns to the list. Mod Menu stays entirely optional — it is not required, not
  bundled, and Hex behaves the same without it.

### Fixes

#### Command suggestions

+ Fixed Hex only noticing a Skyblock event when the **scoreboard** happened to name one — which is almost never. The
  scoreboard shows what the island you are standing on cares about, so a mining event in the Dwarven Mines, Hoppity's
  Hunt anywhere at all, or a farming contest away from the Garden were all invisible, and the event was Hex's strongest
  hint about what you are going to type next. It now reads every place Hypixel states an event, the way SkyHanni and
  Skyblocker do:
    + the **player list**, whose `Event:` widget names the Skyblock-wide event on every island and says how long is
      left — enable the tab-list widgets in Hypixel's own settings and this is the best source there is;
    + the **boss bar**, which is the only place a mining event (`2X POWDER`, `GOBLIN RAID`, `RAFFLE`) appears at all;
    + the **scoreboard**, still, for the island events that do show up there, now with the countdown it sometimes puts
      beside them;
    + **chat**, which shouts an event's start and end before anything else knows about it.
+ An event Hex has never heard of is now detected too, under the name Hypixel gave it, instead of being ignored for not
  being on a list. When several events are running at once, the one ending soonest is the one a suggestion is credited
  to — a mining event with four minutes left says more about what you are about to do than a month-long festival does.
  An event that has not started yet only counts once it is within ten minutes, so a countdown to something a day away no
  longer colours anything.

#### Regions

+ Fixed a region's island being taken from the smaller **area** the scoreboard names — `village`,
  `community center`, `royal mines` — instead of the island it sits on. A region set to `hub` never matched (the
  scoreboard says `village`, never `hub`), and one that spanned two areas stopped firing at the seam. Hex now asks
  Hypixel which island you are on, the way SkyHanni and Skyblocker do, and gets the whole island (`hub`,
  `private island`, `dwarven mines`) — the same wherever you walk on it.
    + Regions made before this fix may have recorded an area name; correct the **Island** field in the region editor if
      one has stopped firing.

#### Config profiles

+ Fixed a profile set to switch on a Skyblock island never activating, for the same reason — an **On a Skyblock island**
  rule for `hub` was compared against the area the scoreboard showed (`village`) and could not match. Island rules, and
  reminders' **Arriving at** / **Leaving island** triggers and **On island** condition, now match the actual island.

### Technical Details

#### Config menu

+ The Options screen button is `net.trilleo.mixin.OptionsScreenMixin` plus `config/gui/OptionsMenuShortcut.kt`, which
  owns the button and all of its geometry. It is added to the screen's `HeaderAndFooterLayout` footer as a padded child
  rather than placed at fixed coordinates, so it tracks Done across a resize — vanilla's `resize` re-arranges the layout
  without re-running `init`.
+ Mod Menu is a `compileOnly` dependency (`modmenu_version` in `gradle.properties`, Terraformers maven), so nothing is
  bundled and `fabric.mod.json`'s `depends` is untouched. `config/ModMenuIntegration.kt` is the only code that names the
  API, and it is reachable only through the `modmenu` entrypoint, which Fabric resolves lazily — with Mod Menu absent
  the class is never loaded and its types are never resolved. It hands back the same
  `HexConfigScreens.create(parent)` screen as every other way in, so Mod Menu is a shortcut, not a second config
  backend.

#### Command suggestions

+ Event detection moved out of `SkyblockCalendar` into a new `net.trilleo.skyblock.SkyblockEvents`, which merges four
  sources instead of parsing one. Each source holds *claims* (name, source, optional start and end) that it replaces
  wholesale on every poll, so a source that stops naming an event withdraws it, while a source that has gone silent — an
  empty player list mid-transfer, a boss bar that glitched away — withdraws nothing. Every claim ages out through a
  per-source TTL (15s for the polled sources, 5min for chat) and at its own countdown, so nothing can stick on an event
  that has ended. `current` is computed on read: running events ranked by time left, then imminent ones within ten
  minutes. The event vocabulary is no longer a gate on detection, only the canonical spelling of the names it knows, so
  what the ranker has already learned against (`dark auction`)
  keeps its key.
+ Added `net.trilleo.skyblock.TabList`, a shared reader of the player list alongside `Sidebar`: it polls
  `getListedOnlinePlayers` once a second (packet data, so it works whether or not Tab is held), sorts with a copy of
  vanilla's `PlayerTabOverlay` ordering built from public getters, cleans each display name through
  `TextClean`, and keeps blank entries because Hypixel separates one widget from the next with one. It reads nothing off
  Skyblock.
+ Added a `BossHealthOverlayAccessor` mixin exposing `BossHealthOverlay.events`, the only way to read boss-bar text
  without drawing it. Both new readers are driven centrally from `Features` (tick, disconnect, chat), outside any
  feature's enabled check, like the sidebar and the island resolver.
+ `SkyblockCalendar` keeps the date and the clock and no longer has an `event`; the sidebar remains one of the event
  resolver's sources through `SkyblockEvents.acceptSidebar`.

#### Regions

+ Island detection now comes from Hypixel's own location data via `/locraw`, in a new
  `net.trilleo.skyblock.IslandResolver`, rather than from the scoreboard sidebar. `SkyblockLocation` separates the
  resolved `island` from the scoreboard `area`; `current` prefers the island and falls back to the area, so nothing
  regresses on a server that never answers `/locraw`. The resolver asks once per world join (islands are separate
  servers), re-asks on an area change without a rejoin at most once every 15s, only sends once the scoreboard confirms
  Skyblock, gives up after a few unanswered tries, and swallows the JSON reply to its own request so it never reaches
  chat or a reminder pattern. Driven centrally from `Features` (tick, join, disconnect, chat).

## Version 1.9.0

### New Features

#### Language

+ Added a **Simplified Chinese (简体中文)** translation, covering every menu, tooltip, confirmation, keybind name and
  reminder preset the mod ships. Hex follows Minecraft's own language setting, so there is nothing to configure — pick a
  language in Options → Language and the mod follows.
    + Text that is matched against Hypixel rather than read stays in English by design: island names typed into a
      reminder, condition or profile rule are compared to the English scoreboard, item IDs are item IDs, and a chat
      pattern has to match the message as the server sent it. Your own region and reminder names are stored as you type
      them, in any language.
    + Not yet covered: the keybind editor screens and the lines Hex prints into chat, which never went through
      translation keys and so stay English for now.

#### Regions

+ Added **Regions** — areas you draw on an island that announce themselves with a title and a sound when you walk into
  them. An island is a big place, and "you are in the Hub" is rarely the thing worth saying.
    + Three ways to draw one, none of which involve typing coordinates. **Region Here** makes a region around where you
      stand in a single keypress. **Mark Region Corner** sets two opposite corners — and while the freecam is flying it
      marks the *camera*, so the top corner of a room can be pinned from the air instead of built up to. **Walk Region**
      records the outline you walk and takes the box around it.
    + **Box, cylinder or sphere**, switchable at any time without drawing the region again. A region stores one box and
      the shape decides how it is read.
    + A customisable title with its own colour, an optional subtitle and a hold time, a sound with the usual id, pitch
      and volume, and a separate message for leaving.
    + **Preview** draws every region on the island as a real shape in the world, labelled, optionally through walls, and
      stays on after the menu closes so you can walk around and look. The region open in the editor is always drawn, so
      a box changes shape behind the menu as you type sizes into it.
    + A **cooldown** per region, so a region across a doorway does not announce itself every time you step through, and
      an **exit margin** so standing on a boundary cannot make one stutter.
    + Regions record the island they were made on and only fire there, since coordinates repeat across islands. One made
      off Skyblock matches anywhere, so they work in singleplayer too.
    + `/hexa region here [radius] [name]`, `mark`, `walk`, `cancel`, `list`, `preview` and `edit`, and five rebindable
      keys under Options → Controls → **Hex**.

#### Reminders

+ Added **Show as a title** to reminders — any reminder can now put its message across the middle of the screen, with
  its own colour, an optional subtitle and a hold time, instead of or alongside the panel row and the sound. Capture
  groups from a chat trigger fill in the subtitle as well as the message.
+ Added **Entering a region** and **Leaving a region** triggers, and **In region** / **Not in region**
  conditions, so the whole countdown, repeat, condition and snooze machinery can hang off an area you drew. **Add
  reminder** in the region editor builds one in a click. Renaming a region updates every reminder that named it.

### Fixes

#### Command Suggestions

+ Fixed the suggestion list drawing on top of Minecraft's own, which is what made it look like entries were spilling out
  of the box: the two lists have different widths and different lengths, so the one underneath showed through wherever
  Hex's was narrower or shorter. Only one list is on screen now, whatever you type.
+ Fixed a suggestion longer than the chat box running off the edge of the screen. Lines that do not fit are now
  shortened with an ellipsis, and the list itself is kept on screen — accepting one still types the whole command,
  ellipsis or not.
+ Fixed the greyed-out inline completion staying on the first suggestion while the highlight moved down the list, so
  **→** took a different command from the one highlighted. It now follows the highlight.
+ The right arrow takes the highlighted suggestion out of the list, not just the inline completion. **Accept with** is
  documented as choosing the key for both, and set to *Right arrow* there had been no way to take a row from the list at
  all short of clicking it.
+ Fixed the **Forget everything** button on the learned-commands screen showing a raw
  `hex.suggest.forget_all.tooltip` line instead of its tooltip.

### Technical Details

+ Vanilla's suggestion popup is now switched off on every refresh rather than once on the transition.
  `ChatScreen.onEdited` calls `setAllowSuggestions(true)` and re-asks on every keystroke *before* Hex is given the edit,
  and a completion that needs no server round trip — a command name, which is most of what gets typed — resolves inside
  that call, so vanilla had rebuilt its list by the time Hex was asked to refresh.
  `showSuggestions` does not consult the flag either, so a Tab reaching vanilla rebuilt the list regardless; Hex's popup
  now consumes Tab whether or not Tab is the accept key. Restoring vanilla's popup still happens once on the transition,
  since that one costs a suggestion request.
+ Overlay rows are measured the way they are drawn — as two segments when part of the line is highlighted as already
  typed — because `Font.width` rounds up to a whole pixel and the sum of two rounded halves can exceed the rounded
  whole, leaving a pixel of text outside the popup's background.
+ Region previews are drawn with `net.minecraft.gizmos`, the game's own world-space shape system, collected through the
  public `Minecraft.collectPerTickGizmos()`. The mod gains a world preview without a renderer, a mixin, or a render
  pipeline of its own.
+ Regions and reminders share one action model and one runner: a region holds the same `ReminderAction` list a reminder
  does and fires through the same `ReminderActions.run`, so there is a single implementation of
  "turn an action into a title or a sound" rather than one per feature.
+ Regions are stored separately in `config/hex/regions.json`, registered with the config registry, so they join config
  profiles and clipboard sharing while the Regions tab's reset button leaves a hand-drawn set alone.
+ Language files are now a set rather than a single file, and [docs/TRANSLATIONS.md](docs/TRANSLATIONS.md)
  states the invariant that holds them together: every locale carries the same key set, in the same order, with matching
  `%s` placeholders. It ships the parity check that proves it, since a key missing from one file renders as its raw id
  and is invisible to anyone testing in English.

## Version 1.8.0

### New Features

#### Command Suggestions

+ Added **Command Suggestions** — Hex learns which commands you run, where you run them and what you run them after, and
  offers them back in the chat box. Everything is learned and stored on your own machine; nothing is ever sent anywhere,
  and nothing is ever run for you.
    + A ranked list above the chat box, ordered by what you actually use rather than alphabetically. Arrow keys move,
      Tab accepts, Escape dismisses, and each row carries a one-word note on why it is there.
    + Inline completion greys out the rest of the line as you type — including arguments Hypixel's own tab-completion
      can never offer, like the warp you always mean by `/warp d`. It only appears when the guess is confident, and how
      confident is a slider.
    + Typing just `/` offers what you are most likely to want *right now*, from where you are standing, what you are
      holding, what you just ran, and what chat said in the last few seconds.
    + It reads Skyblock's own calendar as well — the season, whether it is night on Skyblock rather than where you live,
      and whichever event the scoreboard is counting down. A Dark Auction timer on screen is very nearly you announcing
      `/warp da`, and after a couple of them Hex has worked that out.
    + Suggestions the server offers are folded in and re-ranked rather than replaced, so a command added to Hypixel
      yesterday still appears — just in the right place.
    + Ships knowing the common Skyblock commands so the first session is not blank. Your own use overtakes that within a
      few dozen commands, and it can be switched off entirely.
+ Added a screen showing everything that has been learned, reached from the **Command Suggestions** tab or
  `/hexa suggest dashboard`. Every line shows how often you use it and what Hex has associated it with, and can be
  pinned to the top, blocked, or forgotten. **?** on any row shows the full arithmetic behind that suggestion — every
  signal, its value, and how much it counted for.
+ Added `/hexa suggest` — `stats`, `why <text>`, `forget <command>`, `clear confirm`, `pause`, `resume`, and
  `dashboard`.

#### Privacy

+ Command suggestions never record message text. `/msg`, `/w`, `/r`, `/pc` and the rest are learned as the command and
  the recipient, and the message itself is discarded before anything is written; a command Hex does not recognise learns
  at most its first word. Player names can be left out too, with one toggle.
+ What has been learned lives outside the config-profile system on purpose, so switching profiles cannot swap it and
  **Copy to clipboard** cannot share it.

### Fixes

#### Config Profiles

+ Fixed the Skyblock island freezing for the rest of the session once you switched profile by hand. The sidebar was
  polled from inside the auto-switch check, which returns early on a manual switch — so nothing that reads the sidebar
  was updated again, and a reminder conditional on an island stopped firing after arriving there. The sidebar is now
  read centrally, independently of whether auto-switching is still looking.

#### Auto Update

+ Fixed Hex never checking for updates on startup, however the **Updates** tab was set. Its settings were captured in
  config profiles, so switching profiles restored — and wrote back to disk — whatever the snapshot happened to hold,
  silently turning the startup check off for good. `/hexa update` was unaffected, which is why the manual check kept
  working. The **Updates** tab is now a property of your installation: profiles no longer capture, restore or share it.
+ Fixed the result of the startup check being lost when it arrived after you had already joined a world — a slow
  connection or a large download could outlast the join, and the message was then dropped for the rest of the session
  even though the update had been downloaded. It is now delivered whenever you next have a world open.

### Technical Details

#### Command Suggestions

+ The ranker is a hybrid: recency-decayed counts, naive Bayes over seventeen categorical context features, and an
  order-2 Markov chain each produce one signal, and a nine-parameter online logistic model learns how much to trust each
  of them for this player. It trains by softmax cross-entropy over the list that was actually on screen whenever
  something is taken from it — accepted from the popup, taken as an inline completion, or typed out in full while it was
  showing. Weights are pulled towards the shipped defaults each step, so the stock behaviour is an attractor rather than
  only a starting point, and one strange week cannot run away with it.
+ Every learned count decays lazily rather than on a sweep: a counter stores when its weight was last correct and every
  read discounts from there, so the model forgets at a configurable half-life with no periodic pass over it at any size.
  Naive-Bayes terms are in pointwise-mutual-information form and shrunk by how much evidence stands behind them, which
  is what keeps a command typed once from becoming that island's most confident suggestion.
+ Candidate selection is retrieve-then-rank: a cheap pass over every key on match quality and raw frequency, then the
  full seventeen-feature scoring over the surviving forty. Context is snapshotted once when chat opens rather than per
  keystroke — nothing it reads can change while the chat screen is up, and the hotbar and armour signatures cost a tag
  copy per stack.
+ `ChatScreenMixin` holds no logic; every injection is a one-line delegation to `SuggestSession`, which catches
  everything and disables the feature for the rest of the session on the first exception, so a failure in suggestions
  can never break chat. `CommandSuggestionsAccessor` reads vanilla's in-flight completion request so the server's own
  suggestions can be re-ranked instead of discarded while Hex's popup is up; vanilla's popup is suppressed with
  `setAllowSuggestions(false)` and handed back on the transition, once, rather than per keystroke.
+ The model is written to `config/hex/suggest/model.json` outside `ConfigRegistry` — debounced, on a daemon thread, and
  moved into place from a temporary file so a crash mid-write cannot truncate it. Pruning happens at save time, when the
  structure is being walked anyway.

#### Skyblock

+ The scoreboard sidebar is now read once, centrally, by a new `Sidebar` object, with `SkyblockLocation` and the new
  `SkyblockCalendar` as views over the lines it extracts. There are two interpretations of the same lines now, and
  rebuilding every entry's string twice a second to answer two questions instead of one would be waste; the extraction
  is the expensive part and the interpretation is a regular expression.
+ `SkyblockCalendar` reads the Skyblock date, the Skyblock clock and the named event. The date and time lines get strict
  anchored patterns, because those formats are stable and a strict pattern that fails to match is the right way to read
  a stable format; events get a substring vocabulary instead, extensible from the bundled catalogue, because event lines
  carry timers and suffixes that vary per event and per Hypixel update. The sun/moon glyph is preferred over the hour
  for the day/night split — it is Hypixel stating the answer rather than Hex inferring it.

#### Config

+ `ConfigHandle` takes a `global` flag, and `ConfigRegistry.profiled()` returns everything without it. Snapshot,
  restore, clipboard export/import and the unsaved-changes comparison all run over the profiled set; flush, tick and
  reset still run over every config. Snapshots written before a config became global keep their now-ignored file, so
  nothing has to migrate on disk and an older Hex reads the same directory unchanged.

#### Auto Update

+ The startup check logs every outcome — the version it compared, whether one was found, whether it staged, and why it
  fell back to a link when it did not. Previously only a failed check logged anything, so a check that ran and a check
  that never started were indistinguishable.

## Version 1.7.2

### Fixes

#### Hand Display

+ Fixed **Toggle Swing For Held Item** replying **"That is not a Skyblock item"** for every item, including real
  Skyblock ones. Per-item swing rules could not be added by keybind or by the editor's add button, and existing rules
  never matched, so listed items still swung.

### Technical Details

#### Skyblock Items

+ Hypixel serves the component item format natively, and its `id` / `uuid` now sit at the root of
  `minecraft:custom_data` rather than nested inside an `ExtraAttributes` compound. `SkyblockItem` read only the nested
  compound, so every item looked vanilla. It now reads either layout, matching Skyblocker's `ItemUtils`.
  `SkyblockItem.extraAttributes` is renamed to `attributes` to stop implying the old key.

## Version 1.7.1

### Fixes

#### Auto Update

+ Fixed a console window flashing up with **"The batch file cannot be found"** on Windows after quitting the game with
  an update downloaded. The update itself was applied correctly; only the message was wrong. The updater now runs
  without a window at all.

## Version 1.7.0

### New Features

#### Hand Display

+ Added **per-item swing**. List Skyblock items — by item ID, which covers every copy, or by a single item's UUID — and
  holding one in your main hand hides the swing animation, whatever the Hand tab's own swing switch says. Open the list
  with **Per-item swing…** in the **Hand** tab of `/hexa config` or with `/hexa hand swing`.
+ Added a **Toggle Swing For Held Item** keybind under Options → Controls → **Hex**, which adds the item you are holding
  to the per-item swing list or removes it if it is already there, without opening a menu. Unbound by default;
  `/hexa hand toggle` does the same.

#### Reminders

+ Added **Reminders**, a new feature that tells you when something is about to run out. Every reminder counts down; what
  starts the countdown is up to you — a timer, a chat message, arriving at or leaving an island, joining a world, or
  holding a particular Skyblock item. Edit them with **Reminders…** in the new **Reminders** tab of
  `/hexa config`, or with `/hexa remind edit`.
+ Added **chat triggers**, which match a line of chat as a regular expression (or as plain text) and can start a
  countdown from it. Anything captured with `(…)` can be inserted into the reminder's message with `$1` to `$9`, so a
  pattern reading a cooldown out of a chat line can put the actual number on screen.
+ Added **conditions**, checked at the moment a reminder would fire rather than when it started, so a reminder can be
  limited to one island and stays quiet elsewhere without losing its countdown.
+ Added a movable **reminder panel** showing everything that is counting down, with a live countdown per row and a flash
  when one fires. Drag it into place with **Panel position…**, or nudge it with the arrow keys. It is anchored as a
  fraction of the screen, so it survives a resolution, fullscreen or GUI-scale change, and it hides with the rest of the
  HUD on F1.
+ Added per-reminder **sounds**, with a chosen sound event, pitch and volume, and a **Test** button to hear one before
  committing to it.
+ Added a **preset catalogue**. Adding a preset copies it, so it can be edited freely; an unedited copy is updated in
  place when a later version ships a correction, keeping its on/off state and running countdown, while an edited one is
  left alone and offered **Reset to preset**.
+ Added `/hexa remind in <duration> <text>` for a one-off reminder that deletes itself once it has fired, plus
  `list`, `edit`, `hud`, `presets`, `dismiss` and `snooze`.
+ Added **Dismiss Reminder**, **Snooze Reminder** and **Open Reminders** keybinds under Options → Controls → **Hex**,
  unbound by default. Dismiss and snooze work even with the feature switched off, so a reminder already on screen can
  always be silenced.

### Improvements

#### Hand Display

+ Reworded the **Enabled** tooltip in the **Hand** tab to name what the switch actually governs, now that per-item swing
  rules keep working while it is off.

#### Config Menu

+ The settings list can now keep its scroll position when its rows are rebuilt, so the reminder editor no longer jumps
  to the top when a choice changes which fields apply.

### Technical Details

#### Skyblock

+ Added a reusable Skyblock item system under `net.trilleo.skyblock.item`: a reader that pulls an item's
  `ExtraAttributes` ID and UUID out of its custom-data component, a match-rule model that new match kinds can be added
  to without migrating existing config files, and a main-hand cache invalidated on stack identity so the per-frame swing
  hook never deep-copies NBT.

#### Config

+ Added `swing_items.json`, registered with `ConfigRegistry` so the per-item list takes part in config profiles and
  clipboard export. Kept separate from `hand.json` so resetting the **Hand** tab does not clear the item list.

#### Feature Framework

+ Added a `Feature.onHudRender` hook, dispatched from a single HUD element registered in `Features.bootstrap`. It is
  attached before the vanilla chat element rather than added first or last, so it inherits vanilla's own render
  condition and mod overlays hide with F1 without any feature checking for it.
+ Moved the main-hand item cache's tick and reset out of `HandFeature` and into `Features`, next to
  `ProfileAutoSwitch.tick` and outside the per-feature enabled check. It has more than one consumer now, so a shared
  cache no longer depends on one feature's master switch being on.
+ Extracted Hypixel text cleaning into `util.TextClean` and added `util.Duration` for parsing and formatting human
  durations such as `2h30m`.
+ Generalised `Notify.uiSound` to play any registered sound with a pitch and volume, resolving it by id and falling back
  to the standard UI click. The existing pitch-only call sites are unchanged.
+ Extracted hex colour parsing into `util.HexColor`, shared by the config menu's colour rows and the reminder panel.

#### Reminders

+ Reminder definitions live in `reminders.json`, registered with `ConfigRegistry` so they join config profiles and
  clipboard export. Live countdowns are kept apart in `reminder_state.json`, deliberately unregistered: a running timer
  is machine state, and capturing it in a profile would reset every countdown on a profile switch and hand a friend your
  timers when sharing settings.
+ Countdowns are stored as absolute wall-clock instants rather than tick counts, so they survive a relog, keep running
  while the game is closed, and do not drift with server lag. A deadline missed while away fires once, marked overdue,
  rather than replaying every interval in between.
+ User-written chat patterns are guarded three ways, since they run inside the shared chat event where a throw would
  break chat for the whole mod and a hang would lock the client: the subject is capped at 256 characters, matching is
  bounded by a read budget enforced through a counting `CharSequence` (which caps backtracking without a watchdog
  thread), and the whole evaluation is wrapped so nothing escapes. A pattern that exhausts its budget disables its
  reminder and says which one. Compiled patterns are cached, and a bad one is diagnosed once and never retried.
+ The trigger, condition and action models follow the existing `ItemRule` shape — an enum kind over one generic string
  payload — so a new kind is an appended constant and one branch, with no config migration and no failure when an older
  build reads a newer file.

## Version 1.6.0

### New Features

#### Attack Mode Switch

+ Added a **Cycle Attack Mode** keybind under Options → Controls → **Hex**, which flips Minecraft's **Attack/Destroy**
  control between **Hold** and **Toggle** without leaving the game. Each switch is announced in chat and plays a short
  sound — higher for **Toggle**, lower for **Hold** — and is saved to your Minecraft options. Unbound by default.

#### Config Profiles

+ Added a dedicated **Profiles** screen, reached from the button in the config menu's footer. Every profile is listed
  with its description and when it was last saved, and each row can be switched to, copied, renamed or deleted.
+ Added profile descriptions, so setups with similar names can be told apart.
+ Added automatic profile switching. A profile can be set to activate on a server (`hypixel.net` also matches
  `mc.hypixel.net`), in singleplayer, or on a named Skyblock island, and it is applied when you get there.
+ Added optional capture of Minecraft's own key bindings, so a profile can carry your whole control setup. Turn it on
  with **MC keys** on the Profiles screen — it is off by default.
+ Added **Import as a new profile**, so someone else's settings can be tried without overwriting your own.

#### Config Menu

+ Added a **Reset tab** button that restores the settings on the current tab to their defaults, and a **Reset all**
  button on the Profiles screen for every setting at once. Both confirm first, and neither touches your saved profiles —
  **Discard** brings the settings back.

### Improvements

#### Config Profiles

+ **Changed how profiles are saved.** Profiles used to be written silently whenever you switched away from one. Now the
  active profile is only written when you press **Save**, and a `*` next to its name means your settings have moved away
  from what it holds. Switching with unsaved changes asks whether to save or discard them first, and **Discard** reloads
  the profile as it was last saved.
+ Deleting a profile now asks for confirmation.
+ Pasting settings that came from a newer Hex now says so, instead of silently applying part of them, and a paste from a
  different Hex version notes the mismatch.

### Fixes

#### Config Profiles

+ Fixed typing a new profile name creating a profile per keystroke — naming it `abc` also left behind `a` and
  `ab`. Naming now happens on its own screen and commits once.
+ Fixed saving a profile silently making it the active one.

#### Config Menu

+ Fixed switching profile dropping you back on the first tab and clearing your search.

### Technical Details

#### Config

+ Reworked `ProfileSettings` around a `ProfileEntry` list carrying name, description, timestamps and the auto-switch
  rule. The old name-only `known` list is migrated by the normalizer on first load and then cleared, the same way
  `KeybindConfig` retires its legacy command fields. The new shape is a *added* field rather than a changed one on
  purpose: `JsonConfig.loadFrom` falls back to a fresh default on any parse error, so redeclaring `known`'s element type
  would have silently emptied every existing profile list.
+ Added `ProfileDirtyTracker`, which compares the live configs against the active profile's snapshot by value rather
  than tracking an edited flag — restores mark every config dirty as part of their work, and a flag cannot tell
  "changed" from "changed back". `ConfigRegistry.flushCount` gates how often the comparison runs.
+ Added `ConfigHandle.resetToDefault` and `ConfigRegistry.resetAll` on top of the previously unused
  `JsonConfig.defaultValue`, and a `ConfigCategory.reset` hook so a tab declares its reset in one line.
+ Added `VanillaKeysConfig`, which reads the live options in `current` so snapshots and the dirty check see the true
  bindings, and defers applying them to the first client tick because options do not exist while configs load.
+ Added `SkyblockLocation`, a best-effort scoreboard-sidebar reader isolated so a Hypixel layout change cannot affect
  anything but auto-switching.
+ `ConfigProfiles.switchTo` no longer captures the profile being left; `importFromString` returns a typed
  `ImportResult` instead of a nullable count.
+ `HexConfigScreens.rebuild` now reuses the open screen via `rebuildWidgets` rather than constructing a new one, which
  is what preserves the selected tab and search text.

## Version 1.5.1

### Fixes

#### Config

+ Fixed most of `/hexa config` being empty. Every tab except **Keybinds** showed no settings at all, and switching tabs
  did nothing — the **Hand**, **Freecam**, **Updates** and **Profiles** tabs are all usable again.

### Technical Details

#### Config

+ Fixed the crash behind the empty tabs: `ResettableRow` attached its tooltip from an `init` block that called down into
  the subclass, which runs before the subclass's widgets exist, so every row carrying a value threw an NPE while being
  built. Rows now attach their own tooltip where the widget is constructed. Only action-button rows were unaffected,
  which is why the Keybinds tab alone kept working.
+ Removed stale references to the Cloth Config classes deleted in 1.5.0 from the config KDoc, including descriptions of
  a Save/Cancel step the menu no longer has.

#### Documentation

+ Moved the full feature descriptions out of the README into a dedicated [docs/FEATURES.md](docs/FEATURES.md). The
  README now carries a one-line summary per feature and links to the new doc, which is where each feature's usage and
  configuration is documented from now on.

## Version 1.5.0

### New Features

#### Hand

+ Added main hand display settings: the new **Hand** tab of `/hexa config` moves your held item around in first person
  with **Position X/Y/Z** sliders, resizes it with **Scale**, and turns it with **Rotation X/Y/Z**. A **Reset to
  defaults** button puts everything back.
+ Added a **Swing speed** slider to make the swing animation play faster or slower, and a **Disable swing animation**
  switch to hide it completely. Both are purely visual — your attack cooldown and mining speed are unchanged, and other
  players still see you swing normally. Note that swing speed also applies to your own model in third person, while
  disabling the animation affects first person only.

#### Config

+ Added config profiles: the new **Profiles** tab of `/hexa config` keeps whole named setups side by side. Switch
  between them from the **Active profile** picker — the settings you are leaving are saved into their own profile first,
  so nothing is lost. Create one from your current settings by typing a name into **New profile name**, and remove one
  with **Delete this profile**.
+ Added settings sharing: **Copy settings to clipboard** puts every Hex setting into a block of text you can paste to
  someone else or keep as a backup, and **Paste settings from clipboard** loads one back. Settings the pasted text does
  not mention are left alone, and text that is not a Hex export is reported in chat rather than doing anything.

### Improvements

#### Config

+ Rebuilt the `/hexa config` menu. It gains a search box that filters settings across every tab at once, a properly
  scrolling list in place of the old page arrows, and a reset button on each row that lights up only when that setting
  differs from its default.
+ Settings still apply the moment you change them, so you can drag a slider and watch the result rather than hunting for
  a save button.
+ Every setting label and tooltip is now translatable rather than hardcoded English, so the menu can be translated.
  Tooltips are picked up automatically from the language file.
+ Hex still needs nothing but Fabric API and Fabric Language Kotlin — the new menu is built in, so there is no extra
  config library to install.

### Fixes

#### Config

+ Dragging a slider no longer rewrites the config file on every frame of the drag. Changes are now batched and written
  about a second after you stop, and still flushed immediately when you close the menu, switch profile or quit the game.

### Technical Details

#### Config

+ Added a slider control to the config menu (`SliderEntry`), so settings can now take a number over a range instead of
  only a toggle or a fixed list of choices.
+ Split the config system into a backend-agnostic settings model (`ConfigCategory` / `ConfigEntry`) and a renderer.
  Features describe their settings in the mod's own vocabulary and never touch GUI code, so the entire menu can be
  rewritten without a single feature changing.
+ Rewrote the menu as `HexConfigScreen` on vanilla's `ContainerObjectSelectionList`, which supplies scrolling, the
  scrollbar and keyboard navigation. Rows draw through `extractContent` and the screen chrome through
  `extractBackground`, matching this Minecraft build's extractor render pipeline.
+ Grew the entry model to eight row types — boolean, slider, cycle, enum, action, text, colour and keybind — so features
  stop hand-rolling a sub-screen every time they need something other than a toggle.
+ Slider values are projected onto an integer notch grid and rounded to the precision the step implies, which keeps
  `0.01`-step sliders from drifting into values like `0.30000000000000004`.
+ Added `ConfigRegistry` and `ConfigHandle`, which own every user-facing config generically — debounced writes, a
  central flush at shutdown, profile snapshot/restore, and clipboard export/import. `UpdateStaging`
  deliberately stays out of the registry: a half-downloaded jar is machine state, not a setting.
+ Config entries now carry their default value, which drives the per-row reset button and removes the need for each
  feature to hand-roll a reset action.

## Version 1.4.0

### New Features

#### Keybinds

+ Added control switch shortcuts: bind a key combo to cycle one of Minecraft's own controls between two or more keys.
  For example, bind `Alt + /` to switch **Attack/Destroy** between **Left Button** and **J**, so you can stop your
  clicks from swinging without leaving the game to rebind anything. Add one with the **Add Switch** button on the Hex
  Keybinds screen, then pick the control and the keys to cycle through — mouse buttons work as well as keyboard keys.
  Each switch shows the new binding in chat, plays a short confirmation sound, and is saved to your Minecraft options
  just like a manual rebind, so it survives a restart.

### Technical Details

#### Keybinds

+ Control switches reuse the existing keybind entry list and its per-tick combo detection; entries carry a
  `type` discriminator, and configs written before this release load as command shortcuts unchanged.
+ Added a shared `Notify` helper for prefixed chat lines and UI sounds, replacing the update checker's private copies —
  the mod previously had no sound playback at all.

## Version 1.3.0

### New Features

#### Freecam

+ Added a freecam: press its keybind to detach the camera from your player and fly it around freely to look at your
  surroundings — WASD to move, Space/Shift for up/down, the mouse to look, and the scroll wheel to change speed. Press
  it again to return to normal; your character stays put the whole time. The keybind lives under a new **Hex** category
  in Options → Controls (unbound by default), and the **Freecam** tab of `/hexa config`
  lets you enable/disable the feature and pick a base fly speed.

### Improvements

#### Keybinds

+ Hex's keybinds now live under their own **Hex** category in Options → Controls instead of being mixed into Misc — the
  config, keybinds and freecam binds are grouped together.
+ Added a rebindable keybind to open the Hex config menu (Options → Controls → Hex), alongside `/hexa config`.

### Technical Details

#### Freecam

+ Added the project's first mixins (`CameraMixin`, `ClientInputMixin`, `KeyboardInputMixin`,
  `MouseHandlerMixin`). The camera is repositioned right after `Camera#alignWithEntity` — before the cull frustum is
  built — so chunk culling follows the freecam wherever it flies; mouse look and scroll are redirected, the move vector
  is forced to zero, and the key-press record is blanked so the real player never moves or sends input to the server.
+ Added a reusable `CycleEntry` multiple-choice control to the config-menu framework (a new `ConfigEntry`
  subtype plus a `cycle(...)` builder), and a dedicated "Hex" `KeyMapping.Category`.

## Version 1.2.0

### New Features

#### Config Menu

+ Added a config menu that gathers Hex's settings in one place, split into categories down the side. Open it with
  `/hexa config`. A button on the menu jumps straight to the Keybinds screen.

#### Auto-Update

+ Hex now keeps itself up to date from its GitHub releases. On startup it checks for a newer version in the background
  and, if one is found, downloads it and tells you to restart — the update is applied automatically the next time you
  close the game.
    + Run `/hexa update` to check on demand at any time.
    + The **Updates** tab of `/hexa config` lets you turn the startup check off, opt in to prerelease builds, and check
      for an update on the spot — no more editing `config/hex/update.json` by hand.

### Technical Details

#### Config Menu

+ Added a reusable config-menu framework: a `Feature` contributes a settings tab by overriding
  `settingsCategory()` and returning a `ConfigCategory.build("id", "Title") { toggle(...); action(...) }`, and the
  `ConfigScreen` collects every enabled feature's category into the sidebar automatically. Entries are a small sealed
  `ConfigEntry` hierarchy (`BooleanEntry`, `ActionEntry`), rendered with vanilla widgets like the keybinds screens.

#### Auto-Update

+ Added an update feature that queries the `Trilleo/Hex` GitHub releases API (built-in `java.net.http`, no new
  dependency), compares tags with Fabric's `SemanticVersion`, and stages a verified jar under
  `config/hex/update/`. Because the running jar is file-locked by the JVM, the swap is performed on shutdown by a
  detached OS helper script that copies the new jar into `mods/` and removes the old one once the lock is released.
    + The on-demand check is exposed as `UpdateFeature.checkNow()`, shared by the `/hexa update` command and the config
      menu's "Check for updates now" button. Toggling an update setting in the menu persists it immediately.

## Version 1.1.0

### New Features

#### Keybinds

+ Added keybind shortcuts: bind a key (optionally with Ctrl/Shift/Alt) to run a sequence of commands/chat messages. Each
  action has its own input and its own delay, so you can pace a sequence step by step, and command inputs offer the same
  tab-completion as the chat box. Manage bindings in-game through the Hex Keybinds screen, opened via a rebindable
  keybind in the vanilla Controls menu; press Edit on a binding to arrange its actions.

#### Commands

+ Added a `/hexa` command. Run `/hexa keybinds` to open the Keybinds screen.

### Technical Details

#### Core

+ Added a Feature/Module lifecycle with a central registry that wires all client events (tick, chat, world join/leave,
  shutdown) and the `/hexa` command hub once and dispatches to registered features, a generic
  `JsonConfig` config helper, and migrated the keybind feature onto them.

## Version 1.0.0

### New Features

#### Misc

+ Initial release for Minecraft 26.1.2.
