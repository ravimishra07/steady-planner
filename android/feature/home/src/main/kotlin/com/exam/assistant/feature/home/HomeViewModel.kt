package com.exam.assistant.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.AvailabilityRepository
import com.exam.assistant.core.data.repo.PlanRepository
import com.exam.assistant.core.data.repo.RevisionRepository
import com.exam.assistant.core.data.repo.RollingPlanRepository
import com.exam.assistant.core.data.repo.StudyPreferenceRepository
import com.exam.assistant.core.data.repo.StudySessionRepository
import com.exam.assistant.core.data.repo.TopicProgressRepository
import com.exam.assistant.domain.DAY_TIMELINE_START
import com.exam.assistant.domain.DayTimelineEntry
import com.exam.assistant.domain.FixedCommitmentBlock
import com.exam.assistant.domain.StudySessionRecord
import com.exam.assistant.domain.StudyPlanBlock
import com.exam.assistant.domain.StudySession
import com.exam.assistant.domain.StudySessionStatus
import com.exam.assistant.domain.StudyActivityType
import com.exam.assistant.domain.PlanBlockSource
import com.exam.assistant.domain.PlanBlockStatus
import com.exam.assistant.domain.ExamPack
import com.exam.assistant.domain.SyllabusSection
import com.exam.assistant.domain.SyllabusTopicNode
import com.exam.assistant.domain.TopicProgressStatus
import com.exam.assistant.domain.WeeklyAvailability
import com.exam.assistant.domain.AvailabilityOverride
import com.exam.assistant.domain.effectiveWindowsFor
import com.exam.assistant.domain.findNode
import com.exam.assistant.domain.findSubjectOf
import com.exam.assistant.domain.StudyPlacementIssue
import com.exam.assistant.domain.buildDayTimeline
import com.exam.assistant.domain.computeSyllabusProgress
import com.exam.assistant.domain.currentMinuteOfDay
import com.exam.assistant.domain.findNextFreeSlot
import com.exam.assistant.domain.planMissedDayRecovery
import com.exam.assistant.domain.todayBudgetMinutes
import com.exam.assistant.domain.weekAround
import com.exam.assistant.domain.weekStatusForDayMinutes
import com.exam.assistant.domain.validateStudyPlacement
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
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.min

