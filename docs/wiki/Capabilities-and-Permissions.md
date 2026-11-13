# Capabilities & Permissions

`Plugin.capabilities` holds SDK provider claims. The signed manifest's `capabilities` holds Core `HostCapability` declarations for review. `PluginCapability.MEDIA_SERVER` describes a provider and `MEDIA_SERVER_ACCESS` is a separate manifest request. API 3 isolated entries report no SDK provider claims. Only the runtime's supported subset of manifest capabilities can be used in the isolated lane.

| Isolated declaration | Operation | Grant |
| --- | --- | --- |
| `UI_HOME_EXTENSION` | Host renders a `pf.surface.v1` screen | Requires an integrated host launch route. Display alone needs no TV data grant. |
| `EPG_READ` | Read bounded eligible Live TV channels and guide data | Owner consent and an active surface callback. Stream, logo and provider URLs are excluded. |
| `PLUGIN_SCOPED_STORAGE` | Read, write and delete non-secret plugin records | Owner consent. Keys are 1–64 ASCII letters, digits, underscores or hyphens. Limits are 64 keys, 16 KiB per value and 256 KiB total. Space calls at least 75 ms apart. |
| `NETWORK_EXTERNAL` | Bounded HTTP to an approved origin | Owner origin consent. JSON POST needs its own grant. The host may attach an origin-bound credential. |

Consent is scoped to the signed manifest, plugin, publisher key, account and installation. A new release can require fresh consent. In the host's Plugin permissions UI, owners can revoke grants. Host requests carry a host-owned User-Agent. The broker rejects redirects and does not accept plugin-chosen headers, cookies or credentials. See the [capability guide](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/template/docs/CAPABILITIES.md) for pacing, origin and credential rules.

API 4 adds `DVR_PROVIDER` as an SDK role. The host route requires a signed API 4 `dvr_provider` category with exactly the `DVR_ACCESS` declaration, an enabled Marketplace release, and a Core-verified Premium or Lifetime entitlement. Exactly one DVR provider is active at a time. An API 3 host lacks this route. See the [DVR plugin guide](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/docs/DVR_PLUGIN_GUIDE.md) for per-operation authorization.

[Wiki Home](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/Home)
