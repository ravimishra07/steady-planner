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
import com.exam.assistant.core.data.ProRepository
import com.exam.assistant.core.data.ProStatus
import com.exam.assistant.core.data.SavedPlan
import com.exam.assistant.core.data.decodedCommitments
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.ChapterStageRepository
import com.exam.assistant.core.data.repo.RollingPlanRepository
import com.exam.assistant.core.data.repo.TopicProgressRepository
import com.exam.assistant.core.data.repo.TargetSyllabusRepository
import com.exam.assistant.core.design.BackgroundAppearance
import com.exam.assistant.domain.NEET_EXAM_ID
import com.exam.assistant.domain.syllabusMetrics
import com.exam.assistant.domain.targetStates
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MoreUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val plan: SavedPlan? = null,
    val coveragePercent: Int? = null,
    val examDate: LocalDate? = null,
    val finishByDate: LocalDate? = null,
    /** Live share of the day for coaching students (§5.4); null hides the row for everyone else. */
    val liveBacklogSplit: Int? = null,
    val pro: ProRowState = ProRowState(),
)

/** Settings → Pro: status, buy, restore, and the debug-only override. */
data class ProRowState(
    val isPro: Boolean = false,
    val paywallEnabled: Boolean = true,
    val price: String? = null,
    val status: ProStatus = ProStatus.IDLE,
    val debugForcePro: Boolean = false,
    val showDebugToggle: Boolean = false,
)

