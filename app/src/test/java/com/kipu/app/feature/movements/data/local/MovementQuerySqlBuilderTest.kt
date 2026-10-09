package com.kipu.app.feature.movements.data.local

import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementQuerySqlBuilderTest {
    @Test
    fun revisedTransactionsRemainInTheNetFlowSummary() {
        val sql = MovementQuerySqlBuilder
            .buildNetFlow(userId = "owner", query = MovementHistoryQuery())
            .sql

        assertTrue(
            "A corrected transaction still contributes its current financial effect",
            sql.contains("status IN ('ACTIVE', 'CONFIRMED', 'REVISED')"),
        )
    }
}
