package com.rusuh.app.ui.error

import com.rusuh.app.domain.model.ErrorCode
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * JVM parity test that verifies every [ErrorCode] has a corresponding string resource
 * in both English (values/) and Indonesian (values-id/, values-in/) string files.
 *
 * This test runs on the JVM (androidUnitTest) so it can read the XML files directly.
 */
class ErrorCodeStringParityTest {
    private val root = File("src/commonMain/composeResources")

    private fun parseDocument(path: String): Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(root, path))

    private fun stringKeys(path: String): Set<String> {
        val document = parseDocument(path)
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).mapNotNull { index ->
            nodes.item(index).attributes?.getNamedItem("name")?.nodeValue
        }.toSet()
    }

    private fun stringValue(
        path: String,
        key: String,
    ): String? {
        val document = parseDocument(path)
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it) }
            .firstOrNull { it.attributes?.getNamedItem("name")?.nodeValue == key }
            ?.textContent
    }

    @Test
    fun everyErrorCodeHasEnglishString() {
        val enKeys = stringKeys("values/strings.xml")

        ErrorCode.entries.forEach { code ->
            val key = ErrorMessageMapper.keyFor(code)
            assertTrue(
                "ErrorCode.$code should have EN string resource '${key.resourceName}' but it's missing",
                enKeys.contains(key.resourceName),
            )
        }
    }

    @Test
    fun everyErrorCodeHasIndonesianIdString() {
        val idKeys = stringKeys("values-id/strings.xml")

        ErrorCode.entries.forEach { code ->
            val key = ErrorMessageMapper.keyFor(code)
            assertTrue(
                "ErrorCode.$code should have ID string resource '${key.resourceName}' but it's missing",
                idKeys.contains(key.resourceName),
            )
        }
    }

    @Test
    fun everyErrorCodeHasIndonesianInString() {
        val inKeys = stringKeys("values-in/strings.xml")

        ErrorCode.entries.forEach { code ->
            val key = ErrorMessageMapper.keyFor(code)
            assertTrue(
                "ErrorCode.$code should have IN string resource '${key.resourceName}' but it's missing",
                inKeys.contains(key.resourceName),
            )
        }
    }

    @Test
    fun httpFallbackStringsExistInEnglish() {
        val enKeys = stringKeys("values/strings.xml")
        assertTrue("error_http_client should exist in EN", enKeys.contains("error_http_client"))
        assertTrue("error_http_server should exist in EN", enKeys.contains("error_http_server"))
    }

    @Test
    fun httpFallbackStringsExistInIndonesianId() {
        val idKeys = stringKeys("values-id/strings.xml")
        assertTrue("error_http_client should exist in ID", idKeys.contains("error_http_client"))
        assertTrue("error_http_server should exist in ID", idKeys.contains("error_http_server"))
    }

    @Test
    fun httpFallbackStringsExistInIndonesianIn() {
        val inKeys = stringKeys("values-in/strings.xml")
        assertTrue("error_http_client should exist in IN", inKeys.contains("error_http_client"))
        assertTrue("error_http_server should exist in IN", inKeys.contains("error_http_server"))
    }

    @Test
    fun errorCodeStringsAreNotEmpty() {
        ErrorCode.entries.forEach { code ->
            val key = ErrorMessageMapper.keyFor(code).resourceName

            val enValue = stringValue("values/strings.xml", key)
            assertNotNull("EN value for $key should not be null", enValue)
            assertTrue("EN value for $key should not be empty", enValue!!.isNotBlank())

            val idValue = stringValue("values-id/strings.xml", key)
            assertNotNull("ID value for $key should not be null", idValue)
            assertTrue("ID value for $key should not be empty", idValue!!.isNotBlank())

            val inValue = stringValue("values-in/strings.xml", key)
            assertNotNull("IN value for $key should not be null", inValue)
            assertTrue("IN value for $key should not be empty", inValue!!.isNotBlank())
        }
    }

    @Test
    fun indonesianIdAndInHaveIdenticalErrorCodeStrings() {
        ErrorCode.entries.forEach { code ->
            val key = ErrorMessageMapper.keyFor(code).resourceName
            val idValue = stringValue("values-id/strings.xml", key)
            val inValue = stringValue("values-in/strings.xml", key)
            assertTrue(
                "ID and IN values for $key should be identical but ID='$idValue' and IN='$inValue'",
                idValue == inValue,
            )
        }
    }

    @Test
    fun errorCodeStringsDoNotContainServerDetailPlaceholders() {
        // Strings should not expose server technical details like (validation failed) or (internal error)
        val forbiddenPatterns =
            listOf(
                "\\(validation failed\\)",
                "\\(internal error\\)",
                "\\(unauthorized\\)",
                "\\(not found\\)",
            )

        ErrorCode.entries.forEach { code ->
            val key = ErrorMessageMapper.keyFor(code).resourceName

            listOf("values/strings.xml", "values-id/strings.xml").forEach { path ->
                val value = stringValue(path, key) ?: return@forEach
                forbiddenPatterns.forEach { pattern ->
                    assertTrue(
                        "$key in $path should not contain '$pattern' but found in '$value'",
                        !Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(value),
                    )
                }
            }
        }
    }

    @Test
    fun errorCodeStringsUseApostrophesNotBackslashedQuotes() {
        // Android XML strings should use ' not \' for apostrophes
        ErrorCode.entries.forEach { code ->
            val key = ErrorMessageMapper.keyFor(code).resourceName

            listOf("values/strings.xml", "values-id/strings.xml", "values-in/strings.xml").forEach { path ->
                val value = stringValue(path, key) ?: return@forEach
                assertTrue(
                    "$key in $path should not contain backslash-apostrophe but found in '$value'",
                    !value.contains("\\'"),
                )
            }
        }
    }
}
