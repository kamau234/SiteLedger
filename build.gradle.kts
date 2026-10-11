// AGP 9 ships with built-in Kotlin support (no org.jetbrains.kotlin.android plugin).
// AGP 9.4.1 bundles Kotlin 2.2.10; upgrading the Kotlin Gradle Plugin here is the
// documented mechanism. Kotlin 2.3.21 is chosen to match supabase-kt 3.6.0, which is
// built with Kotlin 2.3.21 (verified from its published Gradle module metadata).
buildscript {
    repositories {
        mavenCentral()
        google()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