class MoreViewModel(
    private val planStore: PlanStore,
    private val examPackRepository: ExamPackRepository,
    private val attemptRepository: AttemptRepository,
    private val topicProgressRepository: TopicProgressRepository,
    private val targetSyllabusRepository: TargetSyllabusRepository,
    private val rollingPlanRepository: RollingPlanRepository,
    private val chapterStageRepository: ChapterStageRepository,
    private val proRepository: ProRepository? = null,
    private val isDebugBuild: Boolean = false,
) : ViewModel() {
    private val _state = MutableStateFlow(
        MoreUiState(pro = ProRowState(paywallEnabled = proRepository?.paywallEnabled ?: true, showDebugToggle = isDebugBuild)),
    )
    val state: StateFlow<MoreUiState> = _state.asStateFlow()

    init {
        proRepository?.let { pro ->
            viewModelScope.launch {
                combine(pro.isPro, pro.formattedPrice, pro.status, pro.debugForcePro) { isPro, price, status, force ->
                    _state.value.pro.copy(isPro = isPro, price = price, status = status, debugForcePro = force)
                }.collect { row -> _state.update { it.copy(pro = row) } }
            }
            viewModelScope.launch { pro.loadProduct() }
        }
    }

    /** Settings → Restore purchase. */
    fun restorePurchase() {
        val pro = proRepository ?: return
        viewModelScope.launch { pro.refreshPurchases(userInitiated = true) }
    }

    fun setDebugForcePro(enabled: Boolean) {
        val pro = proRepository ?: return
        viewModelScope.launch { pro.setDebugForcePro(enabled) }
    }

    fun clearProStatus() {
        proRepository?.clearStatus()
    }

    private var split: Int? = null

    /** Settings → Plan: live/backlog split. Saving replans from today. */
    fun setLiveBacklogSplit(percent: Int) {
        _state.update { it.copy(liveBacklogSplit = percent) }
        viewModelScope.launch { rollingPlanRepository.setLiveBacklogSplit(percent) }
    }

    fun refresh() {
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            runCatching {
                val plan = planStore.load()
                val attempt = attemptRepository.activeAttempt()?.takeIf { it.examId == NEET_EXAM_ID }
                val coverage = if (plan?.examId == NEET_EXAM_ID && attempt != null) {
                    val pack = examPackRepository.examPackFor(NEET_EXAM_ID)
                    syllabusMetrics(
                        pack = pack,
                        overrides = targetStates(targetSyllabusRepository.overridesFor(attempt.id)),
                        progress = topicProgressRepository.allOnce(attempt.id),
                    ).overall.takeIf { it.total > 0 }?.percent
                } else {
                    null
                }
                val usesCoaching = attempt != null && (
                    chapterStageRepository.coachingFor(attempt.id).isNotEmpty() ||
                        chapterStageRepository.batchPosition(attempt.id) != null
                    )
                split = if (usesCoaching) rollingPlanRepository.liveBacklogSplit() else null
                Triple(plan, coverage, attempt)
            }.onSuccess { (plan, coverage, attempt) ->
                _state.update {
                    it.copy(
                        loading = false,
                        loadFailed = false,
                        plan = plan,
                        coveragePercent = coverage,
                        examDate = attempt?.examDate,
                        finishByDate = attempt?.targetCompletionDate,
                        liveBacklogSplit = split,
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
        private val rollingPlanRepository: RollingPlanRepository,
        private val chapterStageRepository: ChapterStageRepository,
        private val proRepository: ProRepository? = null,
        private val isDebugBuild: Boolean = false,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MoreViewModel(
            planStore = planStore,
            examPackRepository = examPackRepository,
            attemptRepository = attemptRepository,
            topicProgressRepository = topicProgressRepository,
            targetSyllabusRepository = targetSyllabusRepository,
            rollingPlanRepository = rollingPlanRepository,
            chapterStageRepository = chapterStageRepository,
            proRepository = proRepository,
            isDebugBuild = isDebugBuild,
        ) as T
    }
}

@Composable
fun MoreRoute(
    planStore: PlanStore,
    examPackRepository: ExamPackRepository,
    attemptRepository: AttemptRepository,
    topicProgressRepository: TopicProgressRepository,
    targetSyllabusRepository: TargetSyllabusRepository,
    rollingPlanRepository: RollingPlanRepository,
    chapterStageRepository: ChapterStageRepository,
    examDateOfficial: Boolean,
    appVersion: String,
    background: BackgroundAppearance,
    onOpenAppearance: () -> Unit,
    onOpenSettings: () -> Unit,
    onRedoOnboarding: () -> Unit,
    onOpenPolicy: (String) -> Unit,
    modifier: Modifier = Modifier,
    proRepository: ProRepository? = null,
    isDebugBuild: Boolean = false,
    onOpenPaywall: () -> Unit = {},
    onLogOut: () -> Unit = {},
    viewModel: MoreViewModel = composeViewModel(
        factory = MoreViewModel.Factory(
            planStore = planStore,
            examPackRepository = examPackRepository,
            attemptRepository = attemptRepository,
            topicProgressRepository = topicProgressRepository,
            targetSyllabusRepository = targetSyllabusRepository,
            rollingPlanRepository = rollingPlanRepository,
            chapterStageRepository = chapterStageRepository,
            proRepository = proRepository,
            isDebugBuild = isDebugBuild,
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
        examDateLabel = state.examDate?.let { date ->
            date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
        },
        examDateOfficial = examDateOfficial,
        finishByLabel = (state.finishByDate ?: plan?.targetDateEpochDay?.let(LocalDate::ofEpochDay))?.let { date ->
            date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
        },
        weekdayHours = plan?.weekdayHours,
        weekendHours = plan?.weekendHours,
        fixedBlockCount = plan?.decodedCommitments()?.size ?: 0,
        coveragePercent = state.coveragePercent,
        liveBacklogSplit = state.liveBacklogSplit,
        onLiveBacklogSplit = viewModel::setLiveBacklogSplit,
        appVersion = appVersion,
        background = background,
        onRetry = viewModel::refresh,
        onOpenAppearance = onOpenAppearance,
        onOpenSettings = onOpenSettings,
        onRedoOnboarding = onRedoOnboarding,
        onOpenPolicy = onOpenPolicy,
        pro = state.pro,
        onOpenPaywall = onOpenPaywall,
        onRestorePurchase = viewModel::restorePurchase,
        onDismissProStatus = viewModel::clearProStatus,
        onDebugForcePro = viewModel::setDebugForcePro,
        onLogOut = onLogOut,
        modifier = modifier,
    )
}
