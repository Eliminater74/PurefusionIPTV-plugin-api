# Troubleshooting

| Symptom | Check |
| --- | --- |
| Gradle cannot find Android SDK | Point `ANDROID_HOME` to an SDK containing Platform 36 and Build Tools 36.0.0. Use the starter wrapper and supported JDK. |
| Gradle cannot find public SDK source | Use a revision containing `SandboxSurfaceV1.kt`. Check the project layout or pass `-PpluginApiDir=/absolute/path/to/sdk`. |
| Entry point fails validation | Check `entryPoint`, constructor, package/class, runtime identity, ServiceLoader registration for discovery tests, and that API classes are compile-only. |
| API level or Android refused | Match the API declaration and host version and use API 26+ for isolation. Check optional protocol support separately. |
| Package/signature fails | Build a single release DEX and confirm the external key pair. Rebuild/re-sign any changed archive. Verify the exact generated signed manifest. |
| Listing rejected | Replace placeholders, check artwork and manifest binding, confirm the portal-assigned namespace and review the server reason. The starter's offline validator has an older category list. |
| Plugin installs but cannot start | Check enablement, entitlement, compatible host/device, signed trust/admission, release revocation and applicable consent. |
| Screen launch is absent | The current host has the Sports Hub slot. Arrange authorized host integration for another surface. |
| Broker denies a call | Check signed declaration, TV consent, current session, origin and POST grant. Honor busy, null and failure results. |
| Subtitles fail | Check provider availability, approved network/credential configuration, and SRT/WebVTT payload validation. Use host status and redact sensitive request data. |

See [starter troubleshooting](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/template/docs/TROUBLESHOOTING.md) for exact paths and task names. For SDK issues, include versions, steps, and a sanitized error in [GitHub Issues](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/issues).

[Wiki Home](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/Home)
