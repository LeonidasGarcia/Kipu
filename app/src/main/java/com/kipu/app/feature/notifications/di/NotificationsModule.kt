package com.kipu.app.feature.notifications.di

import com.kipu.app.feature.notifications.data.OfflineFirstNotificationsRepository
import com.kipu.app.feature.notifications.data.RegisteredNotificationDestinationResolver
import com.kipu.app.feature.notifications.data.sync.NotificationSyncScheduler
import com.kipu.app.feature.notifications.data.sync.WorkManagerNotificationSyncScheduler
import com.kipu.app.feature.notifications.domain.NotificationsRepository
import com.kipu.app.feature.notifications.domain.NotificationDestinationResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {
    @Binds
    @Singleton
    abstract fun bindNotificationsRepository(
        impl: OfflineFirstNotificationsRepository,
    ): NotificationsRepository

    @Binds
    @Singleton
    abstract fun bindNotificationDestinationResolver(
        impl: RegisteredNotificationDestinationResolver,
    ): NotificationDestinationResolver

    @Binds
    @Singleton
    abstract fun bindNotificationSyncScheduler(
        impl: WorkManagerNotificationSyncScheduler,
    ): NotificationSyncScheduler
}
