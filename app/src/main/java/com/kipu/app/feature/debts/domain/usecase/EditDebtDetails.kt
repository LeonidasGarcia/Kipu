package com.kipu.app.feature.debts.domain

import com.kipu.app.feature.debts.domain.model.DebtDescriptionPatch
import com.kipu.app.feature.debts.domain.model.DebtDescriptionState
import com.kipu.app.feature.debts.domain.model.DebtDetailsEditResult

object DebtDescriptionEditor {
    fun apply(current: DebtDescriptionState, patch: DebtDescriptionPatch): DebtDetailsEditResult {
        if (patch.expectedRevision != current.revision) return DebtDetailsEditResult.Conflict(current.revision)
        val name = patch.counterpartyName.trim().replace(WHITESPACE, " ")
        if (name.isBlank()) return DebtDetailsEditResult.Rejected("COUNTERPARTY_REQUIRED")
        val nextRevision = runCatching { Math.addExact(current.revision, 1L) }.getOrNull()
            ?: return DebtDetailsEditResult.Rejected("REVISION_OVERFLOW")
        return DebtDetailsEditResult.Applied(
            current.copy(
                counterpartyName = name,
                dueDate = patch.dueDate,
                notes = patch.notes?.trim()?.takeIf(String::isNotEmpty),
                revision = nextRevision,
            ),
        )
    }

    private val WHITESPACE = Regex("\\s+")
}
