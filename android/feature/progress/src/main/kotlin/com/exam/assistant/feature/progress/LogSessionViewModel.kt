package com.exam.assistant.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.StudySessionRepository
import com.exam.assistant.domain.MANUAL_SESSION_MAX_MINUTES
import com.exam.assistant.domain.MANUAL_SESSION_MIN_MINUTES
import com.exam.assistant.domain.MANUAL_SESSION_STEP_MINUTES
import com.exam.assistant.domain.StudyActivityType
import com.exam.assistant.domain.StudySession
import com.exam.assistant.domain.chapterOf
import com.exam.assistant.domain.currentMinuteOfDay
import com.exam.assistant.domain.editedWith
import com.exam.assistant.domain.findSubjectOf
import com.exam.assistant.domain.manualStudySession
import com.exam.assistant.domain.toEntry
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Logs a session studied away from the timer, or edits/deletes a past one when [sessionId] is set.
 * Nothing here computes a metric: the session is written and every screen re-reads it.
 */
class LogSessionViewModel(
    private val sessionId: String?,
    private val initialDate: LocalDate?,
    private val examPackRepository: ExamPackRepository,
    private val attemptRepository: AttemptRepository,
    private val studySessionRepository: StudySessionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LogSessionUiState(isEdit = sessionId != null))
    val state: StateFlow<LogSessionUiState> = _state.asStateFlow()

    private var attemptId: String? = null
    private var original: StudySession? = null
    private var originalChapterId: String? = null

    init { load() }

    private fun load() = viewModelScope.launch {
        try {
            val attempt = attemptRepository.activeAttempt()
            if (attempt == null) {
                _state.update { it.copy(loading = false, unavailable = true) }
                return@launch
            }
            attemptId = attempt.id
            val pack = examPackRepository.examPackFor(attempt.examId, attempt.id)
            val subjects = pack?.subjects.orEmpty().sortedBy { it.order }.map { subject ->
                SubjectChoice(
                    id = subject.id,
                    name = subject.name,
                    chapters = subject.nodes.sortedBy { it.order }.map { ChapterChoice(it.id, it.title) },
                )
            }
            val session = sessionId?.let { studySessionRepository.byId(it) }
            if (sessionId != null && session == null) {
                _state.update { it.copy(loading = false, unavailable = true) }
                return@launch
            }
            original = session
            if (session != null) {
                val entry = session.toEntry()
                val chapter = entry.nodeId?.let { pack?.chapterOf(it) }
                originalChapterId = chapter?.id ?: entry.nodeId
                _state.update {
                    it.copy(
                        loading = false,
                        subjects = subjects,
                        subjectId = entry.subjectId ?: entry.nodeId?.let { id -> pack?.findSubjectOf(id)?.id },
                        chapterId = chapter?.id ?: entry.nodeId,
                        activityType = entry.activityType,
                        durationMinutes = entry.durationMinutes,
                        date = entry.date,
                        startMinuteOfDay = entry.startMinuteOfDay,
                    )
                }
            } else {
                val today = LocalDate.now()
                val date = (initialDate ?: today).coerceAtMost(today)
                // Default start: an hour before now today, early evening on a past day.
                val start = if (date == today) (currentMinuteOfDay() - DEFAULT_LOOKBACK_MINUTES).coerceAtLeast(0) else PAST_DAY_START
                _state.update {
                    it.copy(
                        loading = false,
                        subjects = subjects,
                        subjectId = subjects.firstOrNull()?.id,
                        date = date,
                        startMinuteOfDay = start,
                    )
                }
            }
        } catch (_: Throwable) {
            _state.update { it.copy(loading = false, unavailable = true) }
        }
    }

    fun selectSubject(id: String) = _state.update {
        if (it.subjectId == id) it else it.copy(subjectId = id, chapterId = null)
    }

    fun selectChapter(id: String?) = _state.update { it.copy(chapterId = id) }

    fun selectType(type: StudyActivityType) = _state.update { it.copy(activityType = type) }

    fun stepDuration(up: Boolean) = _state.update {
        val step = if (up) MANUAL_SESSION_STEP_MINUTES else -MANUAL_SESSION_STEP_MINUTES
        it.copy(durationMinutes = (it.durationMinutes + step).coerceIn(MANUAL_SESSION_MIN_MINUTES, MANUAL_SESSION_MAX_MINUTES))
    }

    fun setDate(date: LocalDate) = _state.update { it.copy(date = date.coerceAtMost(LocalDate.now())) }

    fun setStartMinute(minuteOfDay: Int) = _state.update { it.copy(startMinuteOfDay = minuteOfDay.coerceIn(0, 24 * 60 - 1)) }

    fun save() {
        val current = _state.value
        val attempt = attemptId ?: return
        if (!current.canSave) return
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val existing = original
                if (existing != null) {
                    // An unchanged chapter keeps the topic the session was recorded on.
                    val entry = if (current.chapterId == originalChapterId) current.entry.copy(nodeId = existing.nodeId) else current.entry
                    studySessionRepository.updateSession(existing.editedWith(entry, now))
                } else {
                    studySessionRepository.logManualSession(
                        manualStudySession("manual_${UUID.randomUUID()}", attempt, current.entry, ZoneId.systemDefault(), now),
                        now,
                    )
                }
                _state.update { it.copy(saving = false, finished = true) }
            } catch (_: Throwable) {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    fun delete() {
        val id = original?.id ?: return
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                studySessionRepository.deleteSession(id)
                _state.update { it.copy(saving = false, finished = true) }
            } catch (_: Throwable) {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    class Factory(
        private val sessionId: String?,
        private val initialDate: LocalDate?,
        private val examPackRepository: ExamPackRepository,
        private val attemptRepository: AttemptRepository,
        private val studySessionRepository: StudySessionRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LogSessionViewModel(
            sessionId, initialDate, examPackRepository, attemptRepository, studySessionRepository,
        ) as T
    }

    private companion object {
        const val DEFAULT_LOOKBACK_MINUTES = 60
        const val PAST_DAY_START = 18 * 60
    }
}
