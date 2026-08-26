package com.exam.assistant.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.StudySessionStore
import com.exam.assistant.core.data.SyllabusRepository
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.domain.DAY_TIMELINE_START
import com.exam.assistant.domain.DayTimelineEntry
import com.exam.assistant.domain.StudySessionRecord
import com.exam.assistant.domain.buildDayTimeline
import com.exam.assistant.domain.computeSyllabusProgress
import com.exam.assistant.domain.currentMinuteOfDay
import com.exam.assistant.domain.findNextFreeSlot
import com.exam.assistant.domain.revisionSuggestions
import com.exam.assistant.domain.todayBudget
import com.exam.assistant.domain.weekAround
import com.exam.assistant.domain.weekStatusForDay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.min

class HomeViewModel(
    private val planStore: PlanStore,
    private val syllabusRepository: SyllabusRepository,
    private val syllabusStore: SyllabusStore,
    private val studySessionStore: StudySessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** Emits the session the instant it starts running, so the host can launch full-screen Focus. */
    private val _focusRequests = Channel<StudySessionRecord>(Channel.BUFFERED)
    val focusRequests = _focusRequests.receiveAsFlow()

    private val monthFormatter =
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.Builder().setLanguage("en").setRegion("IN").build())
    private val dayFormatter =
        DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.Builder().setLanguage("en").setRegion("IN").build())
    private val weekdayLabels = listOf("S", "M", "T", "W", "T", "F", "S")

    private var sections: List<com.exam.assistant.domain.SyllabusSection> = emptyList()
    private var allSessions: List<StudySessionRecord> = emptyList()
    private var weekdayHours = 4f
    private var weekendHours = 7f

    init {
        refresh()
        startClockTicker()
    }

    private fun startClockTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                _state.update { it.rebuild() }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val plan = planStore.load()
            sections = syllabusRepository.tier1Sections()
            allSessions = studySessionStore.loadAll()
            weekdayHours = plan?.weekdayHours ?: 4f
            weekendHours = plan?.weekendHours ?: 7f
            val syllabusProgress = computeSyllabusProgress(sections, syllabusStore.load().doneLeaves)
            val today = LocalDate.now()
            val running = allSessions
                .filter { it.date == today }
                .firstOrNull { it.runningEndsAtMs != null && !it.completed }
            _state.update { current ->
                current.copy(
                    loading = false,
                    hasPlan = plan != null,
                    sections = sections,
                    daysUntilExam = plan?.daysUntilExam ?: 0,
                    syllabusPercent = syllabusProgress.percent,
                    activeSprint = running?.let { session ->
                        val endsAt = session.runningEndsAtMs ?: return@let null
                        val left = ((endsAt - System.currentTimeMillis()) / 1000).toInt()
                        ActiveSprintUi(
                            sessionId = session.id,
                            title = session.title,
                            remainingSec = left.coerceAtLeast(0),
                            durationMinutes = session.durationMinutes,
                        )
                    },
                ).rebuild()
            }
        }
    }

    fun selectDate(date: LocalDate) {
        _state.update { it.copy(selectedDate = date).rebuild() }
    }

    fun toggleCalendarExpanded() {
        _state.update { it.copy(calendarExpanded = !it.calendarExpanded) }
    }

    fun openAddStudy() {
        _state.update {
            it.copy(
                sheet = HomeSheet.PickTopic,
                pickerLevel = StudyPickerLevel.Subjects,
                pickerSectionIndex = null,
                pickerTopicPath = emptyList(),
                pickerQuery = "",
                pendingTopic = null,
                customEndMinuteOfDay = null,
            )
        }
    }

    /** Same picker, seeded so the duration step defaults to fit inside [startMinuteOfDay, endMinuteOfDay). */
    fun openAddStudyInGap(startMinuteOfDay: Int, endMinuteOfDay: Int) {
        val gapMinutes = (endMinuteOfDay - startMinuteOfDay).coerceAtLeast(1)
        val defaultDuration = listOf(45, 30, 15).firstOrNull { it <= gapMinutes } ?: gapMinutes
        _state.update {
            it.copy(
                sheet = HomeSheet.PickTopic,
                pickerLevel = StudyPickerLevel.Subjects,
                pickerSectionIndex = null,
                pickerTopicPath = emptyList(),
                pickerQuery = "",
                pendingTopic = null,
                selectedDurationMinutes = defaultDuration,
                customEndMinuteOfDay = endMinuteOfDay,
            )
        }
    }

    fun selectPickerSection(index: Int) {
        _state.update {
            it.copy(
                pickerLevel = StudyPickerLevel.Topics,
                pickerSectionIndex = index,
                pickerTopicPath = emptyList(),
                pickerQuery = "",
            )
        }
    }

    fun openPickerSubtopics(path: List<Int>) {
        _state.update {
            it.copy(
                pickerLevel = StudyPickerLevel.Subtopics,
                pickerTopicPath = path,
                pickerQuery = "",
            )
        }
    }

    fun setPickerQuery(query: String) {
        _state.update { it.copy(pickerQuery = query) }
    }

    fun backInStudyPicker() {
        _state.update { current ->
            when {
                current.sheet == HomeSheet.PickDuration -> current.copy(sheet = HomeSheet.PickTopic)
                current.pickerQuery.isNotBlank() -> current.copy(pickerQuery = "")
                current.pickerLevel == StudyPickerLevel.Subtopics -> current.copy(
                    pickerLevel = StudyPickerLevel.Topics,
                    pickerTopicPath = emptyList(),
                )
                current.pickerLevel == StudyPickerLevel.Topics -> current.copy(
                    pickerLevel = StudyPickerLevel.Subjects,
                    pickerSectionIndex = null,
                )
                else -> current.copy(sheet = HomeSheet.None, pendingTopic = null)
            }
        }
    }

    fun dismissSheet() {
        _state.update {
            it.copy(
                sheet = HomeSheet.None,
                pickerLevel = StudyPickerLevel.Subjects,
                pickerSectionIndex = null,
                pickerTopicPath = emptyList(),
                pickerQuery = "",
                pendingTopic = null,
                customEndMinuteOfDay = null,
            )
        }
    }

    fun pickTopic(
        nodeKey: String,
        title: String,
        sectionName: String,
        subjectId: String,
        topicPath: String = "",
        isRevision: Boolean = false,
    ) {
        _state.update {
            it.copy(
                pendingTopic = PendingTopic(
                    nodeKey = nodeKey,
                    title = title,
                    sectionName = sectionName,
                    subjectId = subjectId,
                    topicPath = topicPath,
                    isRevision = isRevision,
                ),
                sheet = HomeSheet.PickDuration,
            )
        }
    }

    fun pickRevision(suggestion: com.exam.assistant.domain.RevisionSuggestion) {
        pickTopic(
            nodeKey = suggestion.nodeKey,
            title = suggestion.title,
            sectionName = suggestion.sectionName,
            subjectId = suggestion.subjectId,
            topicPath = suggestion.sectionName,
            isRevision = true,
        )
    }

    fun setDurationMinutes(minutes: Int) {
        _state.update { it.copy(selectedDurationMinutes = minutes) }
    }

    fun setScheduledEndMinuteOfDay(minuteOfDay: Int) {
        _state.update { it.copy(customEndMinuteOfDay = minuteOfDay) }
    }

    fun confirmStartSprint() {
        val pending = _state.value.pendingTopic ?: return
        val minutes = _state.value.selectedDurationMinutes
        val studyDate = _state.value.selectedDate
        val customEndMinuteOfDay = _state.value.customEndMinuteOfDay
        viewModelScope.launch {
            val nowMs = System.currentTimeMillis()
            val startingNow = customEndMinuteOfDay == null
            val startMinuteOfDay = if (startingNow) currentMinuteOfDay() else customEndMinuteOfDay - minutes
            val session = StudySessionRecord(
                id = UUID.randomUUID().toString(),
                date = studyDate,
                startMinuteOfDay = startMinuteOfDay,
                durationMinutes = minutes,
                nodeKey = pending.nodeKey,
                title = pending.title,
                sectionName = pending.sectionName,
                subjectId = pending.subjectId,
                isRevision = pending.isRevision,
                completed = false,
                runningEndsAtMs = if (startingNow) nowMs + minutes * 60_000L else null,
            )
            studySessionStore.upsert(session)
            allSessions = studySessionStore.loadAll()
            _state.update {
                it.copy(
                    sheet = HomeSheet.None,
                    pendingTopic = null,
                    customEndMinuteOfDay = null,
                    activeSprint = if (startingNow) {
                        ActiveSprintUi(
                            sessionId = session.id,
                            title = session.title,
                            remainingSec = minutes * 60,
                            durationMinutes = minutes,
                        )
                    } else {
                        it.activeSprint
                    },
                ).rebuild()
            }
            if (startingNow) _focusRequests.trySend(session)
        }
    }

    fun startScheduledSession(sessionId: String) {
        viewModelScope.launch {
            val session = allSessions.firstOrNull { it.id == sessionId } ?: return@launch
            if (session.completed) {
                return@launch
            }
            if (session.runningEndsAtMs != null) {
                // Already running (e.g. tapped again from Today) — just re-enter Focus for it.
                _focusRequests.trySend(session)
                return@launch
            }
            val updated = session.copy(
                runningEndsAtMs = System.currentTimeMillis() + session.durationMinutes * 60_000L,
            )
            studySessionStore.upsert(updated)
            allSessions = studySessionStore.loadAll()
            _state.update {
                it.copy(
                    activeSprint = ActiveSprintUi(
                        sessionId = updated.id,
                        title = updated.title,
                        remainingSec = updated.durationMinutes * 60,
                        durationMinutes = updated.durationMinutes,
                    ),
                ).rebuild()
            }
            _focusRequests.trySend(updated)
        }
    }

    /** Missed-block recovery: reposition on the timeline, never auto-start — the student taps Start when ready. */
    fun rescheduleToNextSlot(sessionId: String) {
        viewModelScope.launch {
            val session = allSessions.firstOrNull { it.id == sessionId } ?: return@launch
            if (session.completed) return@launch
            val slotToday = findNextFreeSlot(allSessions, session.date, session.durationMinutes, currentMinuteOfDay())
            if (slotToday != null) {
                applyReschedule(session, session.date, slotToday)
                return@launch
            }
            val tomorrow = session.date.plusDays(1)
            val slotTomorrow = findNextFreeSlot(allSessions, tomorrow, session.durationMinutes, DAY_TIMELINE_START)
            applyReschedule(session, tomorrow, slotTomorrow ?: session.startMinuteOfDay)
        }
    }

    fun rescheduleToTomorrowSameTime(sessionId: String) {
        viewModelScope.launch {
            val session = allSessions.firstOrNull { it.id == sessionId } ?: return@launch
            if (session.completed) return@launch
            applyReschedule(session, session.date.plusDays(1), session.startMinuteOfDay)
        }
    }

    fun rescheduleToTime(sessionId: String, minuteOfDay: Int) {
        viewModelScope.launch {
            val session = allSessions.firstOrNull { it.id == sessionId } ?: return@launch
            if (session.completed) return@launch
            applyReschedule(session, session.date, minuteOfDay)
        }
    }

    private suspend fun applyReschedule(session: StudySessionRecord, date: LocalDate, startMinuteOfDay: Int) {
        val updated = session.copy(
            date = date,
            startMinuteOfDay = startMinuteOfDay,
            runningEndsAtMs = null,
            completed = false,
        )
        studySessionStore.upsert(updated)
        allSessions = studySessionStore.loadAll()
        _state.update { it.rebuild() }
    }

    private fun HomeUiState.rebuild(): HomeUiState {
        if (!hasPlan) {
            return copy(
                weekDays = emptyList(),
                monthDays = emptyList(),
                monthTitle = "",
                dayTimeline = emptyList(),
                revisionItems = emptyList(),
                completedTodayMinutes = 0,
                plannedTodayMinutes = 0,
                completionPercent = 0,
                dayBudgetHours = 0,
            )
        }

        val today = LocalDate.now()
        val daySessions = allSessions.filter { it.date == selectedDate }
        val budget = todayBudget(weekdayHours, weekendHours, selectedDate)
        val doneMins = daySessions.filter { it.completed }.sumOf { it.durationMinutes }
        val percent = if (budget > 0) {
            min(100, ((doneMins.toFloat() / (budget * 60)) * 100).toInt())
        } else {
            0
        }
        val selectedIsToday = selectedDate == today
        val revisions = if (selectedIsToday) revisionSuggestions(allSessions, today) else emptyList()
        val builtTimeline = buildDayTimeline(
            sessions = daySessions,
            sections = sections,
            pendingRevisions = revisions,
            date = selectedDate,
            today = today,
            nowMinuteOfDay = currentMinuteOfDay(),
        )
        val plannedMinutes = builtTimeline.sumOf { entry ->
            (entry as? DayTimelineEntry.Study)?.block?.durationMinutes ?: 0
        }

        fun dayUi(date: LocalDate, weekdayIndex: Int): WeekDayUi {
            val dayBudget = todayBudget(weekdayHours, weekendHours, date)
            val dayDone = allSessions
                .filter { it.date == date && it.completed }
                .sumOf { it.durationMinutes }
            return WeekDayUi(
                date = date,
                weekdayLabel = weekdayLabels[weekdayIndex],
                dayOfMonth = date.dayOfMonth,
                selected = date == selectedDate,
                status = weekStatusForDay(date, today, dayDone, dayBudget),
            )
        }

        val monthAnchor = selectedDate.withDayOfMonth(1)
        val leadingBlanks = monthAnchor.dayOfWeek.value % 7
        val monthCells = mutableListOf<WeekDayUi?>()
        repeat(leadingBlanks) { monthCells += null }
        for (day in 0 until monthAnchor.lengthOfMonth()) {
            val date = monthAnchor.plusDays(day.toLong())
            monthCells += dayUi(date, date.dayOfWeek.value % 7)
        }

        return copy(
            monthTitle = selectedDate.format(monthFormatter),
            weekDays = weekAround(selectedDate).mapIndexed { index, date -> dayUi(date, index) },
            monthDays = monthCells,
            selectedIsToday = selectedIsToday,
            selectedDayLabel = if (selectedIsToday) "" else selectedDate.format(dayFormatter),
            dayBudgetHours = budget,
            completionPercent = percent,
            dayTimeline = builtTimeline,
            revisionItems = revisions,
            completedTodayMinutes = doneMins,
            plannedTodayMinutes = plannedMinutes,
        )
    }

    class Factory(
        private val planStore: PlanStore,
        private val syllabusRepository: SyllabusRepository,
        private val syllabusStore: SyllabusStore,
        private val studySessionStore: StudySessionStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(planStore, syllabusRepository, syllabusStore, studySessionStore) as T
    }
}
