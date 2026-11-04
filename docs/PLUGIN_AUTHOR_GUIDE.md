# PureFusion IPTV — Plugin Author Guide

This guide is for developers building **external plugins** against the open [PurefusionIPTV-plugin-api](https://github.com/Eliminater74/PurefusionIPTV-plugin-api) repository.

You do **not** have access to the PureFusion IPTV app source. You only need this API + an installed build of the app on a device or emulator.

---

## 1. Requirements

- Android library module (Kotlin), `minSdk` 25+
- JDK 17
- Dependency on **plugin-api** at a [release tag](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases) (e.g. `v1.0.2`)
- PureFusion IPTV **Premium** on device for plugin install (host policy)

---

## 2. Recommended project layout

```text
my-purefusion-plugin/
  plugin-api/              # git submodule → PurefusionIPTV-plugin-api @ v1.0.2
  my-plugin/
    build.gradle.kts
    src/main/
      java/.../MyPlugin.kt
      resources/META-INF/services/dev.eliminater.purefusioniptv.plugin.Plugin
      AndroidManifest.xml
  settings.gradle.kts
```

### settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "my-purefusion-plugin"
include(":my-plugin")
includeBuild("./plugin-api") {
    dependencySubstitution {
        substitute(module("dev.eliminater.purefusioniptv:plugin-api"))
            .using(project(":"))
    }
}
```

### my-plugin/build.gradle.kts

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.myplugin"
    compileSdk = 36
    defaultConfig { minSdk = 25 }
}

dependencies {
    implementation("dev.eliminater.purefusioniptv:plugin-api:1.0.0")
}
```

---

## 3. Choose an interface

Implement `Plugin` and **one or more** capability interfaces:

| Interface | `PluginCapability` | Use for |
|-----------|-------------------|---------|
| `MediaServerProvider` | `MEDIA_SERVER` | Emby / Jellyfin / Plex-style libraries |
| `CloudStorageProvider` | `CLOUD_STORAGE` | Cloud backup targets |
| `SyncProvider` | `SYNC_PROVIDER` | Settings/playlists sync (often paired with cloud) |

Declare capabilities on your plugin:

```kotlin
override val capabilities = listOf(PluginCapability.MEDIA_SERVER)
```

### Plugin identity

| Field | Rules |
|-------|--------|
| `id` | Unique reverse-DNS, e.g. `com.example.myplugin` — **never change** after publish |
| `version` | Semantic version of **your** plugin |
| `minAppVersion` | Lowest PureFusion `major.minor.patch` you support |
| `author` | Your name or org |

---

## 4. Media server plugin checklist

If you implement `MediaServerProvider`:

- [ ] `serverType` — short label shown in UI (e.g. `"Emby"`)
- [ ] `serverIconResourceId` — drawable in **your** plugin module (`R.drawable.ic_…`)
- [ ] `authenticate` / `signOut` / `isAuthenticated`
- [ ] `getMovies()`, `getTVShows()`, `getMediaStreamUrl()` as needed
- [ ] Optional: `createSettingsIntent(context)` for a custom settings `Activity`

When authenticated, the host may show sidebar rows like **“&lt;ServerType&gt; Movies”** in Movies/Shows.

---

## 5. ServiceLoader registration

File:  
`src/main/resources/META-INF/services/dev.eliminater.purefusioniptv.plugin.Plugin`

One fully-qualified class per line:

```text
com.example.myplugin.MyMediaServerPlugin
```

---

## 6. Build & package for install

The host accepts trusted **external plugin packages** (e.g. `.pfp` / runtime dex — see host app documentation). Typical flow:

1. Build your plugin as an Android library → AAR or dex/jar payload.
2. Follow the host’s packaging manifest format (developer docs in app release notes).
3. User: **Settings → Plugins → Install External Plugin** and accepts the disclaimer.

**Development:** debug builds may offer **Debug Plugin Import** for local testing.

### Packaging and signing with the SDK

`PfpPackager.build` writes an unsigned development package (`UNSIGNED_DEV`). Its entry point type is the canonical lowercase `plugin`.

For the Marketplace, sign with `PfpSigner.build(request, DeveloperKey(keyId, privateKey, publicKey), "market_root_v1", outputDir)`, with `trustTier = "verified_partner"` in the request. The signer:

- requires an Ed25519 key pair, verifying that the private and public keys match before writing anything;
- commits `developerKeySha256` (the SHA-256 of the public PEM's Base64 body) in the manifest;
- signs the exact `manifest.json` bytes;
- adds `certs/developer-public-key.pem` and `signatures/developer.sig`.

Ed25519 output is deterministic, so the same inputs and key produce identical bytes. Keep the private key outside your repository and back it up. The Marketplace binds your plugin ID to its fingerprint, so a lost key ends update continuity. The Marketplace's own counter-signature is added only at approval.

---

## 7. Testing without the app source

1. Build your plugin AAR.
2. Install PureFusion IPTV on Android TV device/emulator.
3. Sideload your packaged plugin via Plugin Hub.
4. Enable Premium if required.
5. Verify your integration appears (e.g. media server card, cloud connector).

Use `adb logcat` filtered by your plugin class name and `PluginManager`.

---

## 8. Version upgrades

When the API repo tags **v1.0.2** or higher:

```bash
cd plugin-api
git fetch --tags
git checkout v1.0.2
```

Rebuild your plugin. Read the [release notes](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases) for breaking changes.

---

## 9. What you must not do

- Depend on `dev.eliminater.purefusioniptv` app classes (not public API).
- Ship plugins that access host internals outside `dev.eliminater.purefusioniptv.plugin.*`.
- Assume a security sandbox — plugins are trusted code in the same process.

---

## 10. Get help

- [GitHub Issues](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/issues) — API bugs and feature requests for **this repo only**
- App-specific install errors — contact PureFusion support / app release channel
