package com.kipu.app.feature.settings.domain

import android.content.Intent
import com.kipu.app.feature.settings.domain.model.DeviceAuthorization
import com.kipu.app.feature.settings.domain.model.PermissionSource

interface PermissionSourceGateway {
    fun checkDeviceAuthorization(source: PermissionSource): DeviceAuthorization
    fun createSettingsIntent(source: PermissionSource): Intent
    suspend fun refreshAndSaveDeviceAuthorizations(): Map<PermissionSource, DeviceAuthorization>
}
