plugins { kotlin("jvm") version "2.3.20" }
val sdk = file(gradle.extra["pluginApiSourceDir"] as String)
kotlin {
    sourceSets.main {
        kotlin.srcDir(sdk.resolve("src/main/java/dev/eliminater/purefusioniptv/plugin/packaging"))
        kotlin.srcDir(sdk.resolve("src/main/java/dev/eliminater/purefusioniptv/plugin/api"))
        kotlin.include("**/PfpPackager.kt", "**/PfpSigner.kt", "**/PluginApi.kt", "**/BuildPfp.kt")
    }
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies { implementation(kotlin("stdlib")); implementation(localGroovy()); testImplementation("junit:junit:4.13.2") }
val keyDir = providers.environmentVariable("PUREFUSION_PFP_SIGNING_DIR")
val pfpFile = providers.gradleProperty("pfpFile")
for ((taskName, mode) in mapOf("createSigningKey" to "keygen", "checkSigningKey" to "keycheck",
    "packageDevelopment" to "development", "packageSigned" to "signed", "verifySignedPfp" to "verify")) {
    tasks.register<JavaExec>(taskName) {
        dependsOn("classes")
        // Ordering only, not dependencies: checking/signing must never implicitly
        // create keys. This also works when the user's Gradle enables parallel tasks.
        when (mode) {
            "keycheck" -> mustRunAfter("createSigningKey")
            "signed" -> mustRunAfter("createSigningKey", "checkSigningKey")
            "verify" -> mustRunAfter("packageSigned")
        }
        if (mode in listOf("development", "signed")) dependsOn(":plugin:assembleRelease")
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("BuildPfpKt")
        workingDir = rootProject.projectDir
        args(mode, layout.buildDirectory.dir(mode).get().asFile.absolutePath, keyDir.getOrElse(""), pfpFile.getOrElse(""))
    }
}
