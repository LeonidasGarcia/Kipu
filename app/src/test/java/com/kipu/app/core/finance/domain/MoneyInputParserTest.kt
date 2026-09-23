package com.kipu.app.core.finance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyInputParserTest {
    @Test
    fun `parses exact minor units without floating point rounding`() {
        assertEquals(29L, MoneyInputParser.parseMinorUnits("0.29"))
        assertEquals(10_050L, MoneyInputParser.parseMinorUnits("100.50"))
        assertEquals(150L, MoneyInputParser.parseMinorUnits("1,5"))
        assertEquals(50L, MoneyInputParser.parseMinorUnits(".50"))
    }

    @Test
    fun `rejects values that cannot be represented as cents`() {
        assertNull(MoneyInputParser.parseMinorUnits("1.999"))
        assertNull(MoneyInputParser.parseMinorUnits("-1.00"))
        assertNull(MoneyInputParser.parseMinorUnits("1.2.3"))
        assertNull(MoneyInputParser.parseMinorUnits(""))
        assertNull(MoneyInputParser.parseMinorUnits("999999999999999999999999"))
    }
}
