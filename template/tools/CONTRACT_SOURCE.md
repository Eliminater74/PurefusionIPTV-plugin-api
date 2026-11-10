# Listing contract provenance

`listing-contract.cjs` mirrors `functions/src/pluginMarketplaceListing.ts` at Core revision `54238a155c4eea5e3ac8c7e1a5e80c3c0550a8dd`. Core commit `231f2ea5027f55ec327254ddfcaceabb31373886` added `subtitle_provider` to the category allowlist.

SHA-256 of the exact TypeScript source bytes reviewed is `b4bc01e328f987419e84b8a18f3a6d526e0481497b5a0f68b0d2ad124cb53d9f`.

The vendored file matches the TypeScript 5.9.3 CommonJS/ES2022 output apart from its provenance header. It depends only on Node's crypto module and runs without Firebase, Core, an npm install, an account, or secrets. The current backend is authoritative for Marketplace publication. The PureFusionIPTV contributors wrote the original validator. This SDK distributes it under its LICENSE.
