# Notebook

Somewhere to write things down without leaving the game — dungeon checklists, mining routes, prices you keep forgetting,
what you were in the middle of doing when you logged off.

> Notes are written in **Markdown**, stored as ordinary `.md` files you can open in any text editor, and kept
> per installation rather than per [config profile](Config-Profiles). Switching profiles never touches them.

## Opening it

- **Open notebook…** in the **Notebook** tab of `/hexa config`
- `/hexa note`
- Bind **Open Notebook** under Options → Controls → **Hex** (unbound by default)

## The browser

The notebook screen lists every note you have. Down the left is a sidebar of filters: **All notes**, **Pinned**, then
one entry per folder and one per tag you have used. The search box in the top right searches *everything* — titles,
folders, tags, and the text of the notes themselves — and shows the line it matched underneath each result.

Each row shows a colour bar, the note's icon, its title, and its opening line. The buttons on the right are:

| Button   | Does                                |
|----------|-------------------------------------|
| **☆**   | Pin the note to the top of the list |
| **Edit** | Open it                             |
| **⧉**    | Duplicate it                        |
| **✕**   | Delete it, after asking             |

## Writing a note

**New** creates a note and opens it straight away. If a folder is selected in the sidebar, the new note is filed there.

The editor is the note's title at the top and its text below. There is **no save button** — what you type is kept,
written a couple of seconds after you stop typing and again when you close the screen.

Notes are written in Markdown:

```markdown
# Dungeon checklist

- [ ] Bring Spirit Leap
- [x] Restock Super Boom TNT

> Watch for Livid on floor 5

| Floor | Score |
|-------|-------|
| F7    | 300   |
```

Headings, bullet and numbered lists, task checkboxes, quotes, code blocks, tables and horizontal rules are all standard
Markdown, so a note pasted in from anywhere else already works.

### The toolbar

You do not have to remember any of that syntax. The row of buttons above the text works the way a word processor's
does — select some text, press a button:

| Button               | Does                                                      |
|----------------------|-----------------------------------------------------------|
| **B** *I* ~~S~~ `{}` | Bold, italic, strikethrough, inline code                  |
| **H1** **H2**        | Turn the line into a heading                              |
| **•** **1.**         | Bulleted or numbered list                                 |
| **☑**               | Checklist item — press again to tick it, again to undo it |
| **❝**               | Quote                                                     |
| **—**                | Divider                                                   |
| **⊞**                | Table — inserts an empty one to fill in                   |
| **🔗**               | Link — the selection becomes the label                    |
| **&**                | Opens the colour palette                                  |

Every button is a toggle: pressing **B** on text that is already bold takes the bold off. With nothing selected, a
button leaves the cursor exactly where you need to start typing. **Ctrl+B**, **Ctrl+I** and **Ctrl+E** do bold, italic
and code without reaching for the mouse.

The **&** button opens Minecraft's sixteen colours, plus **chroma** — a colour that flows, the same one
[item customization](Item-Customization) uses — and a swatch that goes back to plain. Below them is a field for **any**
colour: type `#RRGGBB`, press the swatch beside it, and the selection is wrapped in a `&#RRGGBB` code. Colours are
written into the text, so a note keeps them when you export it, and they work anywhere in a note — see
[Chroma text](Chroma-Text).

### The preview

Beside the source is a live preview: the note as it reads, updated as you type. The button at the right of the toolbar
switches between **Source**, **Split** and **Preview**, and your choice is remembered. On a small window, **Source** or
**Preview** on their own give the text more room than a split does.

The preview renders headings at their own size, lists with real bullets and check boxes, quotes with a bar down the
side, code blocks on a slab, dividers as lines, tables as aligned columns with a bold header, and every colour code and
chroma run in colour. **Line spacing** in the settings sets how much air there is between lines here and on the reading
screen.

## Reading a note

**View** on a note's row opens it full width — no toolbar, no source pane, just the note as it reads, for following a
route or a checklist while you play.

It is not quite read-only: **check boxes still tick**. Click a task line and the `x` is written into the note, exactly
as if you had typed it, so a checklist is usable rather than decorative. **Edit** in the footer opens the same note in
the editor.

## Details

**Details…** in the editor opens the rest of a note's properties:

| Setting    | Notes                                                                                              |
|------------|----------------------------------------------------------------------------------------------------|
| **Folder** | A path such as `mining/dwarven`. Folders appear in the sidebar for as long as something is in them |
| **Tags**   | Comma-separated labels. Each becomes a sidebar filter                                              |
| **Colour** | The bar down the left of the note's row                                                            |
| **Icon**   | An item id such as `minecraft:diamond_pickaxe`. Blank means a book                                 |
| **Pinned** | Keep this note at the top of the list                                                              |

