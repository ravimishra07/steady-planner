package com.exam.assistant.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverters
import androidx.room.Upsert
import com.exam.assistant.domain.AvailabilityOverride
import com.exam.assistant.domain.WeeklyAvailability
import java.time.DayOfWeek
import java.time.LocalDate

@Entity(tableName = "weekly_availability", indices = [Index("attemptId", "dayOfWeek")])
data class WeeklyAvailabilityEntity(
    @PrimaryKey val id: String,
    val attemptId: String,
    val dayOfWeek: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
)

fun WeeklyAvailabilityEntity.toDomain() = WeeklyAvailability(
    id = id,
    attemptId = attemptId,
    dayOfWeek = DayOfWeek.valueOf(dayOfWeek),
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
)

fun WeeklyAvailability.toEntity() = WeeklyAvailabilityEntity(
    id = id,
    attemptId = attemptId,
    dayOfWeek = dayOfWeek.name,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
)

@Entity(tableName = "availability_override", indices = [Index("attemptId", "dateEpochDay")])
@TypeConverters(Converters::class)
data class AvailabilityOverrideEntity(
    @PrimaryKey val id: String,
    val attemptId: String,
    val dateEpochDay: Long,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
)

fun AvailabilityOverrideEntity.toDomain() = AvailabilityOverride(
    id = id,
    attemptId = attemptId,
    date = LocalDate.ofEpochDay(dateEpochDay),
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
)

fun AvailabilityOverride.toEntity() = AvailabilityOverrideEntity(
    id = id,
    attemptId = attemptId,
    dateEpochDay = date.toEpochDay(),
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
)

@Dao
interface AvailabilityDao {
    @Upsert
    suspend fun upsertWeekly(entities: List<WeeklyAvailabilityEntity>)

    @Query("SELECT * FROM weekly_availability WHERE attemptId = :attemptId")
    suspend fun weeklyFor(attemptId: String): List<WeeklyAvailabilityEntity>

    @Query("DELETE FROM weekly_availability WHERE attemptId = :attemptId")
    suspend fun deleteWeeklyForAttempt(attemptId: String)

    @Upsert
    suspend fun upsertOverride(entity: AvailabilityOverrideEntity)

    @Query("SELECT * FROM availability_override WHERE attemptId = :attemptId")
    suspend fun overridesFor(attemptId: String): List<AvailabilityOverrideEntity>

    @Query("DELETE FROM availability_override WHERE attemptId = :attemptId")
    suspend fun deleteOverridesForAttempt(attemptId: String)
}
