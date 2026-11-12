pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}

dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}

rootProject.name = "purefusion-dvr-example"
val sdk = providers.gradleProperty("pluginApiDir").orNull?.let { file(it) } ?: file("../..")
require(sdk.resolve("src/main/java/dev/eliminater/purefusioniptv/plugin/api/dvr/DvrProvider.kt").isFile) {
    "Point -PpluginApiDir at a public SDK 1.1.0 checkout."
}
includeBuild(sdk) {
    dependencySubstitution {
        substitute(module("dev.eliminater.purefusioniptv:plugin-api")).using(project(":"))
    }
}
