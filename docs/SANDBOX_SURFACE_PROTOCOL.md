# Isolated plugin screens and the Live TV catalog (experimental, API 3 extension)

This extension lets an isolated plugin provide a full screen inside PureFusionIPTV without any of
its code or views entering the app process. It was built to move the F15 Sports Hub out of Core.

It is not a new API level. A host that does not recognise a declaration refuses the package before
plugin code runs. Two optional interfaces in `dev.eliminater.purefusioniptv.plugin.api` carry it:

| Interface | Implemented by | Purpose |
| --- | --- | --- |
| `SandboxSurfacePluginV1.handleSurface(ByteArray): ByteArray?` | the plugin entry | answers one `pf.surface.v1` request with one document |
| `SandboxLiveCatalogV1.queryLiveCatalog(ByteArray): ByteArray?` | the host capability object | answers one `pf.catalog.v1` request |

Limits are in `SandboxSurfaceProtocolV1`. Both directions are UTF-8 JSON. The host gzip-compresses
them on the Binder wire; the plugin never sees that.

## Declarations and consent

| Manifest capability | Grants | TV consent |
| --- | --- | --- |
| `UI_HOME_EXTENSION` | the host calls `handleSurface` while the user has the plugin's screen open | none (the plugin only describes; the host draws) |
| `EPG_READ` | `queryLiveCatalog`, only during a `handleSurface` call | "read Live TV channels and guide" |
| `PLUGIN_SCOPED_STORAGE` | `readStorage`/`writeStorage`/`deleteStorage` | "private plugin data" |

`EPG_READ` without `NETWORK_EXTERNAL` means the rows a plugin reads cannot leave the device. The host
shows its own consent screen the first time the screen opens, and **Manage → Plugin permissions**
can revoke it. Consent is bound to the exact signed manifest, publisher key, plugin, account and
installation, like the other isolated capabilities.

The isolated lane requires Android 8.0 (API 26). There is no fallback loader on Android 7.1.

## `pf.surface.v1`

### Request (host → plugin)

```json
{
  "schema": "pf.surface.v1",
  "type": "open | continue | refresh | page | section | activate | back | dialog | dialog_input",
  "now": 1762440000000,
  "zone": "America/New_York",
  "state": "<the plugin's last opaque state string>",
  "section": "<current section id>",
  "restore": false,          // open: true when returning from playback/Guide to the same card
  "reset": false,            // refresh: eligible scope or guide content changed
  "page": "next | first",    // page
  "card": "<card id>",       // activate
  "dialog": "<dialog id>", "result": "option | extra | confirm | submit | cancel",
  "option": "<option id>", "value": "<search text>", "values": { "<field id>": "<text>" },
  "legacy": { "digest": "<sha256>", "mode": "merge | sync", "records": { "<key>": "<record>" } }   // first-party Sports Hub only
}
```

`continue` follows any document that set `"continue": true` (for example, programmes after
channels). `section` is sent when the user enters or leaves a section marked `remote`.

### Document (plugin → host)

```json
{
  "schema": "pf.surface.v1",
  "state": "<opaque, ≤ 16K chars>",
  "title": "Sports Hub",
  "subtitle": "Page 1 · 120 channels, 40 EPG listings.",
  "sections": [ { "id": "live_now", "title": "Live Now", "count": 3, "heading": null,
                  "remote": false, "next": true, "first": false } ],
  "rows": { "live_now": [ { "id": "h:today", "header": "Today" }, { "card": "<card id>" } ] },
  "cards": { "<card id>": {
      "title": "...", "subtitle": "...", "badge": "NHL", "time": "6:00 PM–9:00 PM · EPG",
      "progress": 400, "status": "<why it cannot play>",
      "icon": "channel | plugin", "activate": "play | plugin",
      "channel": { "id": 10, "playlistId": 1, "name": "ESPN", "customName": null, "epgId": "espn.us", "tvgName": null },
      "airing": { "epgId": "espn.us", "start": 0, "end": 0, "title": "...", "programmeId": 5 },
      "actions": ["play", "guide", "remind", "favorite", "multiview"] } },
  "emptyText": "...", "loadingText": "...",
  "loading": false, "continue": false,
  "focus": "<card id>", "focusFirst": false, "focusSection": false,
  "status": "<message>", "error": false,
  "dialog": { ... } | null,
  "legacyAck": "<sha256>",
  "teamSync": { "base": "<sha256>", "records": { "<key>": "<record>" } }   // first-party Sports Hub only
}
```

