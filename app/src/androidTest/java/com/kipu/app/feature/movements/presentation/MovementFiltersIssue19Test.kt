package com.kipu.app.feature.movements.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import com.kipu.app.feature.movements.domain.model.MovementHistoryAccessDecision
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MovementFiltersIssue19Test {
    @get:Rule val compose = createComposeRule()

    @Test fun errorsStayBesideAmountsAndCurrencyAndRefreshWithoutApplying() {
        var attempts = 0
        var applied: AdvancedFiltersState? = null
        compose.setContent { KipuTheme { MovementFiltersSheet(MovementHistoryUiState(ownerId = "fixture"), {}, {
            attempts++
            it.validate().also { result -> applied = result.filters }
        }) } }
        compose.onNodeWithTag("input_filter_min_amount").performScrollTo().performTextInput("abc")
        compose.onNodeWithTag("input_filter_max_amount").performScrollTo().performTextInput("5")
        compose.onNodeWithTag("btn_apply_filters").assertIsDisplayed().performClick()
        assertNull(applied)
        compose.onNodeWithTag("input_filter_min_amount").performScrollTo()
        compose.onNodeWithTag("error_filter_min", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("error_filter_currency").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("input_filter_min_amount").performScrollTo().performTextReplacement("10")
        compose.onNodeWithTag("error_filter_min", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("input_filter_max_amount").performScrollTo()
        compose.onNodeWithTag("error_filter_max", useUnmergedTree = true).performScrollTo().assertIsDisplayed().assertTextEquals("El importe máximo debe ser igual o mayor al mínimo.")
        compose.onNodeWithTag("input_filter_max_amount").performTextReplacement("15,50")
        compose.onNodeWithTag("error_filter_max", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("PEN").performScrollTo().performClick()
        compose.onNodeWithTag("error_filter_currency").assertDoesNotExist()
        assertEquals(1, attempts)
        compose.onNodeWithTag("btn_apply_filters").performClick()
        assertEquals(1000L, applied?.minAmountMinor)
        assertEquals(1550L, applied?.maxAmountMinor)
        assertEquals("PEN", applied?.currency)
    }

    @Test fun dateErrorsAreNextToDateControlAndPresetCorrectsDraftOnly() {
        var attempts = 0
        compose.setContent { KipuTheme { MovementFiltersSheet(MovementHistoryUiState(), {}, {
            attempts++
            FilterDraftValidation(null, mapOf("from" to FilterDraftError.DATE, "to" to FilterDraftError.RANGE))
        }) } }
        compose.onNodeWithTag("btn_apply_filters").performClick()
        compose.onNodeWithTag("btn_filter_dates").performScrollTo()
        compose.onNodeWithTag("error_filter_from").assertIsDisplayed().assertTextEquals("Inicio: selecciona una fecha válida.")
        compose.onNodeWithTag("error_filter_to").assertIsDisplayed().assertTextEquals("La fecha de fin debe ser igual o posterior al inicio.")
        compose.onNodeWithText("Hoy").performScrollTo().performClick()
        compose.onNodeWithTag("error_filter_from").assertDoesNotExist()
        compose.onNodeWithTag("error_filter_to").assertDoesNotExist()
        assertEquals(1, attempts)
    }

    @Test fun frequentPeriodsStayCompactAndSecondaryPresetsRemainAvailable() {
        compose.setContent { KipuTheme { MovementFiltersSheet(MovementHistoryUiState(), {}, {
            it.validate()
        }) } }

        compose.onNodeWithText("Todas").assertIsDisplayed()
        compose.onNodeWithText("Hoy").assertIsDisplayed()
        compose.onNodeWithText("Este mes").assertIsDisplayed()
        compose.onNodeWithText("Personalizado").assertIsDisplayed()
        compose.onNodeWithText("7 días").assertDoesNotExist()
        compose.onNodeWithText("Año").assertDoesNotExist()

        compose.onNodeWithText("Más periodos").performClick()
        compose.onNodeWithText("7 días").assertIsDisplayed().performClick()
        compose.onNodeWithText("7 días").assertIsSelected()
        compose.onNodeWithText("Año").assertIsDisplayed()
    }

    @Test fun thousandReferencesAreLazyAndSelectionSurvivesSearchAndRemoval() {
        val selected = mutableStateOf(setOf("id-0"))
        val options = (0..999).map { MovementReferenceOption("id-$it", "Cuenta %04d".format(it)) }
        selector(options, selected)
        compose.onNodeWithTag("btn_reference_Cuentas").performClick()
        // A distant option is not composed until scrolled to; this is not a timing benchmark.
        compose.onNodeWithTag("reference_option_id-999").assertDoesNotExist()
        compose.onNodeWithTag("reference_options").performScrollToKey("id-999")
        compose.onNodeWithTag("reference_option_id-999").performClick()
        compose.onNodeWithTag("input_reference_search").performTextInput("  CUENTA 0000  ")
        compose.onNodeWithTag("reference_option_id-0").assertIsOn()
        assertEquals(setOf("id-0", "id-999"), selected.value)
        compose.onNodeWithTag("reference_option_id-0").performClick()
        compose.onNodeWithTag("input_reference_search").performTextReplacement("no coincide")
        compose.onNodeWithText("No hay opciones que coincidan con tu búsqueda.").assertIsDisplayed()
        assertEquals(setOf("id-999"), selected.value)
        compose.onNodeWithTag("input_reference_search").performTextReplacement("Cuenta 0999")
        compose.onNodeWithTag("reference_option_id-999").assertIsOn()
        compose.onNodeWithText("Listo").performClick()
        compose.onNodeWithText("Cuenta 0999").assertIsDisplayed()
    }

    @Test fun duplicateNamesKeepDistinctIdsAndMissingReferenceCanBeRemoved() {
        val selected = mutableStateOf(setOf("missing-secret-id", "first"))
        selector(listOf(MovementReferenceOption("first", "Efectivo"), MovementReferenceOption("second", "Efectivo")), selected)
        compose.onNodeWithTag("btn_reference_Cuentas").performClick()
        compose.onNodeWithTag("reference_option_first").assertIsOn()
        compose.onNodeWithTag("reference_option_second").assertIsOff().performClick()
        compose.onNodeWithTag("reference_option_missing-secret-id").assertIsOn().performClick()
        compose.onAllNodes(hasText("missing-secret-id", substring = true), useUnmergedTree = true).assertCountEquals(0)
        assertEquals(setOf("first", "second"), selected.value)
    }

    @Test fun emptyCatalogHasDifferentMessageFromUnmatchedSearch() {
        selector(emptyList(), mutableStateOf(emptySet()))
        compose.onNodeWithTag("btn_reference_Cuentas").performClick()
        compose.onNodeWithText("No hay referencias en tu historial.").assertIsDisplayed()
        compose.onNodeWithText("No hay opciones que coincidan con tu búsqueda.").assertDoesNotExist()
        compose.onNodeWithText("Listo").assertIsDisplayed()
    }

    @Test fun lossOfAccessClosesSelectorAndDisablesReopening() {
        val enabled = mutableStateOf(true)
        selector(listOf(MovementReferenceOption("first", "Efectivo")), mutableStateOf(emptySet()), enabled)
        compose.onNodeWithTag("btn_reference_Cuentas").performClick()
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithTag("input_reference_search").assertDoesNotExist()
        compose.onNodeWithTag("btn_reference_Cuentas").assertIsNotEnabled()
    }

    @Test fun ownerChangeDiscardsPreviousDraftAndOpenSelector() {
        val state = mutableStateOf(MovementHistoryUiState(ownerId = "owner-one",
            appliedFilters = AdvancedFiltersState(accountIds = setOf("old-id"))))
        compose.setContent { KipuTheme { MovementFiltersSheet(state.value, {}, { it.validate() }) } }
        compose.onNodeWithTag("btn_reference_Cuentas").performScrollTo().performClick()
        compose.onNodeWithTag("reference_option_old-id").assertIsDisplayed()
        compose.runOnIdle { state.value = MovementHistoryUiState(ownerId = "owner-two") }
        compose.onNodeWithTag("input_reference_search").assertDoesNotExist()
        compose.onNodeWithTag("btn_reference_Cuentas").performScrollTo()
        compose.onNodeWithText("Referencia histórica").assertDoesNotExist()
    }

    @Test fun freeDateApplyPreservesParkedAdvancedCriteria() {
        val previous = AdvancedFiltersState(accountIds = setOf("historical"), minAmountMinor = 2500, currency = "USD")
        var applied: MovementFilterDraft? = null
        compose.setContent { KipuTheme { MovementFiltersSheet(MovementHistoryUiState(appliedFilters = previous,
            accessStatus = MovementHistoryAccessDecision.RevalidationRequired(MovementHistoryQuery())), {}, {
            applied = it
            it.validate()
        }) } }
        compose.onNodeWithText("Hoy").performScrollTo().performClick()
        compose.onNodeWithTag("btn_apply_filters").performClick()
        assertEquals(previous.accountIds, applied?.accountIds)
        assertEquals("25.00", applied?.minAmount)
        assertEquals("USD", applied?.currency)
        assertFalse(applied?.fromDate.isNullOrEmpty())
    }

    @Test fun maskedAmountErrorsDoNotExposeValues() {
        compose.setContent { CompositionLocalProvider(LocalBalanceMasked provides true) { KipuTheme {
            MovementFiltersSheet(MovementHistoryUiState(appliedFilters = AdvancedFiltersState(
                minAmountMinor = 98765, maxAmountMinor = 12345, currency = "PEN")), {}, { it.validate() })
        } } }
        compose.onNodeWithTag("btn_apply_filters").performClick()
        compose.onNodeWithTag("input_filter_max_amount", useUnmergedTree = true).performScrollTo()
        listOf("987.65", "123.45").forEach { value ->
            compose.onAllNodes(hasText(value, substring = true), useUnmergedTree = true).assertCountEquals(0)
            compose.onAllNodes(hasContentDescription(value, substring = true), useUnmergedTree = true).assertCountEquals(0)
        }
        compose.onNodeWithTag("input_filter_max_amount", useUnmergedTree = true).assertContentDescriptionEquals("Importe máximo oculto")
    }

    @Test fun compactDarkViewportWithLargeTextKeepsKeyboardActionsVisible() {
        // Dialogs create their own Android density locals. Override only the fixture view's
        // context so sheet/calendar windows inherit the compact configuration as well.
        compose.setContent { AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
            val compact = Configuration(context.resources.configuration).apply {
                densityDpi = (densityDpi * 1.25f).toInt()
                screenWidthDp = (screenWidthDp / 1.25f).toInt()
                screenHeightDp = (screenHeightDp / 1.25f).toInt()
                fontScale = 1.6f
            }
            val compactContext = ContextThemeWrapper(context, 0).apply {
                applyOverrideConfiguration(compact)
                theme.setTo(context.theme)
            }
            ComposeView(compactContext).apply {
                setContent { KipuTheme(darkTheme = true) {
                    MovementFiltersSheet(MovementHistoryUiState(), {}, { it.validate() })
                } }
            }
        }) }
        compose.onNodeWithTag("input_filter_min_amount").performScrollTo().performClick().performTextInput("25")
        compose.onNodeWithTag("btn_apply_filters").assertIsDisplayed()
        saveCompactEvidence("after-compact-keyboard-dark.png", hasTestTag("panel_advanced_filters"))
        compose.onNodeWithTag("btn_apply_filters").performClick()
        compose.onNodeWithTag("error_filter_currency").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("btn_reference_Cuentas").performScrollTo().performClick()
        compose.onNodeWithTag("input_reference_search").performClick().performTextInput("texto largo")
        compose.onNodeWithText("Listo").assertIsDisplayed().performClick()
        compose.onNodeWithTag("btn_close_filters").assertIsDisplayed()
        compose.onNodeWithTag("btn_filter_dates").performScrollTo().performClick()
        compose.onNodeWithText("Seleccionar fechas").assertIsDisplayed()
        compose.onNodeWithText("Inicio").assertIsDisplayed()
        compose.onNodeWithText("Fin").assertIsDisplayed()
        compose.onNodeWithContentDescription("Escribir fechas").performClick()
        compose.onNodeWithContentDescription("Elegir en el calendario").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").assertIsDisplayed()
        val dateDialog = compose.onNode(isDialog() and hasAnyDescendant(hasText("Seleccionar fechas")))
        val viewportWidth = with(compose.density) {
            InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.widthPixels.toDp()
        }
        val dialogBounds = dateDialog.getUnclippedBoundsInRoot()
        assertTrue("Date dialog must fit the viewport", dialogBounds.right - dialogBounds.left <= viewportWidth)
        saveCompactEvidence("after-compact-calendar-input-dark.png",
            isDialog() and hasAnyDescendant(hasText("Seleccionar fechas")))
        compose.onNodeWithText("Cancelar").performClick()
    }

    private fun saveCompactEvidence(name: String, matcher: SemanticsMatcher) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "issue-19").apply { mkdirs() }
        val bitmap = compose.onNode(matcher).captureToImage().asAndroidBitmap()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun selector(options: List<MovementReferenceOption>, selected: MutableState<Set<String>>,
        enabled: MutableState<Boolean> = mutableStateOf(true)) {
        compose.setContent { KipuTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp)) { ReferenceSelector("Cuentas", options, selected.value, enabled.value, { selected.value = it }) }
        } } }
    }
}
