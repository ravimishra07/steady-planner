package com.exam.assistant.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.exam.assistant.domain.PreferenceStrength
import com.exam.assistant.domain.StudyPreferences
import com.exam.assistant.domain.SubjectPreferredWindow
import com.exam.assistant.domain.SubjectPriority
import com.exam.assistant.domain.SubjectStudyPreference
import java.time.DayOfWeek

@Entity(tableName = "study_preferences")
data class StudyPreferencesEntity(
    @PrimaryKey val attemptId: String,
    val preferredSessionMinutes: Int?,
    val shortBreakMinutes: Int?,
    val longBreakMinutes: Int?,
    val autoScheduleEnabled: Boolean,
    val autoRescheduleMissed: Boolean,
    val autoScheduleRevision: Boolean,
    val defaultStudyPlace: String?,
)

fun StudyPreferencesEntity.toDomain() = StudyPreferences(
    attemptId = attemptId,
    preferredSessionMinutes = preferredSessionMinutes,
    shortBreakMinutes = shortBreakMinutes,
    longBreakMinutes = longBreakMinutes,
    autoScheduleEnabled = autoScheduleEnabled,
    autoRescheduleMissed = autoRescheduleMissed,
    autoScheduleRevision = autoScheduleRevision,
    defaultStudyPlace = defaultStudyPlace,
)

fun StudyPreferences.toEntity() = StudyPreferencesEntity(
    attemptId = attemptId,
    preferredSessionMinutes = preferredSessionMinutes,
    shortBreakMinutes = shortBreakMinutes,
    longBreakMinutes = longBreakMinutes,
    autoScheduleEnabled = autoScheduleEnabled,
    autoRescheduleMissed = autoRescheduleMissed,
    autoScheduleRevision = autoScheduleRevision,
    defaultStudyPlace = defaultStudyPlace,
)

@Entity(tableName = "subject_study_preference", primaryKeys = ["attemptId", "subjectId"])
data class SubjectStudyPreferenceEntity(
    val attemptId: String,
    val subjectId: String,
    val priority: String,
    val targetMinutesPerWeek: Int?,
)

fun SubjectStudyPreferenceEntity.toDomain() = SubjectStudyPreference(
    attemptId = attemptId,
    subjectId = subjectId,
    priority = SubjectPriority.valueOf(priority),
    targetMinutesPerWeek = targetMinutesPerWeek,
)

fun SubjectStudyPreference.toEntity() = SubjectStudyPreferenceEntity(
    attemptId = attemptId,
    subjectId = subjectId,
    priority = priority.name,
    targetMinutesPerWeek = targetMinutesPerWeek,
)

@Entity(tableName = "subject_preferred_window", indices = [Index("attemptId", "subjectId")])
data class SubjectPreferredWindowEntity(
    @PrimaryKey val id: String,
    val attemptId: String,
    val subjectId: String,
    val dayOfWeek: String?,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val strength: String,
)

fun SubjectPreferredWindowEntity.toDomain() = SubjectPreferredWindow(
    id = id,
    attemptId = attemptId,
    subjectId = subjectId,
    dayOfWeek = dayOfWeek?.let(DayOfWeek::valueOf),
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
    strength = PreferenceStrength.valueOf(strength),
)

fun SubjectPreferredWindow.toEntity() = SubjectPreferredWindowEntity(
    id = id,
    attemptId = attemptId,
    subjectId = subjectId,
    dayOfWeek = dayOfWeek?.name,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
    strength = strength.name,
)

@Dao
interface StudyPreferenceDao {
    @Upsert
    suspend fun upsert(entity: StudyPreferencesEntity)

    @Query("SELECT * FROM study_preferences WHERE attemptId = :attemptId")
    suspend fun forAttempt(attemptId: String): StudyPreferencesEntity?

    @Query("DELETE FROM study_preferences WHERE attemptId = :attemptId")
    suspend fun deletePreferencesForAttempt(attemptId: String)

    @Upsert
    suspend fun upsertSubject(entity: SubjectStudyPreferenceEntity)

    @Query("SELECT * FROM subject_study_preference WHERE attemptId = :attemptId")
    suspend fun subjectPreferencesFor(attemptId: String): List<SubjectStudyPreferenceEntity>

    @Query("DELETE FROM subject_study_preference WHERE attemptId = :attemptId")
    suspend fun deleteSubjectPreferencesForAttempt(attemptId: String)

    @Upsert
    suspend fun upsertWindow(entity: SubjectPreferredWindowEntity)

    @Query("SELECT * FROM subject_preferred_window WHERE attemptId = :attemptId")
    suspend fun windowsFor(attemptId: String): List<SubjectPreferredWindowEntity>

    @Query("DELETE FROM subject_preferred_window WHERE attemptId = :attemptId")
    suspend fun deleteWindowsForAttempt(attemptId: String)
}
