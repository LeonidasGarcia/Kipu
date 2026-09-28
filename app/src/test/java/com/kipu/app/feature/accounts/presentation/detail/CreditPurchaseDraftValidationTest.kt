package com.kipu.app.feature.accounts.presentation.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditPurchaseDraftValidationTest {
    @Test
    fun missingCategoryCannotCreateConfirmedPurchaseCandidate() {
        assertFalse(isValidCreditPurchaseDraft(12_500L, "Bodega", null))
        assertFalse(isValidCreditPurchaseDraft(12_500L, "Bodega", " "))
    }

    @Test
    fun purchaseDraftRequiresPositiveAmountAndMerchantAsWellAsCategory() {
        assertFalse(isValidCreditPurchaseDraft(null, "Bodega", "food"))
        assertFalse(isValidCreditPurchaseDraft(0L, "Bodega", "food"))
        assertFalse(isValidCreditPurchaseDraft(12_500L, " ", "food"))
        assertTrue(isValidCreditPurchaseDraft(12_500L, "Bodega", "food"))
    }
}
