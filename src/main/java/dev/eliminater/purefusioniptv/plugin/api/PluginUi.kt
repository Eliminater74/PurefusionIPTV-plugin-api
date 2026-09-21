package dev.eliminater.purefusioniptv.plugin.api

/**
 * How a plugin asks PureFusionIPTV to show something to the user.
 *
 * ## Why a plugin cannot just build an `Intent`
 *
 * Until plugin API level 3, `authenticate` took a `Context` and returned an `Intent`, and
 * `createSettingsIntent` did the same. That worked while plugins ran inside the host process and
 * stopped working the moment they did not.
 *
 * An `Intent` is not the problem on its own — it is `Parcelable` and could technically cross a
 * Binder boundary. Three other things are:
 *
 * 1. **There is no `Context` to give you.** A worker process has its own `Context`, not the
 *    host's, and handing over the host's would put a handle to the host's identity, files and
 *    component registry inside plugin code — the exact thing process isolation exists to prevent.
 * 2. **A worker has no UI task.** It is a `Service` in a process with no `Activity` back stack, so
 *    `startActivity` from there either fails or launches into a new task that behaves nothing like
 *    the rest of the app.
 * 3. **An arbitrary `Intent` is an arbitrary privilege.** A plugin that can hand the host any
 *    `Intent` and have it launched can target the host's own internal Activities, aim at exported
 *    components of other apps, or carry a `file://` URI into something that will open it.
 *
 * So the plugin describes *what it wants shown*, the host decides *whether and how to show it*,
 * and the host owns every part of the launch. That is what this type is.
 *
 * ## What this can express
 *
 * A URL to open, and — for sign-in — the redirect the host should watch for. That is deliberately
 * the shape of a standard OAuth 2.0 authorization-code flow, because it is the one auth model that
 * works identically whether the plugin is in-process, in a worker, or someday somewhere else
 * entirely.
 *
 * It cannot express "launch this SDK's own sign-in Activity", and that is not an oversight. An
 * SDK Activity has to run in a process that has the SDK loaded, which is the worker — the one
 * process that cannot show UI. A plugin needing that must move to a URL-based flow.
 */
data class PluginUiRequest(
    /** What the host is being asked to show. Determines which capability authorises it. */
    val kind: PluginUiKind,

    /**
     * Absolute `https://` URL to open.
     *
     * `https` only, enforced by the host. `file://`, `content://`, `intent://` and custom schemes
     * are refused: the first two would let a plugin point host UI at arbitrary local data, and the
     * last is a way to smuggle an `Intent` back in through a string.
     */
    val url: String,

    /**
     * For [PluginUiKind.AUTHENTICATION]: the redirect scheme that ends the flow.
     *
     * The host watches for a redirect to this scheme, closes the browser, and returns the
     * redirect's query parameters as [PluginUiResult.values]. Must be a scheme the plugin owns —
     * conventionally its plugin id reversed, e.g. `com.example.myplugin`.
     *
     * Null for [PluginUiKind.SETTINGS], where there is nothing to wait for.
     */
    val redirectScheme: String? = null,

    /** Shown in the host's own chrome around the page. Not the page's own title. */
    val title: String? = null,

    /**
     * Extra query parameters appended to [url].
     *
     * Bounded in count and total size by the host. Values are query parameters, not a general
     * channel: anything secret belongs in the OAuth flow itself, not here, because a URL is
     * logged, cached and shown in a browser's address bar.
     */
    val parameters: Map<String, String> = emptyMap(),

    /**
     * For [PluginUiKind.DEVICE_AUTHORIZATION]: the short code the user types on their phone.
     *
     * Displayed by the host next to [url]. It is a one-time, user-facing pairing code with a few
     * minutes of life and no value on its own — it authorises nothing without the user also
     * signing in at the verification URL — so unlike a token it is safe to put on a screen. It is
     * still not logged, because it is an authorisation artefact and the habit is worth keeping.
     *
     * Null for every other kind, and required for this one.
     */
    val userCode: String? = null,
)

