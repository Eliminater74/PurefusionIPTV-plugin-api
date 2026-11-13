# DVR plugin development

SDK 1.1.0 defines the API 4 DVR provider and the `pf.dvr.v1` control protocol. A provider is the DVR control plane: it owns recording rules, series and episode decisions, conflict policy and the recording library's state. The host owns everything that needs Android, protected media or user secrets: capture execution, encryption, storage destinations, the guide, durable wakes, provider state storage and playback.

## Build a provider

1. Compile against the public SDK 1.1.0 source with a `compileOnly` dependency in the executable module. Add the SDK as a JVM test dependency and use an Android API 26+ isolated target.
2. Implement `Plugin`, `SandboxPluginV1` and [`DvrProvider`](../src/main/java/dev/eliminater/purefusioniptv/plugin/api/dvr/DvrProvider.kt) on a public no-argument entry class. Declare level 4 in the compiled entry point and the manifest. Set `Plugin.capabilities` to exactly `listOf(PluginCapability.DVR_PROVIDER)`.
3. Name the entry class in the package manifest and use the existing `META-INF/services/dev.eliminater.purefusioniptv.plugin.api.Plugin` registration for ServiceLoader discovery. The manifest uses category `dvr_provider` and the signed host capability `DVR_ACCESS` (see the [example manifest](../examples/dvr/manifest.json)). An API 3 host rejects API 4 declarations; its isolated providers keep the API 3 admission rules.
4. Acquire the session-scoped `DvrHostServicesV1` facade in `onDvrLoad` after the ordinary sandbox load. Implement `handleDvr` with `DvrProtocolV1.decodeRequest(bytes, DvrEndpointV1.PROVIDER)` and `encodeReply(request, reply)`. Return null for invalid wire input. Report only operations, rule kinds, destination kinds and marker features that the provider implements. Report dynamic destination availability separately.
5. Use only `DvrProtocolV1` to encode and decode SDK types. The SDK types are host-owned; do not pass them to a serialization library you bundle. Encode your own durable state as text, for example with the platform `org.json`.
6. Build and test with a fake host. The [independent example](../examples/dvr/README.md) covers registration, discovery, schedule acceptance, polling, cancellation, denial and a lost-reply retry.

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
      "destinationId": "default",
      "window": { "startEpochMillis": 1800000000000, "endEpochMillis": 1800003600000 },
      "padding": { "beforeSeconds": 60, "afterSeconds": 120 },
      "rule": { "kind": "once" }
    }
  }
}
```

An accepted schedule returns `code: "OK"` and `payload: {"kind":"rule","status":{"ruleId":"rule-18","state":"ACTIVE"}}`. Acceptance gives the caller a durable ID. An occurrence appears separately in recording status and discovery. Unsupported operations return `UNSUPPORTED`; failures use fixed codes and have no payload. `UNAVAILABLE` means the channel, destination or DVR settings prevent recording; the user can fix it. `CONFLICT` carries up to eight structured reasons and may include an authorized recording ID, window and display title. Localize safe messages from codes. Treat all provider display metadata as untrusted plain text.

The codec rejects unknown members, duplicate keys, invalid UTF-8, invalid scalar types, over-depth JSON, missing required fields and mismatched reply types or IDs. The transport must enforce the 16 KiB request and 64 KiB reply bounds at ingress and before sending. The SDK caps pages and destination lists at 20 items, IDs at 128 ASCII characters and display strings at 256 characters. IDs start with a letter or digit and can then contain letters, digits, dots, underscores, tildes and hyphens. Treat them as opaque and scope them by plugin, account and installation. A cursor is opaque and bound to a query. Start a new query if it expires. The codec verifies structure. The host validates ID provenance, ownership, grants, and cross-call state.

## Provider operations

| Operation | Contract |
| --- | --- |
| `describe` | Provider identity, implemented operations, one-time/series/weekly support, destination types, commercial markers/skipping and limitations. NAS support is advertised with the `NAS` destination kind. Skipping requires markers and a host playback integration. |
| `schedule`, `rule_status`, `cancel_rule` | Create or read a rule and cancel future occurrences. `cancel_rule` with `cancelUpcoming` also cancels occurrences that have not started. Existing recordings retain their own IDs and media. |
| `list_rules`, `update_rule` | Page rules with display detail (kind, channel, title, padding, series, episode mode, last guide check). Pause or resume, change new-episodes-only or padding, allow excluded episodes again, or re-check the guide now. |
| `rule_episodes`, `set_episode` | Page a rule's episode decisions and exclude or allow one episode. Excluding an episode cancels its recording if it has not started. |
| `status`, `list_recordings` | Fetch a recording snapshot or a bounded page. Filter by rule or state, including `COMPLETED` and `INTERRUPTED`. |
| `cancel`, `stop`, `update_recording`, `delete_recording` | Cancel an occurrence, ask an active capture to finalize, change a recording's padding, or remove a finished recording with its media. Poll status after a successful control result. |
| `list_destinations` | Report eligible destinations, writability and optional space estimates. |
| `playback` | Request a short-lived host-issued playback handoff for a completed recording on explicit user action. |
| `wake` | Deliver a registered scheduler callback so the provider can reconcile and advance a rule. |
| `notify` | Advisory host events: `STARTUP`, `GUIDE_UPDATED`, `CAPTURES_CHANGED`, `SETTINGS_CHANGED`. Idempotent; reconcile from durable state. |

A one-time request can identify a guide programme or use a channel and explicit time window. A window containing the current time can be used for an immediate start. Times are UTC epoch milliseconds with an exclusive end and a maximum seven-day window. Padding is separate and capped at two hours per side. A series rule is channel-scoped and matches a host series ID. Providers that implement new-only rules skip episodes with unknown new/repeat status. Weekly rules anchor local start time and nominal duration to the first window, specify ISO weekdays and use a zone ID supported by both host and worker. Document the policy for ambiguous or missing local times during daylight-saving transitions.

A recording snapshot carries a stable recording ID, rule ID when applicable, the nominal (guide) window, the applied padding, state, revision and optional byte/time progress, destination, display note and display metadata. Providers can retain a title and channel/programme references after the original guide entry expires. Completed snapshots carry an opaque media ID, duration and optional ordered commercial intervals. Interrupted snapshots carry a fixed interruption reason. `DvrProtocolV1.isValidNextSnapshot` validates an adjacent durable change or exact replay. A scheduled recording's window may move; a running recording keeps its start and may change its end. Revisions increase with durable changes. Pollers can miss revisions, so discard older snapshots and reconcile jumps with authoritative state. Terminal recording snapshots are immutable. Finalize optional commercial markers before publishing completion.

## Host services and authorization

The host's isolated adapter implements `DvrHostServicesV1.exchange`. It accepts only `DvrEndpointV1.HOST` requests and sends typed replies through the existing isolated transport.

| Host service | Responsibility |
| --- | --- |
| `read_programmes` | Resolve and page eligible channel/programme metadata with opaque channel, programme and series IDs. With `seriesId`, the host returns only that series' airings. Each programme can carry new/repeat status and host-normalized episode evidence. The page reports the guide's coverage of the window and whether the guide marks new episodes at all. |
| `host_destinations` | Return host-managed destination IDs approved for this plugin and user. Android storage grants, device/removable paths, NAS connection settings and credentials stay host-side. `default` follows the user's DVR storage setting at the moment each capture is accepted. |
| `start_capture`, `capture_status`, `cancel_capture`, `stop_capture`, `update_capture`, `delete_capture`, `list_captures` | Accept and control durable capture jobs using host-owned Android scheduling, foreground execution, stream resolution and protected storage. A host may refuse overlapping captures beyond its capacity with `CONFLICT`/`CAPTURE_LIMIT`. Host capture IDs have their own namespace. Captures that existed before the provider was installed are listed too. |
| `register_wake`, `cancel_wake` | Schedule or cancel a durable wake by opaque ID. Retry delivery with the original wake request ID. Acknowledge only after the provider has durably reconciled the wake. |
| `create_playback` | Issue an expiring, scoped token for an owned media ID during an authorized playback action. The host resolves and redeems it inside the player. |
| `get_state`, `put_state`, `list_state` | Durable provider state scoped to the plugin, account and installation. Values are opaque text of up to 6 KiB. `put_state` writes up to 8 records and deletes up to 20 in one atomic transaction. `list_state` pages by key prefix in key order. The host enforces a quota and answers `UNAVAILABLE` when it is full. |
| `read_legacy`, `legacy_episodes`, `adopt_legacy` | Rules the host kept before a provider owned scheduling, with their episode history (decisions, evidence tokens and capture IDs). Adopt a rule only after the provider durably holds it; the host then stops offering it and keeps its own copy. |

### Episode evidence

Evidence tokens identify one episode independently of guide row IDs: `p:<provider episode id>`, `se:<season>:<episode>:<part>` and `t:<normalized episode title>`. Airings that share a token are the same episode. A token that appears with two different values of another kind inside one guide window contradicts itself and should not be trusted. An airing without evidence stands only for itself; providers may key it by its start time (`a:<epoch millis>`), which is also the token legacy history uses for such airings.

The host authorizes first-party and third-party DVR providers under the same policy. Exactly one DVR provider is active at a time; the host refuses DVR exchanges from any other installed provider, so two providers never schedule against the same captures. Free accounts have no DVR plugin access and retain normal Free-tier playback and features. Premium and Lifetime cover DVR using the existing entitlement. The host verifies entitlement at admission, activation, control calls and resumed or scheduled work. It also verifies the signed category/capability, the exact worker session and action scope, playlist and destination permissions, rate/concurrency limits and token ownership. A provider role, schema check or test broker alone grants no access. Revocation or entitlement loss prevents new privileged work and triggers the host's policy for in-flight work while preserving existing recording data.

Keep all raw stream URLs, playlist secrets, account tokens, private databases, application paths, file descriptors, signing material, and Android UI/player objects inside the host. A DVR provider supplies policy, schedules, metadata and optional processing within host-granted boundaries. The v1 capture adapter is a control plane with no media-byte or arbitrary file/network channel. NAS support requires a host-mediated destination implementation. Raw media processing or commercial analysis that needs bytes requires its own future host interface and review.

## Timeouts, retries and recovery

Control calls have a five-second SDK deadline, including nested host exchanges. Bound the work of one call: reconcile a few rules, persist a cursor and register a near wake to continue. The host serializes callbacks per worker and enforces deadlines, rate limits, and tenancy retirement. Long recordings, finalization, and future schedules run as durable host jobs independently of a control call. A transport cancellation or missing reply means the outcome is unknown. Use a status call or retry the same mutation, retaining its request ID and semantic payload.

Keep a durable mutation journal keyed by authorized plugin/account/installation, direction and request ID. For schedule, cancel, stop, capture mutations and wake registration or delivery, retain enough information to return the same committed receipt without duplicating a side effect. Reuse with a different operation or payload yields `REQUEST_ID_REUSED`. Tie receipt persistence and state changes together, including across a worker replacement. When a command crosses both directions, durably retain the mapping to a stable host request ID before invoking the broker. A transient `BUSY` permits retry with the original ID. Hosts and providers must define retention at least through the retry and reconciliation lifetime of the affected job. Reject stale IDs outside that window when the outcome cannot be established safely.

After process death, reconnect through a new authorized session, reload durable state and reconcile rules with host capture snapshots. Stale broker references and callbacks are unusable. Keep an index of active rules and capture mappings across unloads. Missed wakes and interrupted jobs may be reconciled after restart. Scope timeout or protocol-fault retirement to the failing worker. Live TV stays usable if the DVR provider is missing, disabled, incompatible or quarantined. Existing captures and their media stay intact while a provider is missing, disabled or being replaced.
