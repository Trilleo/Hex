# Updating

Hex keeps itself up to date from its [GitHub releases](https://github.com/Trilleo/Hex/releases). You do not have to
download anything by hand.

## How it works

1. **On startup**, Hex checks in the background whether a newer release exists.
2. If one does, it **downloads the new jar** into `config/hex/update/` and tells you in chat. The notice reaches you
   once you are in a world — including when a slow download means that is a while after you joined.
3. **When you next close Minecraft**, the new jar is swapped into your `mods` folder and the old one removed.

So an update you are told about on Monday is running the next time you launch the game. Nothing is replaced while you
are playing.

## Controlling it

Everything is on the **Updates** tab of `/hexa config`:

| Setting                 | What it does                                          |
|-------------------------|-------------------------------------------------------|
| **Check on startup**    | Turn the background check off entirely.               |
| **Include prereleases** | Opt in to prerelease builds as well as full releases. |
| **Check now**           | Run the check immediately, without restarting.        |

`/hexa update` does the same as **Check now** from chat.

## These settings are per installation

The **Updates** tab belongs to *your install*, not to a [config profile](Config-Profiles). Switching, saving or pasting
a profile never changes whether Hex updates itself, and the tab is left out of a **Copy to clipboard** blob — you cannot
accidentally turn off someone else's updater by sharing your settings.

They are stored at `config/hex/update.json` if you would rather edit them by hand.

## If the automatic swap fails

The downloaded jar is left in `config/hex/update/`. Move it into your `mods` folder yourself and delete the old
`hex-<version>.jar`. Nothing else is needed — your settings are untouched by an update.

The swap can fail when the `mods` folder is read-only, when the game is force-killed rather than closed, or when a
launcher holds the jar open. If it happens every time, updating manually from the
[releases page](https://github.com/Trilleo/Hex/releases) is perfectly fine — turn **Check on startup** off if the
notices become noise.

## Downgrading

Delete the current jar from `mods`, drop in an older `hex-<version>.jar` from the releases page, and turn off **Check on
startup** so it is not immediately updated again. Config files from a newer version are read by an older one on a
best-effort basis: unknown fields are ignored rather than crashing, but a setting that only exists in the newer version
is lost when the older one saves over the file.
