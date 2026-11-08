pluginManagement {
    resolutionStrategy.eachPlugin {
        if (requested.id.id == "org.jetbrains.kotlin.jvm")
            useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
    }
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "purefusion-plugin-starter"
include(":plugin", ":packaging")

// In the public checkout template/ is beside src/. A copied template can use a sibling
// plugin-api checkout, or an explicit -PpluginApiDir. Core is never a dependency.
val explicitSdk = providers.gradleProperty("pluginApiDir").orNull
val sdk = explicitSdk?.let { file(it) } ?: listOf(rootDir.parentFile, file("plugin-api"), file("../plugin-api"))
    .firstOrNull { it.resolve("src/main/java/dev/eliminater/purefusioniptv/plugin/api/SandboxSurfaceV1.kt").isFile }
    ?: error("Public SDK not found. Clone plugin-api beside this project or set -PpluginApiDir=/path/to/plugin-api")
gradle.extra["pluginApiSourceDir"] = sdk.absolutePath
includeBuild(sdk) {
    dependencySubstitution {
        substitute(module("dev.eliminater.purefusioniptv:plugin-api")).using(project(":"))
    }
}
