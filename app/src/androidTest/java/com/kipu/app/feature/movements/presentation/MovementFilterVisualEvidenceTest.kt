package com.kipu.app.feature.movements.presentation

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.feature.movements.domain.model.*
import com.kipu.app.ui.theme.KipuTheme
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Synthetic presentation fixtures only; this does not verify Premium entitlement. */
class MovementFilterVisualEvidenceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun captureFiltersWithSyntheticReferences() {
        val arguments = InstrumentationRegistry.getArguments()
        val prefix = arguments.getString("evidencePrefix") ?: "after"
        val dark = arguments.getString("evidenceDark") == "true"
        val rows = (0..999).map { index ->
            TransactionItem(Transaction(id = "fixture-$index", userId = "fixture-owner",
                type = MovementType.EXPENSE, amountMinor = 100, currency = "PEN",
                sourceAccountId = "account-$index", categoryId = "fixture-category", occurredAt = 1_758_000_000_000,
                status = TransactionStatus.ACTIVE), sourceAccountAlias = "Cuenta %04d".format(index))
        }
        compose.setContent { KipuTheme(darkTheme = dark) {
            MovementFiltersSheet(MovementHistoryUiState(ownerId = "fixture-owner",
                accessStatus = MovementHistoryAccessDecision.Allowed, allTransactions = rows,
                appliedFilters = AdvancedFiltersState(accountIds = setOf("account-0", "account-999"),
                    minAmountMinor = 10000, maxAmountMinor = 5000)),
                onDismiss = {}, onApply = { it.validate() })
        } }
        save("$prefix-overview-${if (dark) "dark" else "light"}.png", hasTestTag("panel_advanced_filters"))
        compose.onNodeWithTag("btn_apply_filters").performClick()
        compose.onNodeWithTag("input_filter_max_amount").performScrollTo()
        save("$prefix-amount-${if (dark) "dark" else "light"}.png", hasTestTag("panel_advanced_filters"))
        compose.onNode(hasText("Cuentas", substring = true) and hasClickAction()).performScrollTo().performClick()
        save("$prefix-selector-${if (dark) "dark" else "light"}.png", isDialog() and hasAnyDescendant(hasText("Buscar en las opciones")))
        compose.onNodeWithText("Listo").performClick()
        compose.onNodeWithTag("btn_filter_dates").performScrollTo().performClick()
        save("$prefix-calendar-${if (dark) "dark" else "light"}.png", isDialog() and hasAnyDescendant(hasText("Seleccionar fechas")))
        compose.onNodeWithText("Cancelar").performClick()
    }

    private fun save(name: String, matcher: SemanticsMatcher) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "issue-19").apply { mkdirs() }
        val bitmap = compose.onNode(matcher).captureToImage().asAndroidBitmap()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
