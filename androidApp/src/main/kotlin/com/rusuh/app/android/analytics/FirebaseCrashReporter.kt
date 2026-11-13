package com.rusuh.app.android.analytics

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.rusuh.app.crash.CrashReporter
import com.rusuh.app.crash.SafeCrashReporter

class FirebaseCrashReporter(context: Context) : CrashReporter {
    private val crashlytics =
        runCatching {
            if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseCrashlytics.getInstance() else null
        }.onFailure { Log.w("FirebaseCrashReporter", "Crashlytics unavailable") }.getOrNull()
    private val safe =
        SafeCrashReporter(
            object : CrashReporter {
                override fun recordNonFatal(throwable: Throwable) {
                    crashlytics?.recordException(throwable)
                }

                override fun setCustomKey(
                    key: String,
                    value: String,
                ) {
                    crashlytics?.setCustomKey(key, value)
                }

                override fun log(message: String) {
                    crashlytics?.log(message)
                }

                override fun setUserId(id: String?) {
                    crashlytics?.setUserId(id.orEmpty())
                }

                override fun setCollectionEnabled(enabled: Boolean) {
                    crashlytics?.setCrashlyticsCollectionEnabled(enabled)
                }
            },
        )

    override fun recordNonFatal(throwable: Throwable) = safe.recordNonFatal(throwable)

    override fun setCustomKey(
        key: String,
        value: String,
    ) = safe.setCustomKey(key, value)

    override fun log(message: String) = safe.log(message)

    override fun setUserId(id: String?) = safe.setUserId(id)

    override fun setCollectionEnabled(enabled: Boolean) = safe.setCollectionEnabled(enabled)
}
