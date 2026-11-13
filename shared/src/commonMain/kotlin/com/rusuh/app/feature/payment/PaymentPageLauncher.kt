package com.rusuh.app.feature.payment

/**
 * Interface for launching the Midtrans Snap payment page in a system browser tab.
 *
 * On Android: Chrome Custom Tabs via `androidx.browser`.
 * On iOS: SFSafariViewController via platform APIs.
 *
 * The system tab approach (vs. in-app WebView) is required because:
 * 1. Midtrans SDK is deprecated; Snap hosted page is the recommended integration
 * 2. Users can see the real URL (trust indicator)
 * 3. Payment credentials (e.g., bank app deeplinks) work correctly
 * 4. Return to the app happens via App Link / Universal Link
 *
 * Implementations are bound per platform in the Koin PlatformModule.
 */
interface PaymentPageLauncher {
    /**
     * Launch the Snap payment page at [url] in a system browser tab.
     * Returns true if the page was launched successfully, false otherwise.
     */
    fun launch(url: String): Boolean
}

/**
 * Stub implementation for testing that records launches without opening anything.
 */
class FakePaymentPageLauncher : PaymentPageLauncher {
    private val _launches = mutableListOf<String>()
    val launches: List<String> get() = _launches.toList()
    var shouldSucceed: Boolean = true

    override fun launch(url: String): Boolean {
        _launches += url
        return shouldSucceed
    }

    fun reset() {
        _launches.clear()
        shouldSucceed = true
    }
}
