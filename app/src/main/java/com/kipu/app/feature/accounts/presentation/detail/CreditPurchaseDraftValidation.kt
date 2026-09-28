package com.kipu.app.feature.accounts.presentation.detail

internal fun isValidCreditPurchaseDraft(
    amountMinor: Long?,
    merchant: String,
    categoryId: String?,
): Boolean = amountMinor != null && amountMinor > 0L && merchant.isNotBlank() && !categoryId.isNullOrBlank()
