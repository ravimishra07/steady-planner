package com.exam.assistant.feature.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.exam.assistant.core.data.FocusStore
import com.exam.assistant.core.data.SettingsStore
import com.exam.assistant.core.data.StudySessionStore
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.core.data.backup.BackupRepository
import com.exam.assistant.core.data.repo.StudyPreferenceRepository
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
                combine(pro.isPro, pro.status, pro.debugForcePro) { isPro, status, force ->
                    _state.value.pro.copy(isPro = isPro, status = status, debugForcePro = force)
                }.collect { row -> _state.update { it.copy(pro = row) } }
            }
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
    settingsStore: SettingsStore,
    focusStore: FocusStore,
    syllabusStore: SyllabusStore,
    studySessionStore: StudySessionStore,
    examPackRepository: ExamPackRepository,
    attemptRepository: AttemptRepository,
    studyPreferenceRepository: StudyPreferenceRepository,
    topicProgressRepository: TopicProgressRepository,
    targetSyllabusRepository: TargetSyllabusRepository,
    rollingPlanRepository: RollingPlanRepository,
    chapterStageRepository: ChapterStageRepository,
    backupRepository: BackupRepository,
    configExamDate: LocalDate,
    examDateOfficial: Boolean,
    appVersion: String,
    background: BackgroundAppearance,
    onOpenAppearance: () -> Unit,
    onEditCommitments: () -> Unit,
    onRedoOnboarding: () -> Unit,
    onOpenPolicy: (String) -> Unit,
    onReminderChanged: suspend () -> Unit,
    onImported: () -> Unit,
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
    detailViewModel: SettingsDetailViewModel = composeViewModel(
        factory = SettingsDetailViewModel.Factory(
            planStore = planStore,
            settingsStore = settingsStore,
            focusStore = focusStore,
            syllabusStore = syllabusStore,
            studySessionStore = studySessionStore,
            examPackRepository = examPackRepository,
            attemptRepository = attemptRepository,
            studyPreferenceRepository = studyPreferenceRepository,
            rollingPlanRepository = rollingPlanRepository,
            configExamDate = configExamDate,
            examDateOfficial = examDateOfficial,
            onReminderChanged = onReminderChanged,
            backupRepository = backupRepository,
        ),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detail by detailViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.refresh()
        detailViewModel.refresh()
    }
    val context = LocalContext.current
    // Asked in context (§10.1): only when the student turns the reminder on, never at launch.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) detailViewModel.setReminderEnabled(true) else detailViewModel.onReminderPermissionDenied()
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME)) { uri ->
        uri?.let(detailViewModel::exportBackup)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(detailViewModel::requestImport)
    }
    val supportEmail = stringResource(R.string.support_email)
    val supportSubject = stringResource(R.string.more_support_subject)
    val actions = remember(viewModel, detailViewModel, supportEmail) {
        SettingsActions(
            onRetry = {
                viewModel.refresh()
                detailViewModel.refresh()
            },
            onWeekdayHours = { detailViewModel.setWeekdayHours(it); detailViewModel.commit() },
            onWeekendHours = { detailViewModel.setWeekendHours(it); detailViewModel.commit() },
            onFinishBy = { detailViewModel.setFinishBy(it); detailViewModel.commit() },
            onExamDate = { detailViewModel.setExamDate(it); detailViewModel.commit() },
            onEditCommitments = onEditCommitments,
            onLiveBacklogSplit = viewModel::setLiveBacklogSplit,
            onDismissSaveResult = detailViewModel::dismissSaveResult,
            onFocusLength = { detailViewModel.setFocusDurationMinutes(it); detailViewModel.commit() },
            onReminderToggle = { enabled ->
                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    detailViewModel.setReminderEnabled(enabled)
                }
            },
            onReminderTime = detailViewModel::setReminderTime,
            onFocusEndAlert = detailViewModel::setFocusEndAlert,
            onOpenPaywall = onOpenPaywall,
            onRestorePurchase = viewModel::restorePurchase,
            onDismissProStatus = viewModel::clearProStatus,
            onDebugForcePro = viewModel::setDebugForcePro,
            onOpenAppearance = onOpenAppearance,
            onShareUsageData = detailViewModel::setShareUsageData,
            onExportBackup = { exportLauncher.launch(backupFileName(LocalDate.now())) },
            onImportBackup = { importLauncher.launch(BACKUP_OPEN_MIMES) },
            onConfirmImport = { detailViewModel.confirmImport(onImported) },
            onDismissImport = detailViewModel::dismissImport,
            onDismissBackupMessage = detailViewModel::dismissBackupMessage,
            onRedoSetup = onRedoOnboarding,
            onEraseAll = onLogOut,
            onContactSupport = supportEmail.takeIf { it.isNotBlank() }?.let { email ->
                {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                        putExtra(Intent.EXTRA_SUBJECT, supportSubject)
                    }
                    runCatching { context.startActivity(intent) }
                }
            },
            onRateApp = {
                val pkg = context.packageName.removeSuffix(".debug")
                val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                runCatching { context.startActivity(market) }.onFailure { runCatching { context.startActivity(web) } }
            },
            onOpenPolicy = onOpenPolicy,
        )
    }
    MoreScreen(
        detail = detail,
        pro = state.pro,
        fixedBlockCount = state.plan?.decodedCommitments()?.size ?: 0,
        liveBacklogSplit = state.liveBacklogSplit,
        background = background,
        appVersion = appVersion,
        actions = actions,
        modifier = modifier,
    )
}
