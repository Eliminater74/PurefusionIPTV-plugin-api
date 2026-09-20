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
| `PluginApi` level + compatibility rule | Package validation / install pipeline |
| `PfpPackager` — builds `.pfp` packages | Loader, lifecycle orchestration, signing keys |
| `MediaServerProvider` (Emby, Jellyfin, Plex, …) | Player, EPG, playlist core |
| `CloudStorageProvider` (Drive, WebDAV, …) | Closed-source UI |
| `SyncProvider` | Marketplace signing keys |

Package: `dev.eliminater.purefusioniptv.plugin.api`

> **Moved in Phase 2.** Public contracts were previously in `dev.eliminater.purefusioniptv.plugin`,
> which the closed-source host also used for its own loader and installer. They now live in a
> dedicated `…​.plugin.api` package so the host can grant plugins access to the API namespace as a
> unit instead of maintaining a list of individual class names. Update your imports:
>
> ```diff
> - import dev.eliminater.purefusioniptv.plugin.Plugin
> + import dev.eliminater.purefusioniptv.plugin.api.Plugin
> ```
>
> This is a breaking change, made deliberately **before** the first public release. No
> compatibility aliases were added — a never-published API does not deserve permanent deprecated
> bridges in its public surface.

### Repository naming

A standalone plugin lives in its own repository named:

```
PureFusionIPTV-Plugin-NameOfPlugin
```

