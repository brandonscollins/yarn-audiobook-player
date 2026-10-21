import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// One version string, used for both the app's versionName and the artifact name, so the
// build lands at yarn-<version>-debug.apk instead of app-debug.apk.
val appVersionName = "1.6"

// Derived version code: split major.minor[.patch], compute major*10000 + minor*100 + patch
// E.g. "1.2" -> 10200, "1.2.3" -> 10203
val appVersionCode = appVersionName.split(".").let {
    it[0].toInt() * 10000 + it[1].toInt() * 100 + (it.getOrNull(2)?.toInt() ?: 0)
}

// Load keystore config if it exists (created during setup; absent on fresh clones)
val keystoreProps = rootProject.file("keystore.properties").let { f ->
    if (f.exists()) Properties().apply { f.inputStream().use { load(it) } } else null
}

// Fallback when keystore.properties is absent (ADR-017): the same key, synced from OneDrive to every one of
// Brandon's machines. It is the promoted standard debug keystore (ADR-012), so its credentials are the public
// debug defaults, not a secret. Neither present: default debug signing, unsigned release, as before.
val sharedKeystore = File(System.getProperty("user.home"), ".claude/secrets/yarn-signing.keystore")
    .takeIf { keystoreProps == null && it.exists() }

base.archivesName = "yarn-$appVersionName"

android {
    namespace = "io.github.brandonscollins.yarn"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.brandonscollins.yarn"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        keystoreProps?.let { props ->
            create("yarn") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
        sharedKeystore?.let { f ->
            create("yarn") {
                storeFile = f
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("yarn")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("yarn")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Sanctioned in the design phase: real Pause/Replay30/Forward30/Bedtime/GraphicEq glyphs
    // instead of text labels and a hand-drawn pause bar (see decisions.md).
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json) // Android's org.json is a stub in unit tests; UpdaterTest parses latest.json
}
