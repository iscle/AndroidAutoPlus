// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 provides built-in Kotlin support (bundling KGP 2.2.10). We pin the newer Kotlin
        // version we actually want to use here, which also keeps it aligned with the Compose
        // compiler plugin version below. See https://kotl.in/gradle/agp-built-in-kotlin
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    }
}

plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
}