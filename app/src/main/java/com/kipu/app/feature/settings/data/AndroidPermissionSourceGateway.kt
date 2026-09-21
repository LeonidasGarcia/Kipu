package com.kipu.app.feature.settings.data

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.kipu.app.feature.settings.data.local.InstallationPermissionStateEntity
import com.kipu.app.feature.settings.data.local.PermissionConsentDao
import com.kipu.app.feature.settings.domain.PermissionSourceGateway
import com.kipu.app.feature.settings.domain.model.DeviceAuthorization
import com.kipu.app.feature.settings.domain.model.PermissionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPermissionSourceGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: PermissionConsentDao,
) : PermissionSourceGateway {

    override fun checkDeviceAuthorization(source: PermissionSource): DeviceAuthorization {
        return when (source) {
            PermissionSource.OWN_NOTIFICATIONS -> {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (notificationManager != null && notificationManager.areNotificationsEnabled()) {
                    DeviceAuthorization.GRANTED
                } else {
                    DeviceAuthorization.DENIED
                }
            }
            PermissionSource.OTHER_APP_NOTIFICATION_CONTENT -> {
                val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
                if (enabledPackages.contains(context.packageName)) {
                    DeviceAuthorization.GRANTED
                } else {
                    DeviceAuthorization.DENIED
                }
            }
        }
    }

    override fun createSettingsIntent(source: PermissionSource): Intent {
        return when (source) {
            PermissionSource.OWN_NOTIFICATIONS -> {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            PermissionSource.OTHER_APP_NOTIFICATION_CONTENT -> {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
        }
    }

    override suspend fun refreshAndSaveDeviceAuthorizations(): Map<PermissionSource, DeviceAuthorization> {
        val results = mutableMapOf<PermissionSource, DeviceAuthorization>()
        for (source in PermissionSource.entries) {
            val auth = checkDeviceAuthorization(source)
            results[source] = auth
            dao.putInstallationPermission(
                InstallationPermissionStateEntity(
                    source = source.name,
                    deviceAuthorization = auth.name,
                    lastCheckedAt = Instant.now(),
                )
            )
        }
        return results
    }
}
