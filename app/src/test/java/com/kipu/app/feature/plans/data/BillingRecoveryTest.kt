package com.kipu.app.feature.plans.data

import com.kipu.app.feature.plans.data.billing.BillingRepository
import com.kipu.app.feature.plans.data.billing.PlayBillingGateway
import com.kipu.app.feature.plans.domain.model.*
import io.mockk.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BillingRecoveryTest {
    private val gateway = mockk<PlayBillingGateway> {
        every { purchaseUpdates } returns MutableSharedFlow<StorePurchaseUpdate>()
    }
    private fun repository() = BillingRepository(gateway, mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), mockk())

    @Test fun noPurchaseCompletesWithoutLoadingCatalogOrClearingAccess() = runTest {
        coEvery { gateway.recoverPurchaseUpdates() } returns emptyList()
        assertEquals(BillingVerificationResult.Retryable("NO_RECOVERABLE_PURCHASE"), repository().restoreAndVerifyAccess())
        coVerify(exactly = 0) { gateway.queryOffers(any()) }
    }

    @Test fun pendingPurchaseIsTerminalWithoutCreatingPremiumEvidence() = runTest {
        coEvery { gateway.recoverPurchaseUpdates() } returns listOf(StorePurchaseUpdate(productId = "premium", purchaseToken = null, state = StorePurchaseState.PENDING))
        assertEquals(BillingVerificationResult.PaymentPending("premium"), repository().restoreAndVerifyAccess())
    }

    @Test fun networkFailureHasAnExplicitRetryResult() = runTest {
        coEvery { gateway.recoverPurchaseUpdates() } throws IllegalStateException("offline")
        assertEquals(BillingVerificationResult.Retryable("VERIFICATION_UNAVAILABLE"), repository().restoreAndVerifyAccess())
    }

    @Test fun noPlatformCallbackCannotLeaveRecoveryLoadingForever() = runTest {
        coEvery { gateway.recoverPurchaseUpdates() } coAnswers { awaitCancellation() }
        assertEquals(BillingVerificationResult.Retryable("VERIFICATION_TIMEOUT"), repository().restoreAndVerifyAccess())
    }
}
