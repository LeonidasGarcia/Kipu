package com.kipu.app.feature.accounts.presentation.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

enum class CardStyleType { DEBIT, CREDIT }

/** Native vector style for a card. The ID is persisted in cards.style_preset_id. */
data class CardStylePreset(
    val id: String,
    val institutionCode: String,
    val cardType: CardStyleType,
    val network: String,
    val tier: String,
    val familyId: String,
    val familyLabel: String,
    val productName: String,
    val gradientStartHex: String,
    val gradientEndHex: String,
    val accentHex: String,
    val textHex: String,
    val sourceLabel: String,
    val sourceReference: String,
    val selectable: Boolean = true,
    val aliases: Set<String> = emptySet(),
) {
    val gradientBrush: Brush
        get() = Brush.linearGradient(listOf(colorFromHex(gradientStartHex), colorFromHex(gradientEndHex)))
    val accentColor: Color get() = colorFromHex(accentHex)
    val textColor: Color get() = colorFromHex(textHex)
    val tierLabel: String get() = tier.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
}

object CardStylePresets {
    private val genericDebit = CardStylePreset(
        id = "generic-debit",
        institutionCode = "KIPU",
        cardType = CardStyleType.DEBIT,
        network = "UNKNOWN",
        tier = "STANDARD",
        familyId = "generic-debit",
        familyLabel = "Débito genérico",
        productName = "Tarjeta de débito",
        gradientStartHex = "#0F766E",
        gradientEndHex = "#0D544E",
        accentHex = "#80E2D8",
        textHex = "#FFFFFF",
        sourceLabel = "Kipu Andean Modernist",
        sourceReference = "docs/stitch-design-system.md",
    )

    private val genericCredit = CardStylePreset(
        id = "generic-credit",
        institutionCode = "KIPU",
        cardType = CardStyleType.CREDIT,
        network = "UNKNOWN",
        tier = "STANDARD",
        familyId = "generic-credit",
        familyLabel = "Crédito genérico",
        productName = "Tarjeta de crédito",
        gradientStartHex = "#334155",
        gradientEndHex = "#1E293B",
        accentHex = "#94A3B8",
        textHex = "#FFFFFF",
        sourceLabel = "Kipu Andean Modernist",
        sourceReference = "docs/stitch-design-system.md",
    )

