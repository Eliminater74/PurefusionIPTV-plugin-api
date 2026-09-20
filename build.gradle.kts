buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
    }
}

plugins {
    alias(libs.plugins.android.library)
}

group = "dev.eliminater.purefusioniptv"
version = "1.0.0"

android {
    namespace = "dev.eliminater.purefusioniptv.plugin"
    compileSdk = 36

    defaultConfig {
        minSdk = 25
    }

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

    // The compatibility contract in PluginApi is the one part of this module with behaviour rather
    // than just shape, and it is what the host uses to refuse a plugin before loading its code.
    // A module intended to ship publicly should be able to prove that rule on its own.
    testImplementation(libs.junit)
}
