# UI Extensions

Implement `SandboxSurfacePluginV1.handleSurface(request: ByteArray): ByteArray?` alongside `Plugin` and `SandboxPluginV1`. Request `UI_HOME_EXTENSION` in the signed manifest. Exchange bounded UTF-8 JSON documents using [`pf.surface.v1`](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/docs/SANDBOX_SURFACE_PROTOCOL.md).

The host sends open, refresh, page, section, activate, Back and dialog events. Return descriptors for sections, rows, stable card IDs, focus hints, state and dialogs. Host components handle D-pad focus, input and rendering. To let Core apply normal Back navigation, return `handled: false`. Keep Views, Activities, Intents, HTML and executable UI out of this protocol.

With `EPG_READ` and TV consent, the capability object may implement `SandboxLiveCatalogV1`. Query it during a surface call. Cards can request host-owned Play, Guide, Remind, Favorite or Multi-View. Core revalidates channel scope, entitlement and the airing before acting. In-screen forms can store non-secret preferences through scoped storage.

The current host launches the first-party Sports Hub surface through its reserved slot. Marketplace approval by itself creates no generic launch action. Sports Hub's legacy migration/shared team settings are first-party integration details. The template demonstrates in-screen settings. Generic Plugin Hub or website configuration needs a reviewed host adapter, and API 3 exposes no generic remote apply method.

[Wiki Home](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/Home)
