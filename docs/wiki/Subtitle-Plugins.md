# Subtitle Plugins

A signed `subtitle_provider` executable implements `Plugin`, `SandboxPluginV1` and [`SandboxSubtitlePluginV1`](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/src/main/java/dev/eliminater/purefusioniptv/plugin/api/SandboxSubtitleV1.kt). A compatible host calls `handleSubtitles(request: ByteArray): ByteArray?` with bounded UTF-8 `pf.subtitle.v1` JSON.

| Operation | Request | Successful result |
| --- | --- | --- |
| `describe` | Schema and operation | Provider language codes, when available. |
| `search` | Movie/episode metadata and optional language | Up to 100 results with opaque unique IDs, display filenames and language. Use `MOVIE` or `EPISODE` for `contentType`. Metadata may include TMDB/IMDb IDs, year, season and episode. |
| `download` | Opaque result ID and optional language | Base64 bytes with `format` set to `srt` or `vtt`. |

Core accepts `ok`, `security_gated` and `error` statuses. Keep responses within 2 MiB, requests within 16 KiB and decoded downloads within 512 KiB. Each direction also has a 384 KiB compressed transport ceiling. The host validates the response, owns attachment/decoding/rendering and supplies fixed user-facing status text. Paths, player objects, host Context and credentials stay outside the protocol. Calls are serialized and bounded at 30 seconds. Cancel outstanding work and treat cancellation as invalidating the worker's capability session.

## OpenSubtitles example

The first-party plugin searches via the approved provider origin and requests download authorization using the network broker. Core can attach its provisioned developer `Api-Key` to origin-relative requests for the matched OpenSubtitles plugin identity and developer signer. The TV owner still consents to the origin and separately to JSON POST. A permitted public sibling-host download is a credential-free HTTPS GET under broker scope. Core owns the optional first-party TV sign-in, encrypted account state and origin-bound bearer attachment. The worker receives no passwords or tokens. The SDK exposes no generic account exchange or website credential delivery API.

The template's offline listing validator accepts `subtitle_provider` and checks that the listing matches the generated package manifest. Coordinate authorized review using the operator's supported path. The protocol and source describe what a compatible host can do. Live provider access depends on approved release, consent, a configured key and the provider response.

[Wiki Home](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/Home)
