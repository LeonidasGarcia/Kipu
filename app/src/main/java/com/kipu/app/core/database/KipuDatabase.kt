package com.kipu.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kipu.app.feature.plans.data.local.*

@Database(entities = [PlanPreferencesEntity::class, PlanSelectionSyncStateEntity::class, SyncOutboxEntity::class, FeatureAccessCacheEntity::class], version = 1, exportSchema = true)
@TypeConverters(DatabaseConverters::class)
abstract class KipuDatabase : RoomDatabase() { abstract fun planPreferencesDao(): PlanPreferencesDao }
