# API Reference

Types below are in `dev.eliminater.purefusioniptv.plugin.api`. The linked Kotlin definitions are authoritative for signatures and defaults.

| Public contract | Current use |
| --- | --- |
| [`Plugin`, `PluginCapability`](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/src/main/java/dev/eliminater/purefusioniptv/plugin/api/Plugin.kt) | Stable identity, display metadata, app/API compatibility, provider claims, `onLoad(Context)` and `onUnload()`. |
| [`PluginApi`, `PluginApiCompatibilityCheck`](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/src/main/java/dev/eliminater/purefusioniptv/plugin/api/PluginApi.kt) | Evaluates a plugin-declared level and reports unspecified, too old or too new. |
| [`SandboxPluginV1`, `SandboxCapabilitiesV1`, `SandboxHttpResultV1`](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/src/main/java/dev/eliminater/purefusioniptv/plugin/api/SandboxPluginV1.kt) | Context-free initialization, scoped storage and cancellable HTTP. Positive start results are session-local IDs. Poll for pending or sequential chunks, and handle denial, busy and terminal failure. |
| [`SandboxSurfacePluginV1`, `SandboxLiveCatalogV1`, `SandboxSurfaceProtocolV1`](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/src/main/java/dev/eliminater/purefusioniptv/plugin/api/SandboxSurfaceV1.kt) | Host-rendered screen messages and optional host catalog queries during a surface call. |
| [`SandboxSubtitlePluginV1`, `SandboxSubtitleProtocolV1`](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/src/main/java/dev/eliminater/purefusioniptv/plugin/api/SandboxSubtitleV1.kt) | Describe, search and download with typed request/result/response values. |
| [`MediaServerProvider`](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/src/main/java/dev/eliminater/purefusioniptv/plugin/api/MediaServerProvider.kt) | Media browsing, search and `getMediaStreamUrl`. The pinned Emby worker uses this provider contract. |
| [`CloudStorageProvider`](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/src/main/java/dev/eliminater/purefusioniptv/plugin/api/CloudStorageProvider.kt) | Backup-related file/folder operations, account metadata and quota. Public contract without an arbitrary isolated external provider route. |
| [`SyncProvider`, `SyncPayload`](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/src/main/java/dev/eliminater/purefusioniptv/plugin/api/SyncProvider.kt) | Sync operations, conflicts and serializable payload envelopes. Public contract without an arbitrary isolated external provider route. |
| [`PluginUiRequest`, `PluginUiResult`](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/src/main/java/dev/eliminater/purefusioniptv/plugin/api/PluginUi.kt) | Host-mediated provider authentication, device authorization or settings descriptors where the host supports the integration. Distinct from isolated screen forms. |

`PluginCapability` enum constants describe roles. They do not by themselves expose an extension interface or grant access. Trakt, third-party skins, arbitrary EPG write providers, and a new media playback-session/series-parent contract are outside the current author-facing SDK. See [Capabilities & Permissions](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/Capabilities-and-Permissions).

[Wiki Home](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/Home)