    private val verifiedBankPresets: List<CardStylePreset> = """
        "bcp-debit-classic|bcp-debit|BCP|DEBIT|VISA|CLASSIC|Débito|Tarjeta de Débito BCP|#002A8F|#001B5E|#FF7800|#FFFFFF|[OFICIAL] Azul/Naranja BCP|Web BCP Débito"
        "bcp-amex-latam-classic|bcp-amex-latam|BCP|CREDIT|AMEX|CLASSIC|American Express LATAM Pass|Amex Clásica LATAM Pass|#002A8F|#001B5E|#FF7800|#FFFFFF|[OFICIAL] Azul BCP / Naranja|[S092]"
        "bcp-amex-latam-gold|bcp-amex-latam|BCP|CREDIT|AMEX|GOLD|American Express LATAM Pass|Amex Oro LATAM Pass|#E5B83A|#C69214|#FFC600|#001B5E|[INSPIRADO] Oro BCP / Azul|[S093]"
        "bcp-amex-latam-platinum|bcp-amex-latam|BCP|CREDIT|AMEX|PLATINUM|American Express LATAM Pass|Amex Platinum LATAM Pass|#5B6770|#373F47|#C0C8D0|#FFFFFF|[INSPIRADO] Platino grafito|[S094]"
        "bcp-amex-latam-black|bcp-amex-latam|BCP|CREDIT|AMEX|BLACK|American Express LATAM Pass|Amex Black LATAM Pass|#1C1C1C|#0A0A0A|#D4AF37|#FFFFFF|[INSPIRADO] Negro mate Amex|[S091]"
        "bcp-visa-classic|bcp-visa-regular|BCP|CREDIT|VISA|CLASSIC|Clásica|Visa Clásica|#002A8F|#001B5E|#FFC600|#FFFFFF|[OFICIAL] Azul BCP / Amarillo|[S095]"
        "bcp-visa-latam-classic|bcp-visa-latam|BCP|CREDIT|VISA|CLASSIC|Visa LATAM Pass|Visa Clásica LATAM Pass|#002A8F|#001B5E|#FF7800|#FFFFFF|[OFICIAL] Azul BCP / Naranja|[S096]"
        "bcp-visa-latam-gold|bcp-visa-latam|BCP|CREDIT|VISA|GOLD|Visa LATAM Pass|Visa Oro LATAM Pass|#E5B83A|#C69214|#FFC600|#001B5E|[INSPIRADO] Oro BCP / Azul|[S102]"
        "bcp-visa-latam-platinum|bcp-visa-latam|BCP|CREDIT|VISA|PLATINUM|Visa LATAM Pass|Visa Platinum LATAM Pass|#5B6770|#373F47|#E1E4E8|#FFFFFF|[INSPIRADO] Platino BCP|[S103]"
        "bcp-visa-latam-signature|bcp-visa-latam|BCP|CREDIT|VISA|SIGNATURE|Visa LATAM Pass|Visa Signature LATAM Pass|#1F2429|#0D1115|#D4AF37|#FFFFFF|[INSPIRADO] Grafito Signature|[S100]"
        "bcp-visa-latam-sapphire|bcp-visa-latam|BCP|CREDIT|VISA|INFINITE|Visa LATAM Pass|Visa Infinite Sapphire|#0F3B66|#061B30|#4D90CD|#FFFFFF|[INSPIRADO] Zafiro profundo|[S099]"
        "bcp-visa-latam-iridium|bcp-visa-latam|BCP|CREDIT|VISA|INFINITE|Visa LATAM Pass|Visa Infinite Iridium|#1E1F24|#0A0B0D|#A8B2C1|#FFFFFF|[INSPIRADO] Iridio oscuro|[S098]"
        "bcp-visa-qore-classic|bcp-visa-qore|BCP|CREDIT|VISA|CLASSIC|Visa Qore|Visa Clásica Qore|#002A8F|#00194A|#FF7800|#FFFFFF|[OFICIAL] Azul BCP / Naranja|[S097]"
        "bcp-visa-qore-gold|bcp-visa-qore|BCP|CREDIT|VISA|GOLD|Visa Qore|Visa Oro Qore|#E5B83A|#C69214|#FFC600|#001B5E|[INSPIRADO] Oro Qore / Azul|[S105]"
        "bcp-visa-qore-platinum|bcp-visa-qore|BCP|CREDIT|VISA|PLATINUM|Visa Qore|Visa Platinum Qore|#5B6770|#373F47|#C0C8D0|#FFFFFF|[INSPIRADO] Platino Qore|[S104]"
        "bcp-visa-qore-signature|bcp-visa-qore|BCP|CREDIT|VISA|SIGNATURE|Visa Qore|Visa Signature Qore|#1F2429|#0D1115|#D4AF37|#FFFFFF|[INSPIRADO] Grafito Qore|[S106]"
        "bcp-visa-qore-infinite|bcp-visa-qore|BCP|CREDIT|VISA|INFINITE|Visa Qore|Visa Infinite Qore|#101418|#040608|#D4AF37|#FFFFFF|[INSPIRADO] Obsidiana Qore|[S090]"
        "bcp-visa-light|bcp-sin-membresia|BCP|CREDIT|VISA|LIGHT|Sin membresía / Digital|Visa Light|#008075|#005C55|#80E2D8|#FFFFFF|[INSPIRADO] Petróleo sin membresía|[S101]"
        "bcp-visa-io|bcp-digital-io|BCP|CREDIT|VISA|IO|Sin membresía / Digital|Visa iO|#111111|#000000|#00FF87|#FFFFFF|[OFICIAL] Negro y Neón iO #00FF87|[S107]/[S108]"
        "interbank-debit-classic|interbank-debit|INTERBANK|DEBIT|VISA|CLASSIC|Débito|Tarjeta de Débito Interbank|#00B34D|#009940|#052654|#191C1E|[OFICIAL] Verde Interbank #009940|Web Débito"
        "interbank-amex-green|interbank-amex|INTERBANK|CREDIT|AMEX|GREEN|American Express|Amex Green|#00704A|#004830|#D0D7DE|#FFFFFF|[OFICIAL] Verde clásico Amex|[S022]"
        "interbank-amex-gold|interbank-amex|INTERBANK|CREDIT|AMEX|GOLD|American Express|Amex Gold|#D8AB3E|#B58832|#FFD700|#052654|[INSPIRADO] Oro Amex / Azul Interbank|[S022]"
        "interbank-amex-platinum|interbank-amex|INTERBANK|CREDIT|AMEX|PLATINUM|American Express|Amex Platinum|#566270|#353E47|#C8D1DC|#FFFFFF|[INSPIRADO] Platino Amex|[S026]"
        "interbank-amex-black|interbank-amex|INTERBANK|CREDIT|AMEX|BLACK|American Express|Amex Black|#1A1D20|#0B0C0E|#D4AF37|#FFFFFF|[INSPIRADO] Negro Amex|[S026]"
        "interbank-amex-the-platinum-card|interbank-amex|INTERBANK|CREDIT|AMEX|THE_PLATINUM_CARD|American Express|The Platinum Card Amex|#E2E6E9|#BAC2C7|#191C1E|#191C1E|[OFICIAL] Metal cepillado Centurion|[S022]"
        "interbank-visa-classic|interbank-visa|INTERBANK|CREDIT|VISA|CLASSIC|Tarjetas Visa|Visa Clásica|#00B34D|#009940|#052654|#191C1E|[OFICIAL] Verde Interbank #009940|[S024]"
        "interbank-visa-gold|interbank-visa|INTERBANK|CREDIT|VISA|GOLD|Tarjetas Visa|Visa Oro|#D8AB3E|#B58832|#FFD700|#052654|[INSPIRADO] Oro Interbank / Azul|[S024]"
        "interbank-visa-platinum|interbank-visa|INTERBANK|CREDIT|VISA|PLATINUM|Tarjetas Visa|Visa Platinum|#4A5568|#2D3748|#E2E8F0|#FFFFFF|[INSPIRADO] Platino pizarra|[S024]"
        "interbank-visa-signature|interbank-visa|INTERBANK|CREDIT|VISA|SIGNATURE|Tarjetas Visa|Visa Signature|#0A2540|#041020|#D4AF37|#FFFFFF|[INSPIRADO] Azul noche Signature|[S024]"
        "interbank-visa-infinite|interbank-visa|INTERBANK|CREDIT|VISA|INFINITE|Tarjetas Visa|Visa Infinite|#0F172A|#020617|#38BDF8|#FFFFFF|[INSPIRADO] Azul abisal Infinite|[S024]"
        "interbank-visa-access|interbank-sin-membresia|INTERBANK|CREDIT|VISA|ACCESS|Sin membresía|Visa Access|#007B6E|#005047|#5EEAD4|#FFFFFF|[INSPIRADO] Verde azulado sin membresía|[S027]"
        "interbank-visa-premia|interbank-visa|INTERBANK|CREDIT|VISA|PREMIA|Tarjetas Visa|Visa Premia|#1E3A8A|#172554|#F59E0B|#FFFFFF|[INSPIRADO] Azul marino Premia|[S025]"
        "interbank-mastercard-classic|interbank-mastercard|INTERBANK|CREDIT|MASTERCARD|CLASSIC|Tarjetas Mastercard|Mastercard Clásica|#00B34D|#009940|#EB001B|#191C1E|[OFICIAL] Verde Interbank #009940|[S023]"
        "interbank-mastercard-gold|interbank-mastercard|INTERBANK|CREDIT|MASTERCARD|GOLD|Tarjetas Mastercard|Mastercard Oro|#D8AB3E|#B58832|#FF5F00|#052654|[INSPIRADO] Oro Interbank / Azul|[S023]"
        "interbank-mastercard-platinum|interbank-mastercard|INTERBANK|CREDIT|MASTERCARD|PLATINUM|Tarjetas Mastercard|Mastercard Platinum|#4A5568|#2D3748|#EB001B|#FFFFFF|[INSPIRADO] Platino pizarra|[S023]"
        "interbank-guaranteed|interbank-garantizada|INTERBANK|CREDIT|UNKNOWN|GARANTIZADA|Garantizada|Tarjeta Garantía Líquida|#052654|#021430|#009940|#FFFFFF|[INSPIRADO] Azul Interbank con verde|[S019]"
        "bbva-debit-classic|bbva-debit|BBVA|DEBIT|VISA|CLASSIC|Débito|Tarjeta de Débito BBVA|#004481|#002A54|#00A9E0|#FFFFFF|[OFICIAL] Azul Marino BBVA #004481|Web BBVA Débito"
        "bbva-visa-bfree|bbva-bfree|BBVA|CREDIT|VISA|BFREE|Bfree|Bfree Visa|#006EC1|#003E78|#FFFFFF|#FFFFFF|[OFICIAL] Azul Bfree BBVA|[S012]"
        "bbva-mastercard-bfree|bbva-bfree|BBVA|CREDIT|MASTERCARD|BFREE|Bfree|Bfree Mastercard|#006EC1|#003E78|#EB001B|#FFFFFF|[OFICIAL] Azul Bfree BBVA|[S013]"
        "bbva-visa-cero|bbva-sin-membresia|BBVA|CREDIT|VISA|CERO|Cero / Básicas|Cero Visa|#E9EEF3|#CFD8E3|#004481|#004481|[OFICIAL] Blanco Cero / Azul Marino|[S010]"
        "bbva-basica|bbva-sin-membresia|BBVA|CREDIT|UNKNOWN|BASIC|Cero / Básicas|Básica|#4B5563|#374151|#9CA3AF|#FFFFFF|[INSPIRADO] Gris neutro sin desgravamen|[S011]"
        "bbva-visa-platinum|bbva-platinum|BBVA|CREDIT|VISA|PLATINUM|Línea Premium|Platinum Visa|#465362|#29323D|#00A9E0|#FFFFFF|[INSPIRADO] Platino grafito BBVA|[S014]"
        "bbva-mastercard-platinum|bbva-platinum|BBVA|CREDIT|MASTERCARD|PLATINUM|Línea Premium|Platinum Mastercard|#465362|#29323D|#EB001B|#FFFFFF|[INSPIRADO] Platino grafito BBVA|[S014]"
        "bbva-visa-signature|bbva-signature|BBVA|CREDIT|VISA|SIGNATURE|Línea Premium|Visa Signature|#1A283B|#0C141F|#D4AF37|#FFFFFF|[INSPIRADO] Azul noche Signature|[S015]"
        "bbva-visa-infinite|bbva-infinite|BBVA|CREDIT|VISA|INFINITE|Línea Premium|Visa Infinite|#12161A|#06080A|#C5CCD6|#FFFFFF|[INSPIRADO] Negro titanio Infinite|[S016]"
        "bbva-mastercard-black|bbva-black|BBVA|CREDIT|MASTERCARD|BLACK|Línea Premium|Mastercard Black|#12161A|#06080A|#EB001B|#FFFFFF|[INSPIRADO] Negro titanio Black|[S017]"
        "bbva-start|bbva-garantizada|BBVA|CREDIT|UNKNOWN|GARANTIZADA|Garantizada|Start con Respaldo|#004481|#001E3D|#22C55E|#FFFFFF|[INSPIRADO] Azul BBVA con verde|[S018]"
        "scotiabank-debit-classic|scotiabank-debit|SCOTIABANK|DEBIT|VISA|CLASSIC|Débito|Tarjeta de Débito Scotiabank|#FF333B|#ED1C24|#000000|#000000|[OFICIAL] Rojo Scotiabank #ED1C24|Web Débito"
        "scotiabank-singular-signature|scotiabank-singular|SCOTIABANK|CREDIT|VISA|SIGNATURE|Línea Singular|Visa Singular Signature|#141414|#050505|#ED1C24|#FFFFFF|[OFICIAL] Negro Singular / Acento Rojo|[S080]/[S082]"
        "scotiabank-singular-infinite|scotiabank-singular|SCOTIABANK|CREDIT|VISA|INFINITE|Línea Singular|Visa Singular Infinite|#141414|#050505|#D4AF37|#FFFFFF|[OFICIAL] Negro Singular / Acento Oro|[S080]/[S081]"
        "scotiabank-singular-black|scotiabank-singular|SCOTIABANK|CREDIT|MASTERCARD|BLACK|Línea Singular|Mastercard Singular Black|#141414|#050505|#ED1C24|#FFFFFF|[OFICIAL] Negro Singular / Acento Rojo|[S080]/[S083]"
        "falabella-debit-classic|falabella-debit|BANCO_FALABELLA|DEBIT|VISA|CLASSIC|Débito|Débito Banco Falabella|#00843D|#005C2B|#78BE20|#FFFFFF|[OFICIAL] Verde Falabella #00843D|Web Débito"
        "falabella-cmr-visa-classic|falabella-cmr|BANCO_FALABELLA|CREDIT|VISA|CLASSIC|Familia CMR Visa|CMR Visa|#00843D|#005C2B|#78BE20|#FFFFFF|[OFICIAL] Verde Falabella #00843D|[S033]"
        "falabella-cmr-visa-basic|falabella-cmr|BANCO_FALABELLA|CREDIT|VISA|BASIC|Familia CMR Visa|CMR Visa Básica|#F0F4F1|#DDE5DF|#00843D|#005C2B|[INSPIRADO] Blanco perla / Verde oscuro|[S033]"
        "falabella-cmr-visa-platinum|falabella-cmr|BANCO_FALABELLA|CREDIT|VISA|PLATINUM|Familia CMR Visa|CMR Visa Platinum|#505A60|#32383C|#78BE20|#FFFFFF|[INSPIRADO] Platino titanio Falabella|[S033]"
        "falabella-cmr-visa-signature|falabella-cmr|BANCO_FALABELLA|CREDIT|VISA|SIGNATURE|Familia CMR Visa|CMR Visa Signature|#1D2327|#0E1214|#78BE20|#FFFFFF|[INSPIRADO] Carbón grafito / Verde lima|[S033]"
        "falabella-cmr-guaranteed|falabella-cmr|BANCO_FALABELLA|CREDIT|UNKNOWN|GARANTIZADA|Familia CMR Visa|Tarjeta CMR con Garantía|#005C2B|#00381A|#78BE20|#FFFFFF|[INSPIRADO] Verde bosque profundo|[S033]"
        "ripley-debit-mastercard|ripley-debit|RIPLEY|DEBIT|MASTERCARD|CLASSIC|Débito|Débito Banco Ripley|#702082|#3A0847|#FFFFFF|#FFFFFF|[OFICIAL] Púrpura Ripley #702082|Web Débito"
        "ripley-mastercard-classic|ripley-mastercard|RIPLEY|CREDIT|MASTERCARD|CLASSIC|Mastercard|Tarjeta Crédito Ripley MC|#621275|#3A0847|#FFFFFF|#FFFFFF|[OFICIAL] Púrpura Ripley #621275|[S035]"
        "banbif-debit-classic|banbif-debit|BANBIF|DEBIT|VISA|CLASSIC|Débito|Tarjeta de Débito BanBif|#004B7A|#002E54|#009FE3|#FFFFFF|[OFICIAL] Azul Marino BanBif / Cyan|Web Débito"
        "banbif-visa-cero|banbif-sin-membresia|BANBIF|CREDIT|VISA|CERO|Cero membresía|Cero Membresía Visa|#E6F5FC|#C2E5F7|#008CD2|#00386B|[INSPIRADO] Celeste claro / Marino|[S031]"
        "banbif-visa-classic|banbif-visa|BANBIF|CREDIT|VISA|CLASSIC|Tarjetas Visa|Visa Clásica|#004B7A|#002E54|#009FE3|#FFFFFF|[OFICIAL] Azul Marino BanBif / Cyan|[S031]"
        "banbif-visa-gold|banbif-visa|BANBIF|CREDIT|VISA|GOLD|Tarjetas Visa|Visa Oro|#D8AB3E|#B58832|#E8D5A3|#001E3D|[INSPIRADO] Oro BanBif / Azul Noche|[S031]"
        "banbif-visa-platinum|banbif-visa|BANBIF|CREDIT|VISA|PLATINUM|Tarjetas Visa|Visa Platinum|#4D5C6A|#2F3A44|#009FE3|#FFFFFF|[INSPIRADO] Platino pizarra BanBif|[S031]"
        "banbif-visa-signature|banbif-visa|BANBIF|CREDIT|VISA|SIGNATURE|Tarjetas Visa|Visa Signature|#0A2240|#040F1D|#D4AF37|#FFFFFF|[INSPIRADO] Azul noche BanBif|[S031]"
        "banbif-visa-infinite|banbif-visa|BANBIF|CREDIT|VISA|INFINITE|Tarjetas Visa|Visa Infinite|#101418|#050709|#009FE3|#FFFFFF|[INSPIRADO] Negro obsidiana / Cyan|[S031]"
    """.trimIndent().lines().map { row ->
        val fields = row.removeSurrounding("\"").split('|')
        CardStylePreset(
            id = fields[0],
            familyId = fields[1],
            institutionCode = fields[2],
            cardType = CardStyleType.valueOf(fields[3]),
            network = fields[4],
            tier = fields[5],
            familyLabel = fields[6],
            productName = fields[7],
            gradientStartHex = fields[8],
            gradientEndHex = fields[9],
            accentHex = fields[10],
            textHex = fields[11],
            sourceLabel = fields[12],
            sourceReference = fields[13],
        )
    }

