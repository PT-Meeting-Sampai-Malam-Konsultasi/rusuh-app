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

        assertEquals(keys("values/strings.xml"), keys("values-id/strings.xml"))
    }
}
