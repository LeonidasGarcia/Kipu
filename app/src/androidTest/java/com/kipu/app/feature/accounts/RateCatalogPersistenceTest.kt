package com.kipu.app.feature.accounts

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.OperationId
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.accounts.data.OfflineFirstFinancialInstrumentsRepository
import com.kipu.app.feature.accounts.data.remote.CreditProductCatalogDto
import com.kipu.app.feature.accounts.data.remote.FinancialApiResponse
import com.kipu.app.feature.accounts.data.remote.FinancialInstrumentsApi
import com.kipu.app.feature.accounts.data.sync.InstrumentSyncScheduler
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditProductReference
import com.kipu.app.feature.accounts.domain.usecase.UpdatePersonalTea
import com.kipu.app.feature.accounts.presentation.components.CardStylePresets
import com.kipu.app.feature.accounts.presentation.instruments.RateCatalogContext
import com.kipu.app.feature.accounts.presentation.instruments.RateCatalogScreen
import com.kipu.app.feature.accounts.presentation.instruments.resolveRateCatalogContext
import io.mockk.coEvery
import io.mockk.mockk
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real repository/Room round trip with a fixed catalog response, test session and sync scheduler. */
@RunWith(AndroidJUnit4::class)
class RateCatalogPersistenceTest {
    @get:Rule val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val userId = UUID.randomUUID().toString()
    private val databaseName = "rate-catalog-test-${UUID.randomUUID()}.db"
    private lateinit var database: KipuDatabase
    private lateinit var repository: OfflineFirstFinancialInstrumentsRepository
    private lateinit var products: List<CreditProductReference>
    private val api = mockk<FinancialInstrumentsApi>()

