package com.exam.assistant.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.common.AppDispatchers
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.SavedPlan
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.AvailabilityRepository
import com.exam.assistant.core.data.repo.PlanRepository
import com.exam.assistant.core.data.repo.RollingPlanRepository
import com.exam.assistant.core.data.repo.StudyPreferenceRepository
import com.exam.assistant.core.data.repo.TargetSyllabusRepository
import com.exam.assistant.core.data.repo.TopicProgressRepository
import com.exam.assistant.domain.ExamAttempt
import com.exam.assistant.domain.ExamAttemptStatus
import com.exam.assistant.domain.ExamPack
import com.exam.assistant.domain.NEET_EXAM_ID
import com.exam.assistant.domain.StudyPreferences
import com.exam.assistant.domain.TargetNodeState
import com.exam.assistant.domain.TopicProgressStatus
import com.exam.assistant.domain.WeeklyAvailability
import com.exam.assistant.domain.availableHours
import com.exam.assistant.domain.cushion
import com.exam.assistant.domain.leafIds
import com.exam.assistant.domain.totalMinutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.net.URLEncoder
import java.net.URLDecoder
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

class OnboardingViewModel(
    private val planStore: PlanStore,
    private val examPackRepository: ExamPackRepository,
    private val attemptRepository: AttemptRepository,
    private val availabilityRepository: AvailabilityRepository,
    private val studyPreferenceRepository: StudyPreferenceRepository,
    private val targetSyllabusRepository: TargetSyllabusRepository,
    private val topicProgressRepository: TopicProgressRepository,
    private val planRepository: PlanRepository,
    private val rollingPlanRepository: RollingPlanRepository,
    private val dispatchers: AppDispatchers,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private var currentPack: ExamPack? = null
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()
    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()
    private val _openOrganiseAfterFinish = MutableStateFlow(false)
    val openOrganiseAfterFinish: StateFlow<Boolean> = _openOrganiseAfterFinish.asStateFlow()

    init {
        hydrate()
        viewModelScope.launch {
            state.drop(1).collect { current ->
                if (!current.loading && !current.finishing) savedStateHandle[DRAFT_KEY] = encodeDraft(current)
            }
        }
    }

    fun back() {
        _state.update { current ->
            current.step.previous()?.let { current.copy(step = it, finishFailed = false) } ?: current
        }
    }

    fun continueFromCurrent() {
        val current = _state.value
        if (!canContinue(current)) return
        when (current.step) {
            OnboardingStep.Plan -> finish(current)
            OnboardingStep.Syllabus -> _state.update {
                it.copy(step = OnboardingStep.Plan, cushion = computeCushion(it))
            }
            else -> advance()
        }
    }

    fun canContinue(state: OnboardingUiState = _state.value): Boolean = when (state.step) {
        OnboardingStep.Coaching -> state.coachingId.isNotBlank()
        OnboardingStep.Commitments -> commitmentIssues(state).isEmpty()
        OnboardingStep.Hours -> capacityIssues(state).isEmpty()
        OnboardingStep.Syllabus -> !state.useProvidedSyllabus || state.selectedChapterIds.isNotEmpty()
        OnboardingStep.Plan -> !state.finishing
        else -> true
    }

    fun selectExam(examId: String) {
        if (!ExamCatalog.contains(examId)) return
        _state.update {
            it.copy(
                examId = examId,
                useProvidedSyllabus = ExamCatalog.hasBundledSyllabus(examId),
                selectedChapterIds = emptySet(),
                syllabusSubjects = emptyList(),
            )
        }
        loadPackFor(examId)
    }

    fun selectCoaching(id: String) {
        if (id !in setOf("self", "school", "coaching")) return
        _state.update {
            it.copy(
                coachingId = id,
                commitments = commitmentsForStudyMode(it.commitments, id),
            )
        }
    }

    fun setTargetDate(date: LocalDate) {
        val today = LocalDate.now()
        _state.update {
            it.copy(targetDate = date.coerceIn(today.plusDays(7), today.plusDays(730))).refreshCushionIfNeeded()
        }
    }

    fun addCommitment(kind: String) {
        val preset = commitmentPreset(kind) ?: return
        _state.update { it.copy(commitments = it.commitments + preset.copy(id = UUID.randomUUID().toString())) }
    }

    fun removeCommitment(id: String) {
        _state.update { it.copy(commitments = it.commitments.filterNot { item -> item.id == id }) }
    }

    fun renameCommitment(id: String, label: String) {
        updateCommitment(id) { commitment ->
            commitment.copy(customLabel = label.trim().take(40).ifBlank { null })
        }
    }

    fun toggleCommitmentDay(id: String, day: Int) {
        if (day !in 0..6) return
        updateCommitment(id) { commitment ->
            commitment.copy(
                days = if (day in commitment.days) commitment.days - day else commitment.days + day,
            )
        }
    }

    fun setCommitmentStart(id: String, minute: Int) {
        updateCommitment(id) { commitment ->
            val start = minute.coerceIn(0, 23 * 60 + 30)
            commitment.copy(startMinute = start, endMinute = maxOf(start + 30, commitment.endMinute).coerceAtMost(24 * 60))
        }
    }

    fun setCommitmentEnd(id: String, minute: Int) {
        updateCommitment(id) { commitment ->
            commitment.copy(endMinute = minute.coerceIn(commitment.startMinute + 30, 24 * 60))
        }
    }

    fun setWakeMinute(minute: Int) {
        _state.update { it.copy(wakeMinute = minute.coerceIn(0, 23 * 60)) }
    }

    fun setSleepMinute(minute: Int) {
        _state.update { it.copy(sleepMinute = minute.coerceIn(60, 24 * 60)) }
    }

    fun selectWork(id: String) {
        val shape = DAY_SHAPES.firstOrNull { it.id == id } ?: return
        _state.update {
            it.copy(workId = id, weekdayHours = shape.weekdayHours, weekendHours = shape.weekendHours)
        }
    }

    fun setWeekdayHours(hours: Float) {
        _state.update { it.copy(weekdayHours = hours.halfHourStep()).refreshCushionIfNeeded() }
    }

    fun setWeekendHours(hours: Float) {
        _state.update { it.copy(weekendHours = hours.halfHourStep()).refreshCushionIfNeeded() }
    }

    fun setStudyPlace(place: String) {
        _state.update { it.copy(studyPlace = place) }
    }

    fun setUseProvidedSyllabus(useProvided: Boolean) {
        if (useProvided && !ExamCatalog.hasBundledSyllabus(_state.value.examId)) return
        _state.update {
            it.copy(
                useProvidedSyllabus = useProvided,
                selectedChapterIds = if (useProvided) currentPack.topLevelNodeIds() else emptySet(),
            )
        }
    }

    fun toggleTargetChapter(chapterId: String) {
        _state.update {
            it.copy(
                selectedChapterIds = if (chapterId in it.selectedChapterIds) {
                    it.selectedChapterIds - chapterId
                } else {
                    it.selectedChapterIds + chapterId
                },
            )
        }
    }

    fun toggleTargetSubject(subjectId: String) {
        val subjectChapterIds = _state.value.syllabusSubjects
            .firstOrNull { it.id == subjectId }
            ?.chapters
            ?.map { it.id }
            ?.toSet()
            .orEmpty()
        if (subjectChapterIds.isEmpty()) return
        _state.update { current ->
            val allSelected = subjectChapterIds.all { it in current.selectedChapterIds }
            current.copy(
                selectedChapterIds = if (allSelected) {
                    current.selectedChapterIds - subjectChapterIds
                } else {
                    current.selectedChapterIds + subjectChapterIds
                },
            )
        }
    }

    fun dismissFinishError() {
        _state.update { it.copy(finishFailed = false) }
    }

    fun totalHours(state: OnboardingUiState = _state.value): Int =
        availableHours(state.daysUntilTarget, state.weekdayHours.toDouble(), state.weekendHours.toDouble())

    fun commitmentIssues(state: OnboardingUiState = _state.value): List<CommitmentIssue> =
        commitmentIssuesFor(state)

    fun capacityIssues(state: OnboardingUiState = _state.value): List<CapacityIssue> = capacityIssuesFor(state)

    private fun loadPackFor(examId: String) {
        viewModelScope.launch {
            val pack = examPackRepository.examPackFor(examId)
            if (_state.value.examId != examId) return@launch
            currentPack = pack
            val subjects = pack.subjects.map { subject ->
                OnboardingSyllabusSubject(
                    id = subject.id,
                    name = subject.name,
                    chapters = subject.nodes.map { node ->
                        OnboardingSyllabusChapter(
                            id = node.id,
                            name = node.title,
                            classNumber = node.id.split('.').getOrNull(2)?.toIntOrNull(),
                        )
                    },
                )
            }
            _state.update { current ->
                if (current.examId != examId) {
                    current
                } else {
                    current.copy(
                        loading = false,
                        syllabusSubjects = subjects,
                        selectedChapterIds = if (current.useProvidedSyllabus) {
                            current.selectedChapterIds.ifEmpty { pack.topLevelNodeIds() }
                        } else {
                            emptySet()
                        },
                    )
                }
            }
        }
    }

    private fun hydrate() {
        viewModelScope.launch {
            val pack = examPackRepository.examPackFor(NEET_EXAM_ID)
            val attempt = attemptRepository.activeAttempt()?.takeIf { it.examId == NEET_EXAM_ID }
            val saved = planStore.load()?.takeIf { it.examId == NEET_EXAM_ID }
            val preferences = attempt?.let { studyPreferenceRepository.forAttempt(it.id) }
            val overrides = attempt?.let { targetSyllabusRepository.overridesFor(it.id) }.orEmpty()
            val coveredLeafIds = attempt?.let { active ->
                topicProgressRepository.allOnce(active.id)
                    .filter { it.status == TopicProgressStatus.COVERED }
                    .mapTo(mutableSetOf()) { it.nodeId }
            }.orEmpty()
            val excludedIds = overrides.filter { it.state == TargetNodeState.EXCLUDED }.mapTo(mutableSetOf()) { it.nodeId }
            val subjects = pack.subjects.map { subject ->
                OnboardingSyllabusSubject(
                    id = subject.id,
                    name = subject.name,
                    chapters = subject.nodes.map { node ->
                        OnboardingSyllabusChapter(
                            id = node.id,
                            name = node.title,
                            classNumber = node.id.split('.').getOrNull(2)?.toIntOrNull(),
                        )
                    },
                )
            }
            currentPack = pack
            _state.update { current ->
                val hydrated = current.copy(
                    loading = false,
                    editMode = attempt != null,
                    examId = NEET_EXAM_ID,
                    coachingId = saved?.coachingId?.ifBlank { "self" } ?: "",
                    commitments = saved?.commitments?.mapNotNull(::decodeCommitment)?.ifEmpty { defaultCommitments() }
                        ?: defaultCommitments(),
                    wakeMinute = saved?.wakeMinute ?: current.wakeMinute,
                    sleepMinute = saved?.sleepMinute ?: current.sleepMinute,
                    targetDate = attempt?.targetCompletionDate
                        ?: saved?.targetDateEpochDay?.let(LocalDate::ofEpochDay)
                        ?: current.targetDate,
                    workId = saved?.workId ?: current.workId,
                    weekdayHours = preferences?.weekdayTargetMinutes?.div(60f) ?: saved?.weekdayHours ?: current.weekdayHours,
                    weekendHours = preferences?.weekendTargetMinutes?.div(60f) ?: saved?.weekendHours ?: current.weekendHours,
                    studyPlace = preferences?.defaultStudyPlace ?: saved?.studyPlace ?: current.studyPlace,
                    useProvidedSyllabus = saved?.useProvidedSyllabus ?: true,
                    selectedChapterIds = pack.topLevelNodeIds() - excludedIds,
                    coveredChapterIds = pack.subjects.flatMap { it.nodes }
                        .filter { chapter -> chapter.leafIds().isNotEmpty() && chapter.leafIds().all { it in coveredLeafIds } }
                        .mapTo(mutableSetOf()) { it.id },
                    syllabusSubjects = subjects,
                )
                savedStateHandle.get<String>(DRAFT_KEY)?.let { decodeDraft(it, hydrated) } ?: hydrated
            }
        }
    }

    private fun updateCommitment(id: String, transform: (OnboardingCommitment) -> OnboardingCommitment) {
        _state.update { current ->
            current.copy(commitments = current.commitments.map { if (it.id == id) transform(it) else it })
        }
    }

    private fun advance() {
        _state.update { current -> current.step.next()?.let { current.copy(step = it) } ?: current }
    }

    private fun OnboardingUiState.refreshCushionIfNeeded(): OnboardingUiState =
        if (step == OnboardingStep.Plan) copy(cushion = computeCushion(this)) else this

    private fun computeCushion(state: OnboardingUiState) = cushion(
        rawHours = if (state.useProvidedSyllabus) {
            currentPack?.subjects
                ?.flatMap { it.nodes }
                ?.filter { it.id in state.selectedChapterIds }
                ?.sumOf { it.totalMinutes() }
                ?.div(60.0)
                ?: 0.0
        } else {
            0.0
        },
        days = state.daysUntilTarget,
        weekdayHours = state.weekdayHours.toDouble(),
        weekendHours = state.weekendHours.toDouble(),
    )

    private fun finish(state: OnboardingUiState) {
        if (state.finishing) return
        _state.update { it.copy(finishing = true, finishFailed = false) }
        viewModelScope.launch {
            val existingAttempt = attemptRepository.activeAttempt()
            val attemptId = existingAttempt?.id ?: UUID.randomUUID().toString()
            val previousPlan = if (existingAttempt != null) {
                planRepository.planBetween(attemptId, LocalDate.now().minusYears(2), LocalDate.now().plusYears(2))
            } else {
                emptyList()
            }
            val previousAvailability = if (existingAttempt != null) availabilityRepository.weeklyFor(attemptId) else emptyList()
            val previousPreferences = if (existingAttempt != null) studyPreferenceRepository.forAttempt(attemptId) else null
            val previousOverrides = if (existingAttempt != null) targetSyllabusRepository.overridesFor(attemptId) else emptyList()
            try {
                val now = System.currentTimeMillis()
                val pack = currentPack?.takeIf { it.examId == state.examId }
                    ?: examPackRepository.examPackFor(state.examId)
                val attempt = ExamAttempt(
                    id = attemptId,
                    examId = state.examId,
                    syllabusVersion = pack.syllabusVersion,
                    examDate = state.targetDate,
                    targetCompletionDate = state.targetDate,
                    createdAtEpochMs = existingAttempt?.createdAtEpochMs ?: now,
                    updatedAtEpochMs = now,
                    status = ExamAttemptStatus.ACTIVE,
                )

                attemptRepository.upsert(attempt)

                val availability = freeWindowsFor(attemptId, state)
                availabilityRepository.replaceWeekly(attemptId, availability)
                studyPreferenceRepository.upsert(
                    StudyPreferences(
                        attemptId = attemptId,
                        weekdayTargetMinutes = (state.weekdayHours * 60).roundToInt(),
                        weekendTargetMinutes = (state.weekendHours * 60).roundToInt(),
                        autoScheduleRevision = true,
                        defaultStudyPlace = state.studyPlace.trim().ifBlank { null },
                    ),
                )

                targetSyllabusRepository.overridesFor(attemptId).forEach { override ->
                    targetSyllabusRepository.clearOverride(attemptId, override.nodeId)
                }
                val excludedNodes = pack.subjects
                    .flatMap { it.nodes }
                    .filter { !state.useProvidedSyllabus || it.id !in state.selectedChapterIds }
                excludedNodes.forEach { node ->
                    targetSyllabusRepository.setState(attemptId, node.id, TargetNodeState.EXCLUDED, now)
                }
                planStore.save(
                    SavedPlan(
                        examId = state.examId,
                        daysUntilExam = state.daysUntilTarget,
                        workId = state.workId,
                        weekdayHours = state.weekdayHours,
                        weekendHours = state.weekendHours,
                        studyPlace = state.studyPlace.trim(),
                        targetDateEpochDay = state.targetDate.toEpochDay(),
                        coachingId = state.coachingId,
                        wakeMinute = state.wakeMinute,
                        sleepMinute = state.sleepMinute,
                        useProvidedSyllabus = state.useProvidedSyllabus,
                        coveredSubjectIds = emptySet(),
                        commitments = state.commitments.map(::encodeCommitment).toSet(),
                    ),
                )
                rollingPlanRepository.replenish(fromDate = LocalDate.now(), nowMs = now)
                savedStateHandle.remove<String>(DRAFT_KEY)
                _state.update { it.copy(finishing = false) }
                _openOrganiseAfterFinish.value = !state.useProvidedSyllabus || state.selectedChapterIds.isEmpty()
                _finished.value = true
            } catch (_: Exception) {
                runCatching { planRepository.replaceAllForAttempt(attemptId, previousPlan) }
                if (existingAttempt == null) {
                    runCatching { attemptRepository.deleteAttemptAndAllData(attemptId) }
                } else {
                    runCatching { availabilityRepository.replaceWeekly(attemptId, previousAvailability) }
                    runCatching { previousPreferences?.let { studyPreferenceRepository.upsert(it) } }
                    runCatching {
                        targetSyllabusRepository.overridesFor(attemptId).forEach { override ->
                            targetSyllabusRepository.clearOverride(attemptId, override.nodeId)
                        }
                        previousOverrides.forEach { override ->
                            targetSyllabusRepository.setState(
                                attemptId,
                                override.nodeId,
                                override.state,
                                override.updatedAtEpochMs,
                            )
                        }
                    }
                    runCatching { attemptRepository.upsert(existingAttempt) }
                }
                _state.update { it.copy(finishing = false, finishFailed = true) }
            }
        }
    }

    class Factory(
        private val planStore: PlanStore,
        private val examPackRepository: ExamPackRepository,
        private val attemptRepository: AttemptRepository,
        private val availabilityRepository: AvailabilityRepository,
        private val studyPreferenceRepository: StudyPreferenceRepository,
        private val targetSyllabusRepository: TargetSyllabusRepository,
        private val topicProgressRepository: TopicProgressRepository,
        private val planRepository: PlanRepository,
        private val rollingPlanRepository: RollingPlanRepository,
        private val dispatchers: AppDispatchers,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = OnboardingViewModel(
            planStore,
            examPackRepository,
            attemptRepository,
            availabilityRepository,
            studyPreferenceRepository,
            targetSyllabusRepository,
            topicProgressRepository,
            planRepository,
            rollingPlanRepository,
            dispatchers,
            extras.createSavedStateHandle(),
        ) as T
    }

    private companion object {
        const val DRAFT_KEY = "onboarding_draft_v1"
    }
}

