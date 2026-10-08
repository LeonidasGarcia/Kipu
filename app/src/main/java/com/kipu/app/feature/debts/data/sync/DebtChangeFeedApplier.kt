package com.kipu.app.feature.debts.data.sync

import androidx.room.withTransaction
import com.kipu.app.core.database.KipuDatabase
import com.kipu.app.feature.accounts.data.remote.PullChangesResponseDto
import com.kipu.app.feature.accounts.data.remote.SyncChangeItemDto
import com.kipu.app.feature.debts.data.local.DebtDao
import com.kipu.app.feature.debts.data.local.DebtEntity
import com.kipu.app.feature.debts.data.local.DebtEventEntity
import com.kipu.app.feature.debts.data.local.DebtInstallmentEntity
import com.kipu.app.feature.debts.data.local.DebtSyncCheckpointEntity
import com.kipu.app.feature.debts.domain.model.DebtEventType
import com.kipu.app.feature.debts.domain.model.DebtLifecycleStatus
import com.kipu.app.feature.debts.domain.model.DebtObligationType
import com.kipu.app.feature.movements.data.local.MovementDao
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

sealed interface DebtChangeApplyResult {
    data class Applied(val nextSequence: Long, val appliedChanges: Int) : DebtChangeApplyResult
    data class Conflict(val debtId: String, val localRevision: Long, val remoteRevision: Long) : DebtChangeApplyResult
    data class Rejected(val code: String) : DebtChangeApplyResult
}

