package com.kipu.app.feature.accounts.presentation.components

import java.text.Normalizer
import java.util.Locale

/**
 * Single, auditable bridge between the visual card presets and official catalog names.
 *
 * Entries are deliberately limited to published names that differ from their preset labels.
 * Callers must use exact normalized names; this object never performs a partial match.
 */
data class OfficialCreditProductMapping(
    val presetId: String,
    val institutionCode: String,
    val network: String,
    val canonicalCatalogName: String,
    val acceptedNames: Set<String>,
)

object OfficialCreditProductMappings {
    private val mappings = listOf(
        mapping("bcp-amex-latam-classic", "BCP", "AMEX", "American Express Clásica LATAM Pass", "Amex Clásica LATAM Pass"),
        mapping("bcp-amex-latam-gold", "BCP", "AMEX", "American Express Oro LATAM Pass", "Amex Oro LATAM Pass"),
        mapping("bcp-amex-latam-platinum", "BCP", "AMEX", "American Express Platinum LATAM Pass", "Amex Platinum LATAM Pass"),
        mapping("bcp-amex-latam-black", "BCP", "AMEX", "American Express Black LATAM Pass", "Amex Black LATAM Pass"),
        mapping("bcp-visa-latam-sapphire", "BCP", "VISA", "Visa Infinite Sapphire LATAM Pass", "Visa Infinite Sapphire"),
        mapping("bcp-visa-latam-iridium", "BCP", "VISA", "Visa Infinite Iridium LATAM Pass", "Visa Infinite Iridium"),
        mapping("interbank-amex-green", "INTERBANK", "AMEX", "American Express Green", "Amex Green"),
        mapping("interbank-amex-gold", "INTERBANK", "AMEX", "American Express Gold", "Amex Gold"),
        mapping("interbank-amex-platinum", "INTERBANK", "AMEX", "American Express Platinum", "Amex Platinum"),
        mapping("interbank-amex-black", "INTERBANK", "AMEX", "American Express Black", "Amex Black"),
        mapping("interbank-amex-the-platinum-card", "INTERBANK", "AMEX", "The Platinum Card American Express", "The Platinum Card Amex"),
    )

    fun canonicalCatalogNameFor(presetId: String): String? =
        mappings.singleOrNull { it.presetId == presetId }?.canonicalCatalogName

    fun presetIdFor(
        institutionCode: String,
        network: String,
        productName: String,
    ): String? = mappings
        .filter {
            it.institutionCode.equals(institutionCode, ignoreCase = true) &&
                it.network.equals(network, ignoreCase = true) &&
                it.acceptedNames.any { name -> normalizeOfficialProductName(name) == normalizeOfficialProductName(productName) }
        }
        .map(OfficialCreditProductMapping::presetId)
        .singleOrNull()

    private fun mapping(
        presetId: String,
        institutionCode: String,
        network: String,
        canonicalCatalogName: String,
        vararg aliases: String,
    ) = OfficialCreditProductMapping(
        presetId = presetId,
        institutionCode = institutionCode,
        network = network,
        canonicalCatalogName = canonicalCatalogName,
        acceptedNames = setOf(canonicalCatalogName, *aliases),
    )
}

internal fun normalizeOfficialProductName(value: String): String = Normalizer
    .normalize(value, Normalizer.Form.NFD)
    .replace("\\p{Mn}+".toRegex(), "")
    .lowercase(Locale.ROOT)
    .replace("[^a-z0-9]+".toRegex(), " ")
    .trim()
