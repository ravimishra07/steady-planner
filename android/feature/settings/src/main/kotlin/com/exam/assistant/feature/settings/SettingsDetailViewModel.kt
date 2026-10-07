package com.exam.assistant.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.exam.assistant.core.data.DailyReminderSetting
import com.exam.assistant.core.data.backup.BackupRepository
import com.exam.assistant.core.data.backup.ImportResult
import com.exam.assistant.core.data.ExamPackRepository
import com.exam.assistant.core.data.FocusStore
import com.exam.assistant.core.data.PlanStore
import com.exam.assistant.core.data.SettingsStore
import com.exam.assistant.core.data.StudySessionStore
import com.exam.assistant.core.data.SyllabusStore
import com.exam.assistant.core.data.repo.AttemptRepository
import com.exam.assistant.core.data.repo.RollingPlanRepository
import com.exam.assistant.core.data.repo.StudyPreferenceRepository
import com.exam.assistant.domain.DEFAULT_NEET_EXAM_DATE
import com.exam.assistant.domain.MIN_FINISH_BY_DAYS_AHEAD
import com.exam.assistant.domain.PlanMode
import com.exam.assistant.domain.StudyPreferences
import com.exam.assistant.domain.coerceFinishBy
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsDetailUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val saveDone: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val weekdayHours: Float = 4f,
    val weekendHours: Float = 7f,
    val studyPlace: String = "",
    val focusDurationMinutes: Int = 50,
    val planMode: PlanMode = PlanMode.DEADLINE,
    val examDate: LocalDate? = null,
    val finishBy: LocalDate? = null,
    /** The configured NEET date; an exam date the student set themselves is not "expected". */
    val configExamDate: LocalDate = LocalDate.parse(DEFAULT_NEET_EXAM_DATE),
    val examDateOfficial: Boolean = false,
    val showClearDialog: Boolean = false,
    val clearing: Boolean = false,
    val clearFailed: Boolean = false,
    val showDiscardDialog: Boolean = false,
    /** End-of-session sound and vibration. Saved immediately, not part of the draft. */
    val focusEndAlert: Boolean = true,
    /** The daily "Today's plan" reminder. Saved immediately, not part of the draft. Off by default. */
    val reminderEnabled: Boolean = false,
    val reminderMinuteOfDay: Int = DailyReminderSetting.DEFAULT_MINUTE_OF_DAY,
    /** The notification permission was refused while turning the reminder on. */
    val reminderPermissionDenied: Boolean = false,
    /** An export or import is running. */
    val backupBusy: Boolean = false,
    val backupMessage: BackupMessage? = null,
    /** A file was picked for import; waiting on "Replace current data?". */
    val pendingImport: Uri? = null,
) {
    /** True when the exam date is still the configured one, so "expected, not yet announced" applies to it. */
    val examDateIsConfigured: Boolean get() = examDate == configExamDate
}

/** The outcome line under Settings → Your data. */
enum class BackupMessage { EXPORTED, EXPORT_FAILED, IMPORT_TOO_NEW, IMPORT_INCOMPATIBLE, IMPORT_FAILED }

