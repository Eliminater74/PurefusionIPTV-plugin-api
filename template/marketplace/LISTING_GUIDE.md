# Marketplace listing v1

The blank listing-template.json intentionally fails submission validation until completed. Do not add unknown keys. Run prepare to compute local artwork descriptors and synchronize manifest identity; validate checks completeness and exact generated signed-manifest binding; preview consumes the same listing.local.json.

## Field sources and portal mapping

| Listing field | Source / portal entry |
| --- | --- |
| pluginId, displayName, developerId, version, versionCode, category, apiLevel, minHostVersion, minSdk, capabilities | Root build inputs → signed manifest → listing; matching portal identity/compatibility/capability controls. |
| publisher | Your portal publisher display name; preview “by” label. Must match authorized publisher. |
| shortDescription, fullDescription | Editable listing, matching form controls and preview. Include capability rationale and privacy/developer-site links as needed. |
| features | Optional highlights, one per line in portal; preview bullets. |
| changelog | Release notes; required for updates. |
| support.url | HTTPS support URL control; preview Support. Email alone is invalid. |
| icon, screenshots | Image bytes → descriptors; portal file selection recomputes metadata; altText and screenshot caption entered separately. |
| maxHostVersion | Optional only when matching signed manifest. The starter packager has no upper-bound input; omit unless a future public packager supports matching bounds. |

No fields exist for standalone website, support email, privacyPolicyUrl, permissionsExplanation, legal text, review state, owner UID or API ranges. Use fullDescription/support page and separate rights disclosure/worksheet. apiLevel is one compiled level; the host owns accepted ranges. Preview never invents verified status. The current portal requires manual entry/file selection and has no JSON import control.

## Local artwork and contract

Original assets: `artwork/plugin-icon-placeholder.png` (256×256) and `artwork/screenshot-placeholder.png` (1920×1080), with no provider logos/private data/copyrighted captures. Replace bytes with your own final PNGs, keeping filenames for the helper. Additional screenshots use `screenshot-2.png`, etc.; sorted filenames determine display order. Match altText/captions to that order. Repackage after changing the icon.

The local helper handles PNG header dimensions, size and digest; the portal also accepts static JPEG/WebP and fully decodes all uploads. For other formats use manual portal entry; the PNG helper cannot process them. Local checks do not replace decoded-image verification or artwork-rights review. Replace mockups with real TV captures before submission.

`tools/listing-contract.cjs` is a standalone transpilation of the server validator; [provenance](../tools/CONTRACT_SOURCE.md). It needs no private source/runtime. Server errors/current portal guidance remain authoritative as contracts evolve. See [Submission](../docs/SUBMISSION.md).

## Listing fields

Omit optional fields when absent. Explicit `null`, unknown keys at any level, unsupported schema versions, coerced values and duplicate capabilities or asset IDs are invalid. Lengths are UTF-16 code units. Text is nonempty, trimmed and plain. Angle brackets, control characters and directional embedding and override controls are rejected. Only the full description and changelog accept line feeds. Clients render descriptions as text.

| Field | Requirement | JSON type and bounds |
| --- | --- | --- |
| `schemaVersion` | Required | Integer `1`. Independent of the manifest schema and Plugin API level. |
| `pluginId` | Required, manifest-bound | ASCII reverse DNS, 128 characters maximum. At least two dot-separated segments beginning with a letter. Letters, digits and underscores are allowed inside segments. Case-sensitive. |
| `displayName` | Required, manifest-bound | String, 1–80. |
| `developerId` | Required, manifest-bound | String, 1–80. The approved plugin retains its developer identity across releases. |
| `version` | Required, manifest-bound | String, 1–32. Begins with an ASCII letter or digit. Also permits dot, underscore, plus and hyphen. |
| `versionCode` | Required, manifest-bound | Integer 1–2,147,483,647. Must increase for publication under the same plugin ID. |
| `publisher` | Required | String, 1–80. Display label supplied by the developer. Verified status is separate server state. |
| `shortDescription` | Required | String, 1–400. |
| `fullDescription` | Required | String, 1–4,000. |
| `category` | Required, manifest-bound | One existing manifest `PluginCategory` wire value. |
| `apiLevel` | Required, manifest-bound | Integer 1–2,147,483,647. One level the plugin was compiled against. The pinned SDK and host currently use level 3. The package verifier decides host admission. |
| `minHostVersion` | Required, manifest-bound | Minimum app version. One to three dot-separated numeric components, each 0–99. |
| `maxHostVersion` | Optional, manifest-bound | Same numeric format or `major.x`. Omit when the manifest has no upper bound. Its upper bound cannot precede the minimum. |
| `minSdk` | Required, manifest-bound | Integer 1–200. Minimum Android API declared by the package. |
| `capabilities` | Required, manifest-bound | Array of zero or more unique existing `HostCapability` identifiers. A declared empty set uses `[]`. Exact set match, independent of order. |
| `icon` | Required | One artwork descriptor. |
| `screenshots` | Required | 1–8 artwork descriptors in display order. First image is the gallery lead. |
| `features` | Optional | Array of up to 8 plain strings, each 1–120 characters. |
| `changelog` | Required for updates, optional for first publication | One plain text string for this release, 1–2,000 characters. Legacy reviewer queue uses its existing `releaseNotes` label. |
| `support` | Required | Object containing only `url`, a credential-free `https://` link on a dotted ASCII hostname, 512 characters maximum. No port. This is a user-facing support link. |

Categories are `epg_metadata`, `service_integration`, `ui_extension`, `media_server`, `sync`, `theme_pack`, and `runtime_extension`.

Capabilities are `EPG_READ`, `EPG_WRITE`, `PLAYLIST_IMPORT`, `METADATA_READ`, `CATCHUP_PROVIDER`, `MEDIA_SERVER_ACCESS`, `SYNC_READ`, `SYNC_WRITE`, `CLOUD_STORAGE_ACCESS`, `UI_SETTINGS_EXTENSION`, `UI_HOME_EXTENSION`, `UI_PLAYER_ACTION`, `NETWORK_EXTERNAL`, `FILE_EXPORT`, `BACKGROUND_SYNC`, `ANALYTICS_EVENT_WRITE`, and `PLUGIN_SCOPED_STORAGE` (experimental Core broker).

## Artwork

| Descriptor field | Rule |
| --- | --- |
| `assetId` | Required. Lowercase 64-character SHA-256 hex of the immutable server-managed image bytes. Unique across the icon and gallery in one listing. |
| `format` | Required. `image/png`, `image/jpeg` or `image/webp`. Static raster images. |
| `width`, `height` | Required pixel integers. Icon is square, 128–1,024 px per side. Screenshots are 640–3,840 px wide and 360–2,160 px high, with width-to-height ratio 1.6–2.0 inclusive. Prefer 16:9 for TV. |
| `byteSize` | Required integer. Icon is 1–524,288 bytes and each screenshot is 1–2,097,152 bytes. |
| `altText` | Required plain string, 1–160, describing meaningful visual content for accessibility. |
| `caption` | Optional plain string, 1–160, for screenshots only. |
