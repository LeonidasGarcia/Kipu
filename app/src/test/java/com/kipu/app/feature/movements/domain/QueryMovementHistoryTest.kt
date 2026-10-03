package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.AdvancedHistoryCriteria
import com.kipu.app.feature.movements.domain.model.MovementFinancialState
import com.kipu.app.feature.movements.domain.model.MovementHistoryAccessDecision
import com.kipu.app.feature.movements.domain.model.MovementHistoryCursor
import com.kipu.app.feature.movements.domain.model.MovementHistoryPage
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery
import com.kipu.app.feature.movements.domain.model.MovementType
import com.kipu.app.feature.movements.domain.model.Transaction
import com.kipu.app.feature.movements.domain.model.TransactionItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryMovementHistoryTest {

    private class FakeQueryRepository : MovementHistoryQueryRepository {
        var lastExecutedQuery: MovementHistoryQuery? = null

        override suspend fun queryHistory(userId: String, query: MovementHistoryQuery): MovementHistoryPage {
            lastExecutedQuery = query
            return MovementHistoryPage(
                items = listOf(
                    TransactionItem(
                        transaction = Transaction(
                            id = "tx-1",
                            userId = userId,
                            type = MovementType.EXPENSE,
                            amountMinor = 1500L,
                            currency = "PEN",
                            sourceAccountId = "acc-1",
                            categoryId = "cat-1",
                            occurredAt = 1000L,
                        )
                    )
                ),
                nextCursor = null,
                hasMore = false,
                accessDecision = MovementHistoryAccessDecision.Allowed,
            )
        }
    }

    private class FakeEntitlementProvider(var evidence: MovementEntitlementEvidence?) : MovementEntitlementProvider {
        override suspend fun getEffectiveEntitlement(userId: String): MovementEntitlementEvidence? = evidence
    }

    @Test
    fun basicQueryAllowedForFreeUser() = runTest {
        val repository = FakeQueryRepository()
        val provider = FakeEntitlementProvider(null) // Free user
        val useCase = QueryMovementHistory(repository = repository, entitlementProvider = provider)

        val query = MovementHistoryQuery(
            queryText = "Tambo",
            fromInclusive = 100L,
            toExclusive = 500L,
            types = setOf(MovementType.EXPENSE),
        )

        val result = useCase("user-1", query)

        assertEquals(MovementHistoryAccessDecision.Allowed, result.accessDecision)
        assertFalse(result.fallbackUsed)
        assertEquals(1, result.items.size)
        assertEquals("Tambo", repository.lastExecutedQuery?.queryText)
    }

    @Test
    fun advancedQueryDeniedForFreeUserAndFallsBackToBasic() = runTest {
        val repository = FakeQueryRepository()
        val provider = FakeEntitlementProvider(MovementEntitlementEvidence(verified = false, verifiedServerTimeMillis = 0L, entitlementExpiresAtMillis = null))
        val useCase = QueryMovementHistory(repository = repository, entitlementProvider = provider)

        val query = MovementHistoryQuery(
            queryText = "Supermercado",
            fromInclusive = 100L,
            toExclusive = 500L,
            types = setOf(MovementType.EXPENSE),
            advancedCriteria = AdvancedHistoryCriteria(
                categoryIds = setOf("cat-groceries"),
                minAmountMinor = 1000L,
                currency = "PEN",
            ),
        )

        val result = useCase("user-1", query)

        assertTrue(result.accessDecision is MovementHistoryAccessDecision.PremiumRequired)
        assertTrue(result.fallbackUsed)
        // Repository received fallback query without advanced criteria!
        assertNull(repository.lastExecutedQuery?.advancedCriteria)
        assertEquals("Supermercado", repository.lastExecutedQuery?.queryText)
        assertEquals(setOf(MovementType.EXPENSE), repository.lastExecutedQuery?.types)
    }

    @Test
    fun advancedQueryAllowedWhenConcessionActive() = runTest {
        val repository = FakeQueryRepository()
        val verifiedTime = 1_000_000L
        val evidence = MovementEntitlementEvidence(
            verified = true,
            verifiedServerTimeMillis = verifiedTime,
            entitlementExpiresAtMillis = verifiedTime + 1_000_000_000L,
            isLifetime = false,
            monotonicContinuityValid = true,
            trustedNowMillis = verifiedTime + (72L * 3600L * 1000L) - 1L,
        )
        val provider = FakeEntitlementProvider(evidence)
        val useCase = QueryMovementHistory(repository = repository, entitlementProvider = provider)

        val query = MovementHistoryQuery(
            advancedCriteria = AdvancedHistoryCriteria(
                accountIds = setOf("acc-bcp"),
            ),
        )

        // Trusted now is before 72 hours: verifiedTime + 72h - 1ms
        val result = useCase("user-1", query)

        assertEquals(MovementHistoryAccessDecision.Allowed, result.accessDecision)
        assertFalse(result.fallbackUsed)
        assertEquals(setOf("acc-bcp"), repository.lastExecutedQuery?.advancedCriteria?.accountIds)
    }

    @Test
    fun advancedQueryRevalidationRequiredAtExactNotAfter() = runTest {
        val repository = FakeQueryRepository()
        val verifiedTime = 1_000_000L
        val notAfter = verifiedTime + (72L * 3600L * 1000L)
        val evidence = MovementEntitlementEvidence(
            verified = true,
            verifiedServerTimeMillis = verifiedTime,
            entitlementExpiresAtMillis = verifiedTime + 1_000_000_000L,
            monotonicContinuityValid = true,
            trustedNowMillis = notAfter,
        )
        val useCase = QueryMovementHistory(repository = repository, entitlementProvider = FakeEntitlementProvider(evidence))

        val query = MovementHistoryQuery(
            advancedCriteria = AdvancedHistoryCriteria(accountIds = setOf("acc-bcp")),
        )

        // Exact match trustedNow == notAfter must require revalidation
        val result = useCase("user-1", query)

        assertTrue(result.accessDecision is MovementHistoryAccessDecision.RevalidationRequired)
        assertTrue(result.fallbackUsed)
        assertNull(repository.lastExecutedQuery?.advancedCriteria)
    }

    @Test
    fun commercialExpirationEarlierThan72HoursExpiresAtCommercialEnd() = runTest {
        val repository = FakeQueryRepository()
        val verifiedTime = 1_000_000L
        val commercialEnd = verifiedTime + (12L * 3600L * 1000L) // 12 hours from now
        val evidence = MovementEntitlementEvidence(
            verified = true,
            verifiedServerTimeMillis = verifiedTime,
            entitlementExpiresAtMillis = commercialEnd,
            monotonicContinuityValid = true,
            trustedNowMillis = commercialEnd,
        )
        val useCase = QueryMovementHistory(repository = repository, entitlementProvider = FakeEntitlementProvider(evidence))

        val query = MovementHistoryQuery(
            advancedCriteria = AdvancedHistoryCriteria(cardIds = setOf("card-1")),
        )

        // At commercial end, revalidation is required even if 72 hours have not passed!
        val result = useCase("user-1", query)

        assertTrue(result.accessDecision is MovementHistoryAccessDecision.RevalidationRequired)
        assertTrue(result.fallbackUsed)
    }

    @Test
    fun lifetimeUses72HourConcessionCap() = runTest {
        val repository = FakeQueryRepository()
        val verifiedTime = 1_000_000L
        val past72h = verifiedTime + (72L * 3600L * 1000L) + 1L
        val evidence = MovementEntitlementEvidence(
            verified = true,
            verifiedServerTimeMillis = verifiedTime,
            entitlementExpiresAtMillis = null,
            isLifetime = true,
            monotonicContinuityValid = true,
            trustedNowMillis = past72h,
        )
        val useCase = QueryMovementHistory(repository = repository, entitlementProvider = FakeEntitlementProvider(evidence))

        val query = MovementHistoryQuery(
            advancedCriteria = AdvancedHistoryCriteria(merchantIds = setOf("merchant-tambo")),
        )

        // At 72h + 1ms, lifetime requires revalidation
        val result = useCase("user-1", query)

        assertTrue(result.accessDecision is MovementHistoryAccessDecision.RevalidationRequired)
    }

    @Test
    fun rebootWithoutVerifiableContinuityRequiresRevalidation() = runTest {
        val repository = FakeQueryRepository()
        val verifiedTime = 1_000_000L
        val evidence = MovementEntitlementEvidence(
            verified = false,
            verifiedServerTimeMillis = verifiedTime,
            entitlementExpiresAtMillis = verifiedTime + 100_000_000L,
            monotonicContinuityValid = false, // Broken continuity after reboot
            revalidationRequired = true,
        )
        val useCase = QueryMovementHistory(repository = repository, entitlementProvider = FakeEntitlementProvider(evidence))

        val query = MovementHistoryQuery(
            advancedCriteria = AdvancedHistoryCriteria(financialStates = setOf(MovementFinancialState.CONFIRMED)),
        )

        val result = useCase("user-1", query)

        assertTrue(result.accessDecision is MovementHistoryAccessDecision.RevalidationRequired)
        assertTrue(result.fallbackUsed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidAmountRangeThrowsException() {
        AdvancedHistoryCriteria(
            minAmountMinor = 5000L,
            maxAmountMinor = 2000L,
            currency = "PEN",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun amountFilterWithoutCurrencyThrowsException() {
        AdvancedHistoryCriteria(
            minAmountMinor = 1000L,
            currency = null,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidDateRangeThrowsException() {
        MovementHistoryQuery(
            fromInclusive = 5000L,
            toExclusive = 2000L,
        )
    }

    @Test
    fun notAuthorizedWhenUserIdIsBlank() = runTest {
        val repository = FakeQueryRepository()
        val useCase = QueryMovementHistory(repository = repository)

        val result = useCase("", MovementHistoryQuery())

        assertEquals(MovementHistoryAccessDecision.NotAuthorized, result.accessDecision)
        assertNull(repository.lastExecutedQuery)
    }
}