/**
 * What kind of UI a request is for.
 *
 * Kept small and closed. Each value maps to a host capability the plugin must have declared in its
 * manifest, so adding one here is also deciding who is allowed to ask for it.
 */
enum class PluginUiKind {
    /**
     * Sign in to the plugin's backing service.
     *
     * Requires `CLOUD_STORAGE_ACCESS` or `MEDIA_SERVER_ACCESS` in the manifest. Ends when the host
     * sees [PluginUiRequest.redirectScheme].
     */
    AUTHENTICATION,

    /**
     * Configure the plugin.
     *
     * Requires `UI_SETTINGS_EXTENSION`. Ends when the user leaves; there is no result beyond
     * whether they got there.
     */
    SETTINGS,

    /**
     * Sign in using the OAuth 2.0 device authorization grant (RFC 8628).
     *
     * ## Why this exists alongside [AUTHENTICATION]
     *
     * [AUTHENTICATION] assumes a browser on *this* device that can be redirected back to the app.
     * On an Android TV neither half of that is reliable: many TV devices ship without a browser at
     * all, and the ones that do cannot practically be driven with a remote control — typing a
     * Google password on a D-pad keyboard is a genuinely hostile experience.
     *
     * The device grant inverts it. The TV displays a short [PluginUiRequest.userCode] and a
     * verification URL; the user opens that URL on a phone or laptop and types the code there. The
     * plugin polls the token endpoint in the background and knows when it succeeded.
     *
     * ## Why it suits process isolation better
     *
     * There is **no redirect URI**, so there is no scheme for the host to own, nothing to route,
     * and no result to deliver across the boundary. The host's only job is to show two strings.
     * The plugin does the polling and the token exchange itself, which means tokens never enter
     * the host process at all — the strongest form of the property the transport was trying to
     * achieve by other means.
     *
     * Requires `CLOUD_STORAGE_ACCESS` or `MEDIA_SERVER_ACCESS`, the same as [AUTHENTICATION].
     */
    DEVICE_AUTHORIZATION,
}

/**
 * What happened to a [PluginUiRequest].
 *
 * Delivered to `handleAuthResult`. Every outcome is delivered exactly once — including the ones
 * the plugin did not cause — so a plugin waiting on a result never waits forever.
 */
data class PluginUiResult(
    /** Matches the request this answers. Opaque; the host generates and owns it. */
    val requestId: String,

    val outcome: PluginUiOutcome,

    /**
     * Query parameters from the redirect, for [PluginUiOutcome.COMPLETED].
     *
     * For an OAuth authorization-code flow this is where `code` and `state` arrive. Empty for
     * every other outcome.
     *
     * **Verify `state` yourself.** The host cannot: it does not know what value you generated, and
     * a host that accepted any `state` would be no check at all.
     */
    val values: Map<String, String> = emptyMap(),

    /**
     * Why it failed, for [PluginUiOutcome.FAILED] and [PluginUiOutcome.DENIED].
     *
     * A stable token such as `capability_not_declared` or `unsafe_url`, never a message built from
     * the request — that would echo a plugin's own input back into its logs.
     */
    val errorCode: String? = null,
)

enum class PluginUiOutcome {
    /** The user finished the flow. [PluginUiResult.values] carries the redirect parameters. */
    COMPLETED,

    /** The user backed out. Not an error, and not worth reporting as one. */
    CANCELLED,

    /**
     * The host could not complete it — no browser, a navigation failure, a malformed redirect.
     * Distinct from [DENIED]: the request was allowed, the attempt failed.
     */
    FAILED,

    /**
     * The host refused before showing anything: not entitled, capability not declared, or the
     * request failed validation. Retrying without changing something will not help.
     */
    DENIED,

    /**
     * The request outlived its owner — the plugin was unloaded, its worker died, entitlement
     * lapsed, or the user took too long.
     *
     * Delivered so that a plugin suspended on a result is released rather than left waiting. If
     * the plugin is gone there is nobody to deliver to, which is the point: the *host* stops
     * tracking it either way.
     */
    EXPIRED,
}
