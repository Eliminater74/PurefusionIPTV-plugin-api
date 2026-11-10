# Capabilities, isolation and consent

The isolated lane requires Android API 26+, verified Marketplace provenance, developer and Marketplace signatures, and fresh signed admission policy. Trust-tier text cannot authorize execution. Supported sandbox declarations are `UI_HOME_EXTENSION`, `PLUGIN_SCOPED_STORAGE`, `NETWORK_EXTERNAL` and bounded `EPG_READ`. Provider claims stay empty. Other listing enum values do not imply isolated interfaces.

## Scoped storage

`SandboxCapabilitiesV1.readStorage`, `writeStorage` and `deleteStorage` use a host-owned namespace bound to account, developer key and plugin ID. Keys use ASCII letters/digits/underscore/hyphen, 1–64 characters. Limits: 64 keys, 16 KiB per value, 256 KiB total. Space calls >=75 ms apart; the example serializes at 80 ms. One `preferences_v1` JSON record holds count, greeting and step. Null/false can mean missing data, denial, throttling or unavailable storage. Visible state changes only after a confirmed write.

The owner grants private-data access in the host prompt or **Manage → Plugin permissions**. Declaration is not consent. Consent binds the exact signed manifest. Compatible updates can recover retained state after consent; worker restart discards only memory. Disable, revocation and uninstall do not necessarily delete storage. Use the host's private-plugin-data deletion control. This storage is non-secret and outside generic backups; do not put passwords/API keys here.

## Optional brokered HTTP

HTTP is denied by default. To enable it, add `NETWORK_EXTERNAL` to root manifest capabilities, rebuild/re-sign, obtain authorized review, then have the TV owner approve exactly one origin. `PfpPackager` derives `permissions.networkExternal`. There is no Android INTERNET permission or direct socket fallback.

The example sends `startHttp("GET", "/", null)` relative to the approved origin. It polls on `continue` without blocking on a network call. Pending polls are spaced 100 ms, capped at 50 polls and 256 KiB consumed. It displays status/byte count rather than response content. Cancel, Back, unload, timeout and denied/failed polls cancel the exchange. Request IDs never cross generations.

Broker bounds: one exchange per worker, three jobs overall, >=500 ms start spacing, response <=2 MiB, chunks <=32 KiB; GET or separately consented JSON POST <=16 KiB. Plugin code cannot supply headers, cookies or credentials, and the broker refuses redirects. The TV owner can save an origin-bound key through the host UI where supported. Only the host attaches it, and only on the approved origin. An absolute URL is allowed for a credential-free HTTPS GET to another host under the same registrable domain of a consented public origin, on port 443. LAN origins have no sibling-host access. Public origins require HTTPS DNS and RFC1918 IPv4 origins need explicit consent. The template ships without a configured origin or key.

## Catalog (documented only)

`EPG_READ` with `UI_HOME_EXTENSION` enables `(capabilities as? SandboxLiveCatalogV1)?.queryLiveCatalog(...)` during surface calls only, after owner consent. `pf.catalog.v1` bounds entitlement, guide horizon and rows; stream/provider URLs are excluded. Denied calls return null. Follow the SDK protocol rather than reading host databases.

Revocation, entitlement loss, account change, worker death or stale trust can end a session. Handle null/false/negative results, release references and reload durable state next time. No same-UID compatibility exemption is provided to third-party plugins.
