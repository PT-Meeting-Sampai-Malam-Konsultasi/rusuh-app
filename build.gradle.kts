plugins {
    // Apply plugins to subprojects via `apply false`; versions declared here.
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.googleServices) apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        parallel = true
        source.setFrom(files("src"))
    }
}

dependencies {
    kover(project(":shared"))
    kover(project(":androidApp"))
}

kover {
    reports {
        // Coverage is measured only for the code unit tests belong to: ViewModels, use cases, repositories and API
        // services. Views, navigation, DI, DTOs/mappers/models, stubs, platform code and generated code are out.
        filters {
            includes {
                classes(
                    "*ViewModel*",
                    "*UseCase*",
                    "*Repository*",
                    "*ApiService*",
                    "*PaymentReturnLink*",
                    "com.rusuh.app.data.repository.*",
                    "com.rusuh.app.domain.usecase.*",
                )
            }
            excludes {
                classes("*Stub*", "*Fake*", "*\$\$serializer", "*ComposableSingletons*")
            }
        }
        total {
            xml {
                onCheck = false
                xmlFile = layout.buildDirectory.file("reports/kover/report.xml")
            }
            html {
                onCheck = false
                htmlDir = layout.buildDirectory.dir("reports/kover/html")
            }
        }
    }
}
