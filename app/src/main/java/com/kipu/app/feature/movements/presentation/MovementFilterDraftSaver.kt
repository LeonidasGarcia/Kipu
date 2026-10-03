package com.kipu.app.feature.movements.presentation

import androidx.compose.runtime.saveable.mapSaver
import com.kipu.app.feature.movements.domain.model.MovementFinancialState
import com.kipu.app.feature.movements.domain.model.MovementSyncStatus

/** Only presentation draft state is saved, never an authorization decision. */
internal val MovementFilterDraftSaver = mapSaver(
    save = { draft -> mapOf(
        "accounts" to draft.accountIds.toList(), "categories" to draft.categoryIds.toList(),
        "cards" to draft.cardIds.toList(), "merchants" to draft.merchantIds.toList(),
        "financial" to draft.financialStates.map { it.name }, "sync" to draft.syncStatuses.map { it.name },
        "min" to draft.minAmount, "max" to draft.maxAmount, "currency" to draft.currency.orEmpty(),
        "from" to draft.fromDate, "to" to draft.toDate,
    ) },
    restore = { values ->
        fun names(key: String) = (values[key] as? List<*>)?.filterIsInstance<String>().orEmpty().toSet()
        MovementFilterDraft(accountIds = names("accounts"), categoryIds = names("categories"),
            cardIds = names("cards"), merchantIds = names("merchants"),
            financialStates = MovementFinancialState.entries.filter { it.name in names("financial") }.toSet(),
            syncStatuses = MovementSyncStatus.entries.filter { it.name in names("sync") }.toSet(),
            minAmount = values["min"] as? String ?: "", maxAmount = values["max"] as? String ?: "",
            currency = (values["currency"] as? String)?.takeIf(String::isNotBlank),
            fromDate = values["from"] as? String ?: "", toDate = values["to"] as? String ?: "")
    },
)