**Shared first-party copy (`legacy` / `teamSync`).** Only the verified first-party Sports Hub
release installed from the Marketplace, with `PLUGIN_SCOPED_STORAGE`, for a signed-in account.
The host keeps the F15 `sports_team_v1/<league>/<team>` settings as the shared copy of the follows;
its existing TV-to-TV replication and settings backups carry it. On `open` the host sends the copy
(`legacy`) whenever it differs from what this plugin namespace last acknowledged, also when empty.
`mode` is `merge` the first time and after a settings restore (union; nothing is removed) and
`sync` when another TV changed the copy (three-way against the copy the plugin last merged). Hosts
before November 2026 send no `mode`, which means `merge`. The plugin acknowledges with
`legacyAck`. A document may carry `teamSync`: the plugin's complete follow list plus `base`, the
digest of the copy it last merged. The host writes it back only while the copy is still `base` or
exactly what the host last wrote for this namespace, so a change made on another TV is never
overwritten; it is then handed over on the next `open`. Every record must pass the strict F15
codec or the whole `teamSync` is ignored (the document itself still renders).

A document with `"partial": true` only changes the dialog, status line or focus. `"dialog": null`
closes the host dialog; omitting `dialog` leaves it unchanged. `"handled": false` on a `back`
answer lets the host apply its normal Back.

Host rules: every row must resolve to a card or be a header; ids are unique per section; actions
need a channel, `remind` needs an airing; unknown `kind`, `activate` or action values refuse the
whole document. Text is truncated to 240 characters (600 for messages) and stripped of control
characters. Before Play, Guide, Remind, Favorite or Multi-View, the host reloads the channel and
rejects the card if the channel is gone, hidden, not live, not entitled, no longer carries the
card's EPG identity, is ambiguous for the airing, or the airing is no longer current.

### Dialogs

| kind | fields | results |
| --- | --- | --- |
| `choice` | `title`, `message`, `options[{id,label}]`, `cancel` | `option`, `cancel` |
| `search` | `title`, `hint`, `value`, `options`, `extra`, `cancel` | `dialog_input` while typing, `option`, `extra`, `cancel` |
| `confirm` | `title`, `message`, `confirm`, `cancel` | `confirm`, `cancel` |
| `form` | `title`, `fields[{id,hint,value,max,error}]`, `submit`, `cancel` | `submit` with `values`, `cancel` |

## `pf.catalog.v1`

Every request is `{"schema": "pf.catalog.v1", "op": ...}`. Every answer has `"ok"`; a refusal is
`{"ok": false, "error": "invalid_request | too_broad | timeout | unavailable"}` and a denied call
returns null. The host recomputes the eligible playlists and the guide horizon (Free 3 days,
Premium 14 days) on every request, reads only ACTIVE snapshots and live, non-hidden channels, and
never returns stream, logo or provider addresses.

| op | request | response |
| --- | --- | --- |
| `scope` | — | `now`, `forwardMs`, `horizon`, `stamp` (SHA-256), `playlists[{id,name}]` |
| `stamp` | — | `stamp` |
| `channels` | `afterId`, `favoritesOnly`, `groupTerms[]`, `nameTerms[]`, `limit ≤ 257` | `rows[{id,playlistId,name,customName,groupTitle,epgId,tvgName,favorite}]`, ascending id |
| `programmes` | `phase: airing|upcoming`, `now`, `after{start,playlist,epg,title}`, `categoryTerms[]`+`titleTerms[]` **or** `titlePhrases[]`, `limit ≤ 201` | `rows[{id,playlistId,epgId,title,category,start,end}]` in guide order |
| `channelMatches` | `playlistIds[]` (intersected with the eligible set), `epgIds[1..64]` | one row per scoped EPG id with `matchCount` |

Terms are `[a-z0-9]+( [a-z0-9]+)*`, at most 48 characters and 64 per list (and at most 100 bind
slots per statement); they are matched as `%term%` with inner spaces widened. Title phrases are
letters, digits and single spaces, at most 80 characters and 512 per request; the host quotes them
and builds a flat FTS4 `OR` over the guide title index. A request's `now` is ignored when it is
more than 10 minutes from the host clock. One request may run at most 64 SQL statements, and one
surface call at most 160 catalog requests.
