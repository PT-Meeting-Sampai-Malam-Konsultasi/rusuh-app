package com.rusuh.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ComposeResourceStringsTest {
    @Test
    fun stringsXmlHasNoEscapedApostrophes() {
        val resourceDirectory = File("src/commonMain/composeResources")
        val stringsFiles = resourceDirectory.walkTopDown().filter { it.name == "strings.xml" }.toList()

        assertTrue(stringsFiles.isNotEmpty(), "No strings.xml files found in $resourceDirectory")
        stringsFiles.forEach { file ->
            val escapedApostrophes = file.readLines().filter { "\\'" in it }
            assertTrue(escapedApostrophes.isEmpty(), "Found \\' in ${file.path}: $escapedApostrophes")
        }
    }
}