class SettingsDetailViewModel(
    private val planStore: PlanStore,
    private val settingsStore: SettingsStore,
    private val focusStore: FocusStore,
    private val syllabusStore: SyllabusStore,
    private val studySessionStore: StudySessionStore,
    private val examPackRepository: ExamPackRepository,
    private val attemptRepository: AttemptRepository,
    private val studyPreferenceRepository: StudyPreferenceRepository,
    private val rollingPlanRepository: RollingPlanRepository,
    configExamDate: LocalDate = LocalDate.parse(DEFAULT_NEET_EXAM_DATE),
    examDateOfficial: Boolean = false,
    /** Arms or cancels the reminder alarm after its setting changes. Wired by :app. */
    private val onReminderChanged: suspend () -> Unit = {},
    private val backupRepository: BackupRepository? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsDetailUiState(configExamDate = configExamDate, examDateOfficial = examDateOfficial),
    )
    val state: StateFlow<SettingsDetailUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            settingsStore.focusEndAlert.collect { enabled -> _state.update { it.copy(focusEndAlert = enabled) } }
        }
        viewModelScope.launch {
            settingsStore.dailyReminder.collect { reminder ->
                _state.update { it.copy(reminderEnabled = reminder.enabled, reminderMinuteOfDay = reminder.minuteOfDay) }
            }
        }
    }

    /** Call with true only once notifications are allowed; the screen asks for the permission first. */
    fun setReminderEnabled(enabled: Boolean) {
        _state.update { it.copy(reminderEnabled = enabled, reminderPermissionDenied = false) }
        saveReminder()
    }

    fun setReminderTime(minuteOfDay: Int) {
        _state.update { it.copy(reminderMinuteOfDay = minuteOfDay) }
        saveReminder()
    }

    /** The permission was refused: the reminder stays off and the screen says why. */
    fun onReminderPermissionDenied() {
        _state.update { it.copy(reminderEnabled = false, reminderPermissionDenied = true) }
    }

    fun exportBackup(uri: Uri) {
        val backup = backupRepository ?: return
        if (_state.value.backupBusy) return
        _state.update { it.copy(backupBusy = true, backupMessage = null) }
        viewModelScope.launch {
            val ok = backup.exportTo(uri)
            _state.update { it.copy(backupBusy = false, backupMessage = if (ok) BackupMessage.EXPORTED else BackupMessage.EXPORT_FAILED) }
        }
    }

    fun requestImport(uri: Uri) {
        if (!_state.value.backupBusy) _state.update { it.copy(pendingImport = uri, backupMessage = null) }
    }

    fun dismissImport() {
        if (!_state.value.backupBusy) _state.update { it.copy(pendingImport = null) }
    }

    /** Replaces everything with the picked file. On success [onImported] restarts the app on the new data. */
    fun confirmImport(onImported: () -> Unit) {
        val backup = backupRepository ?: return
        val uri = _state.value.pendingImport ?: return
        if (_state.value.backupBusy) return
        _state.update { it.copy(backupBusy = true, backupMessage = null) }
        viewModelScope.launch {
            val result = backup.importFrom(uri)
            val message = when (result) {
                ImportResult.Imported -> null
                ImportResult.TooNew -> BackupMessage.IMPORT_TOO_NEW
                ImportResult.Incompatible -> BackupMessage.IMPORT_INCOMPATIBLE
                ImportResult.Unreadable -> BackupMessage.IMPORT_FAILED
            }
            _state.update { it.copy(backupBusy = result == ImportResult.Imported, pendingImport = null, backupMessage = message) }
            if (result == ImportResult.Imported) onImported()
        }
    }

    fun dismissBackupMessage() {
        _state.update { it.copy(backupMessage = null) }
    }

    private fun saveReminder() {
        val current = _state.value
        viewModelScope.launch {
            settingsStore.setDailyReminder(DailyReminderSetting(current.reminderEnabled, current.reminderMinuteOfDay))
            runCatching { onReminderChanged() }
        }
    }

    fun setFocusEndAlert(enabled: Boolean) {
        _state.update { it.copy(focusEndAlert = enabled) }
        viewModelScope.launch { settingsStore.setFocusEndAlert(enabled) }
    }

    fun refresh() {
        _state.update { it.copy(loading = true, loadFailed = false, saveFailed = false) }
        viewModelScope.launch {
            runCatching {
                val attempt = attemptRepository.activeAttempt()
                val preferences = attempt?.let { studyPreferenceRepository.forAttempt(it.id) }
                val focusSec = settingsStore.focusDurationSec()
                LoadedSettings(
                    weekdayHours = preferences?.weekdayTargetMinutes?.div(60f) ?: 4f,
                    weekendHours = preferences?.weekendTargetMinutes?.div(60f) ?: 7f,
                    studyPlace = preferences?.defaultStudyPlace.orEmpty(),
                    focusDurationMinutes = preferences?.preferredSessionMinutes
                        ?: focusSec / 60,
                    planMode = attempt?.planMode ?: PlanMode.DEADLINE,
                    examDate = attempt?.examDate,
                    finishBy = attempt?.targetCompletionDate,
                )
            }.onSuccess { loaded ->
                _state.update {
                    it.copy(
                        loading = false,
                        loadFailed = false,
                        weekdayHours = loaded.weekdayHours,
                        weekendHours = loaded.weekendHours,
                        studyPlace = loaded.studyPlace,
                        focusDurationMinutes = loaded.focusDurationMinutes,
                        planMode = loaded.planMode,
                        examDate = loaded.examDate,
                        finishBy = loaded.finishBy,
                        hasUnsavedChanges = false,
                    )
                }
            }.onFailure {
                _state.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    fun setWeekdayHours(value: Float) = updateDraft {
        copy(weekdayHours = steppedHours(value))
    }

    fun setWeekendHours(value: Float) = updateDraft {
        copy(weekendHours = steppedHours(value))
    }

    fun setStudyPlace(value: String) = updateDraft {
        copy(studyPlace = value)
    }

    fun setFocusDurationMinutes(minutes: Int) = updateDraft {
        copy(focusDurationMinutes = minutes)
    }

    /** Yes is deadline mode, Not yet is steady mode. Switching regenerates future planner-made blocks only. */
    fun setPlanMode(mode: PlanMode) = updateDraft { copy(planMode = mode) }

    /** Moving the exam in front of the finish-by date pulls finish-by in with it. */
    fun setExamDate(date: LocalDate) = updateDraft {
        val today = LocalDate.now()
        val exam = date.coerceAtLeast(today.plusDays(MIN_FINISH_BY_DAYS_AHEAD.toLong()))
        copy(examDate = exam, finishBy = finishBy?.let { coerceFinishBy(it, exam, today) })
    }

    fun setFinishBy(date: LocalDate) = updateDraft {
        val exam = examDate ?: date
        copy(finishBy = coerceFinishBy(date, exam, LocalDate.now()))
    }

    fun save() {
        val draft = _state.value
        if (draft.loading || draft.saving || !draft.hasUnsavedChanges) return
        _state.update { it.copy(saving = true, saveFailed = false, saveDone = false) }
        viewModelScope.launch {
            runCatching {
                val attempt = attemptRepository.activeAttempt()
                    ?: error("An active attempt is required to save study preferences")
                val currentPreferences = studyPreferenceRepository.forAttempt(attempt.id)
                    ?: StudyPreferences(attemptId = attempt.id)
                val exam = draft.examDate ?: attempt.examDate
                val finishBy = draft.finishBy ?: attempt.targetCompletionDate
                if (exam != attempt.examDate || finishBy != attempt.targetCompletionDate || draft.planMode != attempt.planMode) {
                    attemptRepository.upsert(
                        attempt.copy(
                            examDate = exam,
                            targetCompletionDate = finishBy,
                            planMode = draft.planMode,
                            updatedAtEpochMs = System.currentTimeMillis(),
                        ),
                    )
                }
                studyPreferenceRepository.upsert(
                    currentPreferences.copy(
                        weekdayTargetMinutes = (draft.weekdayHours * 60).roundToInt(),
                        weekendTargetMinutes = (draft.weekendHours * 60).roundToInt(),
                        preferredSessionMinutes = draft.focusDurationMinutes,
                        defaultStudyPlace = draft.studyPlace.trim().ifBlank { null },
                    ),
                )
                // Focus reads the same length (§6.4): keep its store in step.
                settingsStore.setFocusDurationSec(draft.focusDurationMinutes * 60)
                check(rollingPlanRepository.replenish(fromDate = LocalDate.now()) != null) {
                    "The study plan could not be regenerated"
                }
            }.onSuccess {
                _state.update {
                    it.copy(
                        saving = false,
                        saveDone = true,
                        hasUnsavedChanges = false,
                        studyPlace = draft.studyPlace.trim(),
                    )
                }
            }.onFailure {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    fun dismissSaveResult() {
        _state.update { it.copy(saveDone = false, saveFailed = false) }
    }

    /** Returns true when navigation can proceed immediately. */
    fun requestBack(): Boolean {
        if (_state.value.saving) return false
        if (!_state.value.hasUnsavedChanges) return true
        _state.update { it.copy(showDiscardDialog = true) }
        return false
    }

    fun dismissDiscard() {
        _state.update { it.copy(showDiscardDialog = false) }
    }

    fun confirmDiscard() {
        _state.update { it.copy(showDiscardDialog = false, hasUnsavedChanges = false) }
    }

    fun requestClear() {
        if (!_state.value.saving) _state.update { it.copy(showClearDialog = true, clearFailed = false) }
    }

    fun dismissClear() {
        if (!_state.value.clearing) _state.update { it.copy(showClearDialog = false) }
    }

    fun dismissClearError() {
        _state.update { it.copy(clearFailed = false) }
    }

    fun confirmClear(onCleared: () -> Unit) {
        if (_state.value.clearing) return
        _state.update { it.copy(clearing = true, clearFailed = false) }
        viewModelScope.launch {
            runCatching {
                attemptRepository.activeAttempt()?.let { attempt ->
                    examPackRepository.clearCustomChapters(attempt.examId)
                    attemptRepository.deleteAttemptAndAllData(attempt.id)
                }
                planStore.clear()
                syllabusStore.clear()
                focusStore.clear()
                studySessionStore.clear()
            }.onSuccess {
                _state.update { it.copy(clearing = false, showClearDialog = false) }
                onCleared()
            }.onFailure {
                _state.update {
                    it.copy(clearing = false, showClearDialog = false, clearFailed = true)
                }
            }
        }
    }

    private fun updateDraft(transform: SettingsDetailUiState.() -> SettingsDetailUiState) {
        _state.update { current ->
            if (current.loading || current.saving) current
            else current.transform().copy(
                hasUnsavedChanges = true,
                saveDone = false,
                saveFailed = false,
            )
        }
    }

    class Factory(
        private val planStore: PlanStore,
        private val settingsStore: SettingsStore,
        private val focusStore: FocusStore,
        private val syllabusStore: SyllabusStore,
        private val studySessionStore: StudySessionStore,
        private val examPackRepository: ExamPackRepository,
        private val attemptRepository: AttemptRepository,
        private val studyPreferenceRepository: StudyPreferenceRepository,
        private val rollingPlanRepository: RollingPlanRepository,
        private val configExamDate: LocalDate,
        private val examDateOfficial: Boolean,
        private val onReminderChanged: suspend () -> Unit,
        private val backupRepository: BackupRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsDetailViewModel(
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
            ) as T
    }
}

private data class LoadedSettings(
    val weekdayHours: Float,
    val weekendHours: Float,
    val studyPlace: String,
    val focusDurationMinutes: Int,
    val planMode: PlanMode,
    val examDate: LocalDate?,
    val finishBy: LocalDate?,
)

internal fun steppedHours(value: Float): Float = (value * 2).roundToInt() / 2f
