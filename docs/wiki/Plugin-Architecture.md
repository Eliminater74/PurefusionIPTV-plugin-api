# Plugin Architecture

| Layer | Responsibility |
| --- | --- |
| SDK | Public entry point and provider interfaces, version policy, bounded protocol types, archive/signing tools and starter. |
| Core | Verifies and admits packages, manages the installed registry and worker lifecycle, brokers grants, renders TV screens and subtitles, and owns playback/navigation. |
| Marketplace | Verifies uploads, binds developer ownership and signing identity, manages review, signs approved releases and authorizes downloads. |

**Isolated executables.** On API 26+, Core starts a separate isolated-UID service for an admitted Marketplace DEX and loads the verified DEX in memory. The host supplies public API types. The worker returns bounded values through the host transport. Worker code has no direct sockets or access to Core private data. Capability-free entries receive their isolated worker's local application `Context`. Capability-bearing entries and subtitle providers use `SandboxPluginV1.onSandboxLoad` with a host capability object.

**Pinned Emby compatibility.** Marketplace Emby 2.0.3 (versionCode 5) runs in a separate worker process under the application's UID. Core binds this exception to the exact plugin/developer identity, developer signer and signed manifest. Emby needs the worker's app-UID file and network access for its existing implementation. This worker provides process separation and fault containment. App-UID access is outside the isolated third-party contract.

The SDK also has provider-shaped interfaces. Availability of their types is separate from executable admission. See [API Reference](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/API-Reference) and [Security Model](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/Security-Model).

**DVR contract.** SDK 1.1.0 defines bounded `pf.dvr.v1` commands and a session-scoped host facade for the existing isolated worker architecture. The provider owns DVR decisions (rules, series and episodes, conflicts, library state). The host owns entitlement, capture execution, protected media, destinations, the guide, wakes, provider state storage and playback. See the [DVR plugin guide](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/docs/DVR_PLUGIN_GUIDE.md).

[Wiki Home](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/Home)
