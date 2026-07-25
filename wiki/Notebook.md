# Notebook

Somewhere to write things down without leaving the game — dungeon checklists, mining routes, prices you keep
forgetting, what you were in the middle of doing when you logged off.

> Notes are written in **Markdown**, stored as ordinary `.md` files you can open in any text editor, and kept
> per installation rather than per [config profile](Config-Profiles). Switching profiles never touches them.

## Opening it

- **Open notebook…** in the **Notebook** tab of `/hexa config`
- `/hexa note`
- Bind **Open Notebook** under Options → Controls → **Hex** (unbound by default)

## The browser

The notebook screen lists every note you have. Down the left is a sidebar of filters: **All notes**, **Pinned**,
then one entry per folder and one per tag you have used. The search box in the top right searches *everything* —
titles, folders, tags, and the text of the notes themselves — and shows the line it matched underneath each
result.

Each row shows a colour bar, the note's icon, its title, and its opening line. The buttons on the right are:

| Button | Does                                                       |
|--------|------------------------------------------------------------|
| **☆**  | Pin the note to the top of the list                        |
| **Edit** | Open it                                                  |
| **⧉**  | Duplicate it                                               |
| **✕**  | Delete it, after asking                                    |

## Writing a note

**New** creates a note and opens it straight away. If a folder is selected in the sidebar, the new note is filed
there.

The editor is the note's title at the top and its text below. There is **no save button** — what you type is
kept, written a couple of seconds after you stop typing and again when you close the screen.

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

Headings, bullet and numbered lists, task checkboxes, quotes, code blocks, tables and horizontal rules are all
standard Markdown, so a note pasted in from anywhere else already works.

## Details

**Details…** in the editor opens the rest of a note's properties:

| Setting     | Notes                                                                                            |
|-------------|--------------------------------------------------------------------------------------------------|
| **Folder**  | A path such as `mining/dwarven`. Folders appear in the sidebar for as long as something is in them |
| **Tags**    | Comma-separated labels. Each becomes a sidebar filter                                             |
| **Colour**  | The bar down the left of the note's row                                                           |
| **Icon**    | An item id such as `minecraft:diamond_pickaxe`. Blank means a book                                |
| **Pinned**  | Keep this note at the top of the list                                                             |

Folders and tags are never created or deleted — they exist exactly as long as a note uses them.

## Sharing a note

**Export** copies the note to your clipboard, **Import** takes whatever is on your clipboard as a new note.

An exported note carries its folder, tags, colour and icon along with its text, because all of that lives in a
small header at the top of the file. Paste one to a friend and they get the note exactly as you filed it. Plain
Markdown from anywhere else imports fine too — it simply arrives with no folder and no tags, titled after its
first heading.

Importing always creates a **new** note. It never overwrites one you already have, even if the titles match.

## Settings

The **Notebook** tab of `/hexa config`:

| Setting            | Notes                                                                                    |
|--------------------|------------------------------------------------------------------------------------------|
| **Enabled**        | Master switch. With it off the keybind and commands do nothing. Your notes are untouched |
| **Open notebook…** | Opens the browser                                                                        |
| **Sort by**        | **Last edited**, **Newest**, **Title** or **Manual**. Pinned notes stay on top regardless |
| **Show previews**  | Draw each note's first line under its title                                              |

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

- **A note is a file.** Open `notes/` in a text editor and edit a note there; Hex picks up the change next time
  it loads. **Drop a `.md` file into `notes/` and it becomes a note** — that is the whole import path, and it
  works for a file written by anything at all.
- **`index.json` is disposable.** Every note carries its own details in a header, so if the index is deleted or
  damaged the notebook is simply rebuilt by reading the files. You can never lose a note by losing the index.

Notes are **not** part of a [config profile](Config-Profiles) and are not included when you copy settings to the
clipboard. Notes are things you wrote, not a settings loadout, and switching from a "Dwarven" profile to a
"Slayers" one should not swap out what you have written down.

Writes are atomic: a note is written to a temporary file and then moved into place, so a crash mid-save leaves
either the old version or the new one, never half of either.

## Limitations

- **Markdown is stored, not yet rendered.** You write and read notes as Markdown source. A visual editor that
  renders it — along with Minecraft colour codes, chroma text, and clickable item and coordinate chips — is
  coming; notes you write now carry over unchanged, because the text is the note either way.
- **One installation, one notebook.** Notes are not per Skyblock profile or per island.
- A note written by a **newer version of Hex** than you are running is shown read-only rather than saved over,
  so launching an older Hex by accident cannot damage it.
