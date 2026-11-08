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

## Start Building Your First PureFusionIPTV Plugin

Use the **[official working template](template/README.md)** for current isolated API 3 screens/brokers (Android API 26+). It includes its own Gradle wrapper, tests, external developer-key tools, signed single-DEX packaging, listing v1, original artwork and a local card preview.

[Getting Started](template/GETTING_STARTED.md) · [Developer Guide](template/DEVELOPER_GUIDE.md) · [Listing Guide](template/marketplace/LISTING_GUIDE.md) · [Signing](template/docs/SIGNING.md) · [Submission](template/docs/SUBMISSION.md) · [API protocol](docs/SANDBOX_SURFACE_PROTOCOL.md)

Provider examples below describe a different compatibility lane. Local signing is not Marketplace approval. Public third-party executable distribution and website credential delivery remain gated. The template guides supersede historical runtime/signing statements below for new isolated screen plugins.

## What this repo is

| In this repo | Not in this repo |
|--------------|------------------|
| `Plugin` base interface + `PluginCapability` | PureFusion IPTV app source |
| `PluginApi` level + compatibility rule | Package validation / install pipeline |
| `PfpPackager` — builds `.pfp` packages | Loader, lifecycle orchestration, signing keys |
| `PfpSigner` and `template/` — developer signing and starter workflow | Marketplace approval and distribution authority |
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
| `SyncPayload` | Serialised record carried by `SyncConflict` (new at API level 2) |
| `PluginUiRequest` / `PluginUiResult` | How a plugin asks the host to show sign-in or settings (new at API level 3) |

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

### Level 2 — breaking change to `SyncProvider`

`MIN_SUPPORTED` is now **2**. Plugins built against level 1 are refused before their code loads,
and the refusal message tells the user the plugin needs updating.

Two published members changed, and both had to:

| Was (level 1) | Is (level 2) | Why |
|---------------|--------------|-----|
| `SyncConflict.localData: Any`, `remoteData: Any` | `SyncPayload` | `Any` has no serialisation, so these could never cross a process boundary — and the host could not display, compare or store a conflict either, because it had no idea what it was holding |
| `SyncState.Error(message, exception: Throwable?)` | `SyncState.Error(message, errorCode: String?)` | A `Throwable`'s class lives in *your* classloader, so the host has nothing to deserialise it into; its stack trace also routinely carries file paths and request URLs into host logs |

`SyncPayload` is an envelope, not a schema — `contentType`, `content`, and an optional `revision`.
The host never parses `content`; it only needs enough to move the value, show the user which side
is which, and hand the chosen one back to `resolveConflict`. Serialise your record however suits
your backing service.

```kotlin
SyncConflict(
    id = remoteRecord.id,
    dataType = SyncDataType.PLAYLISTS,
    localData = SyncPayload("application/json", localJson, revision = localEtag),
    remoteData = SyncPayload("application/json", remoteJson),
    localTimestamp = localMillis,
    remoteTimestamp = remoteMillis,
)
```

**If you only implement `MediaServerProvider` or `CloudStorageProvider`**, nothing in your code
changes — but you still need to rebuild with `apiLevel = PluginApi.CURRENT`, because the level is
declared per plugin rather than per interface.

### Level 3 — no more `Context` or `Intent`

`MIN_SUPPORTED` is now **3**. Five members changed, and `android.content.Context` and
`android.content.Intent` are gone from the public surface entirely.

| Was (level 2) | Is (level 3) |
|---------------|--------------|
| `authenticate(context: Context): Intent?` | `authenticate(): PluginUiRequest?` |
| `handleAuthResult(resultCode: Int, data: Intent?)` | `handleAuthResult(result: PluginUiResult)` |
| `createSettingsIntent(context: Context): Intent?` | `settingsRequest(): PluginUiRequest?` |
| `iconResourceId: Int` | `iconAssetPath: String?` |
| `serverIconResourceId: Int` | `serverIconAssetPath: String?` |

**Why.** Three separate problems, none of which have a workaround:

1. **There is no `Context` to give you.** A worker process has its own, not the host's, and handing
   over the host's would put a handle to its identity, files and components inside plugin code —
   the thing process isolation exists to prevent.
2. **A worker has no UI task.** It is a `Service` in a process with no `Activity` back stack, so
   `startActivity` from there either fails or launches into a task that behaves nothing like the
   rest of the app.
