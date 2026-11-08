# Developer guide

`manifest.json` is the build identity input. Gradle emits matching BuildConfig; the JDK packager emits the final schema-1 manifest with payload hashes, lower-case `plugin` entrypoint, signing block and permission flags. `category=ui_extension`, API 3 and minSdk 26 match the current experimental isolated lane. `Plugin.capabilities` contains provider claims (empty here); signed manifest capabilities request broker access.

The entrypoint implements `Plugin`, `SandboxPluginV1` and `SandboxSurfacePluginV1`, with a public no-argument constructor. Metadata comes from BuildConfig. Service registration supports discovery tests; production uses the manifest entrypoint. `onSandboxLoad` initializes without Context. `onUnload` cancels HTTP and drops the broker. There are no background executors. Worker state is disposable; one bounded scoped JSON record restores preferences/counter after restart.

Android supplies `org.json`; JVM tests use the public org.json artifact. Release DEX bundles Kotlin stdlib to avoid depending on a minified host's private Kotlin names. API classes are compileOnly. The unminified APK is only an intermediate; the public SDK packager/signer packages one extracted DEX.

## TV interaction

The current host UI constructs only the first-party Sports Hub screen slot. Generic third-party screen discovery/launch is not exposed by the SDK or Plugin Hub today. The descriptors below are tested locally; running this arbitrary plugin ID on a TV additionally requires an authorized host slot/launch integration. Marketplace approval alone does not add that route. Do not copy Sports Hub's ID or first-party migration fields.

`handleSurface` accepts bounded UTF-8 `pf.surface.v1` and returns JSON. Host-owned components render title, text, action cards, section, loading/empty states, errors and preference form. Stable card IDs support D-pad focus; this example focuses `increment` after updates. Back cancels HTTP and returns `handled=false` for normal host navigation. Host dialogs own keyboard/focus. Never return Android Views, Intents, HTML or executable UI. See [surface protocol](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/docs/SANDBOX_SURFACE_PROTOCOL.md).

## Preferences and capabilities

[Settings](docs/SETTINGS.md) explains the working form and website limitation. No generic Settings button is advertised. Steps are integers 1–10; greetings are trimmed plain text 1–80 characters. Writes change visible state only after persistence succeeds. Invalid stored JSON shows defaults with an error; an explicit save replaces it.

[Capabilities](docs/CAPABILITIES.md) covers consent, denial, storage pacing, optional HTTP and cancellation. Missing values, denied consent and busy storage all can return null; the SDK cannot distinguish them. Failed writes remain visibly unsaved. Revocation can terminate the worker without cleanup; persistent records must be independently valid.

## Updates and acceptance

Keep plugin/developer ID and signing key stable. Increase versionCode beyond the published maximum, change version, provide changelog, rebuild/re-sign and regenerate listing. To check a known previous code: `node tools/marketplace.cjs validate marketplace/listing.local.json packaging/build/signed/manifest.json 1`. Portal transactions authoritatively enforce namespace ownership, signer continuity and uniqueness; offline validation cannot claim an ID.

Run both JVM suites and `node --test tools/test/workflow.test.cjs`. They exercise lifecycle, persistence, denial, malformed requests, preferences, HTTP completion/cancellation, signing and listing failures. A real isolated device run needs genuine Marketplace approval/countersignature and signed admission. Do not fabricate trust grants, entitlements, registry or user data. Device D-pad/Back/consent/revocation acceptance is separate from JVM success. API 25 has no fallback.
