package com.exam.assistant.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.SavedPlan
import com.exam.assistant.core.data.decodedCommitments
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.TopicProgressRepository
import com.exam.assistant.core.data.repo.TargetSyllabusRepository
import com.exam.assistant.core.design.BackgroundAppearance
import com.exam.assistant.domain.NEET_EXAM_ID
import com.exam.assistant.domain.TopicProgress
import com.exam.assistant.domain.TopicProgressStatus
import com.exam.assistant.domain.effectiveTargetLeafIds
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MoreUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val plan: SavedPlan? = null,
    val coveragePercent: Int? = null,
)

class MoreViewModel(
    private val planStore: PlanStore,
    private val examPackRepository: ExamPackRepository,
    private val attemptRepository: AttemptRepository,
    private val topicProgressRepository: TopicProgressRepository,
    private val targetSyllabusRepository: TargetSyllabusRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MoreUiState())
    val state: StateFlow<MoreUiState> = _state.asStateFlow()

    fun refresh() {
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            runCatching {
                val plan = planStore.load()
                val attempt = attemptRepository.activeAttempt()?.takeIf { it.examId == NEET_EXAM_ID }
                val coverage = if (plan?.examId == NEET_EXAM_ID && attempt != null) {
                    val pack = examPackRepository.examPackFor(NEET_EXAM_ID)
                    val overrides = targetSyllabusRepository.overridesFor(attempt.id)
                        .associate { it.nodeId to it.state }
                    coveredPercent(
                        pack.effectiveTargetLeafIds(overrides).toList(),
                        topicProgressRepository.allOnce(attempt.id),
                    )
                } else {
                    null
                }
                plan to coverage
            }.onSuccess { (plan, coverage) ->
                _state.update {
                    it.copy(
                        loading = false,
                        loadFailed = false,
                        plan = plan,
                        coveragePercent = coverage,
                    )
                }
            }.onFailure {
                _state.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    class Factory(
        private val planStore: PlanStore,
        private val examPackRepository: ExamPackRepository,
        private val attemptRepository: AttemptRepository,
        private val topicProgressRepository: TopicProgressRepository,
        private val targetSyllabusRepository: TargetSyllabusRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MoreViewModel(
            planStore = planStore,
            examPackRepository = examPackRepository,
            attemptRepository = attemptRepository,
            topicProgressRepository = topicProgressRepository,
            targetSyllabusRepository = targetSyllabusRepository,
        ) as T
    }
}

internal fun coveredPercent(
    leafIds: List<String>,
    progress: List<TopicProgress>,
): Int? {
    val leaves = leafIds.toSet()
    if (leaves.isEmpty()) return null
    val covered = progress.asSequence()
        .filter { it.status == TopicProgressStatus.COVERED && it.nodeId in leaves }
        .map { it.nodeId }
        .distinct()
        .count()
    return ((covered.toDouble() / leaves.size) * 100).roundToInt().coerceIn(0, 100)
}

@Composable
fun MoreRoute(
    planStore: PlanStore,
    examPackRepository: ExamPackRepository,
    attemptRepository: AttemptRepository,
    topicProgressRepository: TopicProgressRepository,
    targetSyllabusRepository: TargetSyllabusRepository,
    appVersion: String,
    background: BackgroundAppearance,
    onOpenAppearance: () -> Unit,
    onOpenSettings: () -> Unit,
    onRedoOnboarding: () -> Unit,
    onOpenPolicy: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MoreViewModel = composeViewModel(
        factory = MoreViewModel.Factory(
            planStore = planStore,
            examPackRepository = examPackRepository,
            attemptRepository = attemptRepository,
            topicProgressRepository = topicProgressRepository,
            targetSyllabusRepository = targetSyllabusRepository,
        ),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val plan = state.plan
    MoreScreen(
        loading = state.loading,
        loadFailed = state.loadFailed,
        planExamLabel = plan?.takeIf { it.examId == NEET_EXAM_ID }?.let {
            stringResource(R.string.settings_exam_neet)
        },
        examDateLabel = plan?.targetDateEpochDay?.let { epochDay ->
            LocalDate.ofEpochDay(epochDay).format(
                DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()),
            )
        },
        weekdayHours = plan?.weekdayHours,
        weekendHours = plan?.weekendHours,
        fixedBlockCount = plan?.decodedCommitments()?.size ?: 0,
        coveragePercent = state.coveragePercent,
        appVersion = appVersion,
        background = background,
        onRetry = viewModel::refresh,
        onOpenAppearance = onOpenAppearance,
        onOpenSettings = onOpenSettings,
        onRedoOnboarding = onRedoOnboarding,
        onOpenPolicy = onOpenPolicy,
        modifier = modifier,
    )
}
