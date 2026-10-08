package com.kipu.app.feature.debts.di

import com.kipu.app.feature.debts.data.OfflineFirstDebtRepository
import com.kipu.app.feature.debts.data.sync.DebtSyncScheduler
import com.kipu.app.feature.debts.data.sync.AndroidDebtNotificationPermissionChecker
import com.kipu.app.feature.debts.data.sync.DebtNotificationPermissionChecker
import com.kipu.app.feature.debts.data.sync.DebtReminderReconciler
import com.kipu.app.feature.debts.data.sync.DebtReminderScheduler
import com.kipu.app.feature.debts.data.sync.LocalDebtReminderReconciler
import com.kipu.app.feature.debts.data.sync.WorkManagerDebtSyncScheduler
import com.kipu.app.feature.debts.data.sync.WorkManagerDebtReminderScheduler
import com.kipu.app.feature.debts.domain.DebtRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DebtsModule {
    @Binds
    abstract fun bindDebtRepository(implementation: OfflineFirstDebtRepository): DebtRepository

    @Binds
    abstract fun bindDebtSyncScheduler(implementation: WorkManagerDebtSyncScheduler): DebtSyncScheduler

    @Binds
    abstract fun bindDebtReminderScheduler(implementation: WorkManagerDebtReminderScheduler): DebtReminderScheduler

    @Binds
    abstract fun bindDebtReminderPermissionChecker(implementation: AndroidDebtNotificationPermissionChecker): DebtNotificationPermissionChecker

    @Binds
    abstract fun bindDebtReminderReconciler(implementation: LocalDebtReminderReconciler): DebtReminderReconciler
}
