# trilleo.net sync

Hex can upload your **attribute levels** and your **Hunting Box** to your [trilleo.net](https://www.trilleo.net)
account. The site's shard collection, fusion calculator and hunting planner then work from what you actually have,
without you typing it in. Each Skyblock profile gets its own collection on the site.

## Linking your account

1. Run `/hexa sync link`, or press **Link trilleo.net account** in the **trilleo.net Sync** tab of `/hexa config`.
2. A code and a link appear in chat. **Click the link** (or open it yourself), sign in on trilleo.net and confirm that
   the code matches. Click the code in chat to copy it.
3. Hex says **Linked to trilleo.net as @you**. That's it.

The code expires after 10 minutes. Run `/hexa sync link` again for a new one, or to show the current code again.

You never type your trilleo.net password into Minecraft. Hex gets a token that can only read and update your Skyblock
collection; it cannot sign in to the site, see your email or change your account.

## Syncing

Open the menus and page through them:

- `/attributemenu` — your attribute levels
- `/huntingbox` — the shards in your Hunting Box

A few seconds after you close a menu, Hex uploads what it read. Open both, one after the other, and they go up together.
Hex syncs at most once a minute, so closing and reopening a menu does not flood the site.

A line in chat says how many levels and Hunting Box counts changed. **Hover over it** to see each change, such as
*Grove: 3 → 4*.

### Whole list or just what you saw

- If you looked at **every page** of a menu and did not search, Hex sends the **whole list**. Anything the menu no
  longer shows is set to 0 on the site, for example a shard you have fused away.
- If you **skipped a page**, or **searched** the Hunting Box, Hex sends **only the shards you saw** and leaves
  everything else on the site as it was.

So a quick look at page one never wipes the rest of your collection.

Attributes you have disabled in the menu are uploaded at their real level.

## Settings

All on the **trilleo.net Sync** tab of `/hexa config`:

| Setting                   | What it does                                                                     |
|---------------------------|----------------------------------------------------------------------------------|
| **Link / Unlink**         | Link an account, or revoke the token and stop uploading.                         |
| **Read attribute menus**  | Off stops Hex from reading the menus at all.                                     |
| **Sync automatically**    | Off means nothing goes up until you run `/hexa sync now` or press **Sync now**.  |
| **Show changes in chat**  | The summary line after each sync.                                                |
| **Sync now**              | Upload what was read right away.                                                 |
| **API address**           | Leave this alone. Only for testing against a local copy of the site.             |

These settings, and the link itself, belong to your installation, not to a [config profile](Config-Profiles). Switching
profiles never changes them, and **Copy to clipboard** never contains your token.

## Commands

```
/hexa sync          — which account is linked, which profile Hex sees, when it last synced
/hexa sync link     — link an account, or show the code again
/hexa sync unlink   — revoke the token and stop uploading
/hexa sync now      — upload right away
```

## Unlinking

`/hexa sync unlink` (or **Unlink** in the settings) revokes the token on trilleo.net and stops uploading. What is
already on the site stays there. You can also revoke Hex from the **Apps** page on trilleo.net. Hex notices the next
time it talks to the site, forgets the token and tells you.

## Good to know

- **Hex must know which profile you are on.** It learns the profile's ID from the `Profile ID:` line Hypixel sends when
  you join Skyblock. A menu read before that line arrives waits for it. If another mod hides that chat line before Hex
  sees it, syncing cannot work.
- **Nothing is read until you link an account.**
- **Up to 10 profiles per account.** If you hit the limit, Hex tells you; delete a profile on the site to make room.
- **Your token is private.** It is stored in `config/hex/trilleo-account.json`. Never share that file. Hex never writes
  the token to its log. See [Config files](Config-Files).
- **Offline or the site is down?** Hex retries a few times, then tells you. Open the menu again later to resend.
