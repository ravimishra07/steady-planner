package com.exam.assistant.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.ProRepository
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.MockTestRepository
import com.exam.assistant.domain.MistakeType
import com.exam.assistant.domain.MockMistake
import com.exam.assistant.domain.MockSubject
import com.exam.assistant.domain.MockTest
import com.exam.assistant.domain.MockTestType
import com.exam.assistant.domain.NEET_MAX_TOTAL
import com.exam.assistant.domain.mockTotal
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Add or edit one mock (PRODUCTION_V1_PLAN §8.1) with its lost-marks chapters (§8.2). */
data class MockFormUiState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val unavailable: Boolean = false,
    val name: String = "",
    val type: MockTestType = MockTestType.FULL_MOCK,
    val date: LocalDate = LocalDate.now(),
    val subjectScores: Map<MockSubject, String> = emptyMap(),
    val totalText: String = "",
    val maxTotalText: String = NEET_MAX_TOTAL.toString(),
    val attemptedText: String = "",
    val correctText: String = "",
    val wrongText: String = "",
    val mistakes: List<MockMistake> = emptyList(),
    val subjects: List<SubjectChoice> = emptyList(),
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val finished: Boolean = false,
    /** Pro, or the paywall switched off remotely. Gates tagging lost-marks chapters (§8.2). */
    val proUnlocked: Boolean = false,
) {
    /** True once any subject score is typed: the total is then their sum and not editable. */
    val totalIsAuto: Boolean get() = MockSubject.entries.any { subjectScores[it].toScore() != null }

    val total: Int? get() = mockTotal(MockSubject.entries.map { subjectScores[it].toScore() }, totalText.toScore())

    val maxTotal: Int? get() = maxTotalText.toScore()?.takeIf { it > 0 }

    val canSave: Boolean
        get() = !loading && !saving && !unavailable && total != null && maxTotal != null

    fun chapterTitle(id: String): String =
        subjects.firstNotNullOfOrNull { s -> s.chapters.firstOrNull { it.id == id }?.title } ?: id
}

internal fun String?.toScore(): Int? = this?.trim()?.toIntOrNull()

