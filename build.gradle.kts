// Top-level build file where you can add configuration options common to all sub-projects/modules.

// AGP 9 compiles Kotlin with a built-in Kotlin plugin (2.2.x). Current AndroidX/OkHttp/Coil/coroutines
// releases are built with Kotlin 2.4, so the compiler is raised here; AGP picks this version up.
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
}
