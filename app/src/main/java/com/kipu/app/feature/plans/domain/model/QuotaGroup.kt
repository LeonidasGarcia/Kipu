package com.kipu.app.feature.plans.domain.model

enum class QuotaGroup {
    INSTRUMENTS,
    CUSTOM_CATEGORIES,
    DEBTS,
    GOALS,
    BUDGETS,
}

data class QuotaGroupState(
    val group: QuotaGroup,
    val limit: Int,
    val activeResourceIds: Set<String>,
    val selectedResourceIds: Set<String>,
    val isPremiumVerified: Boolean,
) {
    val needsSelection: Boolean
        get() = !isPremiumVerified && activeResourceIds.size > limit &&
            activeResourceIds.intersect(selectedResourceIds).isEmpty()

    val usableResourceIds: Set<String>
        get() = when {
            isPremiumVerified || activeResourceIds.size <= limit -> activeResourceIds
            else -> activeResourceIds.intersect(selectedResourceIds).take(limit).toSet()
        }

    val planLockedResourceIds: Set<String>
        get() = activeResourceIds - usableResourceIds
}
