package com.rusuh.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-scanning guard test that fails when new hardcoded `Text("...")` literals
 * appear in composable source files.
 *
 * ViewModels must not produce user-visible text; all UI copy must come from string
 * resources so it can be translated (issue #134). This test scans Kotlin source
 * files in commonMain and fails if it finds `Text("` followed by a non-empty
 * string literal that is not in the allowlist.
 *
 * The allowlist contains legitimate exceptions:
 * - SplashPlaceholder: development-only placeholder that will be replaced
 * - Preview functions: synthetic data for IDE previews
 *
 * How to fix a failure:
 * 1. Replace `Text("Your message")` with `Text(stringResource(Res.string.your_key))`
 * 2. Add the string key to values/strings.xml (English) and values-id/strings.xml + values-in/strings.xml (Indonesian)
 * 3. If the text is truly non-translatable (e.g. brand name, symbol), add it to the allowlist below with a comment
 */
class HardcodedTextGuardTest {
    /**
     * Patterns allowed without string resources. Each entry is a regex pattern.
     * Add entries sparingly and document why each is acceptable.
     */
    private val allowlist =
        listOf(
            // Development placeholder - will be replaced with a real splash screen
            """SplashPlaceholder.*Text\("Splash Screen"\)""",
            // Preview functions contain synthetic Indonesian/English data for IDE previews
            """@.*Preview.*""",
            // Development placeholder - payment screen TODO placeholder
            """TODO.*implement.*payment""",
            // Brand wordmark "RUSUH!" is intentionally hardcoded as it's a logo, not translatable text
            // (The actual brand is handled via Res.string.home_logo_wordmark, this regex is for safety)
        )

    private val sourceRoot = File("src/commonMain/kotlin")

    @Test
    fun noHardcodedTextLiteralsInComposables() {
        if (!sourceRoot.exists()) {
            // Running from a different working directory - skip
            return
        }

        val violations = mutableListOf<String>()
        val textPattern = Regex("""Text\s*\(\s*"([^"]+)"""")

        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val content = file.readText()
                val lines = content.lines()

                lines.forEachIndexed { index, line ->
                    val match = textPattern.find(line)
                    if (match != null) {
                        val stringLiteral = match.groupValues[1]
                        // Skip empty strings
                        if (stringLiteral.isBlank()) return@forEachIndexed

                        // Check if this line or surrounding context is allowlisted
                        val context =
                            buildString {
                                // Include some context lines before
                                for (i in maxOf(0, index - 5)..index) {
                                    append(lines.getOrNull(i) ?: "")
                                    append("\n")
                                }
                            }

                        val isAllowed =
                            allowlist.any { pattern ->
                                Regex(pattern, RegexOption.DOT_MATCHES_ALL).containsMatchIn(context)
                            }

                        if (!isAllowed) {
                            violations.add(
                                "${file.relativeTo(sourceRoot)}:${index + 1}: Text(\"$stringLiteral\")",
                            )
                        }
                    }
                }
            }

        assertTrue(
            buildString {
                appendLine("Found ${violations.size} hardcoded Text(\"...\") literal(s) in composables:")
                appendLine()
                violations.forEach { appendLine("  - $it") }
                appendLine()
                appendLine("Fix: Replace with stringResource(Res.string.your_key) and add the key to strings.xml")
                appendLine("If the text is truly non-translatable, add a regex pattern to the allowlist in this test.")
            },
            violations.isEmpty(),
        )
    }
}
