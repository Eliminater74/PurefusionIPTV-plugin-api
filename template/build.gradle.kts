// Open this directory as an independent Android Studio project.
tasks.register("check") { dependsOn(":plugin:testDebugUnitTest", ":packaging:test") }