3. **An arbitrary `Intent` is an arbitrary privilege.** A plugin that can hand the host any
   `Intent` and have it launched can target the host's internal Activities, aim at other apps'
   exported components, or carry a `file://` URI into something that opens it.

A resource id had a fourth problem: it indexes *your* APK's resource table, which the host has not
loaded, so the integer crossed perfectly and meant nothing on arrival.

#### Migrating an auth flow

Describe an OAuth 2.0 authorization-code flow and let the host run it:

```kotlin
override suspend fun authenticate(): PluginUiRequest? {
    if (isSignedIn()) return null                       // null means "nothing to do"
    return PluginUiRequest(
        kind = PluginUiKind.AUTHENTICATION,
        url = "https://accounts.example.com/o/oauth2/v2/auth",
        redirectScheme = "com.example.myplugin",        // the host watches for this
        title = "Sign in to Example",
        parameters = mapOf(
            "client_id" to CLIENT_ID,
            "response_type" to "code",
            "state" to newState(),                      // keep it; verify it below
        ),
    )
}

override suspend fun handleAuthResult(result: PluginUiResult): Boolean {
    if (result.outcome != PluginUiOutcome.COMPLETED) return false
    if (result.values["state"] != expectedState) return false   // the host cannot check this
    val code = result.values["code"] ?: return false
    return exchangeCodeForToken(code)
}
```

`handleAuthResult` is called **exactly once** per request, including for outcomes you did not
cause — `CANCELLED`, `DENIED`, `EXPIRED`. Treat anything but `COMPLETED` as "not signed in". You
will never be left waiting.

**What the host checks before showing anything:** `https` only; no embedded credentials; a
redirect scheme you own (not `http`/`https`); bounded parameters; the user is Premium; and your
package manifest declares `CLOUD_STORAGE_ACCESS` or `MEDIA_SERVER_ACCESS`. A settings request
needs `UI_SETTINGS_EXTENSION`.

**What no longer works:** an SDK's own sign-in `Activity`. That has to run in a process with the
SDK loaded — which is the worker, the one process that cannot show UI. Move to a URL-based flow.

#### Migrating settings and icons

```kotlin
override fun settingsRequest(): PluginUiRequest? = PluginUiRequest(
    kind = PluginUiKind.SETTINGS,
    url = "https://example.com/app-settings",
    title = "Example settings",
)

override val serverIconAssetPath: String? = "assets/icon.png"   // path inside your .pfp
```

The icon path is resolved by the host against your installed package, so it works while your
plugin is **not running** — which is what the Plugin Hub needs to draw a list. PNG, JPEG or WebP,
under 512 KB, no `..` segments.

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
> Developer signing now uses your Ed25519 key through `PfpSigner`; see the
> [template signing workflow](template/docs/SIGNING.md). Unsigned development packages do not
> qualify for the isolated Marketplace lane.

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

> **Historical provider limitation:** the paragraph below describes the earlier provider path. New isolated screen plugins use the [template](template/README.md). Legacy code used `DexClassLoader`.
> The host applies a namespace allow-list, a crash-counting circuit breaker, and exception
> containment at the call boundary — that is fault *handling*, not a security sandbox. A plugin
> that blocks the main thread, exhausts memory, or crashes a thread the host does not own can still
> take the app down. Treat plugin code as trusted, and do not rely on the host to contain it.
> The out-of-process runtime now exists in the host but does not yet carry the provider APIs —
> see **Process isolation** below.

---

## Process isolation

The host has a second way to run a plugin: a **worker process**, declared with `android:process`,
hosting exactly one plugin. It exists and is exercised, but it is **not yet the default** — so
`MediaServerProvider`, `CloudStorageProvider` and `SyncProvider` still load in-process today.
Nothing below changes how your plugin is loaded right now.

**You do not implement anything to support this.** You write the same `Plugin`,
`CloudStorageProvider`, `MediaServerProvider` or `SyncProvider` you always have. When the host runs
you in a worker it holds a proxy that implements the same interface and forwards each call across
the boundary; inside the worker an adapter turns it back into an ordinary call on your object.
Suspending functions stay suspending, `Flow`s stay `Flow`s, progress callbacks keep firing, and
streams keep streaming. There is no AIDL in the developer API, and there never will be — Binder is
the host's transport, not your contract.

Write your plugin as if it will move, because it will:

