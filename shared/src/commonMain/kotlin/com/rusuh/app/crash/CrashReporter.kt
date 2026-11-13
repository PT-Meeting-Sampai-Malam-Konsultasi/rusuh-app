package com.rusuh.app.crash

import com.rusuh.app.analytics.AnalyticsConsent
import com.rusuh.app.analytics.AnalyticsConsentStore
import com.rusuh.app.data.network.AppEnvironment
import com.rusuh.app.di.BackendMode

interface CrashReporter {
    fun recordNonFatal(throwable: Throwable)

    fun setCustomKey(
        key: String,
        value: String,
    )

    fun log(message: String)

    fun setUserId(id: String?)

    fun setCollectionEnabled(enabled: Boolean)
}

object NoOpCrashReporter : CrashReporter {
    override fun recordNonFatal(throwable: Throwable) = Unit

    override fun setCustomKey(
        key: String,
        value: String,
    ) = Unit

    override fun log(message: String) = Unit

    override fun setUserId(id: String?) = Unit

    override fun setCollectionEnabled(enabled: Boolean) = Unit
}

fun scrubCrashMessage(message: String?): String =
    message.orEmpty()
        .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[redacted-email]")
        .replace(Regex("\\b\\d{16}\\b"), "[redacted-number]")
        .replace(Regex("\\b[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b"), "[redacted-token]")

class SafeCrashReporter(private val delegate: CrashReporter) : CrashReporter by delegate {
    override fun recordNonFatal(throwable: Throwable) {
        delegate.recordNonFatal(RuntimeException(scrubCrashMessage(throwable.message)))
    }
}

class ConsentAwareCrashReporter(
    private val delegate: CrashReporter,
    private val consent: AnalyticsConsentStore,
    private val environment: AppEnvironment,
    private val backendMode: BackendMode,
) : CrashReporter {
    private val allowed get() = environment != AppEnvironment.Mock && backendMode != BackendMode.Stub
    private val enabled get() = allowed && consent.consent.value == AnalyticsConsent.Granted

    override fun recordNonFatal(throwable: Throwable) {
        if (enabled) delegate.recordNonFatal(throwable)
    }

    override fun setCustomKey(
        key: String,
        value: String,
    ) {
        if (enabled) delegate.setCustomKey(key, value)
    }

    override fun log(message: String) {
        if (enabled) delegate.log(scrubCrashMessage(message))
    }

    override fun setUserId(id: String?) {
        if (allowed) delegate.setUserId(if (enabled) id else null)
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        if (allowed) delegate.setCollectionEnabled(enabled && consent.consent.value == AnalyticsConsent.Granted)
    }
}
