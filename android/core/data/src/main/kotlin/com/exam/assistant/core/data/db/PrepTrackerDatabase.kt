package com.exam.assistant.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 4,
    exportSchema = true,
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE study_preferences ADD COLUMN weekdayTargetMinutes INTEGER NOT NULL DEFAULT 240",
                )
                db.execSQL(
                    "ALTER TABLE study_preferences ADD COLUMN weekendTargetMinutes INTEGER NOT NULL DEFAULT 420",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE study_session ADD COLUMN outcome TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE study_preferences ADD COLUMN planningOrder TEXT NOT NULL DEFAULT 'DEFAULT'")
            }
        }
    }
}