sealed interface CommitmentIssue {
    val commitmentId: String?

    data object WakeAfterSleep : CommitmentIssue {
        override val commitmentId: String? = null
    }

    data class NoDays(override val commitmentId: String, val labelRes: Int) : CommitmentIssue
    data class InvalidRange(override val commitmentId: String, val labelRes: Int) : CommitmentIssue
    data class OutsideWakingHours(override val commitmentId: String, val labelRes: Int) : CommitmentIssue
}

sealed interface CapacityIssue {
    data class Weekday(val freeHours: Float) : CapacityIssue
    data class Weekend(val freeHours: Float) : CapacityIssue
}

internal fun capacityIssuesFor(state: OnboardingUiState): List<CapacityIssue> = buildList {
    val weekdayFree = (1..5).minOf { day -> freeMinutesOnDay(day, state) } / 60f
    val weekendFree = listOf(0, 6).minOf { day -> freeMinutesOnDay(day, state) } / 60f
    if (state.weekdayHours > weekdayFree) add(CapacityIssue.Weekday(weekdayFree))
    if (state.weekendHours > weekendFree) add(CapacityIssue.Weekend(weekendFree))
}

private fun Float.halfHourStep(): Float = (this * 2).roundToInt() / 2f

private fun commitmentPreset(kind: String): OnboardingCommitment? = when (kind) {
    "school" -> OnboardingCommitment("", kind, R.string.onboarding_commitment_school, 8 * 60, 14 * 60, (1..6).toSet())
    "coaching" -> OnboardingCommitment("", kind, R.string.onboarding_commitment_coaching, 16 * 60, 19 * 60, (1..6).toSet())
    "lecture" -> OnboardingCommitment("", kind, R.string.onboarding_commitment_lecture, 19 * 60, 21 * 60, (1..6).toSet())
    "tuition" -> OnboardingCommitment("", kind, R.string.onboarding_commitment_tuition, 18 * 60, 19 * 60 + 30, (1..6).toSet())
    "work" -> OnboardingCommitment("", kind, R.string.onboarding_commitment_job, 10 * 60, 18 * 60, (1..6).toSet())
    "meal" -> OnboardingCommitment("", kind, R.string.onboarding_commitment_meal, 13 * 60, 14 * 60, (1..6).toSet())
    "commute" -> OnboardingCommitment("", kind, R.string.onboarding_commitment_commute, 7 * 60 + 30, 8 * 60 + 30, (1..6).toSet())
    "other" -> OnboardingCommitment("", kind, R.string.onboarding_commitment_other, 17 * 60, 18 * 60, (1..6).toSet())
    else -> null
}