class MockFormViewModel(
    private val mockId: String?,
    private val examPackRepository: ExamPackRepository,
    private val attemptRepository: AttemptRepository,
    private val mockTestRepository: MockTestRepository,
    private val proRepository: ProRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MockFormUiState(isEdit = mockId != null))
    val state: StateFlow<MockFormUiState> = _state.asStateFlow()

    private var attemptId: String? = null

    init {
        load()
        viewModelScope.launch { proRepository.unlocked.collect { unlocked -> _state.update { it.copy(proUnlocked = unlocked) } } }
    }

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
                SubjectChoice(subject.id, subject.name, subject.nodes.sortedBy { it.order }.map { ChapterChoice(it.id, it.title) })
            }
            val mock = mockId?.let { mockTestRepository.byId(it) }
            if (mockId != null && mock == null) {
                _state.update { it.copy(loading = false, unavailable = true) }
                return@launch
            }
            val mistakes = mock?.let { mockTestRepository.mistakesFor(it.id) }.orEmpty()
            _state.update { current ->
                if (mock == null) {
                    current.copy(loading = false, subjects = subjects)
                } else {
                    val scores = MockSubject.entries.mapNotNull { s -> mock.score(s)?.let { s to it.toString() } }.toMap()
                    current.copy(
                        loading = false,
                        subjects = subjects,
                        name = mock.name,
                        type = mock.type,
                        date = mock.date,
                        subjectScores = scores,
                        totalText = if (scores.isEmpty()) mock.total.toString() else "",
                        maxTotalText = mock.maxTotal.toString(),
                        attemptedText = mock.attempted?.toString().orEmpty(),
                        correctText = mock.correct?.toString().orEmpty(),
                        wrongText = mock.wrong?.toString().orEmpty(),
                        mistakes = mistakes,
                    )
                }
            }
        } catch (_: Throwable) {
            _state.update { it.copy(loading = false, unavailable = true) }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value.take(MAX_NAME_LENGTH)) }
    fun setType(value: MockTestType) = _state.update { it.copy(type = value) }
    fun setDate(value: LocalDate) = _state.update { it.copy(date = value.coerceAtMost(LocalDate.now())) }
    fun setSubjectScore(subject: MockSubject, value: String) =
        _state.update { it.copy(subjectScores = it.subjectScores + (subject to value.scoreInput())) }
    fun setTotal(value: String) = _state.update { it.copy(totalText = value.scoreInput()) }
    fun setMaxTotal(value: String) = _state.update { it.copy(maxTotalText = value.countInput()) }
    fun setAttempted(value: String) = _state.update { it.copy(attemptedText = value.countInput()) }
    fun setCorrect(value: String) = _state.update { it.copy(correctText = value.countInput()) }
    fun setWrong(value: String) = _state.update { it.copy(wrongText = value.countInput()) }

    fun tagChapter(chapterId: String) = _state.update {
        if (it.mistakes.any { m -> m.chapterId == chapterId }) it
        else it.copy(mistakes = it.mistakes + MockMistake(mockId.orEmpty(), chapterId, MistakeType.CONCEPT))
    }

    fun setMistakeType(chapterId: String, type: MistakeType) = _state.update {
        it.copy(mistakes = it.mistakes.map { m -> if (m.chapterId == chapterId) m.copy(type = type) else m })
    }

    fun removeMistake(chapterId: String) = _state.update { it.copy(mistakes = it.mistakes.filterNot { m -> m.chapterId == chapterId }) }

    fun save() {
        val current = _state.value
        val attempt = attemptId ?: return
        val total = current.total ?: return
        val maxTotal = current.maxTotal ?: return
        if (!current.canSave) return
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                val id = mockId ?: "mock_${UUID.randomUUID()}"
                val auto = current.totalIsAuto
                val mock = MockTest(
                    id = id,
                    attemptId = attempt,
                    date = current.date,
                    name = current.name.trim(),
                    type = current.type,
                    physics = current.subjectScores[MockSubject.PHYSICS].toScore().takeIf { auto },
                    chemistry = current.subjectScores[MockSubject.CHEMISTRY].toScore().takeIf { auto },
                    botany = current.subjectScores[MockSubject.BOTANY].toScore().takeIf { auto },
                    zoology = current.subjectScores[MockSubject.ZOOLOGY].toScore().takeIf { auto },
                    total = total,
                    maxTotal = maxTotal,
                    attempted = current.attemptedText.toScore(),
                    correct = current.correctText.toScore(),
                    wrong = current.wrongText.toScore(),
                )
                mockTestRepository.save(mock, current.mistakes.map { it.copy(mockId = id) }, System.currentTimeMillis())
                _state.update { it.copy(saving = false, finished = true) }
            } catch (_: Throwable) {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    fun delete() {
        val id = mockId ?: return
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                mockTestRepository.delete(id)
                _state.update { it.copy(saving = false, finished = true) }
            } catch (_: Throwable) {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    class Factory(
        private val mockId: String?,
        private val examPackRepository: ExamPackRepository,
        private val attemptRepository: AttemptRepository,
        private val mockTestRepository: MockTestRepository,
        private val proRepository: ProRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MockFormViewModel(mockId, examPackRepository, attemptRepository, mockTestRepository, proRepository) as T
    }

    private companion object {
        const val MAX_NAME_LENGTH = 60
        const val MAX_DIGITS = 4
    }

    /** Digits with an optional leading minus: NEET marks negatively, so a section can be below zero. */
    private fun String.scoreInput(): String {
        val negative = trim().startsWith("-")
        val digits = filter(Char::isDigit).take(MAX_DIGITS)
        return if (negative) "-$digits" else digits
    }

    private fun String.countInput(): String = filter(Char::isDigit).take(MAX_DIGITS)
}
