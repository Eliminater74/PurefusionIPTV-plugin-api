# Troubleshooting

| Problem | Action |
| --- | --- |
| Gradle/SDK failure | Open template/, use its wrapper, set ANDROID_HOME to SDK with Platform 36/Build Tools 36.0.0. |
| Unsupported JDK | AGP needs JDK 17+. Use a Gradle-9.4.1-supported JDK; align Gradle JDK/JAVA_HOME. Targets stay 17. |
| Kotlin mismatch | SDK and template use AGP 9.2.1/Kotlin 2.3.20. Do not apply Kotlin Android separately. |
| SDK not found/API mismatch | Use public source with SandboxSurfaceV1 in ../plugin-api or plugin-api/, or -PpluginApiDir. Level is 3; old host builds need supported updates. |
| Invalid manifest/wrong ID | Root manifest is input. Use generated signed manifest for binding; align class, namespace, service registration and entrypoint after renaming. Use portal namespace. |
| Missing key | Point PUREFUSION_PFP_SIGNING_DIR at protected external files and run checkSigningKey. Generate only for a new identity. |
| Key generation refuses directory | Intentional. Reuse existing identity or choose a fresh external directory. Never overwrite an old key. |
| Signature failure | Never edit signed ZIPs. Verify exact manifest/payload, private/public pair and original fingerprint; rebuild after changes. |
| Update signer mismatch | Reuse original key/ID. Authorized operators handle compromise/migration; bypassing continuity fails. |
| Capability/consent denied | Declare supported capability, obtain review, then TV-owned Plugin permissions consent. Website cannot grant it. |
| Storage null/false | Missing, denied, busy, corrupt namespace or stale worker; space calls >=75 ms, and never claim failed saves succeeded. |
| HTTP denied/busy | Default has no NETWORK_EXTERNAL. Optional release needs declaration and exact origin consent. Starts >=500 ms apart; headers/auth/redirects unavailable. |
| No Settings button | Expected without reviewed adapter. Use in-screen preferences. API 3 has no generic remote apply method. |
| Android incompatibility | Isolated lane needs API 26+. No API-25 same-UID fallback. |
| Marketplace rejection | Read server reason; check namespace/key, version/changelog, manifest binding, rights and publisher eligibility. Local validation cannot grant approval. |
| Bad artwork | See Listing Guide bounds. Local helper accepts PNG; portal fully decodes static PNG/JPEG/WebP. Replace mockups with genuine screenshots. |
| Stale preview | Run prepare then preview after metadata/artwork edits; open generated card.html. |
| Local plugin does not run | Developer signing alone cannot authorize isolated execution; genuine Marketplace signature and admission are required. |
| Approved screen has no launch action | Generic third-party surface discovery is not available. Current Core exposes Sports Hub's slot only; arrange authorized host integration rather than copying its ID. |

Windows uses `.\gradlew.bat`; POSIX uses `bash gradlew`, including source ZIPs without executable file bits. Windows verification does not imply a native Linux build or physical-TV acceptance.
