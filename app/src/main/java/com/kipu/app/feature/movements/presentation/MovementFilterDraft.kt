package com.kipu.app.feature.movements.presentation

import com.kipu.app.core.finance.domain.MoneyInputParser
import com.kipu.app.feature.movements.domain.model.MovementFinancialState
import com.kipu.app.feature.movements.domain.model.MovementSyncStatus
import com.kipu.app.feature.movements.domain.model.MovementType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

data class MovementFilterDraft(
    val accountIds: Set<String> = emptySet(),
    val categoryIds: Set<String> = emptySet(),
    val cardIds: Set<String> = emptySet(),
    val merchantIds: Set<String> = emptySet(),
    val financialStates: Set<MovementFinancialState> = emptySet(),
    val syncStatuses: Set<MovementSyncStatus> = emptySet(),
    val minAmount: String = "",
    val maxAmount: String = "",
    val currency: String? = null,
    val fromDate: String = "",
    val toDate: String = "",
    val movementType: MovementType? = null,
    val comparePreviousMonth: Boolean = false,
) {
    fun validate(zone: ZoneId = ZoneId.systemDefault()): FilterDraftValidation {
        val errors = mutableMapOf<String, FilterDraftError>()
        fun amount(input: String, field: String): Long? = if (input.isBlank()) null else {
            MoneyInputParser.parseMinorUnits(input).also { if (it == null) errors[field] = FilterDraftError.AMOUNT }
        }
        fun date(input: String, field: String): LocalDate? = if (input.isBlank()) null else {
            runCatching { LocalDate.parse(input) }.getOrNull().also { if (it == null) errors[field] = FilterDraftError.DATE }
        }
        val min = amount(minAmount, "min")
        val max = amount(maxAmount, "max")
        if ((min != null || max != null) && currency !in setOf("PEN", "USD")) errors["currency"] = FilterDraftError.CURRENCY
        if (min != null && max != null && min > max) errors["max"] = FilterDraftError.RANGE
        val from = date(fromDate, "from")
        val to = date(toDate, "to")
        if (from != null && to != null && from > to) errors["to"] = FilterDraftError.RANGE
        if (errors.isNotEmpty()) return FilterDraftValidation(null, errors)
        return FilterDraftValidation(AdvancedFiltersState(
            accountIds = accountIds, categoryIds = categoryIds, cardIds = cardIds, merchantIds = merchantIds,
            financialStates = financialStates, syncStatuses = syncStatuses, minAmountMinor = min, maxAmountMinor = max,
            currency = currency.takeIf { min != null || max != null },
            fromDate = from?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
            toDate = to?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
        ), emptyMap())
    }

    companion object {
        fun fromApplied(
            filters: AdvancedFiltersState,
            zone: ZoneId = ZoneId.systemDefault(),
            movementType: MovementType? = null,
            comparePreviousMonth: Boolean = false,
        ) = MovementFilterDraft(
            accountIds = filters.accountIds, categoryIds = filters.categoryIds,
            cardIds = filters.cardIds, merchantIds = filters.merchantIds,
            financialStates = filters.financialStates, syncStatuses = filters.syncStatuses,
            minAmount = filters.minAmountMinor?.let { BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty(),
            maxAmount = filters.maxAmountMinor?.let { BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty(),
            currency = filters.currency,
            fromDate = filters.fromDate?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() }.orEmpty(),
            toDate = filters.toDate?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate().minusDays(1).toString() }.orEmpty(),
            movementType = movementType,
            comparePreviousMonth = comparePreviousMonth,
        )
    }
}

enum class FilterDraftError { AMOUNT, DATE, CURRENCY, RANGE }
data class FilterDraftValidation(val filters: AdvancedFiltersState?, val errors: Map<String, FilterDraftError>)

data class MovementReferenceOption(val id: String, val label: String)

fun MovementFinancialState.uiLabel(): String = when (this) {
    MovementFinancialState.CONFIRMED -> "Confirmado"
    MovementFinancialState.REVISED -> "Corregido"
    MovementFinancialState.VOIDED -> "Anulado"
    MovementFinancialState.LEGACY_FAILED -> "Fallo histórico"
}

fun MovementSyncStatus.uiLabel(): String = when (this) {
    MovementSyncStatus.SYNCED -> "Sincronizado"
    MovementSyncStatus.MIGRATED_LOCAL -> "Histórico local"
    MovementSyncStatus.PENDING -> "Pendiente de sincronizar"
    MovementSyncStatus.IN_FLIGHT -> "Sincronizando"
    MovementSyncStatus.CONFLICT -> "Revisar cambios"
    MovementSyncStatus.FAILED_PERMANENT -> "Requiere revisión"
}
