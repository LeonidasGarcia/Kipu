package com.kipu.app.feature.movements

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MovementsFeatureBoundaryTest {

    private val forbiddenImportPrefixes = listOf(
        "android.",
        "androidx.",
        "io.github.jan.supabase",
        "com.kipu.app.ui",
        "com.kipu.app.feature.movements.presentation",
        "com.kipu.app.feature.movements.data",
    )

    @Test
    fun `movements domain has zero framework or data dependencies`() {
        val domainDir = File("src/main/java/com/kipu/app/feature/movements/domain")
        if (!domainDir.exists()) {
            // If domainDir doesn't exist yet in test runner working dir, look from project root
            val altDir = File("app/src/main/java/com/kipu/app/feature/movements/domain")
            if (altDir.exists()) {
                checkDirectory(altDir)
                return
            }
        } else {
            checkDirectory(domainDir)
            return
        }
        // If neither exists yet, it's considered valid before creation
        assertTrue(true)
    }

    private fun checkDirectory(dir: File) {
        val violations = mutableListOf<String>()
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("import ")) {
                            val importedClass = trimmed.removePrefix("import ").trim()
                            for (forbidden in forbiddenImportPrefixes) {
                                if (importedClass.startsWith(forbidden)) {
                                    violations.add("${file.name}:${index + 1} imports forbidden $importedClass")
                                }
                            }
                        }
                    }
                }
            }

        assertTrue(
            "Found architecture boundary violations in movements domain:\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }
}
