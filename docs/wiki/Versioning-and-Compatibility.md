# Versioning & Compatibility

| Version | What it controls |
| --- | --- |
| Plugin API level | SDK 1.1.0 has `PluginApi.CURRENT = 4` and `MIN_SUPPORTED = 3`. Set the declared level in both the entry point and the manifest. |
| SDK Git tag/revision | Source selection. The [v1.0.2 release](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases/tag/v1.0.2) predates the starter and isolated extensions. Pin a newer revision with the required protocol. |
| SDK library version | This build declares `dev.eliminater.purefusioniptv:plugin-api:1.1.0`. The version identifies the library build. It is available at runtime through `PluginSdk.version`. |
| Host version | `minAppVersion` and manifest `minHostVersion` express the app floor. Optional protocols still require host support. |
| Android version | SDK AAR floor 25. Isolated runtime and starter floor 26. Compile SDK 36 is the build target. |
| Plugin version | `version` labels your own release. `versionCode` increases for Marketplace updates. |

`PluginApiCompatibilityCheck` refuses an unspecified level (the default is 0), levels below the host floor and levels above the host's current level. For general API evolution, a backwards-compatible addition can raise `CURRENT` while preserving `MIN_SUPPORTED`. Changing or removing a published signature requires raising the support floor under the SDK policy. Existing explicitly selected sandbox protocols extend level 3 and require host support independently of that number.

| Declared level | Current API 3 host | Future API 4 host with floor 3 |
| --- | --- | --- |
| Unspecified or 0 | Refused | Refused |
| 2 or lower | Refused | Refused |
| 3 | Compatible | Compatible |
| 4 | Too new | Compatible |

API compatibility is one admission check. Each optional protocol needs its own host integration. Existing API 3 plugins can keep their compiled entry points and manifests. The [DVR protocol](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/docs/DVR_PLUGIN_GUIDE.md) requires both API 4 and the future DVR route. `PluginSdk.currentApi`, `minSupportedApi` and `supportedApiRange` expose the bundled SDK's compatibility information for a future About screen.

## Migration from older contracts

- Level 2 replaced `SyncConflict`'s untyped data with `SyncPayload` and replaced the exception in `SyncState.Error` with a string error code.
- Level 3 replaced provider Context/Intent auth and settings methods with `authenticate()`, `handleAuthResult(PluginUiResult)` and `settingsRequest()` where applicable. The icon properties became package-relative `iconAssetPath` or `serverIconAssetPath`. `Plugin.onLoad(Context)` remains part of the interface.
- Move old imports and the ServiceLoader filename into the `dev.eliminater.purefusioniptv.plugin.api` namespace, rebuild against the new SDK, update the manifest, and retest the intended host route. Changing the number alone cannot make a legacy binary compatible. Check [releases](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases) for the actual migration for each future release.

[Wiki Home](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/Home)
