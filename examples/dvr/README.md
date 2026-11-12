# Synthetic DVR provider example

This Android library compiles against the public SDK alone. A JVM test host supplies synthetic destination and recording snapshots, including a lost-reply retry. The library runs no capture, provider network, Android scheduling or storage. Rule and receipt state are deliberately in memory for the example.

From the SDK root, set `ANDROID_HOME` to Android Platform 36 and Build Tools 36.0.0. Use a JDK supported by the starter's Gradle wrapper.

```sh
bash template/gradlew -p examples/dvr assembleDebug testDebugUnitTest
```

On Windows use `.\template\gradlew.bat` with the same arguments. To open or move the example as its own Gradle project, point `-PpluginApiDir=/absolute/path/to/sdk` at a public SDK 1.1.0 source checkout and use Gradle 9.4.1 or the SDK's wrapper.

The example registers its public entry class with ServiceLoader and declares API 4 and the `DVR_PROVIDER` SDK role. Its [manifest input](manifest.json) illustrates `dvr_provider` and `DVR_ACCESS` for a future host. Current Core is API 3. This project builds an AAR and tests the contract locally. TV packaging, admission and capture require the future Core DVR adapter.

See the [DVR plugin guide](../../docs/DVR_PLUGIN_GUIDE.md) for wire schemas, lifecycle and host-side authorization.
