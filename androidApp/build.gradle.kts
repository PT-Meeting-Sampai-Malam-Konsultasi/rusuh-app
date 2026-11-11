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
        // Mock or real host: NetworkConfig.MOCK_API in :shared, debug builds only (see docs/data-layer.md).
        getByName("debug") {
            // Optional debug-only backend mode override, e.g. ./gradlew assembleDebug -PbackendMode=Stub for the
            // offline demo. Empty keeps the default, real HTTP (see BackendMode and docs/data-layer.md).
            val backendMode = providers.gradleProperty("backendMode").getOrElse("")
            buildConfigField("String", "BACKEND_MODE", "\"$backendMode\"")
        }
        getByName("release") {
            isMinifyEnabled = false
            // Never overridable: a release build is always Production on real HTTP (NetworkConfig.startup).
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

// Guard: a release build can never select the mock API or stub data (see NetworkConfig and docs/data-layer.md).
// NetworkConfig.startup already ignores the flag unless BuildConfig.DEBUG; this also checks the generated release
// constants, so a changed build type cannot turn them on unnoticed.
val verifyReleaseNeverUsesMockApi by tasks.registering {
    dependsOn("generateReleaseBuildConfig")
    val releaseBuildConfig =
        layout.buildDirectory.file("generated/source/buildConfig/release/com/rusuh/app/android/BuildConfig.java")
    doLast {
        val generated = releaseBuildConfig.get().asFile.readText()
        check(generated.contains("boolean DEBUG = false")) { "The release build must have BuildConfig.DEBUG = false" }
        check(generated.contains("BACKEND_MODE = \"\"")) {
            "The release build must not override the backend mode (BACKEND_MODE must be empty)"
        }
        check(!generated.contains("Mock") && !generated.contains("Stub")) {
            "The release build config must not mention the mock or stub backend"
        }
    }
}
tasks.named("check") { dependsOn(verifyReleaseNeverUsesMockApi) }
