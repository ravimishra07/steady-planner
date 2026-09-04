package com.exam.assistant.feature.home

import com.exam.assistant.domain.DayTimelineEntry
import com.exam.assistant.domain.RevisionSuggestion
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
    val revisionItems: List<RevisionSuggestion> = emptyList(),
    val sections: List<SyllabusSection> = emptyList(),
    val sheet: HomeSheet = HomeSheet.None,
    val pickerLevel: StudyPickerLevel = StudyPickerLevel.Subjects,
    val pickerSectionIndex: Int? = null,
    val pickerTopicPath: List<Int> = emptyList(),
    val pickerQuery: String = "",
    val pendingTopic: PendingTopic? = null,
    val selectedDurationMinutes: Int = 30,
    val customEndMinuteOfDay: Int? = null,
    val activeSprint: ActiveSprintUi? = null,
    val completedTodayMinutes: Int = 0,
    val plannedTodayMinutes: Int = 0,
    val missedDayRecovery: MissedDayRecoveryUi? = null,
    val placementIssue: StudyPlacementIssue? = null,
    val daysUntilExam: Int = 0,
    val syllabusPercent: Int = 0,
)
