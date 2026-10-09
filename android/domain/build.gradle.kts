plugins {
    alias(libs.plugins.kotlin.jvm)
}

// No Android dependency on purpose: compiles fast with no resource processing, and
// the build makes it impossible to reach for Context from the scheduler.
kotlin {
    jvmToolchain(17)
}

