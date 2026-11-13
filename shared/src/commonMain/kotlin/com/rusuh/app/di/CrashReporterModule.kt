package com.rusuh.app.di

import com.rusuh.app.crash.ConsentAwareCrashReporter
import com.rusuh.app.crash.CrashReporter
import com.rusuh.app.crash.NoOpCrashReporter
import com.rusuh.app.crash.UnexpectedErrorReporter
import com.rusuh.app.data.network.AppEnvironment
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
            ConsentAwareCrashReporter(platformCrashReporter(), get(), get<AppEnvironment>(), backendMode).also {
                UnexpectedErrorReporter.install(it)
            }
        }
    }
