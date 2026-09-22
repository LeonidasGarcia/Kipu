package com.kipu.app.feature.accounts.domain

import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.UtilizationThreshold
import com.kipu.app.feature.accounts.domain.usecase.CheckUtilizationThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UtilizationAlertsTest {

    private lateinit var checker: CheckUtilizationThresholds
    private lateinit var testCard: CreditCard

    @Before
    fun setup() {
        checker = CheckUtilizationThresholds()
        testCard = CreditCard(
            id = CardId.generate(),
            userId = UserId.generate(),
            alias = "Mi Tarjeta BCP",
            issuer = "BCP",
            network = CardNetwork.VISA,
            lastFourDigits = "1234",
            currency = Currency.PEN,
            creditLimitMinorUnits = 1000_00L, // S/ 1,000.00
            billingDay = 15,
            dueDay = 5,
        )
    }

    @Test
    fun `ascending cross from 40 to 55 percent triggers 50 percent alert`() {
        val alerts = checker.evaluateUtilizationChange(
            card = testCard,
            previousDebtMinorUnits = 400_00L, // 40%
            newDebtMinorUnits = 550_00L,      // 55%
        )

        assertEquals(1, alerts.size)
        assertEquals(UtilizationThreshold.PERCENT_50, alerts[0].threshold)
    }

    @Test
    fun `staying above 50 percent does not trigger duplicate alert`() {
        checker.evaluateUtilizationChange(
            card = testCard,
            previousDebtMinorUnits = 400_00L,
            newDebtMinorUnits = 550_00L,
        )

        // Movement from 55% to 65% (both above 50%, below 80%)
        val alerts = checker.evaluateUtilizationChange(
            card = testCard,
            previousDebtMinorUnits = 550_00L,
            newDebtMinorUnits = 650_00L,
        )

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `single transaction jump from 30 to 85 percent triggers both 50 and 80 percent alerts`() {
        val alerts = checker.evaluateUtilizationChange(
            card = testCard,
            previousDebtMinorUnits = 300_00L, // 30%
            newDebtMinorUnits = 850_00L,      // 85%
        )

        assertEquals(2, alerts.size)
        assertEquals(UtilizationThreshold.PERCENT_50, alerts[0].threshold)
        assertEquals(UtilizationThreshold.PERCENT_80, alerts[1].threshold)
    }

    @Test
    fun `dropping below threshold re-arms it for future triggers`() {
        // Trigger 50%
        checker.evaluateUtilizationChange(
            card = testCard,
            previousDebtMinorUnits = 400_00L,
            newDebtMinorUnits = 550_00L,
        )

        // Drop below 50% (e.g. payment to 20%)
        val dropAlerts = checker.evaluateUtilizationChange(
            card = testCard,
            previousDebtMinorUnits = 550_00L,
            newDebtMinorUnits = 200_00L,
        )
        assertTrue(dropAlerts.isEmpty())

        // Rise again to 60% -> should trigger 50% again
        val riseAlerts = checker.evaluateUtilizationChange(
            card = testCard,
            previousDebtMinorUnits = 200_00L,
            newDebtMinorUnits = 600_00L,
        )
        assertEquals(1, riseAlerts.size)
        assertEquals(UtilizationThreshold.PERCENT_50, riseAlerts[0].threshold)
    }

    @Test
    fun `crossing 100 percent triggers critical alert`() {
        val alerts = checker.evaluateUtilizationChange(
            card = testCard,
            previousDebtMinorUnits = 900_00L,  // 90%
            newDebtMinorUnits = 1050_00L,      // 105%
        )

        assertEquals(1, alerts.size)
        assertEquals(UtilizationThreshold.PERCENT_100, alerts[0].threshold)
    }
}