private fun mergedOccupiedWindows(commitments: List<OnboardingCommitment>): List<Pair<Int, Int>> {
    val merged = mutableListOf<Pair<Int, Int>>()
    commitments.sortedBy { it.startMinute }.forEach { commitment ->
        val last = merged.lastOrNull()
        if (last == null || commitment.startMinute > last.second) {
            merged += commitment.startMinute to commitment.endMinute
        } else {
            merged[merged.lastIndex] = last.first to maxOf(last.second, commitment.endMinute)
        }
    }
    return merged
}

internal fun freeMinutesOnDay(day: Int, state: OnboardingUiState): Int {
    val occupied = mergedOccupiedWindows(state.commitments.filter { day in it.days })
        .sumOf { (start, end) -> end - start }
    return (state.sleepMinute - state.wakeMinute - occupied).coerceAtLeast(0)
}

internal fun commitmentIssuesFor(state: OnboardingUiState): List<CommitmentIssue> {
    val issues = mutableListOf<CommitmentIssue>()
    if (state.sleepMinute <= state.wakeMinute) issues += CommitmentIssue.WakeAfterSleep
    state.commitments.forEach { commitment ->
        if (commitment.days.isEmpty()) issues += CommitmentIssue.NoDays(commitment.id, commitment.labelRes)
        if (commitment.endMinute <= commitment.startMinute) {
            issues += CommitmentIssue.InvalidRange(commitment.id, commitment.labelRes)
        }
        if (commitment.startMinute < state.wakeMinute || commitment.endMinute > state.sleepMinute) {
            issues += CommitmentIssue.OutsideWakingHours(commitment.id, commitment.labelRes)
        }
    }
    return issues.distinct()
}

