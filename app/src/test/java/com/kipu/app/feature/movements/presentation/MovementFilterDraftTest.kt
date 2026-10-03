package com.kipu.app.feature.movements.presentation

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class MovementFilterDraftTest {
    @Test fun decimalAmountsUseMajorUnitsAndRequireCurrency() {
        val valid = MovementFilterDraft(minAmount = "15,50", maxAmount = "20", currency = "PEN").validate()
        assertEquals(1550L, valid.filters?.minAmountMinor)
        assertEquals(2000L, valid.filters?.maxAmountMinor)
        assertNull(MovementFilterDraft(minAmount = "15").validate().filters)
    }

    @Test fun malformedAndReversedRangesNeverBecomeAnEmptyFilter() {
        assertNull(MovementFilterDraft(minAmount = "abc", currency = "PEN").validate().filters)
        assertNull(MovementFilterDraft(minAmount = "30", maxAmount = "20", currency = "PEN").validate().filters)
        assertNull(MovementFilterDraft(fromDate = "2026-02-30").validate().filters)
        assertNull(MovementFilterDraft(fromDate = "2026-10-03", toDate = "2026-10-02").validate().filters)
    }

    @Test fun inclusivePickerDatesBecomeExclusiveLocalDayBoundsAcrossDst() {
        val zone = ZoneId.of("America/New_York")
        val result = MovementFilterDraft(fromDate = "2026-03-08", toDate = "2026-03-08").validate(zone).filters!!
        assertEquals(23 * 60 * 60 * 1000L, result.toDate!! - result.fromDate!!)
        assertEquals(LocalDate.parse("2026-03-09").atStartOfDay(zone).toInstant().toEpochMilli(), result.toDate)
    }

    @Test fun draftPreservesAppliedStateUntilValidatedAndAllowsOpenDateRanges() {
        val applied = AdvancedFiltersState(accountIds = setOf("wallet"), minAmountMinor = 1550, currency = "PEN")
        val draft = MovementFilterDraft.fromApplied(applied).copy(accountIds = setOf("bank"), minAmount = "20")
        assertEquals(setOf("wallet"), applied.accountIds)
        assertEquals(setOf("bank"), draft.validate().filters?.accountIds)
        assertEquals(2000L, draft.validate().filters?.minAmountMinor)
        assertNotNull(MovementFilterDraft(fromDate = "2026-10-01").validate().filters)
    }
}
