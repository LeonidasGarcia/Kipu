package com.kipu.app.feature.accounts

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArchitectureBoundaryTest {

    private val forbiddenImportPrefixes = listOf(
        "android.",
        "androidx.",
        "io.github.jan.supabase",
        "com.kipu.app.ui",
        "com.kipu.app.feature.accounts.presentation",
        "com.kipu.app.feature.accounts.data",
    )

    @Test
    fun `core finance domain has zero framework or data dependencies`() {
        val domainDir = File("src/main/java/com/kipu/app/core/finance/domain")
        assertTrue("Domain directory must exist: ${domainDir.absolutePath}", domainDir.exists())

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
            "Found architecture boundary violations in core finance domain:\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun `accounts domain has zero framework or data dependencies`() {
        val domainDir = File("src/main/java/com/kipu/app/feature/accounts/domain")
        assertTrue("Domain directory must exist: ${domainDir.absolutePath}", domainDir.exists())

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
            "Found architecture boundary violations in accounts domain:\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }
}