internal fun freeWindowsFor(attemptId: String, state: OnboardingUiState): List<WeeklyAvailability> =
    DayOfWeek.entries.flatMap { dayOfWeek ->
        val prototypeDay = dayOfWeek.value % 7
        val occupied = mergedOccupiedWindows(
            state.commitments
                .filter { prototypeDay in it.days }
                .map {
                    it.copy(
                        startMinute = maxOf(it.startMinute, state.wakeMinute),
                        endMinute = minOf(it.endMinute, state.sleepMinute),
                    )
                }
                .filter { it.endMinute > it.startMinute },
        )
        val gaps = mutableListOf<Pair<Int, Int>>()
        var cursor = state.wakeMinute
        occupied.forEach { (start, end) ->
            if (start > cursor) gaps += cursor to start
            cursor = maxOf(cursor, end)
        }
        if (cursor < state.sleepMinute) gaps += cursor to state.sleepMinute
        gaps.mapIndexed { index, (start, end) ->
            WeeklyAvailability(
                id = "onboarding_${attemptId}_${dayOfWeek.name}_$index",
                attemptId = attemptId,
                dayOfWeek = dayOfWeek,
                startMinuteOfDay = start,
                endMinuteOfDay = end,
            )
        }
    }

private fun encodeCommitment(commitment: OnboardingCommitment): String = listOf(
    commitment.id,
    commitment.kind,
    commitment.startMinute,
    commitment.endMinute,
    commitment.days.sorted().joinToString(","),
    URLEncoder.encode(commitment.customLabel.orEmpty(), Charsets.UTF_8.name()),
).joinToString("|")

