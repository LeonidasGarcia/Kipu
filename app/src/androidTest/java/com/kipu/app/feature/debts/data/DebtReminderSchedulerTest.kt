package com.kipu.app.feature.debts.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.kipu.app.feature.debts.data.sync.DebtNotificationPermissionChecker
import com.kipu.app.feature.debts.data.sync.DebtReminderRequest
import com.kipu.app.feature.debts.data.sync.DebtReminderScheduler
import com.kipu.app.feature.debts.data.sync.WorkManagerDebtReminderScheduler
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebtReminderSchedulerTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun initializeWorkManager() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun replanReplacesUniqueWorkForTheSameInstallmentAndDueDateChange() {
        val scheduler = scheduler(allowed = true)
        val first = request("2026-12-10")
        val replacement = request("2027-01-10")

        scheduler.schedule(first)
        scheduler.schedule(replacement)

        val work = workManager.getWorkInfosForUniqueWork(DebtReminderScheduler.uniqueWorkName(first)).get()
        assertEquals(1, work.size)
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
        assertTrue(work.single().tags.contains(DebtReminderScheduler.dueDateTag(replacement)))
        assertTrue(!work.single().tags.contains(DebtReminderScheduler.dueDateTag(first)))
        assertEquals(DebtReminderScheduler.notificationTag(first), DebtReminderScheduler.notificationTag(replacement))
    }

    @Test
    fun retryWithTheSameReminderIdentityKeepsOnlyOnePendingWork() {
        val scheduler = scheduler(allowed = true)
        val reminder = request("2026-12-10")

        scheduler.schedule(reminder)
        scheduler.schedule(reminder)

        val work = workManager.getWorkInfosForUniqueWork(DebtReminderScheduler.uniqueWorkName(reminder)).get()
        assertEquals(1, work.size)
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
        assertEquals(true, work.single().tags.contains(DebtReminderScheduler.dueDateTag(reminder)))
    }

    @Test
    fun disabledNotificationPermissionCancelsPendingReminderWithoutChangingTheDebt() {
        val allowed = scheduler(allowed = true)
        val denied = scheduler(allowed = false)
        val reminder = request("2026-12-10")

        allowed.schedule(reminder)
        denied.schedule(reminder)

        val work = workManager.getWorkInfosForUniqueWork(DebtReminderScheduler.uniqueWorkName(reminder)).get()
        assertTrue(work.isEmpty() || work.all { it.state == WorkInfo.State.CANCELLED })
        assertEquals("2026-12-10", reminder.dueDate.toString())
    }

    private fun scheduler(allowed: Boolean) = WorkManagerDebtReminderScheduler(
        context = context,
        workManager = workManager,
        permissionChecker = object : DebtNotificationPermissionChecker {
            override fun canPostDebtReminder(): Boolean = allowed
        },
    )

    private fun request(dueDate: String) = DebtReminderRequest(
        userId = OWNER_ID,
        debtId = DEBT_ID,
        installmentId = INSTALLMENT_ID,
        dueDate = LocalDate.parse(dueDate),
        leadDays = 3,
        counterpartyName = "Proveedor",
        installmentNumber = 1,
        remainingPrincipalMinor = 3_334L,
        currencyCode = "PEN",
        scheduledAt = Instant.parse("2026-10-08T10:00:00Z"),
    )

    private companion object {
        const val OWNER_ID = "88000000-0000-4000-8000-000000000001"
        const val DEBT_ID = "88000000-0000-4000-8000-000000000002"
        const val INSTALLMENT_ID = "88000000-0000-4000-8000-000000000003"
    }
}
