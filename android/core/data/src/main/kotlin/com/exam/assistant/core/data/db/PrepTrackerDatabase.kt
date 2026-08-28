package com.exam.assistant.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ExamAttemptEntity::class,
        StudyPlanBlockEntity::class,
        StudySessionEntity::class,
        StudySessionSegmentEntity::class,
        TopicProgressEntity::class,
        RevisionStateEntity::class,
        TargetNodeOverrideEntity::class,
        WeeklyAvailabilityEntity::class,
        AvailabilityOverrideEntity::class,
        StudyPreferencesEntity::class,
        SubjectStudyPreferenceEntity::class,
        SubjectPreferredWindowEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class PrepTrackerDatabase : RoomDatabase() {
    abstract fun examAttemptDao(): ExamAttemptDao
    abstract fun studyPlanBlockDao(): StudyPlanBlockDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun topicProgressDao(): TopicProgressDao
    abstract fun revisionStateDao(): RevisionStateDao
    abstract fun targetNodeOverrideDao(): TargetNodeOverrideDao
    abstract fun availabilityDao(): AvailabilityDao
    abstract fun studyPreferenceDao(): StudyPreferenceDao

    companion object {
        @Volatile
        private var instance: PrepTrackerDatabase? = null

        fun get(context: Context): PrepTrackerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PrepTrackerDatabase::class.java,
                    "prep_tracker.db",
                ).build().also { instance = it }
            }
    }
}
