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
}
