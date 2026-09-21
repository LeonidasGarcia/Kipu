package com.kipu.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthDeepLinkHandlerTest {

    @Test
    fun `valid confirm email link returns ConfirmEmail on first call`() {
        val handler = AuthDeepLinkHandler()
        val result = handler.handleDeepLink("https://example.com/auth/confirm?token=valid_token_123")
        assertTrue(result is DeepLinkResult.ConfirmEmail)
    }

    @Test
    fun `replaying confirm email link returns InvalidOrConsumed`() {
        val handler = AuthDeepLinkHandler()
        val url = "https://example.com/auth/confirm?token=replay_token_123"
        val firstResult = handler.handleDeepLink(url)
        assertTrue(firstResult is DeepLinkResult.ConfirmEmail)

        val secondResult = handler.handleDeepLink(url)
        assertTrue(secondResult is DeepLinkResult.InvalidOrConsumed)
    }

    @Test
    fun `valid recovery link returns ResetPassword with token`() {
        val handler = AuthDeepLinkHandler()
        val result = handler.handleDeepLink("https://example.com/auth/recovery?token=recovery_secret_456")
        assertTrue(result is DeepLinkResult.ResetPassword)
        assertEquals("recovery_secret_456", (result as DeepLinkResult.ResetPassword).token)
    }

    @Test
    fun `unexpected path returns InvalidOrConsumed`() {
        val handler = AuthDeepLinkHandler()
        val result = handler.handleDeepLink("https://example.com/auth/unknown?token=token123")
        assertTrue(result is DeepLinkResult.InvalidOrConsumed)
    }

    @Test
    fun `null or empty uri returns InvalidOrConsumed`() {
        val handler = AuthDeepLinkHandler()
        assertTrue(handler.handleDeepLink(null as String?) is DeepLinkResult.InvalidOrConsumed)
        assertTrue(handler.handleDeepLink("") is DeepLinkResult.InvalidOrConsumed)
    }
}
