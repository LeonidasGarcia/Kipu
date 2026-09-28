package com.kipu.app.feature.accounts.presentation

import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.core.session.LocalAccess
import com.kipu.app.core.session.RemoteSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class CreditCardOwnerSessionTest {
    @Test
    fun `credit card owner is the authenticated local account`() {
        val ownerId = UUID.randomUUID().toString()

        assertEquals(
            UserId(ownerId),
            activeCreditCardOwnerId(LocalAccess.Available(ownerId, RemoteSession.Absent)),
        )
    }

    @Test
    fun `credit card registration has no owner when local access is unavailable`() {
        assertNull(activeCreditCardOwnerId(LocalAccess.NoOwner))
        assertNull(activeCreditCardOwnerId(LocalAccess.Protected(UUID.randomUUID().toString(), "APP_LOCKED")))
    }
}
