package com.rusuh.app.android.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.setConsent
import com.rusuh.app.analytics.Analytics

/** Firebase Analytics implementation. Safe to create even when google-services.json is missing. */
class FirebaseAnalyticsImpl(context: Context) : Analytics {
    private val firebaseAnalytics: FirebaseAnalytics? = createAnalytics(context)

    init {
        firebaseAnalytics?.setAnalyticsCollectionEnabled(false)
        firebaseAnalytics?.setConsent {
            analyticsStorage = FirebaseAnalytics.ConsentStatus.DENIED
        }
    }

    override fun log(
        event: String,
        params: Map<String, Any>,
    ) {
        val bundle =
            Bundle().apply {
                params.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Double -> putDouble(key, value)
                        is Boolean -> putBoolean(key, value)
                        else -> putString(key, value.toString())
                    }
                }
            }
        firebaseAnalytics?.logEvent(event, bundle)
    }

    override fun setUserId(id: String?) {
        firebaseAnalytics?.setUserId(id)
    }

    override fun setUserProperty(
        name: String,
        value: String?,
    ) {
        firebaseAnalytics?.setUserProperty(name, value)
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        firebaseAnalytics?.setAnalyticsCollectionEnabled(enabled)
        firebaseAnalytics?.setConsent {
            analyticsStorage =
                if (enabled) {
                    FirebaseAnalytics.ConsentStatus.GRANTED
                } else {
                    FirebaseAnalytics.ConsentStatus.DENIED
                }
        }
    }

    private companion object {
        private const val TAG = "FirebaseAnalyticsImpl"

        fun createAnalytics(context: Context): FirebaseAnalytics? =
            try {
                if (FirebaseApp.getApps(context).isNotEmpty()) {
                    FirebaseAnalytics.getInstance(context)
                } else {
                    null
                }
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                Log.w(TAG, "Firebase Analytics unavailable", e)
                null
            }
    }
}
