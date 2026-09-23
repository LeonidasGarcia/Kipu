package com.kipu.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthDeepLinkHandlerTest {

    @Test
    fun `valid confirm email link returns ConfirmEmail on first call`() {
        val handler = AuthDeepLinkHandler()
        val result = handler.handleDeepLink("https://kipu.app/auth/confirm?token_hash=valid_token_123")
        assertTrue(result is DeepLinkResult.ConfirmEmail)
    }

    @Test
    fun `replaying confirm email link returns InvalidOrConsumed`() {
        val handler = AuthDeepLinkHandler()
        val url = "https://kipu.app/auth/confirm?token_hash=replay_token_123"
        val firstResult = handler.handleDeepLink(url)
        assertTrue(firstResult is DeepLinkResult.ConfirmEmail)

        val secondResult = handler.handleDeepLink(url)
        assertTrue(secondResult is DeepLinkResult.InvalidOrConsumed)
    }

    @Test
    fun `valid recovery callback carries the full session URL`() {
        val handler = AuthDeepLinkHandler()
        val url = "https://kipu.app/auth/recovery#access_token=access&refresh_token=refresh&expires_in=3600&token_type=bearer&type=recovery"
        val result = handler.handleDeepLink(url)
        assertTrue(result is DeepLinkResult.ResetPassword)
        assertEquals(url, (result as DeepLinkResult.ResetPassword).callbackUrl)
    }

    @Test
    fun `unexpected path returns InvalidOrConsumed`() {
        val handler = AuthDeepLinkHandler()
        val result = handler.handleDeepLink("https://kipu.app/auth/unknown?token_hash=token123")
        assertTrue(result is DeepLinkResult.InvalidOrConsumed)
    }

    @Test
    fun `foreign host and bare token cannot open password reset`() {
        val handler = AuthDeepLinkHandler()
        assertTrue(handler.handleDeepLink("https://evil.example/auth/recovery?code=abc") is DeepLinkResult.InvalidOrConsumed)
        assertTrue(handler.handleDeepLink("https://kipu.app/auth/recovery?token=abc") is DeepLinkResult.InvalidOrConsumed)
    }

    @Test
    fun `null or empty uri returns InvalidOrConsumed`() {
        val handler = AuthDeepLinkHandler()
        assertTrue(handler.handleDeepLink(null as String?) is DeepLinkResult.InvalidOrConsumed)
        assertTrue(handler.handleDeepLink("") is DeepLinkResult.InvalidOrConsumed)
    }
}
