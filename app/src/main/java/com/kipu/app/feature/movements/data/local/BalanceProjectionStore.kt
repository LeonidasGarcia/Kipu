package com.kipu.app.feature.movements.data.local

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BalanceProjectionStore @Inject constructor(
    private val movementDao: MovementDao,
) {
    fun observeBalance(userId: String, accountId: String): Flow<Long?> {
        return movementDao.observeBalanceProjection(userId, accountId)
    }

    suspend fun getBalance(userId: String, accountId: String): Long {
        val projection = movementDao.getBalanceProjection(userId, accountId)
        if (projection != null) {
            return projection.balanceMinor
        }
        // Fallback or rebuild from ledger entries
        return rebuildBalanceFromLedger(userId, accountId, "PEN")
    }

    suspend fun rebuildBalanceFromLedger(userId: String, accountId: String, currencyCode: String): Long {
        val sum = movementDao.calculateLedgerSumForAccount(userId, accountId) ?: 0L
        movementDao.upsertBalanceProjection(
            BalanceProjectionEntity(
                userId = userId,
                accountId = accountId,
                balanceMinor = sum,
                currencyCode = currencyCode,
                lastTransactionAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
        )
        return sum
    }
}
