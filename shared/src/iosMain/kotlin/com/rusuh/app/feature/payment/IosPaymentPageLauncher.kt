package com.rusuh.app.feature.payment

import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController
import platform.UIKit.UIApplication

/**
 * iOS implementation of [PaymentPageLauncher] using SFSafariViewController.
 *
 * SFSafariViewController provides a secure, trusted UI for the Midtrans Snap page:
 * - Shows the real URL in the address bar
 * - Shares cookies/session with Safari for payment credentials
 * - Supports bank app deeplinks for e-wallet payments
 * - Returns to the app via Universal Link when payment completes
 *
 * Note: The actual presentation of SFSafariViewController must be done from the UI layer.
 * This class provides the URL and the view controller; the Compose layer presents it.
 */
class IosPaymentPageLauncher : PaymentPageLauncher {
    /**
     * Create an SFSafariViewController for the given URL, or null if the URL is invalid.
     * The caller must present this view controller from their UIViewController.
     */
    fun createSafariViewController(url: String): SFSafariViewController? {
        val nsUrl = NSURL.URLWithString(url) ?: return null
        return SFSafariViewController(nsUrl)
    }

    /**
     * Launch the payment page. On iOS, this opens the URL in Safari as a fallback.
     * For in-app presentation, use [createSafariViewController] instead.
     */
    override fun launch(url: String): Boolean {
        val nsUrl = NSURL.URLWithString(url) ?: return false
        return UIApplication.sharedApplication.openURL(nsUrl)
    }
}
