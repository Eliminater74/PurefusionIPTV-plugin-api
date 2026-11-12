package dev.eliminater.purefusioniptv.plugin.api

/** Build information for the SDK bundled with the host. */
object PluginSdk {
    /** SDK library version from the Gradle build, independent of app versions and Git tags. */
    @JvmStatic val version: String get() = BuildConfig.SDK_LIBRARY_VERSION
    @JvmStatic val currentApi: Int get() = PluginApi.CURRENT
    @JvmStatic val minSupportedApi: Int get() = PluginApi.MIN_SUPPORTED
    @JvmStatic val supportedApiRange: IntRange get() = minSupportedApi..currentApi
}