Folders and tags are never created or deleted — they exist exactly as long as a note uses them.

## Sharing a note

**Export** copies the note to your clipboard, **Import** takes whatever is on your clipboard as a new note.

An exported note carries its folder, tags, colour and icon along with its text, because all of that lives in a small
header at the top of the file. Paste one to a friend and they get the note exactly as you filed it. Plain Markdown from
anywhere else imports fine too — it simply arrives with no folder and no tags, titled after its first heading.

Importing always creates a **new** note. It never overwrites one you already have, even if the titles match.

## Settings

The **Notebook** tab of `/hexa config`:

| Setting                | Notes                                                                                         |
|------------------------|-----------------------------------------------------------------------------------------------|
| **Enabled**            | Master switch. With it off the keybind and commands do nothing. Your notes are untouched      |
| **Open notebook…**     | Opens the browser                                                                             |
| **Sort by**            | **Last edited**, **Newest**, **Title** or **Manual**. Pinned notes stay on top regardless     |
| **Editor layout**      | Whether the editor shows the source, the preview, or both — the toolbar button changes it too |
| **Show previews**      | Draw each note's first line under its title                                                   |
| **Line spacing**       | Space between the lines of a rendered note — the preview and the reading screen               |
| **Background opacity** | How solid the browser and the editor are, from see-through to a flat backdrop                 |

**Background opacity** covers the whole notebook — the header and footer bars, the filter sidebar, and the writing area
of the editor. Turn it down to keep an eye on the game behind the screen, up for the most readable text. Whatever you
set, the outline around the writing area stays, so the editor never loses its edges.

## Commands

See [Commands](Commands) for the full list. In short: `/hexa note` on its own lists what it can do, `new`,
`open`, `list`, `search`, `export` and `import` do what they say.

## Storage

```
config/hex/
  notebook.json          the settings above — travels with a config profile
  notebook/
    index.json           a summary of your notes, so the list opens without reading every file
    notes/<id>.md        one file per note
```

Two things follow from that layout, both deliberate:

- **A note is a file.** Open `notes/` in a text editor and edit a note there; Hex picks up the change next time it
  loads. **Drop a `.md` file into `notes/` and it becomes a note** — that is the whole import path, and it works for a
  file written by anything at all.
- **`index.json` is disposable.** Every note carries its own details in a header, so if the index is deleted or damaged
  the notebook is simply rebuilt by reading the files. You can never lose a note by losing the index.

Notes are **not** part of a [config profile](Config-Profiles) and are not included when you copy settings to the
clipboard. Notes are things you wrote, not a settings loadout, and switching from a "Dwarven" profile to a
"Slayers" one should not swap out what you have written down.

Writes are atomic: a note is written to a temporary file and then moved into place, so a crash mid-save leaves either
the old version or the new one, never half of either.

## Limitations

- **You write in Markdown source, and read the preview beside it.** The editing pane is the text as you typed it,
  markers and all; the preview is what it means. An editor where the text itself is styled as you type — with clickable
  item and coordinate chips — is still to come, and notes you write now carry over unchanged, because the text is the
  note either way.
- **The preview understands most of Markdown, not all of it.** Headings, lists, task boxes, quotes, fenced code,
  dividers, tables, bold, italic, strikethrough, inline code and links are rendered. Anything more exotic is shown as
  the plain text you typed — the note is still correct Markdown, it is just not drawn.
- **A table cell that is too wide wraps**, and its row grows as tall as its tallest cell — nothing you typed is hidden.
  A table ends at the first line with no `|` in it, rather than running on to the next blank line as some Markdown
  renderers do: prose written under a table is prose.
- **Line spacing applies to rendered notes, not to the editing pane.** The pane you type in is Minecraft's own text
  area, which draws at a fixed line height.
- **A line is a line.** Two lines of prose in a row stay two lines in the preview rather than being joined into one
  paragraph, because that is what someone writing a note in a game means by pressing Enter.
- **One installation, one notebook.** Notes are not per Skyblock profile or per island.
- **No note icons on the title screen.** Item icons come from the item registry, which Minecraft only fills in once a
  world is loaded, so a notebook opened from the main menu shows rows without them. Everything else works, and the icons
  are back as soon as you are in a world.
- A note written by a **newer version of Hex** than you are running is shown read-only rather than saved over, so
  launching an older Hex by accident cannot damage it.