    @Before
    fun setUp() = runBlocking {
        // Exact public seed rows, including null/unpublished values; IDs are test-only UUIDs.
        val fixture = InstrumentationRegistry.getInstrumentation().context.assets
            .open("credit-products-2026-09-24.json").bufferedReader().use { it.readText() }
        val catalog = Json.decodeFromString<List<CreditProductCatalogDto>>(fixture)
        assertEquals(44, catalog.size)
        coEvery { api.fetchCreditProducts() } returns FinancialApiResponse.Success(catalog)
        openDatabase()
        products = repository.getCreditProductCatalog().getOrThrow()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun bcpAmexSelectionSurvivesDatabaseReopenWithUsdReference() = runBlocking {
        val product = product("BCP", "American Express Oro LATAM Pass")
        val registered = register(product, Currency.USD, "1001")
        assertEquals("bcp-amex-latam-gold", registered.stylePresetId)
        reopenDatabase()

        val restored = readCard(registered.id)
        assertEquals(registered.stylePresetId, restored.stylePresetId)
        assertEquals(Currency.USD, restored.currency)
        val reference = resolveRateCatalogContext(restored, products) as RateCatalogContext.Resolved
        assertEquals(product, reference.product)
        assertEquals(6500, reference.product.usdTeaMinBps)
        assertEquals(7690, reference.product.usdTeaMaxBps)
        showRates(restored, "bcp-amex-usd.png", "TEA dólares: 65.00% – 76.90%")
    }

    @Test
    fun interbankAmexSelectionSurvivesReopenWithoutInventingUnpublishedRates() = runBlocking {
        val product = product("INTERBANK", "American Express Gold")
        val registered = register(product, Currency.PEN, "1002")
        assertEquals("interbank-amex-gold", registered.stylePresetId)
        reopenDatabase()

        val restored = readCard(registered.id)
        assertEquals(registered.stylePresetId, restored.stylePresetId)
        val reference = resolveRateCatalogContext(restored, products) as RateCatalogContext.Resolved
        assertEquals(product, reference.product)
        assertNull(reference.product.penTeaMinBps)
        assertNull(reference.product.penTeaMaxBps)
        assertNull(reference.product.usdTeaMinBps)
        assertNull(reference.product.usdTeaMaxBps)
        showRates(restored, "interbank-amex-unpublished.png", "TEA soles: No publicado numéricamente")
    }

    @Test
    fun updatingOnePersonalTeaPreservesOtherCardAndCatalogAfterReopen() = runBlocking {
        val bcp = register(product("BCP", "American Express Oro LATAM Pass"), Currency.PEN, "1003")
        val interbank = register(product("INTERBANK", "American Express Gold"), Currency.PEN, "1004")
        val update = UpdatePersonalTea(repository)
        update(bcp.id, 5325).getOrThrow()
        update(interbank.id, 6100).getOrThrow()
        update(bcp.id, 5500).getOrThrow()
        reopenDatabase()

        val restoredBcp = readCard(bcp.id)
        val restoredInterbank = readCard(interbank.id)
        assertEquals(5500, restoredBcp.personalTeaBps)
        assertEquals(6100, restoredInterbank.personalTeaBps)
        assertEquals(products, repository.getCreditProductCatalog().getOrThrow())
        assertTrue(resolveRateCatalogContext(restoredBcp, products) is RateCatalogContext.Resolved)
        assertTrue(resolveRateCatalogContext(restoredInterbank, products) is RateCatalogContext.Resolved)
        showRates(restoredBcp, "bcp-amex-personal-tea.png", "TEA soles: 65.00% – 95.90%", restoredInterbank)
    }

    private fun product(issuer: String, name: String) = products.single {
        it.institutionCode == issuer && it.productName == name
    }

    private suspend fun register(product: CreditProductReference, currency: Currency, lastFour: String): CreditCard {
        // Same exact selection mapping as UnifiedInstrumentFormScreen.
        val preset = CardStylePresets.forProduct(product.institutionCode, product.productName, product.cardNetwork)
        assertNotNull(preset)
        val card = CreditCard(
            id = CardId.generate(), userId = UserId(userId), alias = product.productName,
            issuer = product.institutionCode, network = CardNetwork.valueOf(requireNotNull(product.cardNetwork)),
            lastFourDigits = lastFour, currency = currency, creditLimitMinorUnits = 500_000L,
            billingDay = 10, dueDay = 20, stylePresetId = requireNotNull(preset).id,
        )
        return repository.registerCreditCard(card, OperationId.generate()).getOrThrow()
    }

    private suspend fun readCard(id: CardId) = requireNotNull(repository.observeCardById(id).first()) as CreditCard

    private fun openDatabase() {
        database = Room.databaseBuilder(context, KipuDatabase::class.java, databaseName).build()
        repository = OfflineFirstFinancialInstrumentsRepository(
            database = database, financialApi = api,
            accountDao = database.accountDao(), cardDao = database.cardDao(),
            movementDao = database.financialMovementDao(), syncDao = database.instrumentSyncDao(),
            sessionCoordinator = TestSessionCoordinator(userId),
            syncScheduler = mockk<InstrumentSyncScheduler>(relaxed = true),
        )
    }

    private fun reopenDatabase() {
        database.close()
        openDatabase()
    }

    private fun showRates(card: CreditCard, screenshotName: String, expectedTeaText: String, nextCard: CreditCard? = null) {
        val selectedCard = mutableStateOf(card)
        compose.setContent {
            MaterialTheme {
                RateCatalogScreen(
                    creditCard = selectedCard.value, products = products, catalogError = null,
                    catalogLoading = false, cardLoading = false, events = emptyFlow(),
                    onLoadCatalog = {}, onUpdatePersonalTea = { _, _ -> }, onNavigateBack = {},
                )
            }
        }
        compose.onNodeWithText("Referencia aplicable a esta tarjeta").assertIsDisplayed()
        compose.onNodeWithText(expectedTeaText).assertIsDisplayed()
        card.personalTeaBps?.let {
            compose.onNodeWithText(String.format(java.util.Locale.US, "%.2f", it / 100.0)).assertIsDisplayed()
        }
        compose.waitForIdle()
        captureScreenshot(screenshotName)
        if (nextCard != null) {
            compose.runOnIdle { selectedCard.value = nextCard }
            compose.waitForIdle()
            compose.onNodeWithText(String.format(java.util.Locale.US, "%.2f", requireNotNull(nextCard.personalTeaBps) / 100.0))
                .assertIsDisplayed()
            compose.onNodeWithText("TEA soles: No publicado numéricamente").assertIsDisplayed()
            captureScreenshot("interbank-amex-personal-tea.png")
        }
    }

    private fun captureScreenshot(screenshotName: String) {
        val screenshots = File(context.getExternalFilesDir(null), "issue25-validation").apply { mkdirs() }
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            File(screenshots, screenshotName).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }

    private class TestSessionCoordinator(userId: String) : SessionCoordinator {
        override val remoteSession = MutableStateFlow<RemoteSession>(RemoteSession.Absent)
        override val localAccess = MutableStateFlow<LocalAccess>(LocalAccess.Available(userId, RemoteSession.Absent))
        override val currentOwner = null
        override suspend fun setActiveOwner(userId: String) = Unit
        override suspend fun clearActiveOwner(explicit: Boolean) = Unit
        override suspend fun updateRemoteSession(session: RemoteSession) = Unit
        override suspend fun updateLockState(isLocked: Boolean, reason: String) = Unit
    }
}
