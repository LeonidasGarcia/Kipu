package com.kipu.app.feature.debts.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class DebtRpcRequestDto(
    @SerialName("p_payload") val payload: JsonObject,
)

@Serializable
data class DebtCommandRemoteResult(
    @SerialName("status") val status: String,
    @SerialName("debt_id") val debtId: String? = null,
    @SerialName("event_id") val eventId: String? = null,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("revision") val revision: Long? = null,
    @SerialName("current_revision") val currentRevision: Long? = null,
    @SerialName("remaining_minor") val remainingMinor: Long? = null,
    @SerialName("result") val result: String? = null,
    @SerialName("error") val error: String? = null,
)
