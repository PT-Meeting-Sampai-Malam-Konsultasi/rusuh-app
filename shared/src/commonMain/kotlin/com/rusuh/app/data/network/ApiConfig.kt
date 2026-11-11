package com.rusuh.app.data.network

/**
 * Base URLs of the three independently configurable backend hosts: the main API (`baseUrl`), the queue
 * host (`q.`, [queueBaseUrl]) and the payment host (`pay.`, [payBaseUrl]). Every value is validated and
 * normalised (no trailing slash) on construction, so a bad configuration fails at startup instead of on
 * the first request. Build one per environment with [forEnvironment].
 */
class ApiConfig(
    baseUrl: String,
    queueBaseUrl: String,
    payBaseUrl: String,
) {
    val baseUrl: String = normalise("baseUrl", baseUrl)
    val queueBaseUrl: String = normalise("queueBaseUrl", queueBaseUrl)
    val payBaseUrl: String = normalise("payBaseUrl", payBaseUrl)

    override fun toString(): String = "ApiConfig(baseUrl=$baseUrl, queueBaseUrl=$queueBaseUrl, payBaseUrl=$payBaseUrl)"

    companion object {
        // shortcut: the real hosts are not known yet (the API spec has no `servers`); replace these
        // placeholders when the backend publishes them. They use the reserved `.invalid` TLD (RFC 2606), which
        // never resolves, so a build shipped with them fails with a network error instead of reaching a
        // host nobody confirmed. Each host is configured independently.
        internal const val PLACEHOLDER_PROD_API_URL = "https://api.rusuh.invalid"
        internal const val PLACEHOLDER_PROD_QUEUE_URL = "https://q.rusuh.invalid"
        internal const val PLACEHOLDER_PROD_PAY_URL = "https://pay.rusuh.invalid"

        // shortcut: placeholder staging hosts, same caveat as production.
        internal const val PLACEHOLDER_STAGING_API_URL = "https://api.staging.rusuh.invalid"
        internal const val PLACEHOLDER_STAGING_QUEUE_URL = "https://q.staging.rusuh.invalid"
        internal const val PLACEHOLDER_STAGING_PAY_URL = "https://pay.staging.rusuh.invalid"

        private const val MAX_PORT = 65535
        private val URL_PATTERN =
            Regex(
                """^https?://[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?(:\d{1,5})?(/[^\s?#]*)?$""",
                RegexOption.IGNORE_CASE,
            )
        private val SCHEME_PATTERN = Regex("""^https?://""", RegexOption.IGNORE_CASE)

        /**
         * Configuration for [environment]. [mockBaseUrl] is the platform-specific mock address, used for
         * all three hosts of [AppEnvironment.Mock] (the single Mockoon instance serves them all).
         */
        fun forEnvironment(
            environment: AppEnvironment,
            mockBaseUrl: String = platformMockBaseUrl,
        ): ApiConfig =
            when (environment) {
                AppEnvironment.Mock -> ApiConfig(mockBaseUrl, mockBaseUrl, mockBaseUrl)
                AppEnvironment.Staging ->
                    ApiConfig(
                        PLACEHOLDER_STAGING_API_URL,
                        PLACEHOLDER_STAGING_QUEUE_URL,
                        PLACEHOLDER_STAGING_PAY_URL,
                    )
                AppEnvironment.Production ->
                    ApiConfig(PLACEHOLDER_PROD_API_URL, PLACEHOLDER_PROD_QUEUE_URL, PLACEHOLDER_PROD_PAY_URL)
            }

        private fun normalise(
            name: String,
            raw: String,
        ): String {
            val url = raw.trim()
            require(url.isNotEmpty()) { "ApiConfig.$name must not be blank" }
            require(
                SCHEME_PATTERN.containsMatchIn(url),
            ) { "ApiConfig.$name must start with http:// or https://: '$raw'" }
            require(URL_PATTERN.matches(url) && !url.contains("..")) {
                "ApiConfig.$name is not a well-formed http(s) URL: '$raw'"
            }
            val port = Regex(""":(\d+)(/|$)""").find(url.substringAfter("://"))?.groupValues?.get(1)?.toInt()
            require(port == null || port in 1..MAX_PORT) { "ApiConfig.$name has an invalid port (1-65535): '$raw'" }
            return url.trimEnd('/')
        }
    }
}
