# PureFusion IPTV — Plugin API

[![Release](https://img.shields.io/github/v/release/Eliminater74/PurefusionIPTV-plugin-api?label=release)](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases)
[![License](https://img.shields.io/github/license/Eliminater74/PurefusionIPTV-plugin-api)](LICENSE)
[![Release downloads](https://img.shields.io/github/downloads/Eliminater74/PurefusionIPTV-plugin-api/latest/total?label=latest%20release%20downloads)](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases/latest)
[![All releases downloads](https://img.shields.io/github/downloads/Eliminater74/PurefusionIPTV-plugin-api/total?label=all%20release%20downloads)](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3+-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![minSdk](https://img.shields.io/badge/minSdk-25-3DDC84?logo=android)](https://developer.android.com/)

**Open SDK** for building plugins that extend [PureFusion IPTV](https://github.com/Eliminater74) on Android TV.

The **PureFusion IPTV application is closed source**. You do **not** need the app repo to build a plugin — only this repository.

---

## What this repo is

| In this repo | Not in this repo |
|--------------|------------------|
| `Plugin` base interface + `PluginCapability` | PureFusion IPTV app source |
| `MediaServerProvider` (Emby, Jellyfin, Plex, …) | Player, EPG, playlist core |
| `CloudStorageProvider` (Drive, WebDAV, …) | Closed-source UI |
| `SyncProvider` | Marketplace signing keys |

Package: `dev.eliminater.purefusioniptv.plugin`

**Latest stable API:** [v1.0.2](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases/tag/v1.0.2)  
Download counts above come from **GitHub Releases** assets (source zip/tarball and any AARs you attach to releases).

> **Release tag vs. Gradle coordinate.** These are two different numbers and the difference is
> intentional. The **release tag** (`v1.0.2`) identifies the repository revision you check out and
> pin. The **Gradle artifact version** published by this build is `1.0.0`, and that is what you put
> in your dependency line — it tracks the binary compatibility level of the interfaces, which has
> not changed since `v1.0.0`. Tags `v1.0.1` and `v1.0.2` added interfaces and documentation without
> breaking existing plugins, so the artifact version deliberately stayed at `1.0.0`.
> When you compose this repo with `includeBuild`, the substitution rule below resolves the
> dependency from source and the artifact version is not fetched from a repository at all.

---

## Quick start

### 1. Get the API

```bash
git clone https://github.com/Eliminater74/PurefusionIPTV-plugin-api.git
cd PurefusionIPTV-plugin-api
git checkout v1.0.2
```

Or add as a **Git submodule** in your plugin project:

```bash
git submodule add https://github.com/Eliminater74/PurefusionIPTV-plugin-api.git plugin-api
cd plugin-api && git checkout v1.0.2
```

### 2. Depend on it (Gradle)

In your plugin’s `settings.gradle.kts`:

```kotlin
includeBuild("./plugin-api") {
    dependencySubstitution {
        substitute(module("dev.eliminater.purefusioniptv:plugin-api"))
            .using(project(":"))
    }
}
```

In your plugin module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("dev.eliminater.purefusioniptv:plugin-api:1.0.0")
    // + your deps (OkHttp, Gson, etc.)
}
```

### 3. Implement a plugin

```kotlin
class MyPlugin : Plugin, MediaServerProvider {
    override val id = "com.example.myplugin"
    override val name = "My Server"
    // … see docs/PLUGIN_AUTHOR_GUIDE.md
}
```

### 4. Register for discovery

Create `src/main/resources/META-INF/services/dev.eliminater.purefusioniptv.plugin.Plugin`:

```text
com.example.myplugin.MyPlugin
```

### 5. Install on a device

Users install packages from **PureFusion IPTV → Settings → Plugins → Install External Plugin** (Premium).  
Packaging format and trust rules are described in [PLUGIN_AUTHOR_GUIDE.md](docs/PLUGIN_AUTHOR_GUIDE.md).

---

## API surface (this release)

| Type | Purpose |
|------|---------|
| `Plugin` | Identity, lifecycle (`onLoad` / `onUnload`), version compatibility |
| `PluginCapability` | What the plugin offers (e.g. `MEDIA_SERVER`, `CLOUD_STORAGE`) |
| `MediaServerProvider` | Movies, shows, live TV, search, favorites from a media server |
| `CloudStorageProvider` | Upload/download/list for cloud backup |
| `SyncProvider` | Cross-device sync hooks |

More capability interfaces (EPG, catch-up, UI extensions) may be added in future API versions. Pin a **release tag** and watch [Releases](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases) for breaking changes.

---

## Versioning

| Tag | Notes |
|-----|--------|
| [v1.0.0](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases/tag/v1.0.0) | Initial `Plugin` + `MediaServerProvider` |
| [v1.0.1](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases/tag/v1.0.1) | Adds `CloudStorageProvider` + `SyncProvider` |
| [v1.0.2](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases/tag/v1.0.2) | README, author guide, Apache 2.0 license |

Set `minAppVersion` on your plugin to the lowest PureFusion build you support (e.g. `1.0.0-Beta-9.15`).

---

## Build this library locally

Requirements:

| Component | Version |
|-----------|---------|
| Android Gradle Plugin | 9.2.1 |
| Kotlin | 2.3.20 |
| `compileSdk` / `minSdk` | 36 / 25 |
| Java / Kotlin JVM target | 17 |

A consuming build composed with `includeBuild` must use the **same AGP version** — Gradle refuses
to load two different AGP versions in one composite build.

This repository does **not** ship its own Gradle wrapper. Build it with the wrapper from the
project that consumes it, or open it as a standalone Android library project in Android Studio:

```bash
# from a parent/consuming project that has a wrapper
./gradlew -p plugin-api assembleRelease
```

An Android SDK location is required: set `ANDROID_HOME`, or create a git-ignored
`local.properties` here containing `sdk.dir`.

Output: `build/outputs/aar/`.

---

## Documentation

- **[Plugin Author Guide](docs/PLUGIN_AUTHOR_GUIDE.md)** — project layout, interfaces, packaging, testing
- **Releases & downloads:** [github.com/Eliminater74/PurefusionIPTV-plugin-api/releases](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases)

---

## Security notice

Plugins run **in-process** with the host app. PureFusion provides crash containment for trusted packages, but this is **not** a full sandbox. Only install plugins from authors you trust.

---

## License

[Apache License 2.0](LICENSE) — you may use this API in open or closed-source plugins; retain the license notice in distributions as required by Apache 2.0.

---

## Links

- **Issues / feature requests for the API:** [GitHub Issues](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/issues)
- **PureFusion IPTV app:** closed source — plugin installation only via the published app
