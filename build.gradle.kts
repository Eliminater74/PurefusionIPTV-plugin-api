buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
    }
}

plugins {
    alias(libs.plugins.android.library)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20"
}

group = "dev.eliminater.purefusioniptv"
version = "1.1.0"

android {
    namespace = "dev.eliminater.purefusioniptv.plugin.api"
    compileSdk = 36

    defaultConfig {
        minSdk = 25
        buildConfigField("String", "SDK_LIBRARY_VERSION", "\"$version\"")
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.gson)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    testImplementation(libs.junit)
}