for example `PureFusionIPTV-Plugin-googledrive`, `PureFusionIPTV-Plugin-emby`. The repository name
is a convention for humans — it is **not** your plugin's identity. See
[Plugin identity](#plugin-identity).

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
import dev.eliminater.purefusioniptv.plugin.api.MediaServerProvider
import dev.eliminater.purefusioniptv.plugin.api.Plugin
import dev.eliminater.purefusioniptv.plugin.api.PluginApi

class MyPlugin : Plugin, MediaServerProvider {
    override val id = "com.example.myplugin"          // stable identity, never changes
    override val name = "My Server"                   // display only, free to change
    override val apiLevel = PluginApi.CURRENT         // required — see Plugin API level
    // … see docs/PLUGIN_AUTHOR_GUIDE.md
}
```

### 4. Register for discovery

Create `src/main/resources/META-INF/services/dev.eliminater.purefusioniptv.plugin.api.Plugin`:

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
| `Plugin` | Identity, lifecycle (`onLoad` / `onUnload`), declared API level |
| `PluginCapability` | What the plugin offers (e.g. `MEDIA_SERVER`, `CLOUD_STORAGE`) |
| `PluginApi` | The API level this module publishes, and the range the host accepts |
| `PluginApiCompatibilityCheck` | The rule the host applies **before** loading plugin code |
| `MediaServerProvider` | Movies, shows, live TV, search, favorites from a media server |
| `CloudStorageProvider` | Upload/download/list for cloud backup |
| `SyncProvider` | Cross-device sync hooks |

More capability interfaces (EPG, catch-up, UI extensions) may be added in future API versions. Pin a **release tag** and watch [Releases](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases) for breaking changes.

---

## Plugin API level

`minAppVersion` is a **product** floor — "I need a build with this feature". It is *not* the
compatibility contract for these interfaces, because the app version moves for reasons that have
nothing to do with the API.

`PluginApi.CURRENT` is the contract. It is a monotonic integer that changes **only** when this
module's public surface changes, and it is what the host checks before it loads your code.

```kotlin
override val apiLevel: Int = PluginApi.CURRENT   // captured at build time
```

| Rule | Effect |
|------|--------|
| New interface, new capability constant, or new method **with a default** | `CURRENT` rises, `MIN_SUPPORTED` does not — existing plugins keep working |
| A published member is removed or its signature changes | `MIN_SUPPORTED` rises — plugins below it are refused |
| Plugin declares a level **newer** than the host provides | Refused; the *host* needs updating |
| Plugin declares **no** level | Refused. The host will not guess one |

Refusal happens before any plugin class is loaded, and the reason is surfaced to the user via
`PluginApiCompatibilityCheck.explain(...)`, which always names the side that needs updating.

> **If you wrote a plugin before `apiLevel` existed:** `Plugin.apiLevel` defaults to
> `PluginApi.LEVEL_UNSPECIFIED`, so your plugin still *compiles* but will not be loaded. Add the
> one-line override above.

---

## Plugin identity

Your plugin's canonical identity is `Plugin.id` — a reverse-DNS string such as
`dev.example.myplugin`.

The host keys installed state, enable/disable, update and fault records on this value. It must
stay stable for the life of the plugin. **Never** derive identity from the display name, the
package filename, the repository name, or a position in a list — all of those can change without
the plugin becoming a different plugin, and changing the id makes it one.

Renaming what the user sees is free: change `name`, keep `id`.

---

## Packaging — `.pfp`

PureFusionIPTV plugin packages use the extension **`.pfp`** (`application/vnd.purefusioniptv.plugin`).
A package is a container holding a `manifest.json` plus the payload.

The manifest is validated — schema version, plugin id, host version range, payload hashes,
signing metadata — **before** any executable payload is read. A package that fails validation is
rejected without its code being loaded.

### Building a `.pfp`

`PfpPackager` ships in this SDK. It uses the JDK only — no Android, no PureFusionIPTV core — so a
standalone plugin repository can produce a package from a clean checkout of itself plus this
library.

```kotlin
import dev.eliminater.purefusioniptv.plugin.api.PluginApi
import dev.eliminater.purefusioniptv.plugin.packaging.PfpPackager

val pkg = PfpPackager.build(
    PfpPackager.Request(
        pluginId = "dev.example.myplugin",          // reverse-DNS, stable forever
        displayName = "My Plugin",
        developerId = "dev.example",
        version = "1.0.0",
        versionCode = 1,
        apiLevel = PluginApi.CURRENT,               // required
        minHostVersion = "2.0.0",
        minSdk = 25,
        entryPointClassName = "dev.example.myplugin.MyPlugin",
        payload = listOf(
            PfpPackager.PayloadSource("classes.dex", dexBytes),
        ),
    ),
    outputDir = layout.buildDirectory.dir("pfp").get().asFile,
)
```

Output is `<pluginId>-<version>.pfp`, with this layout:

| Entry | Contents |
|-------|----------|
| `manifest.json` | schema version, identity, `apiLevel`, host bounds, entry point, payload hashes |
| `payload/…` | your executable artifacts |
| `certs/developer-public-key.pem` | signing only — not produced by this tool |
| `signatures/developer.sig` | signing only — not produced by this tool |

The packager fails the build — before writing anything — on an invalid plugin id, a missing
`apiLevel`, an empty payload, a missing entry point, a path that escapes `payload/`, duplicate
entries (including case-only duplicates), and on any attempt to bundle `plugin-api` bytecode.
Output is byte-reproducible for identical inputs.

> **Status:** package *validation* and the install pipeline live in the closed-source host.
> `PfpPackager` produces **unsigned development packages**: SHA-256 payload hashes give
> **integrity**, but not **identity** or **trust** (see [Security notice](#security-notice)).
> Signing requires a real developer key and no developer-facing signing workflow exists yet, so
> none is invented here. Unsigned packages install where the host permits sideload and are refused
> where signatures are required.

---

## Lifecycle, enable/disable and faults

The host owns lifecycle. Your plugin observes it through `onLoad` / `onUnload`.

| State | Meaning |
|-------|---------|
| `INSTALLED` | Package verified; code has never been loaded |
| `ENABLED` | User wants it on; not currently loaded |
| `LOADED` | Code loaded, receiving calls |
| `DISABLED` | User turned it off; will not be loaded |
| `INCOMPATIBLE` | API level outside the supported range — not a fault |
| `FAULTED` | Failed at load or during a call; recoverable |
| `QUARANTINED` | Failed repeatedly; only an explicit user action clears it |

**`onUnload` is not optional.** A user may disable your plugin at any time, and the host does not
restart the app to do it. When `onUnload` returns, your plugin must have released everything it
registered: callbacks, observers, coroutines and jobs, scheduled work, caches, connections, and any
host extension points it registered. A plugin that keeps working through a retained host reference
after `onUnload` is a bug in the plugin.

A plugin that throws repeatedly is quarantined rather than retried forever — PureFusionIPTV will
not enter a crash loop on your behalf, and it will still start and run without you.

> **Honest limitation:** plugins currently execute **in the host process** via `DexClassLoader`.
> The host applies a namespace allow-list, a crash-counting circuit breaker, and exception
> containment at the call boundary — that is fault *handling*, not a security sandbox. A plugin
> that blocks the main thread, exhausts memory, or crashes a thread the host does not own can still
> take the app down. Treat plugin code as trusted, and do not rely on the host to contain it.
> Out-of-process isolation is planned; see the note in **Security notice**.

---

## Versioning

| Tag | Notes |
|-----|--------|
| [v1.0.0](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases/tag/v1.0.0) | Initial `Plugin` + `MediaServerProvider` |
| [v1.0.1](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases/tag/v1.0.1) | Adds `CloudStorageProvider` + `SyncProvider` |
| [v1.0.2](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases/tag/v1.0.2) | README, author guide, Apache 2.0 license |
| _unreleased_ | Adds `PluginApi` level + `Plugin.apiLevel`; documents `.pfp`, identity and lifecycle |

Set `minAppVersion` to the lowest PureFusion build you support (e.g. `1.0.0-Beta-9.15`), and set
`apiLevel` to `PluginApi.CURRENT`. They answer different questions — see
[Plugin API level](#plugin-api-level).

### What is stable vs still evolving

| Area | Status |
|------|--------|
| `Plugin`, `PluginCapability` | **Stable** — in use by shipped plugins |
| `MediaServerProvider`, `CloudStorageProvider`, `SyncProvider` | **Stable** shape; new members may be added with defaults |
| `PluginApi` / `apiLevel` | **New.** The rule is implemented and enforced by the host |
| `.pfp` packaging + manifest schema | **Implemented in the host**, tooling not yet published |
| Lifecycle state machine | **Implemented in the host**; no plugin-facing API yet beyond `onLoad`/`onUnload` |
| Out-of-process isolation | **Not implemented.** Plugins run in the host process today |
| Third-party signing / trust tiers | **Format reserved, not enforced** for third-party keys |

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

Plugins run **in-process** with the host app, loaded via `DexClassLoader`. Only install plugins from
authors you trust.

What the host **does** provide today:

- package validation (manifest schema, plugin id, host version range, payload hashes) before any
  executable payload is read;
- a namespace allow-list restricting which host classes plugin code may link against;
- exception containment at the plugin call boundary, including across `suspend` resumption;
- a crash-counting circuit breaker that quarantines a repeatedly failing plugin instead of
  retrying it forever.

What it **does not** provide:

- process, memory or thread isolation;
- protection from a plugin that blocks the main thread, exhausts memory, crashes a thread the host
  does not own, or calls `System.exit()`;
- enforced trust separation for third-party signing keys.

These are three different properties and the host currently delivers only the first two:
**integrity** (the bytes are what the manifest says), **identity** (which key signed them), and
**trust** (what that key is permitted to do). Trust tiers exist in the manifest format but are not
enforced for third-party keys.

Out-of-process isolation — hosting plugin code in a separate, optionally isolated process behind a
Binder contract — is the intended direction. It is **not implemented**, and nothing in this README
should be read as claiming plugin code is sandboxed.

---

## License

[Apache License 2.0](LICENSE) — you may use this API in open or closed-source plugins; retain the license notice in distributions as required by Apache 2.0.

---

## Links

- **Issues / feature requests for the API:** [GitHub Issues](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/issues)
- **PureFusion IPTV app:** closed source — plugin installation only via the published app
