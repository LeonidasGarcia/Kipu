package com.kipu.app.feature.debts.data

import com.kipu.app.feature.debts.data.local.DebtDao
import com.kipu.app.feature.debts.data.local.DebtLocalDataSource
import com.kipu.app.feature.debts.data.local.DebtSummaryRow
import com.kipu.app.feature.debts.data.sync.DebtSyncScheduler
import com.kipu.app.feature.debts.data.sync.DebtReminderReconciler
import com.kipu.app.feature.debts.data.sync.NoOpDebtReminderReconciler
import com.kipu.app.feature.debts.domain.DebtRepository
import com.kipu.app.feature.debts.domain.model.DebtCommandResult
import com.kipu.app.feature.debts.domain.model.DebtCommandIdentity
import com.kipu.app.feature.debts.domain.model.DebtDeleteResult
import com.kipu.app.feature.debts.domain.model.DebtDescriptionPatch
import com.kipu.app.feature.debts.domain.model.DebtDetailsEditResult
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.debts.domain.model.DebtOpeningMode
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.DebtClosureCommand
import com.kipu.app.feature.debts.domain.model.OpenDebtCommand
import com.kipu.app.feature.debts.domain.model.SettleDebtCommand
import com.kipu.app.feature.debts.domain.model.SetDebtScheduleCommand
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Singleton
class OfflineFirstDebtRepository @Inject constructor(
    private val debtDao: DebtDao,
    private val localDataSource: DebtLocalDataSource,
    private val syncScheduler: DebtSyncScheduler,
    private val reminderReconciler: DebtReminderReconciler = NoOpDebtReminderReconciler,
) : DebtRepository {
    override fun observeDebts(userId: String): Flow<List<DebtSummary>> =
        debtDao.observeSummaries(userId)
            .map { rows -> rows.map { row -> row.toDomain() } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override fun observeDebt(userId: String, debtId: String): Flow<DebtSummary?> =
        debtDao.observeSummary(userId, debtId).map { it?.toDomain() }

    override fun observeInstallments(userId: String, debtId: String): Flow<List<DebtScheduleItem>> =
        debtDao.observeInstallments(userId, debtId).map { installments ->
            installments.map { item ->
                DebtScheduleItem(
                    id = item.id,
                    installmentNumber = item.installmentNumber,
                    dueDate = LocalDate.parse(item.dueDate),
                    principalMinor = item.amountMinor,
                    status = item.status,
                    revision = item.revision,
                )
            }
        }

    override suspend fun openDebt(
        userId: String,
        command: OpenDebtCommand,
        hasPremiumAccess: Boolean,
    ): DebtCommandResult {
        val result = localDataSource.openDebt(userId, command, hasPremiumAccess)
        if (result is DebtCommandResult.Applied || result is DebtCommandResult.Duplicate || result is DebtCommandResult.Retryable) {
            syncScheduler.schedule(userId)
        }
        return result
    }

    override suspend fun editDebtDetails(
        userId: String,
        debtId: String,
        patch: DebtDescriptionPatch,
        identity: DebtCommandIdentity,
    ): DebtDetailsEditResult = localDataSource.editDebtDetails(userId, debtId, patch, identity).also { result ->
        if (result is DebtDetailsEditResult.Applied) syncScheduler.schedule(userId)
    }

    override suspend fun deleteDebtIfUnreferenced(
        userId: String,
        debtId: String,
        identity: DebtCommandIdentity,
    ): DebtDeleteResult = localDataSource.deleteDebtIfUnreferenced(userId, debtId, identity).also { result ->
        if (result is DebtDeleteResult.Deleted) {
            syncScheduler.schedule(userId)
            reminderReconciler.reconcile(userId, debtId)
        }
    }

    override suspend fun settleDebt(userId: String, command: SettleDebtCommand): DebtCommandResult =
        localDataSource.settleDebt(userId, command).also { result ->
            if (result is DebtCommandResult.Applied || result is DebtCommandResult.Duplicate || result is DebtCommandResult.Retryable) {
                syncScheduler.schedule(userId)
                reminderReconciler.reconcile(userId, command.debtId)
            }
        }

    override suspend fun setDebtSchedule(userId: String, command: SetDebtScheduleCommand): DebtCommandResult =
        localDataSource.setDebtSchedule(userId, command).also { result ->
            if (result is DebtCommandResult.Applied || result is DebtCommandResult.Duplicate || result is DebtCommandResult.Retryable) {
                syncScheduler.schedule(userId)
                reminderReconciler.reconcile(userId, command.debtId)
            }
        }

    override suspend fun closeDebt(userId: String, command: DebtClosureCommand): DebtCommandResult =
        localDataSource.closeDebt(userId, command).also { result ->
            if (result is DebtCommandResult.Applied || result is DebtCommandResult.Duplicate || result is DebtCommandResult.Retryable) {
                syncScheduler.schedule(userId)
                reminderReconciler.reconcile(userId, command.debtId)
            }
        }

    private fun DebtSummaryRow.toDomain() = DebtSummary(
        debtId = debtId,
        userId = userId,
        obligationType = DebtObligationType.valueOf(obligationType),
        counterpartyName = counterpartyName,
        principalMinor = principalMinor,
        remainingPrincipalMinor = remainingPrincipalMinor,
        currencyCode = currencyCode,
        openedOn = LocalDate.parse(openedOn),
        dueDate = dueDate?.let(LocalDate::parse),
        reminderLeadDays = reminderLeadDays,
        notes = notes,
        status = DebtLifecycleStatus.valueOf(status),
        syncState = syncState,
        revision = revision,
        openingMode = DebtOpeningMode.valueOf(openingMode),
    )
}
