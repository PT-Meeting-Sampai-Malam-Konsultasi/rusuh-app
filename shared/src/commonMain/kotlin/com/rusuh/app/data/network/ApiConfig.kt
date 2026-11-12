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

    /**
     * True while any host is a `.invalid` placeholder (reserved TLD, RFC 2606, never resolves): that build reaches no
     * backend. No environment uses one today (all hosts are real); the check stays for hosts added later.
     */
    val usesPlaceholderHosts: Boolean =
        listOf(this.baseUrl, this.queueBaseUrl, this.payBaseUrl).any { url ->
            url.substringAfter("://").substringBefore('/').substringBefore(':').endsWith(".invalid")
        }

    override fun toString(): String = "ApiConfig(baseUrl=$baseUrl, queueBaseUrl=$queueBaseUrl, payBaseUrl=$payBaseUrl)"

    companion object {
        /** The real backend host. Its queue and auth services answer; the core/booking routes were not deployed yet. */
        internal const val REAL_HOST_URL = "https://api.rusuh-tiket.web.id"

        /**
         * Every route lives under `/v1` (found in the staging web app's configuration; the OpenAPI file lists no
         * server). Without it every call returns 404.
         */
        internal const val REAL_BASE_URL = "$REAL_HOST_URL/v1"

        // shortcut: the queue (`q.`) and payment (`pay.`) hosts are not confirmed yet, so every host points at the
        // one real host above. Give them their own constants here when the backend publishes them. Staging has no
        // host of its own either: it is the same real host as production.
        internal const val REAL_API_URL = REAL_BASE_URL
        internal const val REAL_QUEUE_URL = REAL_BASE_URL
        internal const val REAL_PAY_URL = REAL_BASE_URL

        private val PORT_PATTERN = Regex(""":(\d+)$""")
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
                AppEnvironment.Staging, AppEnvironment.Production ->
                    ApiConfig(REAL_API_URL, REAL_QUEUE_URL, REAL_PAY_URL)
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
            val port = PORT_PATTERN.find(url.substringAfter("://").substringBefore('/'))?.groupValues?.get(1)?.toInt()
            require(port == null || port in 1..MAX_PORT) { "ApiConfig.$name has an invalid port (1-65535): '$raw'" }
            return url.trimEnd('/')
        }
    }
}
