package com.rusuh.app.data.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ApiConfigTest {
    private fun config(
        api: String = "https://api.test",
        queue: String = "https://q.test",
        pay: String = "https://pay.test",
    ) = ApiConfig(api, queue, pay)

    private fun failure(block: () -> Unit): String =
        assertFailsWith<IllegalArgumentException> { block() }.message.orEmpty()

    @Test
    fun hostsAreIndependent() {
        val config = config()
        assertEquals("https://api.test", config.baseUrl)
        assertEquals("https://q.test", config.queueBaseUrl)
        assertEquals("https://pay.test", config.payBaseUrl)
    }

    @Test
    fun trailingSlashesAndWhitespaceAreNormalised() {
        val config = config(api = " https://api.test/ ", queue = "https://q.test///", pay = "https://pay.test/v1/")
        assertEquals("https://api.test", config.baseUrl)
        assertEquals("https://q.test", config.queueBaseUrl)
        assertEquals("https://pay.test/v1", config.payBaseUrl)
    }

    @Test
    fun portIsAccepted() {
        assertEquals("http://10.0.2.2:3000", config(api = "http://10.0.2.2:3000/").baseUrl)
    }

    @Test
    fun blankUrlFailsNamingTheProperty() {
        listOf("", "   ").forEach { blank ->
            assertTrue(failure { config(api = blank) }.contains("baseUrl"))
            assertTrue(failure { config(queue = blank) }.contains("queueBaseUrl"))
            assertTrue(failure { config(pay = blank) }.contains("payBaseUrl"))
        }
    }

    @Test
    fun nonHttpSchemeFails() {
        listOf("ftp://api.test", "api.test", "ws://api.test", "file:///etc/passwd").forEach { bad ->
            val message = failure { config(api = bad) }
            assertTrue(message.contains("http://"), message)
        }
    }

    @Test
    fun malformedUrlFails() {
        listOf(
            "http://",
            "https:// api.test",
            "http://api.test?x=1",
            "http://api.test#f",
            "http://-bad",
            "http://a:b@c",
            "http://a..b",
            "http://api.test:0",
            "http://api.test:65536",
            "http://api.test:99999/x",
        )
            .forEach { bad ->
                val message = failure { config(pay = bad) }
                assertTrue(message.contains("payBaseUrl"), message)
            }
    }

    @Test
    fun mockUsesOneUrlForAllHosts() {
        val config = ApiConfig.forEnvironment(AppEnvironment.Mock, mockBaseUrl = "http://10.0.2.2:3000")
        assertEquals("http://10.0.2.2:3000", config.baseUrl)
        assertEquals("http://10.0.2.2:3000", config.queueBaseUrl)
        assertEquals("http://10.0.2.2:3000", config.payBaseUrl)
    }

    @Test
    fun mockDefaultsToThePlatformMockUrl() {
        assertEquals(platformMockBaseUrl, ApiConfig.forEnvironment(AppEnvironment.Mock).baseUrl)
    }

    @Test
    fun productionAndStagingUseTheRealHostForAllThreeHosts() {
        // shortcut: the queue and pay hosts are not confirmed, so every host is the one real host for now.
        listOf(AppEnvironment.Staging, AppEnvironment.Production).forEach { env ->
            val config = ApiConfig.forEnvironment(env)
            assertEquals("https://api.rusuh-tiket.web.id", config.baseUrl, "$env api")
            assertEquals("https://api.rusuh-tiket.web.id", config.queueBaseUrl, "$env queue")
            assertEquals("https://api.rusuh-tiket.web.id", config.payBaseUrl, "$env pay")
        }
    }

    @Test
    fun environmentNameParsing() {
        assertEquals(AppEnvironment.Mock, AppEnvironment.fromName("mock"))
        assertEquals(AppEnvironment.Production, AppEnvironment.fromName(" Production "))
        assertTrue(failure { AppEnvironment.fromName("prod") }.contains("prod"))
    }

    @Test
    fun portIsCheckedOnTheAuthorityOnly() {
        assertEquals("https://a.test/x:99999", config(api = "https://a.test/x:99999/").baseUrl)
    }

    @Test
    fun placeholderHostsAreFlaggedPerHost() {
        assertTrue(config(api = "https://api.rusuh.invalid").usesPlaceholderHosts)
        assertTrue(config(queue = "https://q.rusuh.invalid:8443/x").usesPlaceholderHosts)
        assertTrue(config(pay = "https://pay.rusuh.invalid").usesPlaceholderHosts)
        assertTrue(!config().usesPlaceholderHosts)
    }

    @Test
    fun noEnvironmentUsesAPlaceholderHostAnyMore() {
        AppEnvironment.entries.forEach { env ->
            assertTrue(!ApiConfig.forEnvironment(env).usesPlaceholderHosts, "$env")
        }
    }
}
