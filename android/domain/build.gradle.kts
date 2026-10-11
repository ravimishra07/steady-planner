plugins {
    alias(libs.plugins.kotlin.jvm)
}

// No Android dependency on purpose: tests run on the JVM in milliseconds and
// the build makes it impossible to reach for Context from the scheduler.
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
