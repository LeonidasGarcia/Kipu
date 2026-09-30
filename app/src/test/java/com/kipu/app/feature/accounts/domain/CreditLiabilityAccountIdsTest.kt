package com.kipu.app.feature.accounts.domain

import com.kipu.app.feature.accounts.domain.model.CreditLiabilityAccountIds
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditLiabilityAccountIdsTest {
    @Test
    fun `liability account identity is stable and scoped to owner and card`() {
        val userId = "11111111-1111-4111-8111-111111111111"
        val cardId = "22222222-2222-4222-8222-222222222222"

        val accountId = CreditLiabilityAccountIds.accountId(userId, cardId)

        assertEquals(accountId, CreditLiabilityAccountIds.accountId(userId.uppercase(), cardId.uppercase()))
        assertNotEquals(accountId, CreditLiabilityAccountIds.accountId("33333333-3333-4333-8333-333333333333", cardId))
        assertNotEquals(accountId, CreditLiabilityAccountIds.accountId(userId, "44444444-4444-4444-8444-444444444444"))
        assertNotEquals(accountId, CreditLiabilityAccountIds.creationOperationId(userId, cardId))
        assertEquals(3, UUID.fromString(accountId).version())
        assertTrue(UUID.fromString(accountId).variant() == 2)
    }
}
