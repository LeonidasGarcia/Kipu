package com.kipu.app.feature.plans.domain.model

enum class CommercialOption(val amountMinor: Long, val label: String) {
    FREE(0, "Kipu Free"), MONTHLY(499, "Mensual"), ANNUAL(2999, "Anual"), LIFETIME(4999, "Lifetime");
    val currency: String get() = "PEN"
}
