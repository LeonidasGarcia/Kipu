package com.kipu.app.feature.accounts.domain.usecase

import com.kipu.app.core.finance.domain.model.CardId
import com.kipu.app.core.finance.domain.model.Currency
import com.kipu.app.feature.accounts.domain.model.PersonalTea
import com.kipu.app.feature.accounts.domain.model.RateReference
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetReferentialRates @Inject constructor() {
    operator fun invoke(currency: Currency? = null): List<RateReference> {
        val catalog = RateReference.DEFAULT_PERU_CATALOG
        return if (currency != null) {
            catalog.filter { it.currency == currency }
        } else {
            catalog
        }
    }
}

@Singleton
class UpdatePersonalTea @Inject constructor() {

    private val personalTeas = ConcurrentHashMap<String, PersonalTea>()

    operator fun invoke(cardId: CardId, teaBps: Int): Result<PersonalTea> {
        return runCatching {
            val personalTea = PersonalTea(cardId = cardId, teaBps = teaBps)
            personalTeas[cardId.value] = personalTea
            personalTea
        }
    }

    fun getPersonalTea(cardId: CardId): PersonalTea? {
        return personalTeas[cardId.value]
    }
}