| Assumption | Holds in-process | Holds in a worker |
|------------|------------------|-------------------|
| `onLoad(context)` gives you a usable `Context` | yes | yes — but it is the **worker's** context, not the host's |
| Static state is shared with the host app | yes | **no** — a different process has its own statics |
| Static state is shared with other plugins | yes | **no** — one plugin per process |
| A `Toast`, dialog or Activity launch behaves normally | yes | **no** — a worker has no UI task; ask the host |
| `onUnload` runs to completion before teardown | yes | **bounded** — see below |
| An uncaught exception on your own thread kills the app | yes | **no** — it kills your worker only |

**`onUnload` gets a deadline.** In a worker the host allows roughly half a second for `onUnload` to
return, then unbinds and, if the process has not exited, terminates it. This is what makes unload
*real* — the JVM cannot reclaim a classloader that anything still references, so process
termination is the only mechanism that actually frees what your plugin held. Do not put a network
round trip in `onUnload`; flush what must be flushed and return.

**Crashing your worker is survivable, and counted.** Three worker deaths within five minutes
quarantines the plugin until the user clears it. The host does not restart you in a loop.

**Honour cancellation.** This is the one thing the transport genuinely needs from you. When a
screen closes or a user navigates away, the host cancels the coroutine that called you and tells
the worker; the worker cancels the coroutine your method is running in. If your code is a plain
blocking loop with no suspension point, nothing can interrupt it and your work runs to completion
with nobody waiting for the result. In practice: use suspending I/O, check `isActive` in long
loops, and let `CancellationException` propagate rather than swallowing it in a broad `catch`.

**Do not rely on object identity across calls.** The host may hold a proxy rather than your
instance, so an object you return from one call is not guaranteed to come back as the same object
later, and `==` on something the host handed you compares a copy. Identify things by id, the way
you would across any network boundary.

**A few shapes cost more across a boundary than in-process.** A `Flow` is delivered in batches
under a credit window, so a very chatty flow is throttled by design rather than buffered without
limit. A progress callback is rate-limited to roughly twenty updates a second — reporting every
4 KB of a large upload is fine, it simply will not all be delivered, and the final 1.0 always is.
Bulk bytes go through a pipe, never a Binder transaction, so streaming a large file is efficient
while returning one as a `ByteArray` would not be.

**Resource ids do not travel.** `iconResourceId` and `serverIconResourceId` index *your* APK's
resources, which the host process has not loaded. A host running you in a worker reads them as "no
icon" until host-side resource loading exists.

**Non-suspending members are read once, at start.** `supportsFeature`, `getProviderCapabilities`
and the identity properties cannot make a call across the boundary — they are not `suspend`, so a
remote call inside them would block whatever thread asked, possibly the main one. The host reads
them during startup and holds the answer. Make them cheap and stable: they describe what your
plugin *is*, not what it is currently doing.

---

## Plugins require Premium

Plugins are a Premium feature, enforced at runtime rather than only in the UI. Lifetime purchases,
an active subscription, and an active Premium trial all count.

What that means in practice:

- on a free tier, no plugin is enabled, loaded, started, bound to a process, or handed out as a
  provider — the host behaves as though none were installed;
- if entitlement lapses **while your plugin is running**, it is unloaded promptly: in-flight calls
  are cancelled, flow subscriptions end, streams close, and a worker process is terminated. Your
  `onUnload` runs first, within the usual deadline;
- when the user becomes entitled again your plugin is eligible immediately, and its next load gets
  a fresh process and generation. It is not resumed — the previous one is genuinely gone;
- **an entitlement refusal is never recorded against your plugin.** It does not count as a crash
  and cannot contribute to quarantine, so a user who lapses and later re-subscribes does not find
  your plugin blocked.

Nothing is required of you for any of this. There is no entitlement API to call and no state to
handle; treat an unload caused by a lapse exactly like any other unload.

**What isolation this is.** A worker is a separate process at the **same UID**, so it has the app's
permissions and can read the app's private files. That is fault isolation and real resource
reclamation — not a privilege boundary. `android:isolatedProcess` would give the latter, but an
isolated process has no permissions at all, including no network, which no cloud or media-server
plugin could use. The honest summary: the host can now contain your crash and truly unload you; it
still cannot contain what you choose to do with the permissions it has.

**What does not cross yet.** Three members need the host to act on the plugin's behalf and are not
available in a worker: `CloudStorageProvider.authenticate`, `CloudStorageProvider.handleAuthResult`
and `MediaServerProvider.createSettingsIntent`. All three take or return a `Context` or an
`Intent`, which means launching UI — and a worker has no UI task of its own. They fail with a clear
"unsupported" error rather than silently returning null, because a null from `authenticate` means
"already signed in" and reporting that for a sign-in that never happened would be worse than
failing.

