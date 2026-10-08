package com.kipu.app.feature.debts.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebtAmountParserTest {
    @Test
    fun parsesDecimalAndSpanishCommaIntoMinorUnits() {
        assertEquals(12_500L, DebtAmountParser.toMinorUnits("125.00"))
        assertEquals(12_550L, DebtAmountParser.toMinorUnits("125,50"))
        assertEquals(1L, DebtAmountParser.toMinorUnits("0.01"))
    }

    @Test
    fun rejectsZeroNegativeExcessPrecisionAndOverflow() {
        assertNull(DebtAmountParser.toMinorUnits("0"))
        assertNull(DebtAmountParser.toMinorUnits("-1.00"))
        assertNull(DebtAmountParser.toMinorUnits("1.001"))
        assertNull(DebtAmountParser.toMinorUnits("999999999999999999999999"))
        assertNull(DebtAmountParser.toMinorUnits("1,234.56"))
    }
}
