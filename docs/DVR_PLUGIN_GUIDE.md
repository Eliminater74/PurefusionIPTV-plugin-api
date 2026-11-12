# DVR plugin development

SDK 1.1.0 defines the API 4 DVR provider and the `pf.dvr.v1` control protocol. The currently pinned Core uses API 3. Running a DVR provider on TV requires a future Core integration, followed by separately reviewed plugin distribution. The existing Core recorder is a separate implementation.

## Build a provider

1. Compile against the public SDK 1.1.0 source with a `compileOnly` dependency in the executable module. Add the SDK as a JVM test dependency and use an Android API 26+ isolated target.
2. Implement `Plugin`, `SandboxPluginV1` and [`DvrProvider`](../src/main/java/dev/eliminater/purefusioniptv/plugin/api/dvr/DvrProvider.kt) on a public no-argument entry class. Declare level 4 in the compiled entry point and the manifest. Set `Plugin.capabilities` to the DVR provider role `DVR_PROVIDER`.
3. Name the entry class in the package manifest and use the existing `META-INF/services/dev.eliminater.purefusioniptv.plugin.api.Plugin` registration for ServiceLoader discovery. The [example manifest](../examples/dvr/manifest.json) uses the future routing category `dvr_provider` and the separate signed host capability `DVR_ACCESS`. The API 3 host rejects API 4 declarations and has no DVR route. Its existing isolated providers continue to use the API 3 admission rules.
4. Acquire the session-scoped `DvrHostServicesV1` facade in `onDvrLoad` after the ordinary sandbox load. Implement `handleDvr` with `DvrProtocolV1.decodeRequest(bytes, DvrEndpointV1.PROVIDER)` and `encodeReply(request, reply)`. Return null for invalid wire input. Report only operations, rule kinds, destination kinds and marker features that the provider implements. Report dynamic destination availability separately.
5. Build and test with a fake host. The [independent example](../examples/dvr/README.md) covers registration, discovery, schedule acceptance, polling, cancellation, denial and a lost-reply retry.

## Wire format

Each control call carries one UTF-8 JSON object. The receiver fixes the direction. The request envelope requires `schema`, `requestId` and a typed `command`. The reply requires `schema`, the same `requestId` and `code`. Objects under `command`, `payload` and a schedule's `rule` use the explicit `kind` discriminators in the [messages](../src/main/java/dev/eliminater/purefusioniptv/plugin/api/dvr/DvrMessagesV1.kt) and [models](../src/main/java/dev/eliminater/purefusioniptv/plugin/api/dvr/DvrModelsV1.kt). Enum values are their case-sensitive names. Default fields may be omitted.

```json
{
  "schema": "pf.dvr.v1",
  "requestId": "schedule-01",
  "command": {
    "kind": "schedule",
    "schedule": {
      "channelId": "channel-42",
      "programmeId": "programme-18",
      "destinationId": "device-1",
      "window": { "startEpochMillis": 1800000000000, "endEpochMillis": 1800003600000 },
      "padding": { "beforeSeconds": 60, "afterSeconds": 120 },
      "rule": { "kind": "once" }
    }
  }
}
```

An accepted schedule returns `code: "OK"` and `payload: {"kind":"rule","status":{"ruleId":"rule-18","state":"ACTIVE"}}`. Acceptance gives the caller a durable ID. An occurrence appears separately in recording status and discovery. Unsupported operations return `UNSUPPORTED`; failures use fixed codes and have no payload. `CONFLICT` carries up to eight structured reasons and may include an authorized recording ID and window. Localize safe messages from codes. Treat all provider display metadata as untrusted plain text.

The codec rejects unknown members, duplicate keys, invalid UTF-8, invalid scalar types, over-depth JSON, missing required fields and mismatched reply types or IDs. The transport must enforce the 16 KiB request and 64 KiB reply bounds at ingress and before sending. The SDK caps pages and destination lists at 20 items, IDs at 128 ASCII characters and display strings at 256 characters. IDs start with a letter or digit and can then contain letters, digits, dots, underscores, tildes and hyphens. Treat them as opaque and scope them by plugin, account and installation. A cursor is opaque and bound to a query. Start a new query if it expires. The codec verifies structure. Core validates ID provenance, ownership, grants, and cross-call state.

## Provider operations

| Operation | Contract |
| --- | --- |
| `describe` | Provider identity, implemented operations, one-time/series/weekly support, destination types, commercial markers/skipping and limitations. NAS support is advertised with the `NAS` destination kind. Skipping requires markers and a host playback integration. |
| `schedule`, `rule_status`, `cancel_rule` | Create or read a rule and cancel future occurrences. Existing occurrences retain their own IDs and can be canceled separately. |
| `status`, `list_recordings` | Fetch a recording snapshot or a bounded page. Filter by rule or state, including `COMPLETED` and `INTERRUPTED`. |
| `cancel`, `stop` | Cancel an occurrence or ask an active capture to finalize. Poll status after a successful control result. |
| `list_destinations` | Report eligible destinations, writability and optional space estimates. |
| `playback` | Request a short-lived host-issued playback handoff for a completed recording on explicit user action. |
| `wake` | Deliver a registered scheduler callback so the provider can reconcile and advance a rule. |

