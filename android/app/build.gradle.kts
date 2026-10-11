import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Firebase is wired only once android/app/google-services.json exists, so the build stays green
// without it. Without the file FirebaseApp never initialises and the app falls back to no-op analytics.
if (file("google-services.json").exists()) {
    pluginManager.apply(libs.plugins.google.services.get().pluginId)
    pluginManager.apply(libs.plugins.firebase.crashlytics.get().pluginId)
}

android {
    namespace = "com.exam.assistant"
    compileSdk = 35

    // RevenueCat keys from android/local.properties (gitignored). Empty means RevenueCat is off:
    // the paywall stays closed. Debug uses the Test Store key (revenuecat.testApiKey) when set;
    // release only ever uses the Play key (revenuecat.apiKey) — a Test Store key must never ship.
    val local = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    val playKey = local.getProperty("revenuecat.apiKey", "")
    val testKey = local.getProperty("revenuecat.testApiKey", "").ifBlank { playKey }

    defaultConfig {
        applicationId = "com.exam.assistant"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.0"

        buildConfigField("String", "REVENUECAT_API_KEY", "\"$playKey\"")
    }

    // Release signing comes from android/keystore.properties (storeFile, storePassword, keyAlias,
    // keyPassword), which is gitignored. Without the file the release build is left unsigned.
    val keystorePropsFile = rootProject.file("keystore.properties")
    val releaseSigning = if (keystorePropsFile.exists()) {
        val props = Properties().apply { keystorePropsFile.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = rootProject.file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    } else {
        null
    }

    buildTypes {
        release {
            signingConfig = releaseSigning
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "REVENUECAT_API_KEY", "\"$testKey\"")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":domain"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:design"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:home"))
    implementation(project(":feature:syllabus"))
    implementation(project(":feature:focus"))
    implementation(project(":feature:progress"))
    implementation(project(":feature:settings"))

    implementation(libs.core.ktx)
    implementation(libs.splashscreen)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.coroutines.android)
    implementation(libs.glance.appwidget)
    // Glance runs on WorkManager; a direct dependency only to initialise it on demand (see SteadylineApp).
    implementation(libs.work.runtime)
    implementation(libs.revenuecat.purchases.ui)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
