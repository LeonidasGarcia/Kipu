package com.kipu.app.feature.accounts.presentation.instruments

import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditProductReference
import com.kipu.app.feature.accounts.presentation.components.CardStylePresets
import com.kipu.app.feature.accounts.presentation.components.OfficialCreditProductMappings
import com.kipu.app.feature.accounts.presentation.components.normalizeOfficialProductName
import java.util.Locale

/** Resolves a rate only from a unique, explicit product identity. */
sealed interface RateCatalogContext {
    data object LoadingCard : RateCatalogContext
    data class MissingProductIdentity(val card: CreditCard) : RateCatalogContext
    data class NoApplicableReference(val card: CreditCard, val productName: String) : RateCatalogContext
    data class Resolved(val card: CreditCard, val product: CreditProductReference) : RateCatalogContext
}

internal enum class RateCatalogReferenceState { LOADING, ERROR, ABSENT, AVAILABLE }
internal enum class RateCatalogCardState { LOADING, NOT_FOUND, AVAILABLE }

internal fun rateCatalogCardState(isCardLoading: Boolean, card: CreditCard?): RateCatalogCardState = when {
    isCardLoading -> RateCatalogCardState.LOADING
    card == null -> RateCatalogCardState.NOT_FOUND
    else -> RateCatalogCardState.AVAILABLE
}

internal fun personalTeaDraft(
    initializedCardId: String?,
    cardId: String,
    restoredDraft: String,
    persistedTeaBps: Int?,
): String = if (initializedCardId == cardId) restoredDraft else persistedTeaBps?.let(::formatTeaDraft).orEmpty()

private fun formatTeaDraft(teaBps: Int): String = String.format(Locale.US, "%.2f", teaBps / 100.0)

internal fun rateCatalogReferenceState(
    isCatalogLoading: Boolean,
    catalogError: String?,
    context: RateCatalogContext,
): RateCatalogReferenceState = when {
    isCatalogLoading -> RateCatalogReferenceState.LOADING
    catalogError != null -> RateCatalogReferenceState.ERROR
    context is RateCatalogContext.Resolved -> RateCatalogReferenceState.AVAILABLE
    else -> RateCatalogReferenceState.ABSENT
}

fun resolveRateCatalogContext(
    card: CreditCard?,
    products: List<CreditProductReference>,
): RateCatalogContext {
    if (card == null) return RateCatalogContext.LoadingCard
    val preset = (CardStylePresets.byId(card.stylePresetId)
        ?: card.alias?.let { CardStylePresets.forProduct(card.issuer, it, card.network.name) })
        ?.takeIf { it.institutionCode.isNotBlank() && it.productName.isNotBlank() }
        ?: return RateCatalogContext.MissingProductIdentity(card)

    val canonicalProductName = OfficialCreditProductMappings.canonicalCatalogNameFor(preset.id) ?: preset.productName
    val matchingProducts = products.filter { product ->
        product.institutionCode.equals(preset.institutionCode, ignoreCase = true) &&
            product.institutionCode.equals(card.issuer, ignoreCase = true) &&
            product.cardNetwork.equals(preset.network, ignoreCase = true) &&
            product.cardNetwork.equals(card.network.name, ignoreCase = true) &&
            normalizeOfficialProductName(product.productName) == normalizeOfficialProductName(canonicalProductName)
    }

    return matchingProducts.singleOrNull()?.let { RateCatalogContext.Resolved(card, it) }
        ?: RateCatalogContext.NoApplicableReference(card, canonicalProductName)
}