@Singleton
class DebtChangeFeedApplier @Inject constructor(
    private val database: KipuDatabase,
    private val debtDao: DebtDao,
    private val movementDao: MovementDao,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun applyPage(userId: String, page: PullChangesResponseDto): DebtChangeApplyResult {
        if (userId.isBlank() || page.changes.size > MAX_PAGE_SIZE) {
            return DebtChangeApplyResult.Rejected("INVALID_SYNC_PAGE")
        }
        val checkpoint = debtDao.getSyncCheckpoint(userId)?.sequence ?: 0L
        val expectedSequence = checkpoint + 1L
        var expected = expectedSequence
        for (change in page.changes) {
            if (change.sequence != expected) return DebtChangeApplyResult.Rejected("SYNC_SEQUENCE_GAP")
            expected += 1L
        }
        val nextSequence = page.changes.lastOrNull()?.sequence ?: checkpoint
        if (page.nextSequence != nextSequence) return DebtChangeApplyResult.Rejected("SYNC_SEQUENCE_GAP")

        return try {
            database.withTransaction {
                val currentCheckpoint = debtDao.getSyncCheckpoint(userId)?.sequence ?: 0L
                if (currentCheckpoint != checkpoint) throw InvalidDebtChangePage("SYNC_CHECKPOINT_CHANGED")

                var applied = 0
                page.changes.forEach { change ->
                    when (change.entityType.uppercase()) {
                        "DEBT" -> if (change.operation.equals("DELETE", ignoreCase = true)) {
                            applyDebtDeleteChange(userId, change)
                        } else {
                            applyDebtChange(userId, change)
                        }
                        "DEBT_EVENT" -> applyEventChange(userId, change)
                        "DEBT_INSTALLMENT" -> applyInstallmentChange(userId, change)
                    }
                    applied += 1
                }
                if (page.changes.isNotEmpty()) {
                    debtDao.upsertSyncCheckpoint(
                        DebtSyncCheckpointEntity(userId, page.nextSequence, clock.millis()),
                    )
                }
                DebtChangeApplyResult.Applied(page.nextSequence, applied)
            }
        } catch (stale: StaleDebtRevision) {
            database.withTransaction { debtDao.updateSyncState(userId, stale.debtId, "CONFLICT") }
            DebtChangeApplyResult.Conflict(stale.debtId, stale.localRevision, stale.remoteRevision)
        } catch (invalid: InvalidDebtChangePage) {
            DebtChangeApplyResult.Rejected(invalid.code)
        } catch (_: Exception) {
            DebtChangeApplyResult.Rejected("INVALID_REMOTE_DEBT_CHANGE")
        }
    }

    private suspend fun applyDebtChange(userId: String, change: SyncChangeItemDto) {
        val payload = change.payload ?: throw InvalidDebtChangePage("DEBT_PAYLOAD_REQUIRED")
        val remote = json.decodeFromJsonElement<RemoteDebtDto>(payload)
        if (remote.userId != userId || remote.id != change.entityId || remote.revision != change.revision) {
            throw InvalidDebtChangePage("DEBT_OWNER_OR_REVISION_MISMATCH")
        }
        val obligation = runCatching { DebtObligationType.valueOf(remote.obligationType) }.getOrNull()
            ?: throw InvalidDebtChangePage("INVALID_DEBT_TYPE")
        val status = runCatching { DebtLifecycleStatus.valueOf(remote.status) }.getOrNull()
            ?: throw InvalidDebtChangePage("INVALID_DEBT_STATUS")
        if (remote.totalMinor <= 0L || remote.currencyCode !in SUPPORTED_CURRENCIES || remote.revision <= 0L) {
            throw InvalidDebtChangePage("INVALID_DEBT_VALUE")
        }
        val openedOn = runCatching { LocalDate.parse(remote.openedOn) }.getOrNull()
            ?: throw InvalidDebtChangePage("INVALID_DEBT_OPENED_ON")
        val dueDate = remote.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (remote.dueDate != null && dueDate == null) throw InvalidDebtChangePage("INVALID_DEBT_DUE_DATE")
        if (remote.reminderLeadDays != null && remote.reminderLeadDays !in 0..365) {
            throw InvalidDebtChangePage("INVALID_REMINDER_LEAD_DAYS")
        }

        val existing = debtDao.getDebtIncludingDeleted(userId, remote.id)
        if (existing != null && remote.revision < existing.revision) {
            throw StaleDebtRevision(remote.id, existing.revision, remote.revision)
        }
        val entity = DebtEntity(
            id = remote.id,
            userId = userId,
            obligationType = obligation.name,
            counterpartyName = remote.counterpartyName,
            totalMinor = remote.totalMinor,
            currencyCode = remote.currencyCode,
            openedOn = openedOn.toString(),
            openingMode = remote.openingMode,
            dueDate = dueDate?.toString(),
            reminderLeadDays = remote.reminderLeadDays,
            notes = remote.notes,
            status = status.name,
            syncState = "SYNCED",
            revision = remote.revision,
            deletedAt = remote.deletedAt?.let(::parseInstantMillis),
            createdAt = remote.createdAt?.let(::parseInstantMillis) ?: existing?.createdAt ?: clock.millis(),
            updatedAt = remote.updatedAt?.let(::parseInstantMillis) ?: clock.millis(),
        )
        if (existing != null && remote.revision == existing.revision) {
            if (!sameDebtContent(existing, entity)) {
                if (existing.syncState != "SYNCED") {
                    throw StaleDebtRevision(remote.id, existing.revision, remote.revision)
                }
                throw InvalidDebtChangePage("DEBT_REVISION_REUSED")
            }
            if (existing.syncState != "SYNCED") debtDao.updateDebt(entity.copy(createdAt = existing.createdAt))
            return
        }
        if (existing != null && existing.syncState != "SYNCED" && !sameDebtContent(existing, entity)) {
            throw StaleDebtRevision(remote.id, existing.revision, remote.revision)
        }
        debtDao.upsertDebt(entity.copy(createdAt = existing?.createdAt ?: entity.createdAt))
    }

    private suspend fun applyDebtDeleteChange(userId: String, change: SyncChangeItemDto) {
        val payload = change.payload ?: throw InvalidDebtChangePage("DEBT_DELETE_PAYLOAD_REQUIRED")
        val remote = json.decodeFromJsonElement<RemoteDebtDeletionDto>(payload)
        if (remote.userId != userId || remote.id != change.entityId || remote.revision != change.revision) {
            throw InvalidDebtChangePage("DEBT_DELETE_OWNER_OR_REVISION_MISMATCH")
        }
        val existing = debtDao.getDebtIncludingDeleted(userId, remote.id) ?: return
        if (remote.revision < existing.revision) throw StaleDebtRevision(remote.id, existing.revision, remote.revision)
        val events = debtDao.getEvents(userId, remote.id)
        if (events.any { it.eventType != DebtEventType.DISBURSEMENT.name || it.transactionId != null }) {
            throw InvalidDebtChangePage("REMOTE_DELETE_WOULD_REMOVE_FINANCIAL_HISTORY")
        }
        debtDao.deleteHistoricalOpeningEvents(userId, remote.id)
        debtDao.deleteUnreferencedInstallments(userId, remote.id)
        if (debtDao.deleteDebtIfUnreferenced(userId, remote.id) != 1) {
            throw InvalidDebtChangePage("REMOTE_DELETE_DEBT_STILL_REFERENCED")
        }
    }

    private suspend fun applyEventChange(userId: String, change: SyncChangeItemDto) {
        if (change.operation.equals("DELETE", ignoreCase = true)) {
            throw InvalidDebtChangePage("DEBT_HISTORY_CANNOT_BE_DELETED")
        }
        val payload = change.payload ?: throw InvalidDebtChangePage("DEBT_EVENT_PAYLOAD_REQUIRED")
        val remote = json.decodeFromJsonElement<RemoteDebtEventDto>(payload)
        if (remote.userId != userId || remote.id != change.entityId) {
            throw InvalidDebtChangePage("DEBT_EVENT_OWNER_MISMATCH")
        }
        val eventType = runCatching { DebtEventType.valueOf(remote.eventType) }.getOrNull()
            ?: throw InvalidDebtChangePage("INVALID_DEBT_EVENT_TYPE")
        if (remote.amountMinor < 0L || debtDao.getDebtIncludingDeleted(userId, remote.debtId) == null) {
            throw InvalidDebtChangePage("INVALID_DEBT_EVENT_REFERENCE")
        }
        if (remote.transactionId != null && movementDao.getTransactionById(userId, remote.transactionId) == null) {
            throw InvalidDebtChangePage("MISSING_LINKED_TRANSACTION")
        }
        if (remote.interestTransactionId != null && movementDao.getTransactionById(userId, remote.interestTransactionId) == null) {
            throw InvalidDebtChangePage("MISSING_LINKED_INTEREST_TRANSACTION")
        }
        if (remote.installmentId != null && debtDao.getInstallment(userId, remote.debtId, remote.installmentId) == null) {
            throw InvalidDebtChangePage("MISSING_LINKED_INSTALLMENT")
        }
        val event = DebtEventEntity(
            id = remote.id,
            userId = userId,
            debtId = remote.debtId,
            transactionId = remote.transactionId,
            interestTransactionId = remote.interestTransactionId,
            installmentId = remote.installmentId,
            eventType = eventType.name,
            amountMinor = remote.amountMinor,
            principalDeltaMinor = remote.principalDeltaMinor,
            occurredAt = parseInstantMillis(remote.occurredAt),
            createdAt = remote.createdAt?.let(::parseInstantMillis) ?: clock.millis(),
        )
        val existing = debtDao.getEvent(userId, remote.id)
        if (existing != null && existing != event) throw InvalidDebtChangePage("DEBT_EVENT_ID_REUSED")
        if (existing == null) debtDao.insertEvent(event)
    }

    private suspend fun applyInstallmentChange(userId: String, change: SyncChangeItemDto) {
        if (change.operation.equals("DELETE", ignoreCase = true)) {
            throw InvalidDebtChangePage("DEBT_INSTALLMENT_HISTORY_CANNOT_BE_DELETED")
        }
        val payload = change.payload ?: throw InvalidDebtChangePage("DEBT_INSTALLMENT_PAYLOAD_REQUIRED")
        val remote = json.decodeFromJsonElement<RemoteDebtInstallmentDto>(payload)
        if (remote.userId != userId || remote.id != change.entityId ||
            debtDao.getDebtIncludingDeleted(userId, remote.debtId) == null ||
            remote.installmentNumber <= 0 || remote.amountMinor < 0L
        ) throw InvalidDebtChangePage("INVALID_DEBT_INSTALLMENT")
        val dueDate = runCatching { LocalDate.parse(remote.dueDate) }.getOrNull()
            ?: throw InvalidDebtChangePage("INVALID_DEBT_INSTALLMENT_DATE")
        val existing = debtDao.getInstallment(userId, remote.debtId, remote.id)
        if (existing != null && change.revision < existing.revision) {
            throw StaleDebtRevision(remote.debtId, existing.revision, change.revision)
        }
        val installment = DebtInstallmentEntity(
            id = remote.id,
            userId = userId,
            debtId = remote.debtId,
            installmentNumber = remote.installmentNumber,
            dueDate = dueDate.toString(),
            amountMinor = remote.amountMinor,
            status = remote.status,
            revision = change.revision,
            deletedAt = remote.deletedAt?.let(::parseInstantMillis),
            createdAt = remote.createdAt?.let(::parseInstantMillis) ?: existing?.createdAt ?: clock.millis(),
            updatedAt = remote.updatedAt?.let(::parseInstantMillis) ?: clock.millis(),
        )
        if (existing != null && change.revision == existing.revision && existing != installment) {
            throw InvalidDebtChangePage("DEBT_INSTALLMENT_REVISION_REUSED")
        }
        debtDao.upsertInstallment(installment)
    }

    private fun sameDebtContent(left: DebtEntity, right: DebtEntity): Boolean =
        left.obligationType == right.obligationType &&
            left.counterpartyName == right.counterpartyName &&
            left.totalMinor == right.totalMinor &&
            left.currencyCode == right.currencyCode &&
            left.openedOn == right.openedOn &&
            left.openingMode == right.openingMode &&
            left.dueDate == right.dueDate &&
            left.reminderLeadDays == right.reminderLeadDays &&
            left.notes == right.notes &&
            left.status == right.status &&
            left.deletedAt == right.deletedAt

    private fun parseInstantMillis(value: String): Long =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrElse {
            runCatching { LocalDate.parse(value).atStartOfDay(java.time.ZoneId.of("America/Lima")).toInstant().toEpochMilli() }
                .getOrElse { throw InvalidDebtChangePage("INVALID_REMOTE_TIMESTAMP") }
        }

    private class InvalidDebtChangePage(val code: String) : RuntimeException()
    private class StaleDebtRevision(val debtId: String, val localRevision: Long, val remoteRevision: Long) : RuntimeException()

    private companion object {
        const val MAX_PAGE_SIZE = 100
        val SUPPORTED_CURRENCIES = setOf("PEN", "USD")
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class RemoteDebtDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("obligation_type") val obligationType: String,
    @SerialName("counterparty_name") val counterpartyName: String,
    @SerialName("total_minor") val totalMinor: Long,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("opened_on") val openedOn: String,
    @SerialName("opening_mode") val openingMode: String = "HISTORICAL",
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("reminder_lead_days") val reminderLeadDays: Int? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("status") val status: String,
    @SerialName("revision") val revision: Long,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
private data class RemoteDebtDeletionDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("revision") val revision: Long,
)

@Serializable
private data class RemoteDebtEventDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("debt_id") val debtId: String,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("interest_transaction_id") val interestTransactionId: String? = null,
    @SerialName("installment_id") val installmentId: String? = null,
    @SerialName("event_type") val eventType: String,
    @SerialName("amount_minor") val amountMinor: Long,
    @SerialName("principal_delta_minor") val principalDeltaMinor: Long? = null,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
private data class RemoteDebtInstallmentDto(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("debt_id") val debtId: String,
    @SerialName("installment_number") val installmentNumber: Int,
    @SerialName("due_date") val dueDate: String,
    @SerialName("amount_minor") val amountMinor: Long,
    @SerialName("status") val status: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)
