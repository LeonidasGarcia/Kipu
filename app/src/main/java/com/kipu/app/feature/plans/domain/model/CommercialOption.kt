package com.kipu.app.feature.plans.domain.model

enum class CommercialOption(val amountMinor: Long, val label: String) {
    FREE(0, "Plan Free"),
    ANNUAL(2999, "Premium Anual"),
    MONTHLY(499, "Premium Mensual"),
    LIFETIME(4999, "Compra Única Lifetime");

    val currency: String get() = "PEN"
}
