import groovy.json.JsonOutput
import groovy.json.JsonSlurper

buildscript { dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20") } }
plugins { id("com.android.application") version "9.2.1" }
val identity = JsonSlurper().parse(rootProject.file("manifest.json")) as Map<*, *>
android {
    namespace = "dev.example.purefusionstarter"
    compileSdk = 36
    defaultConfig {
        applicationId = identity["pluginId"] as String
        minSdk = identity["minSdk"] as Int
        targetSdk = 36
        versionCode = identity["versionCode"] as Int
        versionName = identity["version"] as String
        buildConfigField("int", "pluginApiLevel", (identity["apiLevel"] as Int).toString())
        for (name in listOf("pluginId", "displayName", "publisher", "description", "version", "minHostVersion"))
            buildConfigField("String", name, JsonOutput.toJson(identity[name]))
    }
    buildTypes { release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("debug") } }
    buildFeatures { buildConfig = true; resValues = false; shaders = false }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isReturnDefaultValues = true }
}
kotlin.compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
dependencies {
    compileOnly("dev.eliminater.purefusioniptv:plugin-api:1.0.0")
    // The isolated loader is child-first for bundled libraries; do not rely on a
    // release host keeping its own Kotlin runtime names after minification.
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.20")
    testImplementation("dev.eliminater.purefusioniptv:plugin-api:1.0.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
