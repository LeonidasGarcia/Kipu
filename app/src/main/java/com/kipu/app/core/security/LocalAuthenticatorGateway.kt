package com.kipu.app.core.security

import androidx.fragment.app.FragmentActivity
import com.kipu.app.core.security.model.LocalAuthenticatorCapability

interface LocalAuthenticatorGateway {
    fun getCapability(): LocalAuthenticatorCapability
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onResult: (success: Boolean, error: String?) -> Unit,
    )
}
