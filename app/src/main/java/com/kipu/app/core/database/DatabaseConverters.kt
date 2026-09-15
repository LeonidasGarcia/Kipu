package com.kipu.app.core.database

import androidx.room.TypeConverter
import java.time.Instant
import java.util.UUID
import com.kipu.app.feature.plans.data.local.OutboxStatus
import com.kipu.app.feature.plans.domain.model.PlanSelection

class DatabaseConverters {
    @TypeConverter
    fun uuidToString(value: UUID?): String? = value?.toString()

    @TypeConverter
    fun stringToUuid(value: String?): UUID? = value?.let(UUID::fromString)

    @TypeConverter
    fun instantToEpochMicros(value: Instant?): Long? = value?.let {
        Math.addExact(
            Math.multiplyExact(it.epochSecond, MICROS_PER_SECOND),
            it.nano / NANOS_PER_MICRO,
        )
    }

    @TypeConverter
    fun epochMicrosToInstant(value: Long?): Instant? = value?.let {
        val seconds = Math.floorDiv(it, MICROS_PER_SECOND)
        val micros = Math.floorMod(it, MICROS_PER_SECOND)
        Instant.ofEpochSecond(seconds, micros * NANOS_PER_MICRO)
    }

    @TypeConverter fun planSelectionToString(value: PlanSelection?): String? = value?.name
    @TypeConverter fun stringToPlanSelection(value: String?): PlanSelection? = value?.let(PlanSelection::valueOf)
    @TypeConverter fun outboxStatusToString(value: OutboxStatus?): String? = value?.name
    @TypeConverter fun stringToOutboxStatus(value: String?): OutboxStatus? = value?.let(OutboxStatus::valueOf)

    private companion object {
        const val MICROS_PER_SECOND = 1_000_000L
        const val NANOS_PER_MICRO = 1_000L
    }
}