private fun decodeCommitment(encoded: String): OnboardingCommitment? {
    val parts = encoded.split('|')
    if (parts.size < 5) return null
    val kind = parts[1]
    val label = commitmentPreset(kind)?.labelRes ?: R.string.onboarding_commitment_other
    val start = parts[2].toIntOrNull() ?: return null
    val end = parts[3].toIntOrNull() ?: return null
    val days = parts[4].split(',').mapNotNull(String::toIntOrNull).filter { it in 0..6 }.toSet()
    val custom = parts.getOrNull(5)?.let { runCatching { URLDecoder.decode(it, Charsets.UTF_8.name()) }.getOrNull() }
        ?.trim()?.ifBlank { null }
    return OnboardingCommitment(parts[0], kind, label, start, end, days, custom)
}

private fun ExamPack?.topLevelNodeIds(): Set<String> =
    this?.subjects?.flatMap { it.nodes }?.map { it.id }?.toSet().orEmpty()

private fun encodeDraft(state: OnboardingUiState): String = JSONObject().apply {
    put("step", state.step.name)
    put("coaching", state.coachingId)
    put("wake", state.wakeMinute)
    put("sleep", state.sleepMinute)
    put("date", state.targetDate.toEpochDay())
    put("work", state.workId)
    put("weekday", state.weekdayHours.toDouble())
    put("weekend", state.weekendHours.toDouble())
    put("place", state.studyPlace)
    put("provided", state.useProvidedSyllabus)
    put("selected", JSONArray(state.selectedChapterIds.toList()))
    put("commitments", JSONArray(state.commitments.map(::encodeCommitment)))
}.toString()

