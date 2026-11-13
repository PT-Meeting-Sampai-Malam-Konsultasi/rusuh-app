package com.rusuh.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Validates parity between English (values/), Indonesian for Android (values-in/),
 * and Indonesian for iOS (values-id/) string resources.
 *
 * Android's Locale.getLanguage() returns "in" for Indonesian (legacy ISO 639-1),
 * while iOS's NSLocale.languageCode returns "id" (BCP 47). Compose Multiplatform
 * Resources uses the platform's language code to resolve resource qualifiers, so
 * we need both folders with identical content.
 */
class LanguageStringParityTest {
    private val root = File("src/commonMain/composeResources")

    private fun parseDocument(path: String): Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(root, path))

    private fun keys(path: String): Set<String> {
        val document = parseDocument(path)
        val resources = document.documentElement
        return (0 until resources.childNodes.length).mapNotNull { index ->
            resources.childNodes.item(index).attributes?.getNamedItem("name")?.nodeValue
        }.toSet()
    }

    private fun textContents(path: String): Map<String, String> {
        val document = parseDocument(path)
        val resources = document.documentElement
        return (0 until resources.childNodes.length)
            .map { resources.childNodes.item(it) }
            .filter { it.nodeName in SUPPORTED_RESOURCE_TYPES }
            .mapNotNull { node ->
                val name = node.attributes?.getNamedItem("name")?.nodeValue
                val content =
                    when (node.nodeName) {
                        "string" -> node.textContent
                        else -> serializeChildren(node) // string-array or plurals
                    }
                name?.let { it to content }
            }
            .toMap()
    }

    private fun serializeChildren(node: Node): String {
        val sb = StringBuilder()
        for (i in 0 until node.childNodes.length) {
            val child = node.childNodes.item(i)
            if (child.nodeName == "item") {
                val quantity = child.attributes?.getNamedItem("quantity")?.nodeValue
                val prefix = if (quantity != null) "[$quantity]" else ""
                sb.append("$prefix${child.textContent}\n")
            }
        }
        return sb.toString()
    }

    @Test
    fun englishAndIndonesianResourceKeysMatch() {
        // Indonesian folder uses "in" (Java's ISO 639-1 code) not "id" (BCP 47) to match
        // what Locale.getLanguage() returns at runtime on Android.
        assertEquals(keys("values/strings.xml"), keys("values-in/strings.xml"))
    }

    @Test
    fun englishAndIndonesianIdFolderKeysMatch() {
        // iOS uses "id" (BCP 47) for Indonesian via NSLocale.languageCode
        assertEquals(keys("values/strings.xml"), keys("values-id/strings.xml"))
    }

    @Test
    fun indonesianAndroidAndIosFoldersHaveIdenticalContent() {
        // values-id (iOS) and values-in (Android) must have identical keys AND values
        // so Indonesian users see the same strings on both platforms.
        val idContents = textContents("values-id/strings.xml")
        val inContents = textContents("values-in/strings.xml")

        assertEquals(
            "values-id and values-in must have identical keys",
            idContents.keys,
            inContents.keys,
        )

        val mismatches = idContents.filter { (key, value) -> inContents[key] != value }
        assertTrue(
            "values-id and values-in must have identical content. Mismatched keys: ${mismatches.keys}",
            mismatches.isEmpty(),
        )
    }

    @Test
    fun homeEventTitleHasEnglishAndIndonesianValues() {
        fun text(path: String): String {
            val document = parseDocument(path)
            val nodes = document.getElementsByTagName("string")
            return (0 until nodes.length).map { nodes.item(it) }
                .first { it.attributes.getNamedItem("name")?.nodeValue == "home_section_all_events" }
                .textContent
        }

        assertEquals("Semua event", text("values-in/strings.xml"))
        assertEquals("Semua event", text("values-id/strings.xml"))
        assertEquals("All events", text("values/strings.xml"))
    }

    private companion object {
        val SUPPORTED_RESOURCE_TYPES = setOf("string", "string-array", "plurals")
    }
}
