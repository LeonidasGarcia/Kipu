package com.kipu.app.feature.categories

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArchitectureBoundaryTest {

    private val forbiddenImportPrefixes = listOf(
        "android.",
        "androidx.",
        "io.github.jan.supabase",
        "com.kipu.app.ui",
        "com.kipu.app.feature.categories.presentation",
        "com.kipu.app.feature.categories.data",
    )

    @Test
    fun `categories domain has zero framework or data dependencies`() {
        val domainDir = File("src/main/java/com/kipu/app/feature/categories/domain")
        if (!domainDir.exists()) {
            return
        }

        val violations = mutableListOf<String>()

        domainDir.walkTopDown()
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
            "Found architecture boundary violations in categories domain:\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }
}
