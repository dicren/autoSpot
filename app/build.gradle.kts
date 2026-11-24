import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 has built-in Kotlin: no `kotlin-android` plugin. Compose still needs
    // its compiler plugin, whose version must match the Kotlin version.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.spotless)
    alias(libs.plugins.baselineprofile)
}

/** Reads the Maps key from `local.properties`, falling back to the committed template. */
fun mapsApiKey(): String {
    val candidates = listOf(
        rootProject.file("local.properties"),
        rootProject.file("local.defaults.properties")
    )
    return candidates.firstNotNullOfOrNull { file ->
        if (!file.exists()) return@firstNotNullOfOrNull null
        Properties().apply { file.inputStream().use { load(it) } }.getProperty("MAPS_API_KEY")
    } ?: "YOUR_API_KEY_HERE"
}

android {
    namespace = "com.dicren.autospot"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.dicren.autospot"
        minSdk = 31
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The Maps SDK key is injected as a manifest placeholder instead of using the
        // secrets plugin, which does not populate unit-test manifests under AGP 9.
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey()
    }

    signingConfigs {
        val keystorePropertiesFile = rootProject.file("keystore.properties")
        if (keystorePropertiesFile.exists()) {
            val keystoreProperties = Properties().apply {
                keystorePropertiesFile.inputStream().use { load(it) }
            }
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    lint {
        // False positive: the app has no fragments at all. The detector looks for the
        // androidx.fragment artifact and fires when it is absent.
        disable += "InvalidFragmentVersionForActivityResult"
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    sourceSets {
        // Room's MigrationTestHelper reads exported schemas from the app's assets. They are only
        // needed for local unit tests, so they go into the debug variant and never into release.
        getByName("debug") {
            assets.srcDir("$projectDir/schemas")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Location services
    implementation(libs.play.services.location)

    // Google Maps
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Coroutines for Play Services
    implementation(libs.kotlinx.coroutines.play.services)

    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.sqlite.bundled)
    implementation(libs.sqlite.async)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.room.testing)
    testImplementation(libs.sqlite.framework)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)

    baselineProfile(project(":baselineprofile"))

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(
            mapOf(
                // Test names are backticked sentences; Compose state/api names are descriptive.
                "ktlint_standard_function-naming" to "disabled",
                "ktlint_standard_class-naming" to "disabled",
            )
        )
    }
}
