package com.kipu.app.feature.movements.domain

import com.kipu.app.feature.movements.domain.model.MovementHistoryPage
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery

interface MovementHistoryQueryRepository {
    suspend fun queryHistory(userId: String, query: MovementHistoryQuery): MovementHistoryPage
}