A one-time request can identify a guide programme or use a channel and explicit time window. A window containing the current time can be used for an immediate start. Times are UTC epoch milliseconds with an exclusive end and a maximum seven-day window. Padding is separate and capped at two hours per side. A series rule is channel-scoped and matches a host series ID. Providers that implement new-only rules skip episodes with unknown new/repeat status. Weekly rules anchor local start time and nominal duration to the first window, specify ISO weekdays and use a zone ID supported by both host and worker. Document the policy for ambiguous or missing local times during daylight-saving transitions.

A recording snapshot carries a stable recording ID, rule ID when applicable, window, state, revision and optional byte/time progress and display metadata. Providers can retain a title and channel/programme references after the original guide entry expires. Completed snapshots carry an opaque media ID, duration and optional ordered commercial intervals. Interrupted snapshots carry a fixed interruption reason. `DvrProtocolV1.isValidNextSnapshot` validates an adjacent durable change or exact replay. Revisions increase with durable changes. Pollers can miss revisions, so discard older snapshots and reconcile jumps with authoritative state. Terminal recording snapshots are immutable. Finalize optional commercial markers before publishing completion.

## Host services and authorization

A future Core adapter implements `DvrHostServicesV1.exchange`. It accepts only `DvrEndpointV1.HOST` requests and sends typed replies through the existing isolated transport.

| Host service | Responsibility |
| --- | --- |
| `read_programmes` | Resolve and page eligible channel/programme metadata with opaque channel, programme and series IDs. |
| `host_destinations` | Return host-managed destination IDs approved for this plugin and user. Android storage grants, device/removable paths, NAS connection settings and credentials stay host-side. |
| `start_capture`, `capture_status`, `cancel_capture`, `stop_capture` | Accept and control durable capture jobs using host-owned Android scheduling, foreground execution, stream resolution and protected storage. Host capture IDs have their own namespace. |
| `register_wake`, `cancel_wake` | Schedule or cancel a durable wake by opaque ID. Retry delivery with the original wake request ID. Acknowledge only after the provider has durably reconciled the wake. |
| `create_playback` | Issue an expiring, scoped token for an owned media ID during an authorized playback action. Core resolves and redeems it inside the player. |

Core authorizes first-party and third-party DVR providers under the same policy. Free accounts have no DVR plugin access and retain normal Free-tier playback and features. Premium and Lifetime cover DVR using the existing entitlement. Core verifies entitlement at admission, activation, control calls and resumed or scheduled work. Core also verifies the signed category/capability, user consent, exact worker session and action scope, playlist and destination permissions, rate/concurrency limits and token ownership. A provider role, schema check or test broker alone grants no access. Revocation or entitlement loss prevents new privileged work and triggers Core's policy for in-flight work while preserving existing recording data.

Keep all raw stream URLs, playlist secrets, account tokens, private databases, application paths, file descriptors, signing material, and Android UI/player objects inside Core. A DVR provider supplies policy, schedules, metadata and optional processing within host-granted boundaries. The v1 capture adapter is a control plane with no media-byte or arbitrary file/network channel. NAS support requires a host-mediated destination implementation. Raw media processing or commercial analysis that needs bytes requires its own future host interface and review.

## Timeouts, retries and recovery

Control calls have a five-second SDK deadline, including nested host exchanges. The host serializes callbacks per worker and enforces deadlines, rate limits, and tenancy retirement. Long recordings, finalization, and future schedules run as durable host jobs independently of a control call. A transport cancellation or missing reply means the outcome is unknown. Use a status call or retry the same mutation, retaining its request ID and semantic payload.

Keep a durable mutation journal keyed by authorized plugin/account/installation, direction and request ID. For schedule, cancel, stop, capture mutations and wake registration or delivery, retain enough information to return the same committed receipt without duplicating a side effect. Reuse with a different operation or payload yields `REQUEST_ID_REUSED`. Tie receipt persistence and state changes together, including across a worker replacement. When a command crosses both directions, durably retain the mapping to a stable host request ID before invoking the broker. A transient `BUSY` permits retry with the original ID. Core and providers must define retention at least through the retry and reconciliation lifetime of the affected job. Reject stale IDs outside that window when the outcome cannot be established safely.

After process death, reconnect through a new authorized session, reload durable state and reconcile rules with host capture snapshots. Stale broker references and callbacks are unusable. Keep an index of active rules and capture mappings across unloads. Missed wakes and interrupted jobs may be reconciled after restart. Scope timeout or protocol-fault retirement to the failing worker. Live TV stays usable if the DVR provider is missing, disabled, incompatible or quarantined.

Core still needs manifest admission and protocol negotiation, the isolated transport binding, per-call grant enforcement, durable scheduling and capture, protected playback handoff, fault isolation, and real TV acceptance. Recorder extraction and preservation of existing recordings and schedules follow as a later milestone.
