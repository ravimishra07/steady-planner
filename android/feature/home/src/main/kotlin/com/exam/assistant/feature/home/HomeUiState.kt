package com.exam.assistant.feature.home

import com.exam.assistant.domain.DayTimelineEntry
import com.exam.assistant.domain.SyllabusSection
import com.exam.assistant.domain.StudyPlacementIssue
import com.exam.assistant.domain.StudyActivityType
import com.exam.assistant.domain.WeekDayStatus
import java.time.LocalDate

data class WeekDayUi(
    val date: LocalDate,
    val dayOfMonth: Int,
    val selected: Boolean,
    val status: WeekDayStatus,
)

enum class HomeSheet {
    None,
    PickTopic,
    PickDuration,
}

enum class StudyPickerLevel {
    Subjects,
    Topics,
    Subtopics,
}

data class PendingTopic(
    val nodeKey: String,
    val title: String,
    val sectionName: String,
    val subjectId: String,
    val topicPath: String = "",
    val activityType: StudyActivityType = StudyActivityType.LEARN,
)

val PendingTopic.isRevision: Boolean get() = activityType == StudyActivityType.REVISION

data class ActiveSprintUi(
    val sessionId: String,
    val title: String,
    val remainingSec: Int,
    val durationMinutes: Int,
)

data class MissedDayRecoveryUi(
    val missedCount: Int,
    val availableTodayMinutes: Int,
    val nextSessionId: String,
    val nextSessionTitle: String,
    val canStartNow: Boolean,
)

/** Deadline mode: part of the remaining work cannot be scheduled before finish-by. Estimate, until calibrated. */
data class PlanFitUi(
    val shortByMinutes: Int,
    val finishBy: LocalDate,
    val calibrated: Boolean,
)

/** "You missed N blocks. Rebalance the next days?" */
data class MissedBlocksUi(
    val count: Int,
    val since: LocalDate,
)

/** One chapter the student can say their batch taught today. */
data class CoachingChapterOption(
    val chapterId: String,
    val title: String,
    val subjectId: String,
    val subjectName: String,
)

/** "Physics: 23 lectures, 41 DPPs behind". Numbers from Metrics.coachingBacklogMetrics. */
data class CoachingBacklogRowUi(
    val subjectName: String,
    val lectures: Int,
    val dpps: Int,
)

/** Coaching on Today (§4.5, §5.4). Null when the student has no counts and no batch position. */
data class CoachingTodayUi(
    val batchChapterId: String?,
    val batchChapterTitle: String?,
    val batchLecture: Int,
    val chapters: List<CoachingChapterOption>,
    val backlog: List<CoachingBacklogRowUi>,
    val clearDate: LocalDate?,
    val liveSplit: Int,
)

data class HomeUiState(
    val loading: Boolean = true,
    val hasPlan: Boolean = false,
    val selectedDate: LocalDate = LocalDate.now(),
    val monthTitle: String = "",
    val weekDays: List<WeekDayUi> = emptyList(),
    val monthDays: List<WeekDayUi?> = emptyList(),
    val calendarExpanded: Boolean = false,
    val selectedIsToday: Boolean = true,
    val selectedDayLabel: String = "",
    val dayBudgetMinutes: Int = 0,
    val completionPercent: Int = 0,
    val dayTimeline: List<DayTimelineEntry> = emptyList(),
    val sections: List<SyllabusSection> = emptyList(),
    val sheet: HomeSheet = HomeSheet.None,
    val pickerLevel: StudyPickerLevel = StudyPickerLevel.Subjects,
    val pickerSectionIndex: Int? = null,
    val pickerTopicPath: List<Int> = emptyList(),
    val pickerQuery: String = "",
    val pendingTopic: PendingTopic? = null,
    /** When this topic's next revision falls due if the pending session is completed unrated; null for a custom topic. */
    val pendingRevisionDue: LocalDate? = null,
    val selectedDurationMinutes: Int = 30,
    val customEndMinuteOfDay: Int? = null,
    val activeSprint: ActiveSprintUi? = null,
    val completedTodayMinutes: Int = 0,
    val plannedTodayMinutes: Int = 0,
    val missedDayRecovery: MissedDayRecoveryUi? = null,
    val placementIssue: StudyPlacementIssue? = null,
    val daysUntilExam: Int = 0,
    val syllabusPercent: Int = 0,
    val planFit: PlanFitUi? = null,
    val missedBlocks: MissedBlocksUi? = null,
    val coaching: CoachingTodayUi? = null,
)
