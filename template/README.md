# PureFusionIPTV Plugin Developer Template

A working API 3 isolated plugin: customize → build → test → sign → verify → complete listing → submit for review.

The starter uses the API level in its manifest for both the compiled entry point and package. It stays at API 3 when built against SDK 1.1.0. For the API 4 contract, see the [independent DVR example](../examples/dvr/README.md).

Open **this directory** in Android Studio. You need public Plugin API source, Android SDK 36/Build Tools 36.0.0, a Gradle-supported JDK 17+, and Node 24 for listing/setup tools. Core, Firebase credentials and first-party signing keys are not dependencies. The Gradle 9.4.1 wrapper is included. AGP 9.2.1 and Kotlin 2.3.20 match the SDK; JVM bytecode targets Java 17.

Start with [Getting Started](GETTING_STARTED.md), then [Developer Guide](DEVELOPER_GUIDE.md), [Listing Guide](marketplace/LISTING_GUIDE.md), [Signing](docs/SIGNING.md), [Submission](docs/SUBMISSION.md), [Capabilities](docs/CAPABILITIES.md), and [Troubleshooting](docs/TROUBLESHOOTING.md). See the [Plugin Author Guide](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/docs/PLUGIN_AUTHOR_GUIDE.md), [surface protocol](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/docs/SANDBOX_SURFACE_PROTOCOL.md) and [Wiki page index](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/docs/wiki/README.md) for the SDK and host contracts.

```sh
# Windows: use .\gradlew.bat instead of bash gradlew
bash gradlew :plugin:testDebugUnitTest :packaging:test :packaging:packageDevelopment
node --test tools/test/workflow.test.cjs
node tools/marketplace.cjs prepare
node tools/marketplace.cjs preview
```

The example has a host-rendered screen, a counter, validated text/numeric preferences, scoped persistence, Back handling, and an optional cancellable brokered HTTP example (denied by default). It passes no Views or executable UI to Core. Generic website settings and website credential delivery are unavailable. Brokered HTTP can use host-owned origin credentials where supported.

**Host launch limitation:** current Core exposes a screen slot for Sports Hub only. Arbitrary third-party IDs have no generic home/Plugin Hub screen-launch route yet. This example's surface is functional in local contract tests; on-TV screen acceptance additionally needs an authorized host launch integration. Do not reuse Sports Hub's identity to obtain its slot. This template does not modify Core to add discovery.

**Runtime boundary:** isolated execution requires Android API 26+, a compatible host with the required sandbox extensions, genuine Marketplace signatures and admission, and TV consent for data access. A local developer signature or development sideload does not grant that execution path. Public third-party executable distribution remains gated; arrange authorized review/testing through the existing portal. Local broker tests do not prove Marketplace approval or TV acceptance.

```text
template/
  manifest.json                 editable inputs, not the final signed manifest
  settings.gradle.kts           public SDK composite discovery
  gradlew, gradlew.bat, gradle/  independent wrapper
  plugin/                       DEX build, lifecycle/surface/broker example + tests
  packaging/                    SDK packager/signer, key tools, preflight + tests
  marketplace/                  listing v1, original placeholder PNGs, card preview
  tools/                        Node setup/listing/preview; PowerShell init wrapper
  docs/                         capability, settings, signing, submission guides
```

The APK is an intermediate DEX container, never installed. `packageDevelopment` writes an unsigned `.pfp` under `packaging/build/development/`; `packageSigned` writes a developer-signed `.pfp` under `packaging/build/signed/` and re-verifies it. Keep the same plugin ID and signing key for updates. No task publishes anything.
