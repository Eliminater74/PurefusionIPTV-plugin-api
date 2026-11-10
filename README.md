# PureFusion IPTV Plugin SDK

[![Release](https://img.shields.io/github/v/release/Eliminater74/PurefusionIPTV-plugin-api?label=release)](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases)
[![License](https://img.shields.io/github/license/Eliminater74/PurefusionIPTV-plugin-api)](LICENSE)

Build Android TV plugins independently of the PureFusionIPTV application. This open SDK provides Kotlin contracts, an official starter, a `.pfp` packager and Ed25519 developer signing tools. The closed-source Core app owns package admission, worker processes, TV UI, playback, permissions and lifecycle. The Marketplace owns review, countersigning and release distribution.

| Contract | Current requirement |
| --- | --- |
| Plugin API | Level **3** (`CURRENT = MIN_SUPPORTED = 3`). The host must also implement any optional protocol the plugin uses. |
| Android | The SDK library has `minSdk = 25`. Isolated execution and the starter require Android 8.0 / API **26+**. |
| Build tools | JDK 17 or a later JDK supported by the Gradle 9.4.1 wrapper, Android Platform 36 and Build Tools 36.0.0. The starter also uses Node 24 for its listing tools. |
| SDK revision | Pin a public source revision containing `template/` and the required sandbox interfaces. The [v1.0.2 source release](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases/tag/v1.0.2) predates the starter and sandbox extensions. |
| Gradle dependency | `dev.eliminater.purefusioniptv:plugin-api:1.0.0` is the build coordinate. The starter substitutes the pinned source through `includeBuild`. |

## Quick start

1. Download the [public SDK source](https://github.com/Eliminater74/PurefusionIPTV-plugin-api) at a suitable revision. Open [the official starter](template/README.md) in Android Studio, or copy it into your plugin repository and point it at the SDK. See [Getting Started](template/GETTING_STARTED.md) for layout, identity setup and Gradle configuration.
2. Give the plugin a stable reverse-DNS ID and match its manifest, entry class and compiled metadata. Use the template's `compileOnly` SDK dependency so the DEX uses the host's API classes. The installed package's manifest selects the entry point. The starter also includes the service registration at `src/main/resources/META-INF/services/dev.eliminater.purefusioniptv.plugin.api.Plugin`.
3. Build and test from the starter directory. On Windows, use `.\gradlew.bat` with the same tasks.

   ```sh
   bash gradlew :plugin:testDebugUnitTest :packaging:test :packaging:packageDevelopment
   node --test tools/test/workflow.test.cjs
   ```

4. Follow [Signing](template/docs/SIGNING.md) to create or reuse an external Ed25519 key, run `:packaging:packageSigned` and check it with `:packaging:verifySignedPfp`. The package contains one DEX and a signed manifest with payload hashes. Developer signing proves key possession. Isolated Marketplace execution also requires Marketplace signing and admission.
5. Complete the [listing card and artwork](template/marketplace/LISTING_GUIDE.md), then run `node tools/marketplace.cjs prepare`, `validate` and `preview` as separate commands. Enter the assigned identity and listing fields manually in the developer portal. Follow [Submission](template/docs/SUBMISSION.md) for review, approved audience, install and update requirements. Keep the plugin ID and signer and increase `versionCode` for updates.

## What plugins can do

- **Host-rendered TV screens.** The API 3 [surface protocol](docs/SANDBOX_SURFACE_PROTOCOL.md) supports bounded screen descriptors, host-owned navigation and forms. Sports Hub uses this path with a reserved host entry point. Generic third-party screen launch is unavailable.
- **Subtitle providers.** The [subtitle contract](docs/wiki/Subtitle-Plugins.md) supplies search results and SRT or WebVTT bytes to the host. Core validates and renders them. OpenSubtitles uses a scoped, host-owned broker and first-party credential provisioning.
- **Media and data providers.** The SDK defines media-server, cloud-storage and sync contracts. Emby uses the media-server contract through a pinned first-party compatibility worker. Availability of an interface in the SDK does not grant a third-party runtime route.

Isolated plugins have no direct sockets or access to Core private data. The host can grant consent-bound HTTP, scoped non-secret storage and bounded Live TV catalog reads to approved releases. The template supports a local build and test loop. Public third-party executable distribution, website credential delivery and generic remote plugin settings remain gated or unavailable.

The SDK is licensed under [Apache 2.0](LICENSE).

## Developer reference

[Plugin Author Guide](docs/PLUGIN_AUTHOR_GUIDE.md) · [Capabilities](template/docs/CAPABILITIES.md) · [Troubleshooting](template/docs/TROUBLESHOOTING.md) · [Wiki page index](docs/wiki/README.md) · [GitHub Wiki](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki) (after publication)
