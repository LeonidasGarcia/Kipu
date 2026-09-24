package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CardPreset
import com.kipu.app.feature.accounts.presentation.AccountUiEvent
import com.kipu.app.feature.accounts.presentation.AccountsViewModel
import com.kipu.app.feature.accounts.presentation.InstrumentsUiState
import com.kipu.app.ui.theme.KipuTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CardFormScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rejectsOversizedLastFourPasteWithoutDisplayingOrKeepingTheDigits() {
        setScreen()
        val pastedDigits = "1234567890123456"
        val lastFourField = compose.onNodeWithTag("card_last_four_digits")

        lastFourField.performScrollTo().performTextInput(pastedDigits)

        compose.onNodeWithText("Solo se guardan los últimos 4 dígitos. La entrada se rechazó y no se guardó.")
            .assertIsDisplayed()
        compose.onNodeWithText(pastedDigits).assertDoesNotExist()
        assertEquals("", lastFourField.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)

        lastFourField.performTextInput("1234")

        compose.onNodeWithText("Solo se guardan los últimos 4 dígitos. La entrada se rechazó y no se guardó.")
            .assertDoesNotExist()
        assertEquals("1234", lastFourField.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithText("•••• 1234").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun creditTermsShowErrorsForMissingMalformedAndOutOfRangeValues() {
        setScreen()
        compose.onNodeWithText("Tarjeta de Crédito").performClick()

        compose.onNodeWithText("Línea de crédito autorizada (PEN)")
            .performScrollTo()
            .performTextClearance()
        compose.onNodeWithText("Ingresa una línea de crédito autorizada.").assertIsDisplayed()
        compose.onNodeWithText("Línea de crédito autorizada (PEN)")
            .performScrollTo()
            .performTextInput("abc")
        compose.onNodeWithText("Ingresa un importe válido con hasta dos decimales.").assertIsDisplayed()

        compose.onNodeWithText("Día de corte (1-31)")
            .performScrollTo()
            .performTextClearance()
        compose.onNodeWithText("Ingresa el día de corte.").assertIsDisplayed()
        compose.onNodeWithText("Día de pago (1-31)")
            .performScrollTo()
            .performTextClearance()
        compose.onNodeWithText("Día de pago (1-31)")
            .performScrollTo()
            .performTextInput("32")
        compose.onNodeWithText("Ingresa el día de pago entre 1 y 31.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Registrar Tarjeta").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun acceptsDay31AndPassesItThroughWithoutClamping() {
        val viewModel = createViewModel()
        setScreen(viewModel)

        compose.onNodeWithText("Tarjeta de Crédito").performClick()
        compose.onNodeWithTag("card_last_four_digits").performScrollTo().performTextInput("1234")
        compose.onNodeWithText("Día de corte (1-31)")
            .performScrollTo()
            .performTextClearance()
        compose.onNodeWithText("Día de corte (1-31)")
            .performScrollTo()
            .performTextInput("31")

        compose.onNodeWithText("Registrar Tarjeta").performScrollTo().assertIsEnabled().performClick()

        verify(timeout = 5_000) {
            viewModel.registerCreditCard(
                alias = "",
                issuer = "BCP",
                network = CardNetwork.VISA,
                lastFourDigits = "1234",
                currency = Currency.PEN,
                creditLimitMinorUnits = 100_000L,
                billingDay = 31,
                dueDay = 5,
                personalTeaBps = null,
                preset = CardPreset.BCP_VISA,
                colorToken = CardPreset.BCP_VISA.defaultColorToken,
                iconToken = CardPreset.BCP_VISA.defaultIconToken,
                onSuccess = any(),
            )
        }
    }

    private fun setScreen(viewModel: AccountsViewModel = createViewModel()) {
        compose.setContent {
            KipuTheme {
                CardFormScreen(viewModel = viewModel, onNavigateBack = {})
            }
        }
    }

    private fun createViewModel(): AccountsViewModel = mockk(relaxed = true) {
        every { instrumentsUiState } returns MutableStateFlow(InstrumentsUiState(isLoading = false))
        every { events } returns emptyFlow<AccountUiEvent>()
        coEvery { checkDuplicateCard(any(), any(), any()) } returns false
    }
}
