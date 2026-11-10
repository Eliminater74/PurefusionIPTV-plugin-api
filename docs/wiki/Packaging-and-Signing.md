# Packaging & Signing

The starter extracts one `classes.dex` from its release APK and packages it as `payload/classes.dex` in a `.pfp` ZIP (`application/vnd.purefusioniptv.plugin`). The schema-1 `manifest.json` identifies the plugin and entry point, declares compatibility and permissions, and records SHA-256 hashes for payload files. The entry type is lowercase `plugin`. The APK is intermediate. Keep host API bytecode out of the DEX.

Run the tasks from `template/` with `bash gradlew` or Windows `.\gradlew.bat`.

| Task | Result |
| --- | --- |
| `:packaging:packageDevelopment` | Unsigned inspection package in `packaging/build/development/`. |
| `:packaging:createSigningKey` | Creates a new key set at `PUREFUSION_PFP_SIGNING_DIR`, an absolute fresh directory outside the repository. |
| `:packaging:checkSigningKey` | Validates the selected existing Ed25519 key pair. |
| `:packaging:packageSigned` | Builds and checks the signed `.pfp` in `packaging/build/signed/` and writes the exact generated manifest beside it. |
| `:packaging:verifySignedPfp` | Checks the default signed archive or `-PpfpFile=/absolute/path/to/package.pfp`, and binds the archive to current project identity. With the signing directory set, it also pins the expected public key. |

`PfpSigner` signs the exact manifest bytes with Ed25519. The manifest includes the public key fingerprint, and the archive carries `certs/developer-public-key.pem` and `signatures/developer.sig`. Developer signing proves key possession and authenticates the recorded hashes. The Marketplace countersigns after approval and applies its separate release/admission policy. Changing bytes after signing invalidates verification. Protect and reuse the key across releases.

Follow the [Signing guide](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/template/docs/SIGNING.md) for external key layout and the [submission guide](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/Marketplace-Submission) for the review path.

[Wiki Home](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/Home)
