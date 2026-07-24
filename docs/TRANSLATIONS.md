# Translations

Hex ships one language file per locale in `src/main/resources/assets/hex/lang/`:

| File         | Locale                        | Role                                            |
|--------------|-------------------------------|-------------------------------------------------|
| `en_us.json` | English (US)                  | Source of truth — new keys are added here first |
| `zh_cn.json` | Simplified Chinese (简体中文) | Translation                                     |

Minecraft picks the file matching the player's language and falls back to `en_us.json` for anything it cannot find — so
a missing key does not crash, it silently renders as the raw key id (`hex.config.hand.scale`) in whatever screen it
appears on. That failure is invisible to anyone testing in English, which is why the parity rule below is not optional.

## The rule

**Every language file carries exactly the same key set, in the same order.** Adding, renaming or removing a key means
doing it in *all* of them, in the same commit as the code change. A translation you cannot write yet is still added as a
key — copy the English text in rather than leaving the key out.

Alongside that:

- **Placeholders are identical across files.** `%s` and `%d` are filled positionally by
  `Component.translatable(key, arg…)`, so their count and order must match the English. Word order in the translated
  sentence can differ freely; the *sequence of placeholders* cannot.
- **Files are UTF-8 without a BOM.** A BOM makes the JSON unparseable at load.
- **Escaped quotes need not survive translation.** `\"%s\"` in English is written `“%s”` in Chinese — full-width quotes
  are the correct typography and need no escaping.

## What is not translated

Some strings are not language and stay identical in every file:

- The mod name (`Hex`) and Minecraft/Fabric identifiers.
- Skyblock item IDs and their hint text — `HYPERION`, `UUID`.
- Key names printed as-is — `Tab`.
- **Anything matched against Hypixel's own text.** The island hints (`private island`, `dwarven mines`) are examples of
  what the player types into a field that is compared to the English scoreboard. Translating them would produce a hint
  that cannot match anything.

## Adding a user-visible string

Route it through a key; never hand `Component.literal` a sentence:

```kotlin
// yes
Button.builder(Component.translatable("hex.regions.add_here")) { … }

// no — invisible to every locale but English
Button.builder(Component.literal("Add here")) { … }
```

`Component.literal` remains correct for values that are not language: player names, region names, item IDs, formatted
numbers, and glyphs like `✎` / `✕`.

## Adding a new locale

1. Copy `en_us.json` to the new locale code (`de_de.json`, `ja_jp.json`, …) — Minecraft's codes are lowercase
   `language_country`.
2. Translate the values, leaving the keys and their order untouched.
3. Run the parity check below.
4. Add the locale to the table at the top of this file and to the **Language** section of
   [FEATURES.md](FEATURES.md).

No registration step is needed — Minecraft discovers language files by filename.

## Parity check

Run from the repository root before finishing a change that touched any language file. It reports missing and extra
keys, order drift, and placeholder mismatches against `en_us.json`:

```bash
python - <<'PY'
import json, pathlib, re
lang = pathlib.Path("src/main/resources/assets/hex/lang")
base = json.loads((lang / "en_us.json").read_text(encoding="utf-8"))
ph = lambda s: re.findall(r"%[\d$]*[sdf]", s)
ok = True
for f in sorted(lang.glob("*.json")):
    if f.name == "en_us.json":
        continue
    other = json.loads(f.read_text(encoding="utf-8"))
    for msg, keys in (("missing", base.keys() - other.keys()), ("extra", other.keys() - base.keys())):
        for k in sorted(keys):
            print(f"{f.name}: {msg} key {k}"); ok = False
    if base.keys() == other.keys() and list(base) != list(other):
        print(f"{f.name}: key order differs from en_us.json"); ok = False
    for k in base.keys() & other.keys():
        if ph(base[k]) != ph(other[k]):
            print(f"{f.name}: {k} placeholders {ph(base[k])} vs {ph(other[k])}"); ok = False
print("language files are in sync" if ok else "language files are OUT OF SYNC")
PY
```
