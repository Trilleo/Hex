# Adding a feature

The mechanics are small; the surrounding checklist is what makes a change shippable. Read
[Architecture](Architecture) first if you have not.

## 1. Write the feature

One package under `src/main/kotlin/net/trilleo/<name>/`, with a `Feature` object at its root:

```kotlin
object MyFeature : Feature {
    override val id = "mything"
    override val enabled get() = MyConfig.settings.enabled

    override fun onInit() {
        MyConfig.load()
        myKey = KeyMapping("key.hex.mything.do", InputConstants.UNKNOWN.value, Hex.KEY_CATEGORY)
        KeyMappingHelper.registerKeyMapping(myKey)
    }

    override fun onClientTick(client: Minecraft) {
        while (myKey.consumeClick()) {
            …
        }
    }

    override fun registerCommands(hex: LiteralArgumentBuilder<FabricClientCommandSource>) {
        hex.then(Commands.literal("mything").executes { … })
    }

    override fun settingsCategory(): ConfigCategory = ConfigCategory.build("mything") {
        toggle(
            "enabled", default = true,
            get = { MyConfig.settings.enabled },
            set = { MyConfig.settings.enabled = it; MyConfig.save() })
        color(
            "tint", default = "#FFFF55", chroma = true,
            get = { MyConfig.settings.tint },
            set = { MyConfig.settings.tint = it; MyConfig.markDirty() })
    }
}
```

Then one line in `Hex.onInitializeClient`:

```kotlin
Features.register(MyFeature)
```

Guidelines:

- **Ship keybinds unbound** (`InputConstants.UNKNOWN.value`) and in `Hex.KEY_CATEGORY`.
- **Ask for a colour with `color(...)`, never a hand-rolled control.** That row is the whole
  [colour picker](Colour-Picker) — the palettes, the shared recent colours, hex and RGB entry, chroma — so a new
  feature is consistent with every existing one without trying to be. Read the stored value back through
  `ColorValue.resolve`, which folds a hex colour, `"chroma"` and an empty value into one packed ARGB and never fails.
  Pass `chroma = true` only where whatever draws the colour re-reads it every frame or every tick; a colour baked into
  a cached component cannot animate, and offering chroma there ships a setting that visibly does nothing.
- **Never register a Fabric callback yourself** — override the `Feature` hook instead. If the hook you need does not
  exist, add it to `Feature` and wire it once in `Features`.
- **`onHudRender` is a frame callback, not a tick.** Do no work there beyond drawing: format, measure, sort and read
  config in `onClientTick` and leave a prepared model behind.
- **Nest commands under `/hexa`.** A bare parent command should print its subcommands rather than guess.
- **Opening a screen from a command must be deferred** (`client.execute { … }`) — a screen opened mid-command is undone
  when the chat screen that ran it closes.

## 2. Persist its settings

```kotlin
object MyConfig {
    private val config = JsonConfig(
        name = "mything",
        type = object : TypeToken<MySettings>() {}.type,
        default = { MySettings() },
        normalizer = { /* repair GSON's reflection gaps: null lists, null enums */ },
    )

    val handle = ConfigRegistry.register(
        ConfigHandle(config, adopt = { settings = it }, current = { settings }),
    )
}
```

- `name` is the file base name **and** the key it is addressed by in an exported profile blob, so it must stay stable
  across versions.
- Pass `global = true` only if the data describes the *installation* rather than a loadout (see
  [Architecture](Architecture)).
- Keep transient machine state **out** of `ConfigRegistry` entirely.

## 3. The four-part checklist

Every change that touches the shipped mod finishes with all of these, **in the same commit or PR**:

### Translate every string

- Any user-visible string goes through a translation key and `Component.translatable` — never
  `Component.literal("some English text")`.
- **Every language file carries exactly the same key set, in the same order.** Adding a key to `en_us.json` means adding
  it to `zh_cn.json` too, in the same place. A key present in one file and missing from another renders as the raw key
  id in game — invisible to anyone testing in English.
- `%s` / `%d` placeholders: identical count and order across files.
- Run the parity check in [Translating](Translating) before you finish.

### Update the changelog

Add an entry under `## Unreleased` in `CHANGELOG.md`, in the SkyHanni-style format: a `###` category (`New Features`,
`Improvements`, `Fixes`, `Technical Details`, `Removed Features`), then a `#### Feature Area` heading, then `+` bullets.

Write player-facing entries for gameplay changes; refactors, build and tooling changes go under **Technical Details**.
See [Releasing](Releasing).

### Document the feature

Every user-visible feature gets a `##` section in `docs/FEATURES.md`: what it does, how the player enables and
configures it, any limitation worth stating up front. Write for a **player** — implementation notes belong in the
changelog's Technical Details.

Update this wiki too: a new page, a link in `_Sidebar.md`, and a row in the [Home](Home) table.

### Check the README and docs

- `README.md` carries a **one-line bullet per feature** — add one, and keep the detail in `docs/FEATURES.md`. Also
  update it if the change touches installation, dependencies, MC/Java version, build instructions or licensing.
- If the change affects a workflow documented in `docs/` (releasing, translations, commit structure), update that doc in
  the same task.

## 4. Before opening the PR

```bash
./gradlew build
```

and run the client at least once (`./gradlew runClient`) — a feature that compiles is not a feature that works. Then see
[Contributing](Contributing) for the commit convention.

`build` runs the unit tests under `src/test/kotlin`. Most of Hex cannot be unit tested — it draws screens and reads a
live client — so that source set is deliberately narrow: it covers the pieces that are **pure logic and touch no
Minecraft runtime**, and where being wrong loses something the player cannot get back. The notebook's note-file handling
is the current example. If what you are writing fits that description, add tests; if it needs a `Minecraft`
instance, do not try, and test it by playing.
