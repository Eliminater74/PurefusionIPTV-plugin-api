# Security Model

Verified Marketplace executables in the modern lane run on Android API 26+ with a separate isolated UID. Core verifies provenance, developer and Marketplace signatures, payload hashes, manifest identity, compatibility, entitlement and signed admission before execution. The worker loads one verified DEX. It has no direct network or Core private file access. API 25 cannot fall back to a same-UID third-party worker.

Core owns brokers and TV consent. Only approved origins, non-secret private plugin records and bounded catalog data cross their respective grants. Each session can refuse requests after consent withdrawal, disablement, account or entitlement changes, stale trust, revocation or worker failure. Revocation can end a worker without cleanup. Keep secrets in host-managed credential facilities where the host supports the exact integration. Public plugin storage and website configuration are unsuitable for credential delivery.

The separate Emby compatibility worker shares Core's UID and is restricted to the pinned first-party release identity. The trust tier, a copied ID, a developer signature or a manifest flag cannot grant that worker to a third party. Use [Capabilities & Permissions](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/Capabilities-and-Permissions) for the isolated author contract.

[Wiki Home](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/Home)
