# Getting Started

1. Download a pinned [SDK source revision](https://github.com/Eliminater74/PurefusionIPTV-plugin-api) containing the starter and sandbox contracts you use. The v1.0.2 release predates those files. Open `template/` from the SDK checkout, or copy it to your project and make the SDK available in `plugin-api/`, beside the template, or through `-PpluginApiDir=/absolute/path/to/sdk`.
2. Install Android Platform 36 and Build Tools 36.0.0. Set `ANDROID_HOME`. Use JDK 17 or a later JDK supported by the included Gradle 9.4.1 wrapper. Install Node 24 for the setup and listing tools. The Android build uses AGP 9.2.1 and Kotlin 2.3.20 and targets JVM 17.
3. Follow the [starter setup](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/template/GETTING_STARTED.md) to customize the root manifest and listing. A local test can use a synthetic identity. A submission uses the portal-assigned developer ID and namespace.
4. Adapt `StarterPlugin` and keep a public no-argument constructor. If you rename its class, update the manifest input's `entryPoint` and the class name inside `src/main/resources/META-INF/services/dev.eliminater.purefusioniptv.plugin.api.Plugin`. The manifest selects the installed entry point.
5. From the template directory, use the wrapper and listing tests. On Windows, use `.\gradlew.bat` with the same task names.

   ```sh
   bash gradlew :plugin:testDebugUnitTest :packaging:test :packaging:packageDevelopment
   node --test tools/test/workflow.test.cjs
   ```

The composite build substitutes public SDK source for `dev.eliminater.purefusioniptv:plugin-api:1.0.0`. The plugin uses `compileOnly` so the runtime supplies API classes. The APK is intermediate. For device acceptance, use an authorized host integration and Marketplace release. Continue with [Packaging & Signing](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/Packaging-and-Signing).

For the repository-wide checks and local reproduction commands, see [Continuous integration](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/blob/main/README.md#continuous-integration).

[Wiki Home](https://github.com/Eliminater74/PurefusionIPTV-plugin-api/wiki/Home)
