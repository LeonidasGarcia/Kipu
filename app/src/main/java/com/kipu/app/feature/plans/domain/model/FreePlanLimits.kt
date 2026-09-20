package com.kipu.app.feature.plans.domain.model

data class FreePlanLimits(val policyVersion: Int = 1, val instruments: Int = 4, val customCategories: Int = 5, val debts: Int = 2, val goals: Int = 2, val budgets: Int = 2)
