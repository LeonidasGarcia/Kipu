package com.kipu.app.feature.debts.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kipu.app.MainActivity
import com.kipu.app.R
import com.kipu.app.core.session.SessionCoordinator
import com.kipu.app.feature.debts.data.local.DebtDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import java.text.NumberFormat

object DebtReminderDestination {
    fun uri(debtId: String): String = "kipu://debts/detail/$debtId"
}

@HiltWorker
class DebtReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val debtDao: DebtDao,
    private val sessionCoordinator: SessionCoordinator,
    private val permissionChecker: DebtNotificationPermissionChecker,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.failure()
        val debtId = inputData.getString(KEY_DEBT_ID) ?: return Result.failure()
        val installmentId = inputData.getString(KEY_INSTALLMENT_ID) ?: return Result.failure()
        val activeOwner = sessionCoordinator.currentOwner?.verifiedUserId
        if (activeOwner != userId || !permissionChecker.canPostDebtReminder()) return Result.success()

        val debt = debtDao.getDebt(userId, debtId) ?: return Result.success()
        val installment = debtDao.getInstallment(userId, debtId, installmentId) ?: return Result.success()
        val dueDate = inputData.getString(KEY_DUE_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return Result.failure()
        val leadDays = inputData.getInt(KEY_LEAD_DAYS, -1)
        if (debt.status != "ACTIVE" || debt.reminderLeadDays != leadDays || installment.status !in setOf("PENDING", "PARTIAL") ||
            installment.dueDate != dueDate.toString() || permissionChecker.canPostDebtReminder().not()
        ) return Result.success()
        val remaining = debtDao.getInstallmentRemaining(userId, debtId, installmentId)
        if (remaining <= 0L) return Result.success()

        createChannel()
        val number = inputData.getInt(KEY_INSTALLMENT_NUMBER, installment.installmentNumber)
        val counterparty = inputData.getString(KEY_COUNTERPARTY_NAME).orEmpty().ifBlank { "tu deuda" }
        val currencyCode = inputData.getString(KEY_CURRENCY_CODE) ?: debt.currencyCode
        val amount = formatAmount(remaining, currencyCode)
        val contentIntent = Intent(applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(DebtReminderDestination.uri(debtId))
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            "$userId:$debtId:$installmentId".hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle("Recordatorio de cuota")
            .setContentText("Cuota $number de $counterparty: $amount. Este aviso no registra un pago.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "La cuota planificada $number de $counterparty tiene $amount pendiente. Este aviso no registra un pago.",
            ))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(
            "debt-reminder:$userId:$debtId:$installmentId",
            0,
            notification,
        )
        return Result.success()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Recordatorios de deuda", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisos locales de cuotas planificadas; no registran pagos."
            },
        )
    }

    private fun formatAmount(amountMinor: Long, currencyCode: String): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("es").setRegion("PE").build())
        runCatching { Currency.getInstance(currencyCode) }.getOrNull()?.let { formatter.currency = it }
        return formatter.format(amountMinor / 100.0)
    }

    companion object {
        const val KEY_USER_ID = "debt_reminder_user_id"
        const val KEY_DEBT_ID = "debt_reminder_debt_id"
        const val KEY_INSTALLMENT_ID = "debt_reminder_installment_id"
        const val KEY_DUE_DATE = "debt_reminder_due_date"
        const val KEY_LEAD_DAYS = "debt_reminder_lead_days"
        const val KEY_INSTALLMENT_NUMBER = "debt_reminder_installment_number"
        const val KEY_COUNTERPARTY_NAME = "debt_reminder_counterparty_name"
        const val KEY_REMAINING_MINOR = "debt_reminder_remaining_minor"
        const val KEY_CURRENCY_CODE = "debt_reminder_currency_code"
        const val CHANNEL_ID = "debt-reminders"
    }
}
