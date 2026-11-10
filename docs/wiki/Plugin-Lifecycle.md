# Plugin Lifecycle

1. **Approval** is a Marketplace decision for a particular release and audience. An **installed** plugin has a local verified package/registry entry. **Enabled** records user intent. **Loaded** means the runtime has activated the plugin. These events can happen at different times. Incompatibility, trust deferral and faults can prevent activation while inventory still shows the installation. The host checks Premium plugin entitlement and feature availability at runtime. Entitlement loss stops active workers without counting as a plugin fault.
2. Core checks package integrity, compatibility and trust before starting executable code. The installed manifest names a public no-argument `Plugin` entry point. The worker checks runtime identity against the manifest. An admitted isolated entry must report an empty `Plugin.capabilities`.
3. A capability-free isolated entry outside the subtitle category receives its isolated worker application `Context` in `onLoad`. The pinned Emby compatibility worker also calls `onLoad`, with its own app-UID application context. Isolated capability-bearing and subtitle entries receive `onSandboxLoad(capabilities)`, with no Context argument.
4. During graceful unload, cancel pending work and release references in `onUnload()`. Disable, revocation, a crash, a timeout or worker replacement may terminate the worker before cleanup finishes. Keep memory disposable and commit durable records independently.
5. Core owns failure containment. Repeated worker failures can quarantine a plugin until the user clears the quarantine. A new worker has a fresh capability session. Drop old HTTP IDs and handle null, denied and failed calls. Avoid retry loops and do not infer a successful save from a failed write.

See [Security Model](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/Security-Model) for trust and [Troubleshooting](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/Troubleshooting) for diagnosis.

[Wiki Home](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/Home)
