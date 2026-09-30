package com.kipu.app.feature.accounts.domain.usecase

import com.kipu.app.core.finance.domain.model.AccountId
import com.kipu.app.core.finance.domain.model.Money
import com.kipu.app.core.finance.domain.model.OperationId
import com.kipu.app.feature.accounts.domain.FinancialInstrumentsRepository
import com.kipu.app.feature.accounts.domain.model.Account
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import java.time.Instant
import javax.inject.Inject

class QuotaExceededException(message: String = "Límite de instrumentos activos alcanzado para el plan Free") :
    IllegalStateException(message)

class CreateLiquidAccount @Inject constructor(
    private val repository: FinancialInstrumentsRepository,
) {
    suspend operator fun invoke(
        account: Account,
        operationId: OperationId = OperationId.generate(),
    ): Result<Account> {
        if (account.type == com.kipu.app.feature.accounts.domain.model.AccountType.CREDIT_LIABILITY) {
            return Result.failure(IllegalArgumentException("Credit liability accounts are managed by their card"))
        }
        if (account.isComputableForQuota) {
            val currentCount = repository.getActiveComputableCount()
            if (currentCount >= FinancialInstrumentsRepository.FREE_TIER_MAX_COMPUTABLE_INSTRUMENTS) {
                return Result.failure(QuotaExceededException("Límite de instrumentos activos alcanzado para el plan Free (máx. ${FinancialInstrumentsRepository.FREE_TIER_MAX_COMPUTABLE_INSTRUMENTS})"))
            }
        }
        return repository.createLiquidAccount(account, operationId)
    }
}

class RecordOpeningAdjustment @Inject constructor(
    private val repository: FinancialInstrumentsRepository,
) {
    suspend operator fun invoke(
        accountId: AccountId,
        correctedAmount: Money,
        correctedDate: Instant,
        operationId: OperationId = OperationId.generate(),
    ): Result<Unit> {
        if (correctedAmount.minorUnits < 0) {
            return Result.failure(IllegalArgumentException("El saldo inicial corregido no puede ser negativo"))
        }
        return repository.recordOpeningAdjustment(accountId, correctedAmount, correctedDate, operationId)
    }
}

class UpdateInstrumentAppearance @Inject constructor(
    private val repository: FinancialInstrumentsRepository,
) {
    suspend operator fun invoke(
        accountId: AccountId,
        alias: String,
        preset: AccountPreset?,
        colorToken: String?,
        iconToken: String?,
        operationId: OperationId = OperationId.generate(),
    ): Result<Unit> {
        if (alias.isBlank() || alias.trim().length > 80) {
            return Result.failure(IllegalArgumentException("El alias debe tener entre 1 y 80 caracteres"))
        }
        return repository.updateAccountAppearance(accountId, alias.trim(), preset, colorToken, iconToken, operationId)
    }
}

class ArchiveInstrument @Inject constructor(
    private val repository: FinancialInstrumentsRepository,
) {
    suspend operator fun invoke(
        instrumentId: String,
        isCard: Boolean,
        operationId: OperationId = OperationId.generate(),
    ): Result<Unit> {
        return repository.archiveInstrument(instrumentId, isCard, operationId)
    }
}

class ReactivateInstrument @Inject constructor(
    private val repository: FinancialInstrumentsRepository,
) {
    suspend operator fun invoke(
        instrumentId: String,
        isCard: Boolean,
        isComputableForQuota: Boolean,
        operationId: OperationId = OperationId.generate(),
    ): Result<Unit> {
        if (isComputableForQuota) {
            val currentCount = repository.getActiveComputableCount()
            if (currentCount >= FinancialInstrumentsRepository.FREE_TIER_MAX_COMPUTABLE_INSTRUMENTS) {
                return Result.failure(QuotaExceededException("No se puede reactivar: límite de instrumentos activos alcanzado (máx. ${FinancialInstrumentsRepository.FREE_TIER_MAX_COMPUTABLE_INSTRUMENTS})"))
            }
        }
        return repository.reactivateInstrument(instrumentId, isCard, operationId)
    }
}