private fun decodeDraft(raw: String, base: OnboardingUiState): OnboardingUiState = runCatching {
    val json = JSONObject(raw)
    fun strings(name: String): Set<String> {
        val array = json.optJSONArray(name) ?: return emptySet()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.toSet()
    }
    val restoredCommitments = strings("commitments").mapNotNull(::decodeCommitment)
    base.copy(
        step = runCatching { OnboardingStep.valueOf(json.optString("step")) }.getOrDefault(base.step),
        coachingId = json.optString("coaching", base.coachingId),
        wakeMinute = json.optInt("wake", base.wakeMinute),
        sleepMinute = json.optInt("sleep", base.sleepMinute),
        targetDate = LocalDate.ofEpochDay(json.optLong("date", base.targetDate.toEpochDay())),
        workId = json.optString("work", base.workId),
        weekdayHours = json.optDouble("weekday", base.weekdayHours.toDouble()).toFloat(),
        weekendHours = json.optDouble("weekend", base.weekendHours.toDouble()).toFloat(),
        studyPlace = json.optString("place", base.studyPlace),
        useProvidedSyllabus = json.optBoolean("provided", base.useProvidedSyllabus),
        selectedChapterIds = strings("selected").ifEmpty { base.selectedChapterIds },
        commitments = restoredCommitments.ifEmpty { base.commitments },
    )
}.getOrDefault(base)
