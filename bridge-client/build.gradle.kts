// Bridge API v1 client (plan §18): DTOs, mapping to :core domain, HTTP transport and the polling feed.
// Pure Kotlin/JVM so it is tested with MockWebServer and the contract fixtures without a device.
// Android-only pieces (Keystore token vault, permissions) live in :app behind the interfaces declared here.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.test {
    // Contract fixtures shared with the PC bridge repository (gtav-riding-companion-bridge/bridge/fixtures/v1), checked out
    // next to this one. Override with -PbridgeRepo=PATH. The old folder names gtbikev-companion-bridge and companion-web are still accepted.
    val bridgeRepo = providers.gradleProperty("bridgeRepo").orNull?.let(::file)
        ?: listOf("../gtav-riding-companion-bridge", "../gtbikev-companion-bridge", "../companion-web").map(rootProject.projectDir::resolve).firstOrNull { it.isDirectory }
        ?: rootProject.projectDir.resolve("../gtav-riding-companion-bridge")
    systemProperty("fixturesDir", bridgeRepo.resolve("bridge/fixtures/v1").canonicalPath)
}
