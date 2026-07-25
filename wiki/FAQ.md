# FAQ

### Is Hex allowed on Hypixel?

Hex is designed to comply with the [Hypixel Server Rules](https://hypixel.net/rules) on allowed modifications. It
displays information the game already gives you and improves quality of life — no automation, no unfair advantage,
nothing that plays for you. That said, **use at your own risk**: no third-party mod is officially endorsed by Hypixel.

### Do other players need Hex?

No. Hex is **client-side only**. It has no server half, no `main` entrypoint, and nothing it does is visible to anyone
else. [Item customization](Item-Customization) in particular changes only what *your* client draws — everyone else sees
your item exactly as it was.

### Does Hex send my data anywhere?

The only network traffic Hex makes is to **GitHub**, to check for and download a new release
([auto-update](Updating)) — and you can turn that off. Everything else, including everything
[command suggestions](Command-Suggestions) learns, stays in `config/hex/` on your machine.

### Does it work outside Skyblock?

Mostly. The general features — [config profiles](Config-Profiles), [keybind shortcuts](Keybind-Shortcuts),
[control switches](Control-Switches), [attack mode](Attack-Mode-Switch), [freecam](Freecam),
[hand display](Hand-Display), [reminders](Reminders), [regions](Regions) — work anywhere, including singleplayer.

Features that read Skyblock's own data need Skyblock: [per-item swing](Per-Item-Swing),
[item customization](Item-Customization), island-based triggers and conditions, and the Skyblock signals inside
[command suggestions](Command-Suggestions).

### Does Hex require Mod Menu?

No. [Mod Menu](https://modrinth.com/mod/modmenu) is optional. With it installed, its settings button opens Hex's config
menu; without it, Hex behaves identically. It is not bundled and not listed as a dependency.

### How do I open the settings if I am not in a world?

From the title screen: **Options** → the small **□** button next to **Done**. That is the only way in from the title
screen, since `/hexa config` needs a chat box and the keybind needs a world.

### Why is nothing bound to a key?

Every Hex keybind ships **unbound**, on purpose — a mod should not take keys from you on install. Bind what you want
under **Options → Controls → Hex**. See [Keybinds](Keybinds).

### I changed a setting and it did not save

That is the design. Settings apply immediately but are **not** written into your profile until you press **Save** on the
Profiles screen. A `*` next to the profile name means you have unsaved changes. See
[Config profiles](Config-Profiles).

### Why did my profile not switch when I warped?

Three possible reasons, all deliberate:

1. You have **unsaved changes** — auto-switching is skipped rather than losing them, and you get a message instead.
2. You **switched profiles by hand** this session, which turns auto-switching off until you disconnect.
3. Another profile **higher in the list** claims the same place.

### Can I customize a stackable item?

No. Customizations are keyed on the item's **UUID**, which Hypixel gives only to non-stackable items — that is exactly
what makes a customization follow *your* Hyperion rather than every Hyperion. Pressing the key on a stackable item says
so rather than storing something that could never apply.

### Why does opening the item editor close the chest I was in?

Minecraft tells the server a container is closed the moment another screen replaces it. Nothing is lost besides your
place in that menu — the customization is saved against the item's UUID either way. See
[Item customization](Item-Customization).

### Does command suggestions read my messages?

No. It records the command and, for `/msg`-style commands, who you sent it to — the **message text is thrown away before
anything is written to disk**. A command it has never heard of records at most its first word. Turn **Learn player
names** off and the recipients go too.

### Will sharing my settings share my command history?

No. **Copy to clipboard** contains profiled settings only. What command suggestions has learned, your item
customizations, your reminder countdowns and your updater settings are all left out. See
[Config files](Config-Files).

### Does the freecam let me see through walls?

It moves your view, nothing more. It shows what your client already knows — nothing is revealed that the server has not
already sent to you.

### Do the hand settings affect my damage or reach?

No. Position, scale, rotation and swing are **purely cosmetic**. Attack cooldown, mining speed and reach are untouched.

### How do I translate Hex into my language?

Copy `en_us.json`, translate the values, keep the keys and their order identical, and open a pull request. See
[Translating](Translating).

### Where do I report a bug or ask for a feature?

[GitHub issues](https://github.com/Trilleo/Hex/issues). Check [Troubleshooting](Troubleshooting) first, and include your
Hex version (`/hexa` prints it) and your `logs/latest.log`.
