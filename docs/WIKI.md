# The GitHub wiki

The wiki pages live in [`wiki/`](../wiki) in this repository and are **published by pushing them to the wiki's own Git
repository** (`https://github.com/Trilleo/Hex.wiki.git`). Keeping the source here means wiki edits go through the same
review as code, and a feature PR can update the wiki in the same commit.

## The pages

| File                                                                                                                     | Page                                                                                                                                                                                                                           |
|--------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Home.md`                                                                                                                | Landing page — feature table and quick reference                                                                                                                                                                               |
| `_Sidebar.md`                                                                                                            | Navigation shown beside every page                                                                                                                                                                                             |
| `_Footer.md`                                                                                                             | Footer shown under every page                                                                                                                                                                                                  |
| `Installation.md`, `Updating.md`                                                                                         | Getting started                                                                                                                                                                                                                |
| `Configuration.md`, `Commands.md`, `Keybinds.md`, `Config-Files.md`                                                      | Cross-cutting reference                                                                                                                                                                                                        |
| One page per feature                                                                                                     | `Config-Profiles`, `Keybind-Shortcuts`, `Control-Switches`, `Attack-Mode-Switch`, `Freecam`, `Hand-Display`, `Per-Item-Swing`, `Item-Customization`, `Chroma-Text`, `Reminders`, `Regions`, `Command-Suggestions`, `Languages` |
| `FAQ.md`, `Troubleshooting.md`                                                                                           | Help                                                                                                                                                                                                                           |
| `Building-from-Source.md`, `Architecture.md`, `Adding-a-Feature.md`, `Translating.md`, `Releasing.md`, `Contributing.md` | Development                                                                                                                                                                                                                    |

## Conventions

- **A file name is a page title.** `Config-Files.md` is the page *Config files*; hyphens render as spaces in the wiki's
  page list. Renaming a file breaks every link to it and any external bookmark, so treat names as stable.
- **Links between pages are plain relative Markdown**: `[Regions](Regions)`, without the `.md`. This form works both in
  the published wiki and when browsing `wiki/` on GitHub.
- **Links into the repository are absolute URLs** (`https://github.com/Trilleo/Hex/blob/master/CHANGELOG.md`), because
  the wiki is a different repository and relative paths would not resolve.
- `_Sidebar.md` and `_Footer.md` are special names GitHub renders around every page. A new page needs a line in
  `_Sidebar.md`, and a user-visible feature also needs a row in the `Home.md` table.

## Publishing

The wiki repo has no branch protection and no CI; a push is a publish.

```bash
git clone https://github.com/Trilleo/Hex.wiki.git /tmp/hex-wiki
cp wiki/*.md /tmp/hex-wiki/
cd /tmp/hex-wiki && git add -A && git commit -m "Internal: Sync wiki from main repository" && git push
```

The wiki must be **enabled and initialised** in the repository settings before that clone URL exists — create the first
page through the web UI once, then push over it.

## Keeping it in sync

Keeping the wiki current is **step 4 of the after-every-change checklist in [CLAUDE.md](../CLAUDE.md)**, not an
afterthought: the wiki is what a player finds from a search engine, so a page that lags behind the mod is worse than no
page at all.

The wiki restates what [FEATURES.md](FEATURES.md) documents, aimed at a player arriving from a search engine rather than
a reader going through the repo. When a feature changes, both move together:

1. `docs/FEATURES.md` — the canonical description.
2. `wiki/<Feature>.md` — the same change, in the wiki's voice.
3. `wiki/Home.md` and `wiki/_Sidebar.md` — only when a page is added or renamed.

Version-specific facts appear in `Installation.md` and `Building-from-Source.md` (Minecraft, Java, Fabric, dependency
versions); check them whenever `gradle.properties` changes.
