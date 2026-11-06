// Pure Kotlin domain module: models, repository interfaces, rules and demo scenarios.
// No Android, HTTP or CLR types (PLAN_ANDROID.md §6).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// JDK 17 toolchain (installed; foojay can provision it) instead of the Gradle daemon JDK, and the same
// bytecode level as :app.
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
