package com.kipu.app.feature.movements.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import com.kipu.app.core.session.*
import com.kipu.app.feature.movements.domain.*
import kotlinx.coroutines.flow.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.kipu.app.feature.movements.domain.model.*
import com.kipu.app.ui.component.LocalBalanceMasked
import com.kipu.app.ui.theme.KipuTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MovementUxRefinementTest {
    @get:Rule val compose = createComposeRule()
    private fun item(status: TransactionStatus = TransactionStatus.ACTIVE) = TransactionItem(Transaction(categoryId = "ux-category",
        id = "ux-movement", userId = "ux-owner", type = MovementType.EXPENSE, amountMinor = 12345,
        currency = "PEN", sourceAccountId = "cash", occurredAt = 1_758_000_000_000, status = status),
        sourceAccountAlias = "Efectivo", categoryName = "Alimentación")

    @Test fun voidedDetailIsReadableAndOffersNoFinancialActions() {
        compose.setContent { KipuTheme { MovementDetailSheet(item(TransactionStatus.VOIDED), emptyList(), false, false, {}, {}, {}) } }
        compose.onNodeWithText("Movimiento anulado · Solo lectura").assertIsDisplayed()
        compose.onNodeWithTag("detail_edit").assertDoesNotExist()
        compose.onNodeWithTag("detail_void").assertDoesNotExist()
    }

    @Test fun detailAmountIsAbsentFromMaskedSemantics() {
        compose.setContent { CompositionLocalProvider(LocalBalanceMasked provides true) {
            KipuTheme { MovementDetailSheet(item(), emptyList(), false, false, {}, {}, {}) }
        } }
        compose.onAllNodes(hasText("123.45", substring = true), useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("123.45", substring = true), useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("detail_amount", useUnmergedTree = true).assertContentDescriptionEquals("Monto oculto")
    }

    @Test fun voidConfirmationAmountIsMasked() {
        compose.setContent { CompositionLocalProvider(LocalBalanceMasked provides true) {
            KipuTheme { VoidMovementDialog(item(), {}, {}) }
        } }
        compose.onAllNodes(hasText("123.45", substring = true), useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("123.45", substring = true), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun beforeAfterAmountsAreMasked() {
        compose.setContent { CompositionLocalProvider(LocalBalanceMasked provides true) {
            KipuTheme { MovementChangeSummary(MovementEditorUiState(isLoading = false,
                initialAmountMinor = 12345, amountText = "678.90"), reducedMotion = true) }
        } }
        compose.onNodeWithText("Antes").assertIsDisplayed()
        compose.onNodeWithText("Después").assertIsDisplayed()
        compose.onAllNodes(hasText("123.45", substring = true), useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodes(hasText("678.90", substring = true), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun closingFilterDraftDoesNotApplyIt() {
        var applied = 0
        var dismissed = false
        compose.setContent { KipuTheme { MovementFiltersSheet(MovementHistoryUiState(accessStatus = MovementHistoryAccessDecision.Allowed),
            onDismiss = { dismissed = true }, onApply = { applied++; it.validate() }) } }
        compose.onNodeWithTag("input_filter_min_amount").performScrollTo().performTextInput("15,50")
        compose.onNodeWithText("Cancelar").performScrollTo().performClick()
        assertTrue(dismissed)
        assertEquals(0, applied)
    }

    @Test fun filterDraftSurvivesUiRestorationWithoutApplyingOrGrantingAccess() {
        val restoration = StateRestorationTester(compose)
        var applied = 0
        restoration.setContent { KipuTheme {
            MovementFiltersSheet(MovementHistoryUiState(ownerId = "ux-owner", accessStatus = MovementHistoryAccessDecision.Allowed),
                onDismiss = {}, onApply = { applied++; it.validate() })
        } }
        compose.onNodeWithTag("input_filter_min_amount").performScrollTo().performTextInput("15,50")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("input_filter_min_amount").performScrollTo().assertTextContains("15,50")
        assertEquals(0, applied)
    }

    @Test fun reducedMotionFeedbackReplacesItsStateImmediately() {
        val state = mutableStateOf(MovementHistoryUiState(fallbackUsed = true,
            accessStatus = MovementHistoryAccessDecision.RevalidationRequired(MovementHistoryQuery())))
        compose.setContent { KipuTheme { MovementAccessCard(state.value, {}, {}, {}, reducedMotion = true) } }
        compose.onNodeWithText("Verifica tu acceso Premium").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(recovery = HistoryAccessRecovery.VERIFYING) }
        compose.onNodeWithText("Verifica tu acceso Premium").assertDoesNotExist()
        compose.onAllNodesWithText("Verificando acceso…").assertCountEquals(1)
        compose.onNodeWithTag("btn_revalidate_premium").assertDoesNotExist()
    }

    @Test fun animatedFeedbackKeepsOutgoingAndIncomingContentIdentity() {
        val state = mutableStateOf(MovementHistoryUiState(fallbackUsed = true,
            accessStatus = MovementHistoryAccessDecision.RevalidationRequired(MovementHistoryQuery())))
        compose.mainClock.autoAdvance = false
        compose.setContent { KipuTheme { MovementAccessCard(state.value, {}, {}, {}, reducedMotion = false) } }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { state.value = state.value.copy(recovery = HistoryAccessRecovery.NO_PURCHASE) }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("Verifica tu acceso Premium").assertExists()
        compose.onNodeWithText("No encontramos una compra recuperable").assertExists()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Verifica tu acceso Premium").assertDoesNotExist()
        compose.onNodeWithText("No encontramos una compra recuperable").assertIsDisplayed()
    }

    @Test fun editorAndConflictNeverExposeMaskedAmountsInSemantics() {
        val state = mutableStateOf(MovementEditorUiState(isLoading = false, initialAmountMinor = 12345,
            amountText = "678.90", hasConflict = true, conflictProposedAmountMinor = 98765))
        compose.setContent { CompositionLocalProvider(LocalBalanceMasked provides true) { KipuTheme {
            MovementEditorContent(state.value, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        } } }
        compose.onNodeWithTag("editor_amount_input", useUnmergedTree = true).performScrollTo()
        listOf("123.45", "678.90", "987.65").forEach { amount ->
            compose.onAllNodes(hasText(amount, substring = true), useUnmergedTree = true).assertCountEquals(0)
            compose.onAllNodes(hasContentDescription(amount, substring = true), useUnmergedTree = true).assertCountEquals(0)
        }
        compose.onNodeWithTag("editor_amount_input", useUnmergedTree = true).assertContentDescriptionEquals("Importe oculto")
    }

    @Test fun expandedTextHistoryKeepsAmountsReadableAndProducesVisualEvidence() {
        compose.setContent { KipuTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Historial", style = MaterialTheme.typography.headlineMedium)
                        Text("Hoy", style = MaterialTheme.typography.labelLarge)
                        TransactionRow(item())
                        TransactionRow(item(TransactionStatus.VOIDED).copy(transaction = item(TransactionStatus.VOIDED).transaction.copy(id = "ux-voided")))
                        MovementAccessCard(MovementHistoryUiState(fallbackUsed = true,
                            accessStatus = MovementHistoryAccessDecision.RevalidationRequired(MovementHistoryQuery())), {}, {}, {}, reducedMotion = true)
                    }
                }
            }
        } }
        compose.onNodeWithTag("tx_amount_ux-movement", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Anulado").assertIsDisplayed()
        saveScreenshot("history-large-font.png")
    }

    @Test fun historyRouteOpensVoidedDetailAndRendersLightAndDarkRecovery() {
        val rows = listOf(item(), item(TransactionStatus.VOIDED).let { it.copy(transaction = it.transaction.copy(id = "ux-voided")) })
        val sessions = object : SessionCoordinator {
            override val localAccess = MutableStateFlow<LocalAccess>(LocalAccess.Available("ux-owner", RemoteSession.Absent))
            override val remoteSession = MutableStateFlow<RemoteSession>(RemoteSession.Absent)
            override val currentOwner: LocalOwner? = null
            override suspend fun setActiveOwner(userId: String) = Unit
            override suspend fun clearActiveOwner(explicit: Boolean) = Unit
            override suspend fun updateRemoteSession(session: RemoteSession) = Unit
            override suspend fun updateLockState(isLocked: Boolean, reason: String) = Unit
        }
        val repository = object : MovementRepository {
            override fun observeTransactions(userId: String) = flowOf(rows)
            override fun observeRecentTransactions(userId: String, limit: Int) = flowOf(rows.take(limit))
            override suspend fun getTransactionById(userId: String, transactionId: String) = rows.firstOrNull { it.transaction.id == transactionId }?.transaction
            override suspend fun registerTransaction(command: RegisterTransactionCommand) = RegisterTransactionResult.Failure("Read-only UI fixture")
            override suspend fun findSimilarTransactions(userId: String, sourceAccountId: String, type: MovementType, amountMinor: Long,
                currency: String, occurredAt: Long, windowMillis: Long) = emptyList<Transaction>()
            override fun observeBalance(userId: String, accountId: String): Flow<Long?> = flowOf(null)
        }
        val query = QueryMovementHistory(object : MovementHistoryQueryRepository {
            override suspend fun queryHistory(userId: String, query: MovementHistoryQuery) = MovementHistoryPage(rows, null, false, MovementHistoryAccessDecision.Allowed)
        }, entitlementProvider = object : MovementEntitlementProvider {
            override suspend fun getEffectiveEntitlement(userId: String) = MovementEntitlementEvidence(
                verified = false, verifiedServerTimeMillis = 0, entitlementExpiresAtMillis = null, revalidationRequired = true)
        })
        val model = MovementHistoryViewModel(repository, sessions, queryMovementHistoryUseCase = query)
        val store = androidx.lifecycle.ViewModelStore().apply { put("ux", model) }
        val dark = mutableStateOf(false)
        try {
            compose.setContent { KipuTheme(darkTheme = dark.value) { MovementHistoryRoute(viewModel = model) } }
            compose.waitUntil(5_000) { model.uiState.value.allTransactions.size == 2 }
            compose.onNodeWithTag("tx_row_ux-voided").performScrollTo().performClick()
            compose.onNodeWithText("Movimiento anulado · Solo lectura").assertIsDisplayed()
            compose.onNodeWithTag("detail_edit").assertDoesNotExist()
            compose.onNodeWithText("Cerrar detalle").performScrollTo().performClick()
            compose.runOnIdle { model.onAccountFilterToggled("cash") }
            compose.waitUntil(5_000) { model.uiState.value.fallbackUsed }
            compose.onNodeWithTag("input_search_movements").performScrollTo()
            compose.onNodeWithTag("btn_open_filters").assertIsDisplayed()
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            saveScreenshot("history-route-light.png")
            compose.runOnIdle { dark.value = true }
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            compose.onNodeWithText("Verifica tu acceso Premium").assertIsDisplayed()
            saveScreenshot("history-route-dark.png")
        } finally { store.clear() }
    }

    private fun saveScreenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "sprint4-ux").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
