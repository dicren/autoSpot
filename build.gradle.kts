// Top-level build file. AGP 9 compiles Kotlin itself (built-in Kotlin), so the
// `org.jetbrains.kotlin.android` plugin must NOT be applied anywhere. The
// classpath entries below exist only to pin KGP and KSP above the versions AGP
// bundles by default; the Compose compiler plugin version must match KGP.
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:${libs.versions.ksp.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
}
