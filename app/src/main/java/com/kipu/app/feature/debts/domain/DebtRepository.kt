package com.kipu.app.feature.debts.domain

import com.kipu.app.feature.debts.domain.model.DebtCommandResult
import com.kipu.app.feature.debts.domain.model.DebtCommandIdentity
import com.kipu.app.feature.debts.domain.model.DebtDeleteResult
import com.kipu.app.feature.debts.domain.model.DebtDescriptionPatch
import com.kipu.app.feature.debts.domain.model.DebtDetailsEditResult
import com.kipu.app.feature.debts.domain.model.DebtSummary
import com.kipu.app.feature.debts.domain.model.DebtClosureCommand
import com.kipu.app.feature.debts.domain.model.DebtScheduleItem
import com.kipu.app.feature.debts.domain.model.SetDebtScheduleCommand
import com.kipu.app.feature.debts.domain.model.OpenDebtCommand
import com.kipu.app.feature.debts.domain.model.SettleDebtCommand
import kotlinx.coroutines.flow.Flow

interface DebtRepository {
    fun observeDebts(userId: String): Flow<List<DebtSummary>>

    fun observeDebt(userId: String, debtId: String): Flow<DebtSummary?>

    fun observeInstallments(userId: String, debtId: String): Flow<List<DebtScheduleItem>>

    suspend fun openDebt(
        userId: String,
        command: OpenDebtCommand,
        hasPremiumAccess: Boolean,
    ): DebtCommandResult

    suspend fun editDebtDetails(
        userId: String,
        debtId: String,
        patch: DebtDescriptionPatch,
        identity: DebtCommandIdentity,
    ): DebtDetailsEditResult

    suspend fun deleteDebtIfUnreferenced(
        userId: String,
        debtId: String,
        identity: DebtCommandIdentity,
    ): DebtDeleteResult

    suspend fun settleDebt(userId: String, command: SettleDebtCommand): DebtCommandResult

    suspend fun setDebtSchedule(userId: String, command: SetDebtScheduleCommand): DebtCommandResult

    suspend fun closeDebt(userId: String, command: DebtClosureCommand): DebtCommandResult
}
