# Examples

| Example | Contract to study | Scope |
| --- | --- | --- |
| [Official starter](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/template/README.md) | `Plugin`, `SandboxPluginV1`, `SandboxSurfacePluginV1`. Test a host-rendered counter, preferences, storage, Back and optional cancellable HTTP. | Builds, signs and verifies locally. TV launch requires authorized host integration. |
| [Synthetic DVR provider](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/examples/dvr/README.md) | API 4 discovery, scheduling, status and cancellation over `pf.dvr.v1`. | Builds and tests against the public SDK with a fake host. The first-party PureFusion DVR plugin is the complete provider. |
| Emby | `MediaServerProvider` supplies libraries, media navigation and stream URLs. | Its pinned Marketplace release runs in a separate app-UID compatibility worker. Its direct network and private-session access are specific to that exception. |
| Sports Hub | `pf.surface.v1`, consented `pf.catalog.v1`, host navigation and scoped settings. | Extracted from Core and installed through the restricted Marketplace path. The Sports Hub launch slot and legacy settings integration are first-party. |
| OpenSubtitles | `pf.subtitle.v1` describe, search and download. The host renders results and attaches supported text subtitles. | The provider uses the approved network broker and first-party host-managed credentials. Live availability depends on release, host, consent and service configuration. |

Start with the SDK template and use the [API Reference](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/API-Reference), [UI Extensions](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/UI-Extensions) and [Subtitle Plugins](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/Subtitle-Plugins) to evaluate the contract for your integration.

[Wiki Home](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/Home)
