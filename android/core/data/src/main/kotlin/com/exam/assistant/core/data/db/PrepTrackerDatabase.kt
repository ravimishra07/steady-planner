package com.exam.assistant.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.exam.assistant.domain.DEFAULT_NEET_EXAM_DATE
import java.time.LocalDate

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
        ChapterStageEntity::class,
        ChapterOverrideEntity::class,
        ChapterCoachingEntity::class,
        BatchPositionEntity::class,
    ],
    version = 10,
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
    abstract fun chapterStageDao(): ChapterStageDao
    abstract fun chapterOverrideDao(): ChapterOverrideDao
    abstract fun coachingDao(): CoachingDao

    companion object {
        @Volatile
        private var instance: PrepTrackerDatabase? = null

        fun get(context: Context): PrepTrackerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PrepTrackerDatabase::class.java,
                    "prep_tracker.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
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

        /** Revision on-time tracking and rating-replaces-schedule: both nullable, so existing rows stay valid. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE study_session ADD COLUMN dueDateAtCompletionEpochDay INTEGER")
                db.execSQL("ALTER TABLE study_session ADD COLUMN priorIntervalDays INTEGER")
            }
        }

        /**
         * Plan modes and the exam date split (PRODUCTION_V1_PLAN §3.1, §3.2). Non-destructive:
         * both new columns have defaults, so every existing row stays valid and every existing
         * student stays in deadline mode. Before this version the exam date was a copy of the
         * finish-by date, so a row where the two match gets the expected NEET date, never an
         * earlier one than the finish-by date it was copied from.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exam_attempt ADD COLUMN planMode TEXT NOT NULL DEFAULT 'DEADLINE'")
                db.execSQL("ALTER TABLE study_preferences ADD COLUMN allowLearnInRevisionPhase INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE exam_attempt SET examDateEpochDay = " +
                        "CASE WHEN targetCompletionDateEpochDay > $EXPECTED_NEET_2027_EPOCH_DAY " +
                        "THEN targetCompletionDateEpochDay ELSE $EXPECTED_NEET_2027_EPOCH_DAY END " +
                        "WHERE examDateEpochDay IS NULL OR examDateEpochDay = targetCompletionDateEpochDay",
                )
            }
        }

        /**
         * Phase C (PRODUCTION_V1_PLAN §3.3). Non-destructive: one new nullable column. An attempt
         * made before the "Which class / attempt?" question simply has no starting point.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exam_attempt ADD COLUMN startingPoint TEXT")
            }
        }

        /**
         * Per-chapter stages (PRODUCTION_V1_PLAN §5.1). Additive only. Existing covered chapters
         * need no copy: Learned is read from topic_progress, which this migration leaves untouched.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chapter_stage` (`attemptId` TEXT NOT NULL, `chapterId` TEXT NOT NULL, " +
                        "`stage` TEXT NOT NULL, `count` INTEGER NOT NULL DEFAULT 1, `updatedAtEpochMs` INTEGER, " +
                        "PRIMARY KEY(`attemptId`, `chapterId`, `stage`))",
                )
            }
        }

        /**
         * Chapter renames (PRODUCTION_V1_PLAN §5.2). Additive only: no row means the bundled
         * title, so every existing chapter keeps its name and its progress.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chapter_override` (`attemptId` TEXT NOT NULL, `chapterId` TEXT NOT NULL, " +
                        "`customTitle` TEXT, PRIMARY KEY(`attemptId`, `chapterId`))",
                )
            }
        }

        /**
         * Coaching backlog tracker (PRODUCTION_V1_PLAN §5.4). Additive only: a student with no
         * rows here has no coaching backlog and the planner behaves exactly as before.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chapter_coaching` (`attemptId` TEXT NOT NULL, `chapterId` TEXT NOT NULL, " +
                        "`lectures` INTEGER NOT NULL, `dpps` INTEGER NOT NULL, `lecturesDone` INTEGER NOT NULL, " +
                        "`dppsDone` INTEGER NOT NULL, PRIMARY KEY(`attemptId`, `chapterId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `batch_position` (`attemptId` TEXT NOT NULL, `chapterId` TEXT NOT NULL, " +
                        "`lecture` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`attemptId`))",
                )
            }
        }

        /** The compile-time default exam date as an epoch day. */
        private val EXPECTED_NEET_2027_EPOCH_DAY: Long = LocalDate.parse(DEFAULT_NEET_EXAM_DATE).toEpochDay()
    }
}
