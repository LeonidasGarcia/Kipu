package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.ui.graphics.Color
import com.kipu.app.feature.accounts.domain.model.AccountPreset
import com.kipu.app.feature.accounts.domain.model.CardNetwork
import com.kipu.app.feature.accounts.domain.model.CardPreset
import com.kipu.app.feature.accounts.domain.model.CreditProductReference
import com.kipu.app.feature.accounts.presentation.components.CardStylePreset

data class BankChoice(
    val code: String,
    val name: String,
    val accountPreset: AccountPreset,
    val cardPreset: CardPreset,
    val color: Color,
    val textColor: Color,
    val isRetail: Boolean = false,
    val subtitle: String? = null,
    val logoAcronym: String = name.take(3).uppercase(),
)

data class CatalogProductChoice(
    val name: String,
    val network: CardNetwork,
    val reference: CreditProductReference? = null,
    val stylePreset: CardStylePreset? = null,
) {
    val familyId: String get() = stylePreset?.familyId ?: "verified-products-other"
    val familyLabel: String get() = stylePreset?.familyLabel ?: "Otros productos verificados"
}

val bankChoices = listOf(
    BankChoice(
        code = "BCP",
        name = "BCP",
        accountPreset = AccountPreset.BCP,
        cardPreset = CardPreset.BCP_VISA,
        color = Color(0xFFFFC600),
        textColor = Color(0xFF002A8F),
        isRetail = false,
        subtitle = "Entidad actual",
        logoAcronym = "BCP",
    ),
    BankChoice(
        code = "BBVA",
        name = "BBVA",
        accountPreset = AccountPreset.BBVA,
        cardPreset = CardPreset.BBVA_VISA,
        color = Color(0xFF004481),
        textColor = Color.White,
        isRetail = false,
        subtitle = "Continental",
        logoAcronym = "BBVA",
    ),
    BankChoice(
        code = "INTERBANK",
        name = "Interbank",
        accountPreset = AccountPreset.INTERBANK,
        cardPreset = CardPreset.INTERBANK_VISA,
        color = Color(0xFF009940),
        textColor = Color(0xFF191C1E),
        isRetail = false,
        subtitle = "Grupo Intercorp",
        logoAcronym = "IBK",
    ),
    BankChoice(
        code = "SCOTIABANK",
        name = "Scotiabank",
        accountPreset = AccountPreset.SCOTIABANK,
        cardPreset = CardPreset.SCOTIABANK_MASTERCARD,
        color = Color(0xFFED1C24),
        textColor = Color(0xFFFFFFFF),
        isRetail = false,
        subtitle = "Perú",
        logoAcronym = "SCO",
    ),
    BankChoice(
        code = "BANBIF",
        name = "BanBif",
        accountPreset = AccountPreset.GENERIC,
        cardPreset = CardPreset.GENERIC,
        color = Color(0xFF005A8C),
        textColor = Color.White,
        isRetail = false,
        subtitle = "BanBif Perú",
        logoAcronym = "BIF",
    ),
    BankChoice(
        code = "BANCO_FALABELLA",
        name = "Tarjeta CMR Falabella",
        accountPreset = AccountPreset.GENERIC,
        cardPreset = CardPreset.GENERIC,
        color = Color(0xFF00843D),
        textColor = Color.White,
        isRetail = true,
        subtitle = "Banco Falabella Perú",
        logoAcronym = "CMR",
    ),
    BankChoice(
        code = "CENCOSUD",
        name = "Tarjeta Cencosud / Metro",
        accountPreset = AccountPreset.GENERIC,
        cardPreset = CardPreset.GENERIC,
        color = Color(0xFF004B93),
        textColor = Color.White,
        isRetail = true,
        subtitle = "Banco Cencosud Scotiabank",
        logoAcronym = "CEN",
    ),
    BankChoice(
        code = "RIPLEY",
        name = "Tarjeta Ripley",
        accountPreset = AccountPreset.GENERIC,
        cardPreset = CardPreset.GENERIC,
        color = Color(0xFF621275),
        textColor = Color.White,
        isRetail = true,
        subtitle = "Banco Ripley Perú",
        logoAcronym = "R",
    ),
)
