package com.rusuh.app.di

import com.rusuh.app.crash.ConsentAwareCrashReporter
import com.rusuh.app.crash.CrashReporter
import com.rusuh.app.crash.NoOpCrashReporter
import com.rusuh.app.crash.UnexpectedErrorReporter
import com.rusuh.app.data.network.AppEnvironment
import com.rusuh.app.data.network.LocaleProvider
import org.koin.core.module.Module
import org.koin.dsl.module

fun crashReporterModule(
    backendMode: BackendMode,
    platformCrashReporter: () -> CrashReporter = {
        NoOpCrashReporter
    },
): Module =
    module {
        single<CrashReporter> {
            val env = get<AppEnvironment>()
            val locale = get<LocaleProvider>()
            ConsentAwareCrashReporter(platformCrashReporter(), get(), env, backendMode).also { reporter ->
                UnexpectedErrorReporter.install(reporter)
                reporter.setCustomKey("flavor", env.name)
                reporter.setCustomKey("backend_mode", backendMode.name)
                reporter.setCustomKey("language", locale.language().tag)
            }
        }
    }
