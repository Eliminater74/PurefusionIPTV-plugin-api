# Developer signing and verification

Use your own Ed25519 identity, reused across releases. No task reads first-party keys or the Marketplace authority's private key. Developer signatures prove key possession; authorized ownership/review establish distribution eligibility.

Set `PUREFUSION_PFP_SIGNING_DIR` to an absolute directory outside the **whole repository**, then use the wrapper:

| Task | Effect |
| --- | --- |
| `:packaging:createSigningKey` | Creates external Ed25519 PKCS#8 private/SPKI public PEM and stable key ID; refuses existing directories and project-local paths. |
| `:packaging:checkSigningKey` | Checks all three files and matching pair; prints only public fingerprint. |
| `:packaging:packageDevelopment` | Builds unsigned single-DEX package for inspection; no isolated execution authorization. |
| `:packaging:packageSigned` | Builds release DEX, signs using SDK and re-verifies output. Never creates a key implicitly. |
| `:packaging:verifySignedPfp` | Checks signed output, or `-PpfpFile=/path/file.pfp`, and current project identity. With signing environment set, additionally pins that public key. |

Files: `developer-private.pem` (secret), `developer-public.pem` (public), `developer-key-id.txt` (stable label). Reuse the complete set. Imported OpenSSL Ed25519 PKCS#8/SPKI keys in this layout also work. Key ID differs from portal developer ID.

Creation restricts the directory to owner-only POSIX access or inheritable Windows owner ACL **before writing secrets**, failing when neither is supported. Imported keys need equivalent protection. Use a protected parent/filesystem outside cloud sync, Git and CI artifacts. Git ignore rules are defense in depth. Never print private material.

Keep encrypted offline backups of all three files, protect encryption credentials separately, and test restoration privately. Losing the key breaks update continuity. Compromise requires authorized migration/revocation, not a silent replacement key.

## Signature contract

Archive: `manifest.json`, `payload/classes.dex`, `payload/assets/icon.png`, `certs/developer-public-key.pem`, `signatures/developer.sig`. The exact UTF-8 manifest bytes are Ed25519-signed. It commits payload SHA-256 and developer fingerprint (SHA-256 over whitespace-free public PEM Base64 body, not SPKI DER). Reformatting after signing invalidates it.

`market_root_v1` is a public authority identifier. Current distribution expects `verified_partner` and that authority ID in the signed manifest. This is protocol metadata, **not a developer-granted verified status**. Server publisher eligibility is separate. Developers never create `signatures/marketplace.sig`; Marketplace adds its distribution signature after review.

## Local preflight scope

The verifier checks bounded ZIP entries, duplicate/case collisions, safe paths, expected entries, schema/API/minSdk, single executable, hashes, certificate binding, Ed25519 signature, project identity and private PEM/secret filenames. Packaging includes explicit DEX/icon only, never source directories or key files. Local checks do not replace independent approval, ownership, current revocation, decoded-artwork verification, audience or TV runtime admission. Production remains authoritative.