    /** Confirmed bank presets are kept separate from the legacy preset_id field. */
    val all: List<CardStylePreset> = listOf(genericDebit, genericCredit) + verifiedBankPresets

    val genericDebitStyle: CardStylePreset get() = genericDebit
    val genericCreditStyle: CardStylePreset get() = genericCredit

    fun byId(id: String?): CardStylePreset? = id?.let { value -> all.firstOrNull { it.id == value } }

    fun forInstitution(institutionCode: String, cardType: CardStyleType): List<CardStylePreset> =
        all.filter {
            it.selectable && it.institutionCode.equals(institutionCode, ignoreCase = true) && it.cardType == cardType
        }

    /** Matches only an exact normalized catalog name or an explicit alias; ambiguous matches are rejected. */
    fun forProduct(institutionCode: String, productName: String): CardStylePreset? {
        val normalized = normalize(productName)
        val candidates = all.filter { it.institutionCode.equals(institutionCode, ignoreCase = true) && it.selectable }
        val exact = candidates.filter { preset ->
            normalize(preset.productName) == normalized || preset.aliases.any { normalize(it) == normalized }
        }
        if (exact.size == 1) return exact.single()
        val contained = candidates.filter { preset ->
            normalize(preset.productName).contains(normalized) || normalized.contains(normalize(preset.productName)) ||
                preset.aliases.any { normalize(it).contains(normalized) || normalized.contains(normalize(it)) }
        }
        return contained.singleOrNull()
    }

    private fun normalize(value: String): String = java.text.Normalizer
        .normalize(value, java.text.Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase()
        .replace("[^a-z0-9]+".toRegex(), " ")
        .trim()

}

fun colorFromHex(hex: String): Color {
    val value = hex.removePrefix("#").toLong(16)
    val argb = if (hex.length == 7) 0xFF000000L or value else value
    return Color(argb)
}
