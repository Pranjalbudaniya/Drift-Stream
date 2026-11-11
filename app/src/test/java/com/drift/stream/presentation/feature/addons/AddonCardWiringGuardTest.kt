package com.drift.stream.presentation.feature.addons

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AddonCardWiringGuardTest {
    private fun resolveAddonsDir(): File {
        val candidates =
            listOf(
                File("src/main/java/com/drift/stream/presentation/feature/addons"),
                File("app/src/main/java/com/drift/stream/presentation/feature/addons"),
                File(System.getProperty("user.dir"), "src/main/java/com/drift/stream/presentation/feature/addons"),
                File(System.getProperty("user.dir"), "app/src/main/java/com/drift/stream/presentation/feature/addons"),
            )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("Cannot find presentation/feature/addons in candidates")
    }

    @Test
    fun CM5_everyAddonCardCallSiteInMain_passesTheShareWiring() {
        val dir = resolveAddonsDir()
        val ktFiles = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("Expected to find Kotlin files in addons feature", ktFiles.isNotEmpty())

        val addonCardCallRegex = Regex("""(?<!Shared)AddonCard\s*\(""")
        var callSitesFound = 0

        for (file in ktFiles) {
            val content = file.readText()
            val lines = content.lines()

            for (i in lines.indices) {
                val line = lines[i]
                if (addonCardCallRegex.containsMatchIn(line) && !line.contains("fun AddonCard(")) {
                    // Check if this call site is inside preview code
                    val precedingLines = lines.take(i + 1).takeLast(40).joinToString("\n")
                    if (precedingLines.contains("@Preview")) {
                        continue
                    }

                    callSitesFound++
                    val callInvocation = lines.drop(i).take(20).joinToString("\n")
                    assertTrue(
                        "Call site in ${file.name} must pass onToggleShared: $callInvocation",
                        callInvocation.contains("onToggleShared"),
                    )
                    assertTrue(
                        "Call site in ${file.name} must pass profileCount: $callInvocation",
                        callInvocation.contains("profileCount"),
                    )
                }
            }
        }

        assertTrue("Expected at least 1 non-preview AddonCard call site in main", callSitesFound >= 1)
    }
}