class HomeViewModel(
    private val examPackRepository: ExamPackRepository,
    private val attemptRepository: AttemptRepository,
    private val availabilityRepository: AvailabilityRepository,
    private val planRepository: PlanRepository,
    private val studySessionRepository: StudySessionRepository,
    private val topicProgressRepository: TopicProgressRepository,
    private val studyPreferenceRepository: StudyPreferenceRepository,
    private val revisionRepository: RevisionRepository,
    private val rollingPlanRepository: RollingPlanRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** Emits the session the instant it starts running, so the host can launch full-screen Focus. */
    private val _focusRequests = Channel<StudySessionRecord>(Channel.BUFFERED)
    val focusRequests = _focusRequests.receiveAsFlow()

    private var sections: List<com.exam.assistant.domain.SyllabusSection> = emptyList()
    private var allSessions: List<StudySessionRecord> = emptyList()
    private var pack: ExamPack? = null
    private var attemptId: String? = null
    private var planBlocks: List<StudyPlanBlock> = emptyList()
    private var history: List<StudySession> = emptyList()
    private var dueRevisions: List<com.exam.assistant.domain.RevisionSuggestion> = emptyList()
    private var weekdayHours = 4f
    private var weekendHours = 7f
    private var wakeMinute = DAY_TIMELINE_START
    private var sleepMinute = com.exam.assistant.domain.DAY_TIMELINE_END
    private var weeklyAvailability: List<WeeklyAvailability> = emptyList()
    private var availabilityOverrides: List<AvailabilityOverride> = emptyList()

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
            val attempt = attemptRepository.activeAttempt()
            attemptId = attempt?.id
            pack = attempt?.let { examPackRepository.examPackFor(it.examId) }
            sections = pack?.toPickerSections().orEmpty()
            val preferences = attempt?.let { studyPreferenceRepository.forAttempt(it.id) }
            weekdayHours = preferences?.weekdayTargetMinutes?.div(60f) ?: 4f
            weekendHours = preferences?.weekendTargetMinutes?.div(60f) ?: 7f
            weeklyAvailability = attempt?.let { availabilityRepository.weeklyFor(it.id) }.orEmpty()
            availabilityOverrides = attempt?.let { availabilityRepository.overridesFor(it.id) }.orEmpty()
            wakeMinute = weeklyAvailability.minOfOrNull { it.startMinuteOfDay } ?: DAY_TIMELINE_START
            sleepMinute = weeklyAvailability.maxOfOrNull { it.endMinuteOfDay } ?: com.exam.assistant.domain.DAY_TIMELINE_END
            val today = LocalDate.now()
            if (attempt != null) reloadCanonical(attempt.id)
            dueRevisions = if (attempt != null) {
                revisionRepository.dueBy(attempt.id, today).mapNotNull { revision ->
                    val node = pack?.findNode(revision.nodeId) ?: return@mapNotNull null
                    val subject = pack?.findSubjectOf(revision.nodeId) ?: return@mapNotNull null
                    com.exam.assistant.domain.RevisionSuggestion(
                        nodeKey = revision.nodeId,
                        title = node.title,
                        sectionName = subject.name,
                        subjectId = subject.id,
                        studiedOn = revision.lastReviewedAtEpochMs?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        } ?: today,
                    )
                }
            } else emptyList()
            val covered = attempt?.let { active ->
                topicProgressRepository.allOnce(active.id)
                    .filter { it.status == TopicProgressStatus.COVERED }
                    .mapTo(mutableSetOf()) { it.nodeId }
            }.orEmpty()
            val syllabusProgress = computeSyllabusProgress(sections, covered)
            val running = allSessions
                .filter { it.date == today }
                .firstOrNull { it.runningEndsAtMs != null && !it.completed }
            _state.update { current ->
                current.copy(
                    loading = false,
                    hasPlan = attempt != null,
                    sections = sections,
                    daysUntilExam = attempt?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it.targetCompletionDate).toInt().coerceAtLeast(0) } ?: 0,
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

    private suspend fun reloadCanonical(id: String) {
        val today = LocalDate.now()
        planBlocks = planRepository.planBetween(id, today.minusYears(2), today.plusYears(2))
        history = studySessionRepository.between(id, today.minusYears(2), today.plusYears(2))
        val sessionsByBlock = history.filter { it.planBlockId != null }.associateBy { it.planBlockId }
        val currentPack = pack
        allSessions = planBlocks
            .filter { it.status != PlanBlockStatus.RESCHEDULED && it.status != PlanBlockStatus.SKIPPED }
            .map { block ->
                val actual = sessionsByBlock[block.id]
                val node = block.nodeId?.let { currentPack?.findNode(it) }
                val subject = block.nodeId?.let { currentPack?.findSubjectOf(it) }
                StudySessionRecord(
                    id = block.id,
                    date = block.scheduledDate,
                    startMinuteOfDay = block.startMinuteOfDay,
                    durationMinutes = block.plannedMinutes,
                    nodeKey = block.nodeId.orEmpty(),
                    title = node?.title ?: block.customTitle ?: "Study session",
                    sectionName = subject?.name.orEmpty(),
                    subjectId = block.subjectId.orEmpty(),
                    isRevision = block.activityType == StudyActivityType.REVISION,
                    completed = block.status == PlanBlockStatus.COMPLETED || actual?.status == StudySessionStatus.COMPLETED,
                    focusedMinutes = actual?.focusedSeconds?.div(60),
                    runningEndsAtMs = actual?.takeIf { it.status == StudySessionStatus.RUNNING }
                        ?.let { it.startedAtEpochMs + block.plannedMinutes * 60_000L },
                )
            }
    }

    fun selectDate(date: LocalDate) {
        _state.update { it.copy(selectedDate = date, placementIssue = null).rebuild() }
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
                placementIssue = null,
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
                placementIssue = null,
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
                placementIssue = null,
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
                    activityType = if (isRevision) StudyActivityType.REVISION else StudyActivityType.LEARN,
                ),
                sheet = HomeSheet.PickDuration,
                placementIssue = null,
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
        _state.update { it.copy(selectedDurationMinutes = minutes, placementIssue = null) }
    }

    fun setActivityType(type: StudyActivityType) {
        if (type !in setOf(StudyActivityType.LEARN, StudyActivityType.PRACTICE, StudyActivityType.REVISION, StudyActivityType.MOCK_TEST)) return
        _state.update { current ->
            current.copy(pendingTopic = current.pendingTopic?.copy(activityType = type))
        }
    }

    fun setScheduledEndMinuteOfDay(minuteOfDay: Int) {
        _state.update { it.copy(customEndMinuteOfDay = minuteOfDay, placementIssue = null) }
    }

    fun confirmStartSprint() {
        val current = _state.value
        val pending = current.pendingTopic ?: return
        val minutes = current.selectedDurationMinutes
        val studyDate = current.selectedDate
        if (!current.selectedIsToday) {
            _state.update { it.copy(placementIssue = StudyPlacementIssue.OUTSIDE_DAY) }
            return
        }
        val startMinuteOfDay = currentMinuteOfDay()
        val issue = validateStudyPlacement(
            sessions = allSessions,
            date = studyDate,
            startMinuteOfDay = startMinuteOfDay,
            durationMinutes = minutes,
            fixedCommitments = fixedBlocksFor(studyDate),
            dayStartMinute = wakeMinute,
            dayEndMinute = sleepMinute,
        )
        if (issue != null) {
            _state.update { it.copy(placementIssue = issue) }
            return
        }
        viewModelScope.launch {
            val nowMs = System.currentTimeMillis()
            val block = createManualBlock(pending, studyDate, startMinuteOfDay, minutes, nowMs) ?: return@launch
            planRepository.upsert(block)
            attemptId?.let { reloadCanonical(it) }
            val session = allSessions.first { it.id == block.id }.copy(runningEndsAtMs = nowMs + minutes * 60_000L)
            allSessions = allSessions.map { if (it.id == session.id) session else it }
            _state.update {
                it.copy(
                    sheet = HomeSheet.None,
                    pendingTopic = null,
                    customEndMinuteOfDay = null,
                    placementIssue = null,
                    activeSprint = ActiveSprintUi(
                        sessionId = session.id,
                        title = session.title,
                        remainingSec = minutes * 60,
                        durationMinutes = minutes,
                    ),
                ).rebuild()
            }
            _focusRequests.trySend(session)
        }
    }

    fun confirmAddToPlan() {
        val current = _state.value
        val pending = current.pendingTopic ?: return
        val minutes = current.selectedDurationMinutes
        val studyDate = current.selectedDate
        val endMinuteOfDay = current.customEndMinuteOfDay
            ?: (currentMinuteOfDay() + minutes).coerceAtMost(24 * 60)
        val startMinuteOfDay = endMinuteOfDay - minutes
        val issue = validateStudyPlacement(
            sessions = allSessions,
            date = studyDate,
            startMinuteOfDay = startMinuteOfDay,
            durationMinutes = minutes,
            fixedCommitments = fixedBlocksFor(studyDate),
            dayStartMinute = wakeMinute,
            dayEndMinute = sleepMinute,
        )
        if (issue != null) {
            _state.update { it.copy(placementIssue = issue) }
            return
        }
        viewModelScope.launch {
            val block = createManualBlock(pending, studyDate, startMinuteOfDay, minutes, System.currentTimeMillis()) ?: return@launch
            planRepository.upsert(block)
            attemptId?.let { reloadCanonical(it) }
            _state.update {
                it.copy(
                    sheet = HomeSheet.None,
                    pendingTopic = null,
                    customEndMinuteOfDay = null,
                    placementIssue = null,
                ).rebuild()
            }
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
            startSessionNow(session)
        }
    }

    fun startMissedSessionNow(sessionId: String) {
        viewModelScope.launch {
            val session = allSessions.firstOrNull { it.id == sessionId } ?: return@launch
            if (session.completed || session.runningEndsAtMs != null) return@launch
            startSessionNow(session)
        }
    }

    fun replanRestOfToday() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val missed = allSessions.filter {
                it.date == today && !it.completed && it.runningEndsAtMs == null &&
                    it.startMinuteOfDay + it.durationMinutes <= currentMinuteOfDay()
            }
            val fixedByDate = (0L..RECOVERY_HORIZON_DAYS.toLong()).associate { offset ->
                val date = today.plusDays(offset)
                date to fixedBlocksFor(date)
            }
            val placements = planMissedDayRecovery(
                sessions = allSessions,
                date = today,
                nowMinuteOfDay = currentMinuteOfDay(),
                fixedCommitmentsByDate = fixedByDate,
                dayStartMinute = wakeMinute,
                dayEndMinute = sleepMinute,
                horizonDays = RECOVERY_HORIZON_DAYS,
            )
            placements.forEach { placement ->
                missed.firstOrNull { it.id == placement.sessionId }?.let { session ->
                    persistReschedule(session, placement.date, placement.startMinuteOfDay)
                }
            }
            attemptId?.let { reloadCanonical(it) }
            _state.update {
                it.copy(
                    placementIssue = if (placements.size < missed.size) {
                        StudyPlacementIssue.NO_AVAILABLE_TIME
                    } else {
                        null
                    },
                ).rebuild()
            }
        }
    }

    private suspend fun beginSession(session: StudySessionRecord) {
        val updated = session.copy(
            runningEndsAtMs = System.currentTimeMillis() + session.durationMinutes * 60_000L,
        )
        allSessions = allSessions.map { if (it.id == updated.id) updated else it }
        _state.update {
            it.copy(
                placementIssue = null,
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

    private suspend fun startSessionNow(session: StudySessionRecord) {
        val today = LocalDate.now()
        if (session.date != today) {
            _state.update { it.copy(placementIssue = StudyPlacementIssue.OUTSIDE_DAY) }
            return
        }
        val nowMinute = currentMinuteOfDay()
        val issue = validateStudyPlacement(
            sessions = allSessions,
            date = today,
            startMinuteOfDay = nowMinute,
            durationMinutes = session.durationMinutes,
            fixedCommitments = fixedBlocksFor(today),
            dayStartMinute = wakeMinute,
            dayEndMinute = sleepMinute,
            excludingSessionId = session.id,
        )
        if (issue != null) {
            _state.update { it.copy(placementIssue = issue) }
            return
        }
        val moved = if (session.startMinuteOfDay == nowMinute) {
            session
        } else {
            persistReschedule(session, today, nowMinute)
        }
        beginSession(moved)
    }

    /** Reposition one block on the timeline without losing its canonical history. */
    fun rescheduleToNextSlot(sessionId: String) {
        viewModelScope.launch {
            val session = allSessions.firstOrNull { it.id == sessionId } ?: return@launch
            if (session.completed) return@launch
            val slotToday = findNextFreeSlot(
                allSessions,
                session.date,
                session.durationMinutes,
                currentMinuteOfDay(),
                fixedBlocksFor(session.date),
                wakeMinute,
                sleepMinute,
            )
            if (slotToday != null) {
                applyReschedule(session, session.date, slotToday)
                return@launch
            }
            val tomorrow = session.date.plusDays(1)
            val slotTomorrow = findNextFreeSlot(
                allSessions,
                tomorrow,
                session.durationMinutes,
                wakeMinute,
                fixedBlocksFor(tomorrow),
                wakeMinute,
                sleepMinute,
            )
            if (slotTomorrow != null) {
                applyReschedule(session, tomorrow, slotTomorrow)
            } else {
                _state.update { it.copy(placementIssue = StudyPlacementIssue.NO_AVAILABLE_TIME) }
            }
        }
    }

    fun rescheduleToTomorrowSameTime(sessionId: String) {
        viewModelScope.launch {
            val session = allSessions.firstOrNull { it.id == sessionId } ?: return@launch
            if (session.completed) return@launch
            val date = session.date.plusDays(1)
            val issue = validateStudyPlacement(
                sessions = allSessions,
                date = date,
                startMinuteOfDay = session.startMinuteOfDay,
                durationMinutes = session.durationMinutes,
                fixedCommitments = fixedBlocksFor(date),
                dayStartMinute = wakeMinute,
                dayEndMinute = sleepMinute,
                excludingSessionId = session.id,
            )
            if (issue == null) applyReschedule(session, date, session.startMinuteOfDay)
            else _state.update { it.copy(placementIssue = issue) }
        }
    }

    fun rescheduleToTime(sessionId: String, minuteOfDay: Int) {
        viewModelScope.launch {
            val session = allSessions.firstOrNull { it.id == sessionId } ?: return@launch
            if (session.completed) return@launch
            val issue = validateStudyPlacement(
                sessions = allSessions,
                date = session.date,
                startMinuteOfDay = minuteOfDay,
                durationMinutes = session.durationMinutes,
                fixedCommitments = fixedBlocksFor(session.date),
                dayStartMinute = wakeMinute,
                dayEndMinute = sleepMinute,
                excludingSessionId = session.id,
            )
            if (issue == null) applyReschedule(session, session.date, minuteOfDay)
            else _state.update { it.copy(placementIssue = issue) }
        }
    }

    fun skipSession(sessionId: String) {
        viewModelScope.launch {
            planRepository.skip(sessionId, System.currentTimeMillis())
            rollingPlanRepository.replenish()
            attemptId?.let { reloadCanonical(it) }
            _state.update { it.copy(placementIssue = null).rebuild() }
        }
    }

    private suspend fun applyReschedule(session: StudySessionRecord, date: LocalDate, startMinuteOfDay: Int) {
        persistReschedule(session, date, startMinuteOfDay)
        rollingPlanRepository.replenish()
        attemptId?.let { reloadCanonical(it) }
        _state.update { it.copy(placementIssue = null).rebuild() }
    }

    private suspend fun persistReschedule(
        session: StudySessionRecord,
        date: LocalDate,
        startMinuteOfDay: Int,
    ): StudySessionRecord {
        val normalized = planRepository.byId(session.id) ?: return session
        val replacementId = UUID.randomUUID().toString()
        planRepository.reschedule(
            original = normalized,
            newDate = date,
            newStartMinuteOfDay = startMinuteOfDay,
            newBlockId = replacementId,
            nowMs = System.currentTimeMillis(),
        )
        val updated = session.copy(id = replacementId, date = date, startMinuteOfDay = startMinuteOfDay, runningEndsAtMs = null, completed = false)
        allSessions = allSessions.filterNot { it.id == session.id } + updated
        return updated
    }

    private fun createManualBlock(
        pending: PendingTopic,
        date: LocalDate,
        startMinuteOfDay: Int,
        minutes: Int,
        nowMs: Long,
    ): StudyPlanBlock? {
        val id = attemptId ?: return null
        return StudyPlanBlock(
            id = UUID.randomUUID().toString(),
            attemptId = id,
            nodeId = pending.nodeKey.takeIf(String::isNotBlank),
            subjectId = pending.subjectId.takeIf(String::isNotBlank),
            customTitle = pending.title.takeIf { pending.nodeKey.isBlank() },
            activityType = pending.activityType,
            scheduledDate = date,
            startMinuteOfDay = startMinuteOfDay,
            plannedMinutes = minutes,
            status = PlanBlockStatus.PLANNED,
            source = if (pending.activityType == StudyActivityType.REVISION) PlanBlockSource.REVISION_ENGINE else PlanBlockSource.MANUAL,
            rescheduledFromId = null,
            replacedById = null,
            createdAtEpochMs = nowMs,
            updatedAtEpochMs = nowMs,
        )
    }

    private fun fixedBlocksFor(date: LocalDate): List<FixedCommitmentBlock> {
        val free = effectiveWindowsFor(date, weeklyAvailability, availabilityOverrides)
            .sortedBy { it.first }
        val fixed = mutableListOf<FixedCommitmentBlock>()
        var cursor = wakeMinute
        free.forEachIndexed { index, (start, end) ->
            if (start > cursor) {
                fixed += FixedCommitmentBlock("fixed_${date}_$index", "other", cursor, start, "Fixed time")
            }
            cursor = maxOf(cursor, end)
        }
        if (cursor < sleepMinute) {
            fixed += FixedCommitmentBlock("fixed_${date}_end", "other", cursor, sleepMinute, "Fixed time")
        }
        return fixed
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
                dayBudgetMinutes = 0,
                missedDayRecovery = null,
            )
        }

        val today = LocalDate.now()
        val daySessions = allSessions.filter { it.date == selectedDate }
        val budgetMinutes = todayBudgetMinutes(weekdayHours, weekendHours, selectedDate)
        val doneMins = daySessions.filter { it.completed }.sumOf { it.durationMinutes }
        val percent = if (budgetMinutes > 0) {
            min(100, ((doneMins.toFloat() / budgetMinutes) * 100).toInt())
        } else {
            0
        }
        val selectedIsToday = selectedDate == today
        val nowMinute = currentMinuteOfDay()
        val missedSessions = if (selectedIsToday) {
            daySessions.filter {
                !it.completed && it.runningEndsAtMs == null &&
                    it.startMinuteOfDay + it.durationMinutes <= nowMinute
            }.sortedBy { it.startMinuteOfDay }
        } else {
            emptyList()
        }
        val revisions = if (selectedIsToday && missedSessions.isEmpty()) {
            dueRevisions
        } else {
            emptyList()
        }
        val builtTimeline = buildDayTimeline(
            sessions = daySessions,
            sections = sections,
            pendingRevisions = revisions,
            date = selectedDate,
            today = today,
            nowMinuteOfDay = nowMinute,
            fixedCommitments = fixedBlocksFor(selectedDate),
            dayStartMinute = wakeMinute,
            dayEndMinute = sleepMinute,
        )
        val plannedMinutes = builtTimeline.sumOf { entry ->
            (entry as? DayTimelineEntry.Study)?.block?.durationMinutes ?: 0
        }
        val nextMissed = missedSessions.firstOrNull()
        val recovery = nextMissed?.let { session ->
            val availableTodayMinutes = builtTimeline
                .filterIsInstance<DayTimelineEntry.Gap>()
                .sumOf { gap -> (gap.endMinuteOfDay - maxOf(gap.startMinuteOfDay, nowMinute)).coerceAtLeast(0) }
            MissedDayRecoveryUi(
                missedCount = missedSessions.size,
                availableTodayMinutes = availableTodayMinutes,
                nextSessionId = session.id,
                nextSessionTitle = session.title,
                canStartNow = validateStudyPlacement(
                    sessions = allSessions,
                    date = today,
                    startMinuteOfDay = nowMinute,
                    durationMinutes = session.durationMinutes,
                    fixedCommitments = fixedBlocksFor(today),
                    dayStartMinute = wakeMinute,
                    dayEndMinute = sleepMinute,
                    excludingSessionId = session.id,
                ) == null,
            )
        }

        fun dayUi(date: LocalDate): WeekDayUi {
            val dayBudget = todayBudgetMinutes(weekdayHours, weekendHours, date)
            val dayDone = allSessions
                .filter { it.date == date && it.completed }
                .sumOf { it.durationMinutes }
            return WeekDayUi(
                date = date,
                dayOfMonth = date.dayOfMonth,
                selected = date == selectedDate,
                status = weekStatusForDayMinutes(date, today, dayDone, dayBudget),
            )
        }

        val monthAnchor = selectedDate.withDayOfMonth(1)
        val leadingBlanks = monthAnchor.dayOfWeek.value - 1
        val monthCells = mutableListOf<WeekDayUi?>()
        repeat(leadingBlanks) { monthCells += null }
        for (day in 0 until monthAnchor.lengthOfMonth()) {
            val date = monthAnchor.plusDays(day.toLong())
            monthCells += dayUi(date)
        }

        return copy(
            weekDays = weekAround(selectedDate).map(::dayUi),
            monthDays = monthCells,
            selectedIsToday = selectedIsToday,
            selectedDayLabel = "",
            dayBudgetMinutes = budgetMinutes,
            completionPercent = percent,
            dayTimeline = builtTimeline,
            revisionItems = revisions,
            completedTodayMinutes = doneMins,
            plannedTodayMinutes = plannedMinutes,
            missedDayRecovery = recovery,
        )
    }

    class Factory(
        private val examPackRepository: ExamPackRepository,
        private val attemptRepository: AttemptRepository,
        private val availabilityRepository: AvailabilityRepository,
        private val planRepository: PlanRepository,
        private val studySessionRepository: StudySessionRepository,
        private val topicProgressRepository: TopicProgressRepository,
        private val studyPreferenceRepository: StudyPreferenceRepository,
        private val revisionRepository: RevisionRepository,
        private val rollingPlanRepository: RollingPlanRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(
                examPackRepository,
                attemptRepository,
                availabilityRepository,
                planRepository,
                studySessionRepository,
                topicProgressRepository,
                studyPreferenceRepository,
                revisionRepository,
                rollingPlanRepository,
            ) as T
    }
}

private const val RECOVERY_HORIZON_DAYS = 7

private fun ExamPack.toPickerSections(): List<SyllabusSection> = subjects.map { subject ->
    SyllabusSection(
        name = subject.name,
        questions = subject.questions ?: 0,
        id = subject.id,
        topics = subject.nodes.map { node ->
            fun convert(value: com.exam.assistant.domain.SyllabusNode): SyllabusTopicNode = SyllabusTopicNode(
                name = value.title,
                hours = value.estimatedMinutes?.div(60.0),
                children = value.children.map(::convert),
                id = value.id,
            )
            convert(node)
        },
    )
}
