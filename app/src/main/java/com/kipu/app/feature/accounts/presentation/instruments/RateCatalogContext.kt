package com.kipu.app.feature.accounts.presentation.instruments

import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditProductReference
import com.kipu.app.feature.accounts.presentation.components.CardStylePresets
import java.text.Normalizer

/**
 * Resolves a catalog entry only when the stored visual product identity maps to one exact
 * catalog product. Issuer or card network alone are deliberately insufficient.
 */
sealed interface RateCatalogContext {
    data object LoadingCard : RateCatalogContext
    data class MissingProductIdentity(val card: CreditCard) : RateCatalogContext
    data class NoApplicableReference(val card: CreditCard, val productName: String) : RateCatalogContext
    data class Resolved(val card: CreditCard, val product: CreditProductReference) : RateCatalogContext
}

internal enum class RateCatalogReferenceState {
    LOADING,
    ERROR,
    ABSENT,
    AVAILABLE,
}

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
    val preset = CardStylePresets.byId(card.stylePresetId)
        ?.takeIf { it.institutionCode.isNotBlank() && it.productName.isNotBlank() }
        ?: return RateCatalogContext.MissingProductIdentity(card)

    val matchingProducts = products.filter { product ->
        product.institutionCode.equals(preset.institutionCode, ignoreCase = true) &&
            product.cardNetwork.equals(preset.network, ignoreCase = true) &&
            product.cardNetwork.equals(card.network.name, ignoreCase = true) &&
            normalizeCatalogIdentity(product.productName) == normalizeCatalogIdentity(preset.productName)
    }

    return matchingProducts.singleOrNull()?.let { RateCatalogContext.Resolved(card, it) }
        ?: RateCatalogContext.NoApplicableReference(card, preset.productName)
}

internal fun normalizeCatalogIdentity(value: String): String = Normalizer
    .normalize(value, Normalizer.Form.NFD)
    .replace("\\p{Mn}+".toRegex(), "")
    .lowercase()
    .replace("[^a-z0-9]+".toRegex(), " ")
    .trim()
