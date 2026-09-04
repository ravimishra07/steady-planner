package com.exam.assistant.feature.onboarding

import androidx.annotation.StringRes
import com.exam.assistant.domain.Cushion
import com.exam.assistant.domain.NEET_EXAM_ID
import java.time.LocalDate

data class OnboardingUiState(
    val loading: Boolean = true,
    val editMode: Boolean = false,
    val step: OnboardingStep = OnboardingStep.Appearance,
    val examId: String = NEET_EXAM_ID,
    val coachingId: String = "",
    val commitments: List<OnboardingCommitment> = defaultCommitments(),
    val wakeMinute: Int = DEFAULT_WAKE_MINUTE,
    val sleepMinute: Int = DEFAULT_SLEEP_MINUTE,
    val targetDate: LocalDate = LocalDate.now().plusDays(DEFAULT_DAYS.toLong()),
    val workId: String = WORK_COLLEGE,
    val weekdayHours: Float = 4f,
    val weekendHours: Float = 7f,
    val studyPlace: String = "",
    val useProvidedSyllabus: Boolean = true,
    val selectedChapterIds: Set<String> = emptySet(),
    val coveredChapterIds: Set<String> = emptySet(),
    val syllabusSubjects: List<OnboardingSyllabusSubject> = emptyList(),
    val cushion: Cushion? = null,
    val finishing: Boolean = false,
    val finishFailed: Boolean = false,
) {
    val canGoBack: Boolean get() = step != OnboardingStep.Appearance
    val daysUntilTarget: Int
        get() = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), targetDate).toInt().coerceAtLeast(1)
}

enum class CoverageBaseline {
    STARTING_FRESH,
    PARTLY_COVERED,
    MOSTLY_REVISION,
}

data class OnboardingCommitment(
    val id: String,
    val kind: String,
    @StringRes val labelRes: Int,
    val startMinute: Int,
    val endMinute: Int,
    /** Sunday = 0, Monday = 1, through Saturday = 6. */
    val days: Set<Int>,
    val customLabel: String? = null,
)

data class OnboardingSyllabusSubject(
    val id: String,
    val name: String,
    val chapters: List<OnboardingSyllabusChapter>,
)

data class OnboardingSyllabusChapter(
    val id: String,
    val name: String,
    val classNumber: Int?,
)

internal data class DayShapeOption(
    val id: String,
    @StringRes val labelRes: Int,
    val weekdayHours: Float,
    val weekendHours: Float,
)

internal val DAY_SHAPES = listOf(
    DayShapeOption(WORK_FULLTIME, R.string.onboarding_shape_fulltime, 8f, 8f),
    DayShapeOption(WORK_WORKING, R.string.onboarding_shape_working, 3f, 8f),
    DayShapeOption(WORK_COLLEGE, R.string.onboarding_shape_college, 4f, 7f),
)

internal const val WORK_FULLTIME = "ft"
internal const val WORK_WORKING = "job"
internal const val WORK_COLLEGE = "col"
internal const val DEFAULT_DAYS = 118
internal const val DEFAULT_WAKE_MINUTE = 6 * 60
internal const val DEFAULT_SLEEP_MINUTE = 23 * 60
internal const val PROGRESS_SEGMENTS = 6

internal fun defaultCommitments(): List<OnboardingCommitment> {
    val everyDay = (0..6).toSet()
    return listOf(
        OnboardingCommitment(
            id = "seed-lunch",
            kind = "meal",
            labelRes = R.string.onboarding_commitment_lunch,
            startMinute = 13 * 60,
            endMinute = 14 * 60,
            days = everyDay,
        ),
        OnboardingCommitment(
            id = "seed-dinner",
            kind = "meal",
            labelRes = R.string.onboarding_commitment_dinner,
            startMinute = 20 * 60 + 30,
            endMinute = 21 * 60 + 30,
            days = everyDay,
        ),
    )
}

internal fun commitmentsForStudyMode(
    current: List<OnboardingCommitment>,
    modeId: String,
): List<OnboardingCommitment> {
    val withoutModeSeed = current.filterNot { it.id == "seed-school" || it.id == "seed-coaching" }
    val seeded = when (modeId) {
        "school" -> OnboardingCommitment(
            id = "seed-school",
            kind = "school",
            labelRes = R.string.onboarding_commitment_school,
            startMinute = 8 * 60,
            endMinute = 14 * 60,
            days = (1..6).toSet(),
        )
        "coaching" -> OnboardingCommitment(
            id = "seed-coaching",
            kind = "coaching",
            labelRes = R.string.onboarding_commitment_coaching,
            startMinute = 16 * 60,
            endMinute = 19 * 60,
            days = (1..6).toSet(),
        )
        else -> null
    }
    return seeded?.let { listOf(it) + withoutModeSeed } ?: withoutModeSeed
}
