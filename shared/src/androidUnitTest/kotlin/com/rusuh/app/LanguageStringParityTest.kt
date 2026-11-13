package com.rusuh.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LanguageStringParityTest {
    @Test
    fun englishAndIndonesianResourceKeysMatch() {
        val root = File("src/commonMain/composeResources")

        fun keys(path: String): Set<String> {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(root, path))
            val resources = document.documentElement
            return (0 until resources.childNodes.length).mapNotNull { index ->
                resources.childNodes.item(index).attributes?.getNamedItem("name")?.nodeValue
            }.toSet()
        }

        // Indonesian folder uses "in" (Java's ISO 639-1 code) not "id" (BCP 47) to match
        // what Locale.getLanguage() returns at runtime on Android.
        assertEquals(keys("values/strings.xml"), keys("values-in/strings.xml"))
    }

    @Test
    fun homeEventTitleHasEnglishAndIndonesianValues() {
        val root = File("src/commonMain/composeResources")

        fun text(path: String): String {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(root, path))
            val nodes = document.getElementsByTagName("string")
            return (0 until nodes.length).map { nodes.item(it) }
                .first { it.attributes.getNamedItem("name")?.nodeValue == "home_section_all_events" }
                .textContent
        }

        assertEquals("Semua event", text("values-in/strings.xml"))
        assertEquals("All events", text("values/strings.xml"))
    }
}
