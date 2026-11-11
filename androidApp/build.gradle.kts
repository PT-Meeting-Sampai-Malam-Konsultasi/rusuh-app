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
            // Optional backend mode override, e.g. ./gradlew assembleDebug -PbackendMode=Real (Mockoon over HTTP).
            // Empty keeps the default for the environment (see BackendMode and docs/data-layer.md).
            val backendMode = providers.gradleProperty("backendMode").getOrElse("")
            buildConfigField("String", "BACKEND_MODE", "\"$backendMode\"")
        }
        getByName("release") {
            isMinifyEnabled = false
            buildConfigField("String", "APP_ENVIRONMENT", "\"Production\"")
            buildConfigField("String", "BACKEND_MODE", "\"\"")
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

// Guard: cleartext HTTP may only be configured in the debug source set (see docs/data-layer.md).
val verifyNoCleartextOutsideDebug by tasks.registering {
    val srcDir = layout.projectDirectory.dir("src")
    // The merged release manifest must be clean too, whatever a library or flavour might contribute.
    dependsOn("processReleaseManifest")
    val releaseManifest =
        layout.buildDirectory.file("intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml")
    doLast {
        val merged = releaseManifest.get().asFile.readText()
        check(!merged.contains("usesCleartextTraffic") && !merged.contains("networkSecurityConfig")) {
            "The merged release manifest must not allow cleartext or set a network security config"
        }
        val root = srcDir.asFile
        val manifest = root.resolve("main/AndroidManifest.xml").readText()
        check(!manifest.contains("usesCleartextTraffic") && !manifest.contains("networkSecurityConfig")) {
            "src/main/AndroidManifest.xml must not allow cleartext or set a network security config"
        }
        val configs = root.walkTopDown().filter { it.name == "network_security_config.xml" }.toList()
        check(configs.all { it.relativeTo(root).path.startsWith("debug/") }) {
            "network_security_config.xml is only allowed under src/debug: $configs"
        }
    }
}
tasks.named("check") { dependsOn(verifyNoCleartextOutsideDebug) }
