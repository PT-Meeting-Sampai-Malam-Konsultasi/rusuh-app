import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kover)
}

// Apply google-services plugin only when google-services.json exists, so CI builds without the file work.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
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
        // CI passes -PversionCode=<run number> so every distributed build is distinguishable.
        versionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FLAVOR DIMENSION: "environment"
    // ─────────────────────────────────────────────────────────────────────────
    flavorDimensions += "environment"

    productFlavors {
        create("staging") {
            dimension = "environment"
            // Same applicationId for both flavors (same Firebase app).
            applicationId = "com.rusuh.app.android"
            // Launcher name via resValue so we don't need per-flavor res folders.
            resValue("string", "app_name", "Rusuh Staging")
            // ENVIRONMENT tells NetworkConfig which API to use: Staging flavor = "Staging".
            buildConfigField("String", "ENVIRONMENT", "\"Staging\"")
            // Staging allows mock/stub in debug builds (controlled by isDebug && this flag).
            buildConfigField("Boolean", "ALLOW_DEBUG_OPTIONS", "true")
        }
        create("production") {
            dimension = "environment"
            applicationId = "com.rusuh.app.android"
            resValue("string", "app_name", "Rusuh")
            // Production flavor = "Production" environment always.
            buildConfigField("String", "ENVIRONMENT", "\"Production\"")
            // Production never allows mock/stub, even in debug builds.
            buildConfigField("Boolean", "ALLOW_DEBUG_OPTIONS", "false")
        }
    }

    buildTypes {
        getByName("debug") {
            // Note: BACKEND_MODE and MOCK_API_OVERRIDE are set per-variant below to support
            // production flavor ignoring these flags.
        }
        getByName("release") {
            isMinifyEnabled = false
            // Note: BACKEND_MODE and MOCK_API_OVERRIDE are set per-variant below.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Read the Gradle properties once for use in variant configuration.
val backendModeProp = providers.gradleProperty("backendMode").getOrElse("")
val mockApiProp = providers.gradleProperty("mockApi").getOrElse("")
require(mockApiProp.isEmpty() || mockApiProp == "true" || mockApiProp == "false") {
    "-PmockApi must be true or false"
}

// Configure BACKEND_MODE and MOCK_API_OVERRIDE per variant.
// Staging debug: uses the Gradle properties (allows mock/stub).
// Production (any build type) and all release builds: always empty (no overrides).
android.applicationVariants.configureEach {
    val isProduction = flavorName == "production"
    val isRelease = buildType.name == "release"

    if (isProduction || isRelease) {
        // Production flavor or release build: never allow overrides.
        buildConfigField("String", "BACKEND_MODE", "\"\"")
        buildConfigField("String", "MOCK_API_OVERRIDE", "\"\"")
    } else {
        // Staging debug: allow the Gradle properties.
        buildConfigField("String", "BACKEND_MODE", "\"$backendModeProp\"")
        buildConfigField("String", "MOCK_API_OVERRIDE", "\"$mockApiProp\"")
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
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// ─────────────────────────────────────────────────────────────────────────────
// VERIFICATION GUARDS (flavor-aware)
// ─────────────────────────────────────────────────────────────────────────────

// Guard: cleartext HTTP is off in the main and release manifests; only the debug source set may allow it for the mock.
val verifyNoCleartextOutsideDebug by tasks.registering {
    val srcDir = layout.projectDirectory.dir("src")
    // Depend on all release manifest processing tasks (both flavors).
    dependsOn("processStagingReleaseManifest", "processProductionReleaseManifest")
    val stagingReleaseManifest =
        layout.buildDirectory.file(
            "intermediates/merged_manifests/stagingRelease/processStagingReleaseManifest/AndroidManifest.xml",
        )
    val productionReleaseManifest =
        layout.buildDirectory.file(
            "intermediates/merged_manifests/productionRelease/processProductionReleaseManifest/AndroidManifest.xml",
        )
    doLast {
        // Check main manifest: cleartext is declared off (Android 7 to 8 would allow it by default).
        val mainManifest = srcDir.file("main/AndroidManifest.xml").asFile.readText()
        check(mainManifest.contains("android:usesCleartextTraffic=\"false\"")) {
            "src/main/AndroidManifest.xml must declare android:usesCleartextTraffic=\"false\""
        }
        check(!mainManifest.contains("networkSecurityConfig")) {
            "src/main/AndroidManifest.xml must not set a network security config"
        }
        // Check release merged manifests for all flavors.
        listOf(stagingReleaseManifest, productionReleaseManifest).forEach { provider ->
            val file = provider.get().asFile
            if (file.exists()) {
                val merged = file.readText()
                val cleartextOff = merged.contains("android:usesCleartextTraffic=\"false\"")
                check(cleartextOff && !merged.contains("networkSecurityConfig")) {
                    "The merged release manifest ${file.name} must disable cleartext, no network security config"
                }
            }
        }
        // Check network_security_config.xml location.
        val configs = srcDir.asFile.walkTopDown().filter { it.name == "network_security_config.xml" }.toList()
        check(configs.all { it.relativeTo(srcDir.asFile).path.startsWith("debug/") }) {
            "network_security_config.xml is only allowed under src/debug: $configs"
        }
    }
}
tasks.named("check") { dependsOn(verifyNoCleartextOutsideDebug) }

// Guard: a release build can never select the mock API or stub data.
// Now flavor-aware: checks both stagingRelease and productionRelease.
val verifyReleaseNeverUsesMockApi by tasks.registering {
    dependsOn("generateStagingReleaseBuildConfig", "generateProductionReleaseBuildConfig")
    val stagingReleaseBuildConfig =
        layout.buildDirectory.file(
            "generated/source/buildConfig/staging/release/com/rusuh/app/android/BuildConfig.java",
        )
    val productionReleaseBuildConfig =
        layout.buildDirectory.file(
            "generated/source/buildConfig/production/release/com/rusuh/app/android/BuildConfig.java",
        )
    doLast {
        listOf("staging" to stagingReleaseBuildConfig, "production" to productionReleaseBuildConfig).forEach { pair ->
            val (flavor, provider) = pair
            val file = provider.get().asFile
            if (file.exists()) {
                val generated = file.readText()
                check(Regex("""boolean\s+DEBUG\s*=\s*false\s*;""").containsMatchIn(generated)) {
                    "The ${flavor}Release build must have BuildConfig.DEBUG = false"
                }
                check(Regex("""String\s+BACKEND_MODE\s*=\s*"";""").containsMatchIn(generated)) {
                    "The ${flavor}Release build must not override the backend mode"
                }
                check(Regex("""String\s+MOCK_API_OVERRIDE\s*=\s*"";""").containsMatchIn(generated)) {
                    "The ${flavor}Release build must not override the mock API"
                }
            }
        }
    }
}
tasks.named("check") { dependsOn(verifyReleaseNeverUsesMockApi) }

// Hook the guard into all release assemble/bundle tasks (both flavors + aggregate).
tasks.matching {
    it.name == "assembleRelease" ||
        it.name == "bundleRelease" ||
        it.name.matches(Regex("assemble(Staging|Production)Release")) ||
        it.name.matches(Regex("bundle(Staging|Production)Release"))
}.configureEach {
    dependsOn(verifyReleaseNeverUsesMockApi)
}

// Guard: production flavor must NEVER allow debug options, even in debug builds.
val verifyProductionFlavorHasNoDebugOptions by tasks.registering {
    dependsOn("generateProductionDebugBuildConfig", "generateProductionReleaseBuildConfig")
    val productionDebugBuildConfig =
        layout.buildDirectory.file(
            "generated/source/buildConfig/production/debug/com/rusuh/app/android/BuildConfig.java",
        )
    val productionReleaseBuildConfig =
        layout.buildDirectory.file(
            "generated/source/buildConfig/production/release/com/rusuh/app/android/BuildConfig.java",
        )
    doLast {
        listOf("productionDebug" to productionDebugBuildConfig, "productionRelease" to productionReleaseBuildConfig)
            .forEach { pair ->
                val (variant, provider) = pair
                val file = provider.get().asFile
                if (file.exists()) {
                    val generated = file.readText()
                    check(Regex("""Boolean\s+ALLOW_DEBUG_OPTIONS\s*=\s*false\s*;""").containsMatchIn(generated)) {
                        "Production flavor must have ALLOW_DEBUG_OPTIONS = false in $variant"
                    }
                    check(Regex("""String\s+BACKEND_MODE\s*=\s*"";""").containsMatchIn(generated)) {
                        "Production flavor must have empty BACKEND_MODE in $variant"
                    }
                    check(Regex("""String\s+MOCK_API_OVERRIDE\s*=\s*"";""").containsMatchIn(generated)) {
                        "Production flavor must have empty MOCK_API_OVERRIDE in $variant"
                    }
                }
            }
    }
}
tasks.named("check") { dependsOn(verifyProductionFlavorHasNoDebugOptions) }

// Guard: merged manifests must declare INTERNET (both flavors × both build types).
val verifyMergedManifestsHaveInternet by tasks.registering {
    dependsOn(
        "processStagingDebugManifest",
        "processStagingReleaseManifest",
        "processProductionDebugManifest",
        "processProductionReleaseManifest",
    )
    val manifests =
        listOf(
            "stagingDebug" to "processStagingDebugManifest",
            "stagingRelease" to "processStagingReleaseManifest",
            "productionDebug" to "processProductionDebugManifest",
            "productionRelease" to "processProductionReleaseManifest",
        ).map { (variant, task) ->
            variant to layout.buildDirectory.file("intermediates/merged_manifests/$variant/$task/AndroidManifest.xml")
        }
    doLast {
        manifests.forEach { pair ->
            val (variant, provider) = pair
            val file = provider.get().asFile
            if (file.exists()) {
                val merged = file.readText()
                val internet = Regex("""<uses-permission[^>]*android:name="android\.permission\.INTERNET"""")
                check(internet.containsMatchIn(merged)) {
                    "The merged $variant manifest must declare android.permission.INTERNET"
                }
            }
        }
    }
}
tasks.named("check") { dependsOn(verifyMergedManifestsHaveInternet) }
