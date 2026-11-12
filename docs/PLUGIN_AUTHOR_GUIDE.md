# PureFusion IPTV Plugin Author Guide

Use the [official template](../template/README.md) to build an API 3 plugin without Core source. Use the [DVR plugin guide](DVR_PLUGIN_GUIDE.md) and [independent example](../examples/dvr/README.md) for the API 4 DVR contract. See [Getting Started](../template/GETTING_STARTED.md) for setup and build commands.

## Set up a plugin project

Use the included Gradle 9.4.1 wrapper, JDK 17 or a supported later JDK, Android Platform 36 and Build Tools 36.0.0. The SDK AAR has a minimum Android API of 25. Isolated execution requires Android API 26 or higher. SDK 1.1.0 uses the build coordinate `dev.eliminater.purefusioniptv:plugin-api:1.1.0`. The examples resolve their dependency through source substitution. Keep the dependency `compileOnly` in the executable module and use the SDK as a JVM test dependency.

The SDK Git release tag and build coordinate are separate from the Plugin API integer. Pick a revision with the starter and required protocol sources. The older tag `v1.0.2` predates the sandbox additions. See [Versioning and Compatibility](wiki/Versioning-and-Compatibility.md).

## Define identity and the entry point

Use a stable reverse-DNS `Plugin.id` under your assigned namespace for Marketplace submission. Repository and package filenames can change independently of the plugin ID. `name` is the display label, `author` identifies the developer or organization, and `version` is the plugin's release label. Keep `minAppVersion` aligned with the package's `minHostVersion`. Declare the API level your plugin targets in both the compiled entry point and the manifest. The general starter takes this value from its manifest and targets level 3.

Implement `Plugin` with a public no-argument constructor. The generated manifest's `entrypoints[].className` selects the installed entry point. For ServiceLoader-based discovery, add the following file under the plugin module.

```text
src/main/resources/META-INF/services/dev.eliminater.purefusioniptv.plugin.api.Plugin
```

Put one fully qualified implementation class per line. The [starter service file](../template/plugin/src/main/resources/META-INF/services/dev.eliminater.purefusioniptv.plugin.api.Plugin) matches the starter's actual entry class. When renaming the class, update both the service file and the manifest input's `entryPoint`.

## Select the runtime contract

| Use case | Entry point contract | Host boundary |
| --- | --- | --- |
| Host-rendered screen | `Plugin`, `SandboxPluginV1`, `SandboxSurfacePluginV1` | Declare `UI_HOME_EXTENSION`. A TV screen needs a host launch integration. Use `pf.surface.v1` descriptors for UI and Back. |
| Subtitle provider | `Plugin`, `SandboxPluginV1`, `SandboxSubtitlePluginV1` | Signed `subtitle_provider` category on a compatible host. Core owns playback and subtitle rendering. See the [subtitle protocol guide](wiki/Subtitle-Plugins.md). |
| DVR provider | `Plugin`, `SandboxPluginV1`, `DvrProvider` | API 4 contract. The future Core adapter owns authorization, stream access, scheduling, storage and playback. See the [DVR guide](DVR_PLUGIN_GUIDE.md). |
| Capability-free executable | `Plugin` | An admitted isolated package without capabilities or a subtitle category receives the isolated worker's local application `Context` in `onLoad`. |
| Provider compatibility | `MediaServerProvider`, `CloudStorageProvider`, `SyncProvider` | These are SDK contracts. The current external executable policy reserves the app-UID worker path for the pinned first-party Emby release. |

Keep `Plugin.capabilities` empty for API 3 isolated entry points. The future DVR route uses the `DVR_PROVIDER` role and a separate signed `DVR_ACCESS` request. A provider role describes the entry point. Core verifies the signed declaration and consent before granting any access.

Capability-bearing and subtitle entry points initialize through `onSandboxLoad(capabilities)` in their isolated worker. Implement the required `onLoad(Context)` member, but do not rely on it for those entry points. Release references and cancel work in `onUnload`. Persist only confirmed, independently valid records because worker termination can skip cleanup. Use the [capability guide](../template/docs/CAPABILITIES.md) for supported operations.

## Package, verify and test

The template compiles an intermediate APK, extracts its single `classes.dex` and packages it with the SDK. Follow the [Developer Guide](../template/DEVELOPER_GUIDE.md) and [Signing](../template/docs/SIGNING.md). `:packaging:packageDevelopment` builds an unsigned inspection package. `:packaging:packageSigned` uses your external developer key. `:packaging:verifySignedPfp` checks the signed output locally. The intermediate APK is a build input for packaging.

Use the template's JVM and listing tests for the independent developer loop. Verify a TV integration only through an authorized Marketplace release, eligible device, applicable consent and a supported host entry point. Importing a development package does not confer isolated Marketplace admission. See [Troubleshooting](../template/docs/TROUBLESHOOTING.md).

## Prepare a release

Use the [Listing Guide](../template/marketplace/LISTING_GUIDE.md) for required metadata and artwork, and [Submission](../template/docs/SUBMISSION.md) for portal entry and review. Public third-party executable distribution is gated. Preserve the plugin ID, developer identity and signing key across updates. Increase `versionCode`, provide a changelog, and submit the new signed package for review.

Report SDK issues through [GitHub Issues](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/issues). For installation or admission issues, capture the host and Android versions, plugin API level, release version and visible error. Keep credentials and sensitive request data out of reports.
