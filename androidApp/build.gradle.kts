import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kover)
}

// Pin Kotlin's JVM target to 17 so it matches android.compileOptions (17)
// regardless of the JDK running Gradle. Without this, compileDebugKotlin
// follows the Gradle JDK (e.g. 21), causing an "Inconsistent JVM-target
// compatibility" error on JDK21 while CI on JDK17 stays green.
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

android {
    namespace = "com.rusuh.app.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.rusuh.app.android"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        // Environment per build type, parsed by AppEnvironment.fromName (see docs/data-layer.md).
        getByName("debug") {
            buildConfigField("String", "APP_ENVIRONMENT", "\"Mock\"")
        }
        getByName("release") {
            isMinifyEnabled = false
            buildConfigField("String", "APP_ENVIRONMENT", "\"Production\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
}
