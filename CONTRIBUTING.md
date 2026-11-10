# Contributing to PureFusionIPTV Plugin SDK

This repository contains the public Kotlin contracts, packaging and signing tools, and standalone starter. The closed-source PureFusionIPTV application and Marketplace services are developed separately.

1. Discuss proposed public contract changes in [GitHub Issues](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/issues).
2. Fork and branch from `main`.
3. Follow the [API compatibility policy](docs/wiki/Versioning-and-Compatibility.md). Assess binary compatibility independently of Git tag and Gradle artifact versions.
4. Describe the change, its compatibility impact and the focused verification in the pull request.

Use the starter wrapper to test the SDK from `template/`. The SDK root has no separate wrapper. On Windows, substitute `.\gradlew.bat`.

```sh
bash gradlew -p .. :testDebugUnitTest
```

Use [Getting Started](template/GETTING_STARTED.md) for the independent starter checks. Match existing Kotlin style in [SDK source](src/main/java/dev/eliminater/purefusioniptv/plugin).