Four more are blocked by the API rather than by the transport: `SyncProvider.sync`, `syncAll`,
`resolveConflict` and `getPendingConflicts`. They carry `SyncConflict`, whose `localData` and
`remoteData` are declared `Any` — and there is no way to serialise `Any`. Giving those fields a
declared type is an API change, and it is on the roadmap rather than improvised inside a codec.
`SyncState.Error` likewise crosses with its message but without its `Throwable`: a plugin's
exception class does not exist in the host's classloader, so it could not be reconstructed there
even if it were sent.

---

## Versioning

| Tag | Notes |
|-----|--------|
| [v1.0.0](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases/tag/v1.0.0) | Initial `Plugin` + `MediaServerProvider` |
| [v1.0.1](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases/tag/v1.0.1) | Adds `CloudStorageProvider` + `SyncProvider` |
| [v1.0.2](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/releases/tag/v1.0.2) | README, author guide, Apache 2.0 license |
| _unreleased_ | **API level 2.** Adds `PluginApi` level + `Plugin.apiLevel`; repairs the `SyncProvider` schema (`SyncPayload`, `errorCode`); documents `.pfp`, identity, lifecycle, [process isolation](#process-isolation) and Premium gating |

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
| Out-of-process isolation | **Runtime and transport implemented, not yet the default.** Worker process, unload sequence, and remote proxying for calls, flows, streams, progress and `StateFlow`. All three providers are proxied end to end. See [Process isolation](#process-isolation) |
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

The SDK root uses a consuming project's wrapper; the [public template](template/) now includes
one. Build from the SDK root with `template/gradlew -p . test` (Windows:
`template\gradlew.bat -p . test`), or use another consuming wrapper / Android Studio:

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

API 3 also has an additive isolated subtitle contract, `SandboxSubtitlePluginV1`, selected by the
signed manifest category `subtitle_provider`. Implement it alongside `Plugin` and `SandboxPluginV1`
with an empty legacy `Plugin.capabilities` list. Its separate `pf.subtitle.v1` channel accepts only
`describe`, `search`, and `download`; it does not use the host-rendered screen protocol.
`SandboxSubtitleMediaV1`, `SandboxSubtitleRequestV1`, `SandboxSubtitleResultV1`, and
`SandboxSubtitleResponseV1` document the JSON field names. `contentType` is `MOVIE` or `EPISODE`.
The host accepts ISO-like language tags, opaque bounded result IDs, at most 100 results, and only
SRT/VTT file bytes delivered as base64. All rendering and attachment stay in the trusted host.

Requests are limited to 16 KiB, JSON responses to 2 MiB, decoded subtitle data to 512 KiB, and a
call to 30 seconds. Both directions also have a 384 KiB compressed Binder limit; data that does
not fit is refused even when below the decoded limit. Cancellation/timeout closes that worker
tenancy and its brokers; lifecycle activation can start a fresh worker for a later request.
The host rechecks signed identity, generation, account, enablement, entitlement and trust before
and after calls. Provider messages are never trusted user-facing errors. Credential fields,
filesystem paths, arbitrary JSON fields, and legacy `SUBTITLE_ENGINE` privileges are unsupported.
Current credential-dependent providers remain gated until a reviewed credential broker exists.

Current verified Marketplace DEX plugins run in Android isolated UIDs on API 26+, with narrow brokers, host-rendered descriptors, TV-owned consent and signed admission/revocation. See [capability boundaries](template/docs/CAPABILITIES.md) and [surface protocol](docs/SANDBOX_SURFACE_PROTOCOL.md). Declarations request access; they cannot grant consent or publisher authority. No API-25 same-UID fallback exists.

Legacy provider contracts and the exact pinned first-party Emby compatibility release have different constraints. They do not grant third-party plugins app-private data, Android services or arbitrary network destinations. Use the template for new isolated screens; do not copy first-party exceptions. Public distribution remains independently gated.

---

## License

[Apache License 2.0](LICENSE) — you may use this API in open or closed-source plugins; retain the license notice in distributions as required by Apache 2.0.

---

## Links

- **Issues / feature requests for the API:** [GitHub Issues](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/issues)
- **PureFusion IPTV app:** closed source — plugin installation only via the published app
