package com.kipu.app.feature.accounts.presentation.instruments

import com.kipu.app.feature.accounts.domain.model.CreditCard
import com.kipu.app.feature.accounts.domain.model.CreditProductReference
import com.kipu.app.feature.accounts.presentation.components.CardStylePresets
import java.text.Normalizer
import java.util.Locale

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

internal enum class RateCatalogCardState {
    LOADING,
    NOT_FOUND,
    AVAILABLE,
}

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
    val preset = CardStylePresets.byId(card.stylePresetId)
        ?.takeIf { it.institutionCode.isNotBlank() && it.productName.isNotBlank() }
        ?: return RateCatalogContext.MissingProductIdentity(card)

    val canonicalProductName = canonicalProductNamesByPresetId[preset.id] ?: preset.productName
    val matchingProducts = products.filter { product ->
        product.institutionCode.equals(preset.institutionCode, ignoreCase = true) &&
            product.cardNetwork.equals(preset.network, ignoreCase = true) &&
            product.cardNetwork.equals(card.network.name, ignoreCase = true) &&
            normalizeCatalogIdentity(product.productName) == normalizeCatalogIdentity(canonicalProductName)
    }

    return matchingProducts.singleOrNull()?.let { RateCatalogContext.Resolved(card, it) }
        ?: RateCatalogContext.NoApplicableReference(card, canonicalProductName)
}

/** Explicit, reviewed aliases between persisted visual presets and catalog identities. */
private val canonicalProductNamesByPresetId = mapOf(
    "bcp-visa-latam-sapphire" to "Visa Infinite Sapphire LATAM Pass",
    "bcp-visa-latam-iridium" to "Visa Infinite Iridium LATAM Pass",
    "bcp-amex-latam-classic" to "American Express Clásica LATAM Pass",
    "bcp-amex-latam-gold" to "American Express Oro LATAM Pass",
    "bcp-amex-latam-platinum" to "American Express Platinum LATAM Pass",
    "bcp-amex-latam-black" to "American Express Black LATAM Pass",
    "interbank-amex-green" to "American Express Green",
    "interbank-amex-gold" to "American Express Gold",
    "interbank-amex-platinum" to "American Express Platinum",
    "interbank-amex-black" to "American Express Black",
    "interbank-amex-the-platinum-card" to "The Platinum Card American Express",
)

internal fun normalizeCatalogIdentity(value: String): String = Normalizer
    .normalize(value, Normalizer.Form.NFD)
    .replace("\\p{Mn}+".toRegex(), "")
    .lowercase()
    .replace("[^a-z0-9]+".toRegex(), " ")
    .trim()
