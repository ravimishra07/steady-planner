package com.exam.assistant.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.MockTestRepository
import com.exam.assistant.domain.MockSubject
import com.exam.assistant.domain.MockTest
import com.exam.assistant.domain.MockTrendPoint
import com.exam.assistant.domain.mockTrend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The "Tests" screen (PRODUCTION_V1_PLAN §8.1): every logged mock, newest first, and the trend. */
data class TestsUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val hasPlan: Boolean = true,
    val mocks: List<MockTest> = emptyList(),
    val trend: List<MockTrendPoint> = emptyList(),
    /** Subject lines drawn on the trend chart next to the total. */
    val visibleSubjects: Set<MockSubject> = emptySet(),
)

class TestsViewModel(
    private val attemptRepository: AttemptRepository,
    private val mockTestRepository: MockTestRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TestsUiState())
    val state: StateFlow<TestsUiState> = _state.asStateFlow()

    fun refresh() = viewModelScope.launch {
        try {
            val attempt = attemptRepository.activeAttempt()
            if (attempt == null) {
                _state.update { it.copy(loading = false, hasPlan = false) }
                return@launch
            }
            val mocks = mockTestRepository.allFor(attempt.id)
            _state.update {
                it.copy(loading = false, loadFailed = false, hasPlan = true, mocks = mocks, trend = mockTrend(mocks))
            }
        } catch (_: Throwable) {
            _state.update { it.copy(loading = false, loadFailed = true) }
        }
    }

    fun toggleSubject(subject: MockSubject) = _state.update {
        it.copy(visibleSubjects = if (subject in it.visibleSubjects) it.visibleSubjects - subject else it.visibleSubjects + subject)
    }

    class Factory(
        private val attemptRepository: AttemptRepository,
        private val mockTestRepository: MockTestRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TestsViewModel(attemptRepository, mockTestRepository) as T
    }
}
