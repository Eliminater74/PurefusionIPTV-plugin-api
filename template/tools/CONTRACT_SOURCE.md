# Listing contract provenance

listing-contract.cjs is the standalone CommonJS transpilation of the PureFusionIPTV Marketplace listing validator, functions/src/pluginMarketplaceListing.ts, reviewed at Core 65197e85b0da8ce6a47dbf97bfcfe987bc39e955.

Source SHA-256: 6bc19d0ac248dd0fbd7f84a12925b394eb36724b8ed4d03cd08c021162c1a286

It depends only on Node's crypto module. No Firebase, private Core source, npm install, account, or secrets are needed. It validates the same listing v1 shape and manifest binding as the portal backend. Keep this artifact synchronized when that contract changes; the backend remains authoritative. Original project authors: PureFusionIPTV contributors. Distributed as part of this SDK under its LICENSE.
