package com.kipu.app.feature.debts.data.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kipu.app.feature.debts.data.local.DebtDao
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

data class DebtReminderRequest(
    val userId: String,
    val debtId: String,
    val installmentId: String,
    val dueDate: LocalDate,
    val leadDays: Int,
    val counterpartyName: String,
    val installmentNumber: Int,
    val remainingPrincipalMinor: Long,
    val currencyCode: String,
    val scheduledAt: Instant = Instant.now(),
)

fun interface DebtNotificationPermissionChecker {
    fun canPostDebtReminder(): Boolean
}

interface DebtReminderScheduler {
    fun schedule(request: DebtReminderRequest)
    fun cancel(userId: String, debtId: String, installmentId: String)
    fun cancelDebt(userId: String, debtId: String)
    fun cancelUser(userId: String)

    companion object {
        fun uniqueWorkName(request: DebtReminderRequest): String =
            "debt-reminder:${request.userId}:${request.debtId}:${request.installmentId}"

        fun notificationTag(request: DebtReminderRequest): String =
            "debt-reminder:${request.userId}:${request.debtId}:${request.installmentId}"

        fun dueDateTag(request: DebtReminderRequest): String =
            "debt-reminder-due:${request.userId}:${request.debtId}:${request.installmentId}:${request.dueDate}"

        fun debtTag(userId: String, debtId: String): String = "debt-reminder-debt:$userId:$debtId"
        fun userTag(userId: String): String = "debt-reminder-user:$userId"
    }
}

object NoOpDebtReminderScheduler : DebtReminderScheduler {
    override fun schedule(request: DebtReminderRequest) = Unit
    override fun cancel(userId: String, debtId: String, installmentId: String) = Unit
    override fun cancelDebt(userId: String, debtId: String) = Unit
    override fun cancelUser(userId: String) = Unit
}

@Singleton
class AndroidDebtNotificationPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : DebtNotificationPermissionChecker {
    override fun canPostDebtReminder(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}

@Singleton
class WorkManagerDebtReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val permissionChecker: DebtNotificationPermissionChecker,
) : DebtReminderScheduler {
    override fun schedule(request: DebtReminderRequest) {
        if (request.userId.isBlank() || request.debtId.isBlank() || request.installmentId.isBlank() ||
            request.leadDays !in 0..365 || request.remainingPrincipalMinor <= 0L
        ) {
            cancel(request.userId, request.debtId, request.installmentId)
            return
        }
        if (!permissionChecker.canPostDebtReminder()) {
            cancel(request.userId, request.debtId, request.installmentId)
            return
        }
        val notificationAt = request.dueDate.minusDays(request.leadDays.toLong())
            .atStartOfDay(LIMA_ZONE).toInstant().toEpochMilli()
        val delayMillis = (notificationAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val work = OneTimeWorkRequestBuilder<DebtReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    DebtReminderWorker.KEY_USER_ID to request.userId,
                    DebtReminderWorker.KEY_DEBT_ID to request.debtId,
                    DebtReminderWorker.KEY_INSTALLMENT_ID to request.installmentId,
                    DebtReminderWorker.KEY_DUE_DATE to request.dueDate.toString(),
                    DebtReminderWorker.KEY_LEAD_DAYS to request.leadDays,
                    DebtReminderWorker.KEY_INSTALLMENT_NUMBER to request.installmentNumber,
                    DebtReminderWorker.KEY_COUNTERPARTY_NAME to request.counterpartyName,
                    DebtReminderWorker.KEY_REMAINING_MINOR to request.remainingPrincipalMinor,
                    DebtReminderWorker.KEY_CURRENCY_CODE to request.currencyCode,
                ),
            )
            .addTag(DebtReminderScheduler.userTag(request.userId))
            .addTag(DebtReminderScheduler.debtTag(request.userId, request.debtId))
            .addTag(DebtReminderScheduler.dueDateTag(request))
            .build()
        workManager.enqueueUniqueWork(
            DebtReminderScheduler.uniqueWorkName(request),
            ExistingWorkPolicy.REPLACE,
            work,
        )
    }

    override fun cancel(userId: String, debtId: String, installmentId: String) {
        workManager.cancelUniqueWork("debt-reminder:$userId:$debtId:$installmentId")
    }

    override fun cancelDebt(userId: String, debtId: String) {
        workManager.cancelAllWorkByTag(DebtReminderScheduler.debtTag(userId, debtId))
    }

    override fun cancelUser(userId: String) {
        workManager.cancelAllWorkByTag(DebtReminderScheduler.userTag(userId))
    }

    private companion object {
        val LIMA_ZONE: ZoneId = ZoneId.of("America/Lima")
    }
}

interface DebtReminderReconciler {
    suspend fun reconcile(userId: String, debtId: String? = null)
}

object NoOpDebtReminderReconciler : DebtReminderReconciler {
    override suspend fun reconcile(userId: String, debtId: String?) = Unit
}

@Singleton
class LocalDebtReminderReconciler @Inject constructor(
    private val debtDao: DebtDao,
    private val scheduler: DebtReminderScheduler,
) : DebtReminderReconciler {
    override suspend fun reconcile(userId: String, debtId: String?) {
        if (userId.isBlank()) return
        val summaries = debtDao.observeSummaries(userId).first()
        if (debtId == null) {
            scheduler.cancelUser(userId)
        } else {
            scheduler.cancelDebt(userId, debtId)
        }
        summaries.asSequence()
            .filter { debtId == null || it.debtId == debtId }
            .filter { it.status == DebtLifecycleStatus.ACTIVE.name && it.reminderLeadDays != null }
            .forEach { debt ->
                debtDao.getInstallments(userId, debt.debtId)
                    .asSequence()
                    .filter { it.status == "PENDING" || it.status == "PARTIAL" }
                    .forEach { installment ->
                        val remaining = debtDao.getInstallmentRemaining(userId, debt.debtId, installment.id)
                        if (remaining > 0L) {
                            scheduler.schedule(
                                DebtReminderRequest(
                                    userId = userId,
                                    debtId = debt.debtId,
                                    installmentId = installment.id,
                                    dueDate = LocalDate.parse(installment.dueDate),
                                    leadDays = requireNotNull(debt.reminderLeadDays),
                                    counterpartyName = debt.counterpartyName,
                                    installmentNumber = installment.installmentNumber,
                                    remainingPrincipalMinor = remaining,
                                    currencyCode = debt.currencyCode,
                                ),
                            )
                        }
                    }
            }
    }
}
