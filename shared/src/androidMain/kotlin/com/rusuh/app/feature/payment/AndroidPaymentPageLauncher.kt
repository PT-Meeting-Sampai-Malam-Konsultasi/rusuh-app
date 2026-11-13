package com.rusuh.app.feature.payment

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Android implementation of [PaymentPageLauncher] using Chrome Custom Tabs.
 *
 * Chrome Custom Tabs provide a secure, trusted UI for the Midtrans Snap page:
 * - Shows the real URL in the address bar
 * - Shares cookies/session with Chrome for payment credentials
 * - Supports bank app deeplinks for e-wallet payments
 * - Returns to the app via App Link when payment completes
 *
 * Falls back to the default browser if Custom Tabs are not available.
 */
class AndroidPaymentPageLauncher(
    private val context: Context,
) : PaymentPageLauncher {
    override fun launch(url: String): Boolean =
        try {
            val uri = Uri.parse(url)
            val customTabsIntent =
                CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                    .build()
            // Add FLAG_ACTIVITY_NEW_TASK when launching from a non-Activity context
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(context, uri)
            true
        } catch (_: Exception) {
            // Fall back to default browser
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(browserIntent)
                true
            } catch (_: Exception) {
                false
            }
        }
}
