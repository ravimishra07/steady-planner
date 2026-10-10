// Port of feature/settings/SettingsDetailViewModel.kt.
// Uri → File/Blob (import, from <input type=file>) or a file name (export, browser download), passed straight to
// BackupRepository.exportTo / importFrom. viewModelScope collectors are released in onCleared().
import { MutableStateFlow } from '../../platform/flow.js';
import { dataClass, enumClass, roundToInt, isBlank } from '../../domain/kt.js';
import { DailyReminderSetting } from '../../data/SettingsStore.js';
import { ImportResult } from '../../data/backup/BackupRepository.js';
import { DEFAULT_NEET_EXAM_DATE, MIN_FINISH_BY_DAYS_AHEAD, coerceFinishBy } from '../../domain/ExamDates.js';
import { PlanMode } from '../../domain/PlanMode.js';
import { StudyPreferences } from '../../domain/StudyPreferences.js';
import { today as localToday, plusDays, maxDate, parseDate, nowMs } from '../../domain/time.js';

export const SettingsDetailUiState = dataClass({
  loading: true,
  loadFailed: false,
  saving: false,
  saveFailed: false,
  saveDone: false,
  hasUnsavedChanges: false,
  weekdayHours: 4,
  weekendHours: 7,
  studyPlace: '',
  focusDurationMinutes: 50,
  planMode: PlanMode.DEADLINE,
  examDate: null,
  finishBy: null,
  /** The configured NEET date; an exam date the student set themselves is not "expected". */
  configExamDate: parseDate(DEFAULT_NEET_EXAM_DATE),
  examDateOfficial: false,
  showClearDialog: false,
  clearing: false,
  clearFailed: false,
  showDiscardDialog: false,
  /** End-of-session sound and vibration. Saved immediately, not part of the draft. */
  focusEndAlert: true,
  /** The daily "Today's plan" reminder. Saved immediately, not part of the draft. Off by default. */
  reminderEnabled: false,
  reminderMinuteOfDay: DailyReminderSetting.DEFAULT_MINUTE_OF_DAY,
  /** The notification permission was refused while turning the reminder on. */
  reminderPermissionDenied: false,
  /** Anonymous usage data and crash reports (§11.3). Saved immediately. On by default. */
  shareUsageData: true,
  /** An export or import is running. */
  backupBusy: false,
  backupMessage: null,
  /** A file was picked for import; waiting on "Replace current data?". */
  pendingImport: null,
}, {
  /** True when the exam date is still the configured one, so "expected, not yet announced" applies to it. */
  get examDateIsConfigured() { return this.examDate === this.configExamDate; },
});

/** The outcome line under Settings → Your data. */
export const BackupMessage = enumClass(['EXPORTED', 'EXPORT_FAILED', 'IMPORT_TOO_NEW', 'IMPORT_INCOMPATIBLE', 'IMPORT_FAILED']);

export class SettingsDetailViewModel {
  constructor(planStore, settingsStore, focusStore, syllabusStore, studySessionStore, examPackRepository,
    attemptRepository, studyPreferenceRepository, rollingPlanRepository,
    configExamDate = parseDate(DEFAULT_NEET_EXAM_DATE), examDateOfficial = false,
    /** Arms or cancels the reminder alarm after its setting changes. Wired by :app. */
    onReminderChanged = async () => {}, backupRepository = null) {
    Object.assign(this, {
      planStore, settingsStore, focusStore, syllabusStore, studySessionStore, examPackRepository,
      attemptRepository, studyPreferenceRepository, rollingPlanRepository, onReminderChanged, backupRepository,
    });
    this._state = new MutableStateFlow(SettingsDetailUiState({ configExamDate, examDateOfficial }));
    this.state = this._state.asStateFlow();
    this._unsubs = [];
    this.refresh();
    this._unsubs.push(settingsStore.focusEndAlert.subscribe((enabled) => this._update((it) => it.copy({ focusEndAlert: enabled }))));
    this._unsubs.push(settingsStore.shareUsageData.subscribe((enabled) => this._update((it) => it.copy({ shareUsageData: enabled }))));
    this._unsubs.push(settingsStore.dailyReminder.subscribe((reminder) =>
      this._update((it) => it.copy({ reminderEnabled: reminder.enabled, reminderMinuteOfDay: reminder.minuteOfDay }))));
  }

  _update(fn) { this._state.update(fn); }

  /** AppContainer follows the stored value and switches Firebase Analytics and Crashlytics with it. */
  setShareUsageData(enabled) {
    this._update((it) => it.copy({ shareUsageData: enabled }));
    this.settingsStore.setShareUsageData(enabled);
  }

  /** Call with true only once notifications are allowed; the screen asks for the permission first. */
  setReminderEnabled(enabled) {
    this._update((it) => it.copy({ reminderEnabled: enabled, reminderPermissionDenied: false }));
    this._saveReminder();
  }

  setReminderTime(minuteOfDay) {
    this._update((it) => it.copy({ reminderMinuteOfDay: minuteOfDay }));
    this._saveReminder();
  }

  /** The permission was refused: the reminder stays off and the screen says why. */
  onReminderPermissionDenied() {
    this._update((it) => it.copy({ reminderEnabled: false, reminderPermissionDenied: true }));
  }

  exportBackup(uri) {
    const backup = this.backupRepository; if (!backup) return;
    if (this._state.value.backupBusy) return;
    this._update((it) => it.copy({ backupBusy: true, backupMessage: null }));
    (async () => {
      const ok = await backup.exportTo(uri);
      this._update((it) => it.copy({ backupBusy: false, backupMessage: ok ? BackupMessage.EXPORTED : BackupMessage.EXPORT_FAILED }));
    })();
  }

  requestImport(uri) {
    if (!this._state.value.backupBusy) this._update((it) => it.copy({ pendingImport: uri, backupMessage: null }));
  }

  dismissImport() {
    if (!this._state.value.backupBusy) this._update((it) => it.copy({ pendingImport: null }));
  }

  /** Replaces everything with the picked file. On success [onImported] restarts the app on the new data. */
  confirmImport(onImported) {
    const backup = this.backupRepository; if (!backup) return;
    const uri = this._state.value.pendingImport; if (uri == null) return;
    if (this._state.value.backupBusy) return;
    this._update((it) => it.copy({ backupBusy: true, backupMessage: null }));
    (async () => {
      const result = await backup.importFrom(uri);
      const message = result === ImportResult.Imported ? null
        : result === ImportResult.TooNew ? BackupMessage.IMPORT_TOO_NEW
          : result === ImportResult.Incompatible ? BackupMessage.IMPORT_INCOMPATIBLE
            : BackupMessage.IMPORT_FAILED;
      this._update((it) => it.copy({ backupBusy: result === ImportResult.Imported, pendingImport: null, backupMessage: message }));
      if (result === ImportResult.Imported) onImported();
    })();
  }

  dismissBackupMessage() { this._update((it) => it.copy({ backupMessage: null })); }

  _saveReminder() {
    const current = this._state.value;
    (async () => {
      await this.settingsStore.setDailyReminder(DailyReminderSetting({ enabled: current.reminderEnabled, minuteOfDay: current.reminderMinuteOfDay }));
      try { await this.onReminderChanged(); } catch { /* runCatching */ }
    })();
  }

  setFocusEndAlert(enabled) {
    this._update((it) => it.copy({ focusEndAlert: enabled }));
    this.settingsStore.setFocusEndAlert(enabled);
  }

  refresh() {
    this._update((it) => it.copy({ loading: true, loadFailed: false, saveFailed: false }));
    (async () => {
      let loaded;
      try {
        const attempt = await this.attemptRepository.activeAttempt();
        const preferences = attempt ? await this.studyPreferenceRepository.forAttempt(attempt.id) : null;
        const focusSec = await this.settingsStore.focusDurationSec();
        loaded = {
          weekdayHours: preferences?.weekdayTargetMinutes != null ? preferences.weekdayTargetMinutes / 60 : 4,
          weekendHours: preferences?.weekendTargetMinutes != null ? preferences.weekendTargetMinutes / 60 : 7,
          studyPlace: preferences?.defaultStudyPlace ?? '',
          focusDurationMinutes: preferences?.preferredSessionMinutes ?? Math.trunc(focusSec / 60),
          planMode: attempt?.planMode ?? PlanMode.DEADLINE,
          examDate: attempt?.examDate ?? null,
          finishBy: attempt?.targetCompletionDate ?? null,
        };
      } catch (e) {
        console.warn(e);
        this._update((it) => it.copy({ loading: false, loadFailed: true }));
        return;
      }
      this._update((it) => it.copy({ loading: false, loadFailed: false, ...loaded, hasUnsavedChanges: false }));
    })();
  }

  setWeekdayHours(value) { this._updateDraft((s) => s.copy({ weekdayHours: steppedHours(value) })); }
  setWeekendHours(value) { this._updateDraft((s) => s.copy({ weekendHours: steppedHours(value) })); }
  setStudyPlace(value) { this._updateDraft((s) => s.copy({ studyPlace: value })); }
  setFocusDurationMinutes(minutes) { this._updateDraft((s) => s.copy({ focusDurationMinutes: minutes })); }

  /** Yes is deadline mode, Not yet is steady mode. Switching regenerates future planner-made blocks only. */
  setPlanMode(mode) { this._updateDraft((s) => s.copy({ planMode: mode })); }

  /** Moving the exam in front of the finish-by date pulls finish-by in with it. */
  setExamDate(date) {
    this._updateDraft((s) => {
      const today = localToday();
      const exam = maxDate(date, plusDays(today, MIN_FINISH_BY_DAYS_AHEAD));
      return s.copy({ examDate: exam, finishBy: s.finishBy != null ? coerceFinishBy(s.finishBy, exam, today) : null });
    });
  }

  setFinishBy(date) {
    this._updateDraft((s) => {
      const exam = s.examDate ?? date;
      return s.copy({ finishBy: coerceFinishBy(date, exam, localToday()) });
    });
  }

  save() {
    const draft = this._state.value;
    if (draft.loading || draft.saving || !draft.hasUnsavedChanges) return;
    this._update((it) => it.copy({ saving: true, saveFailed: false, saveDone: false }));
    (async () => {
      try {
        const attempt = await this.attemptRepository.activeAttempt();
        if (!attempt) throw new Error('An active attempt is required to save study preferences');
        const currentPreferences = (await this.studyPreferenceRepository.forAttempt(attempt.id)) ?? StudyPreferences({ attemptId: attempt.id });
        const exam = draft.examDate ?? attempt.examDate;
        const finishBy = draft.finishBy ?? attempt.targetCompletionDate;
        if (exam !== attempt.examDate || finishBy !== attempt.targetCompletionDate || draft.planMode !== attempt.planMode) {
          await this.attemptRepository.upsert(attempt.copy({
            examDate: exam, targetCompletionDate: finishBy, planMode: draft.planMode, updatedAtEpochMs: nowMs(),
          }));
        }
        await this.studyPreferenceRepository.upsert(currentPreferences.copy({
          weekdayTargetMinutes: roundToInt(draft.weekdayHours * 60),
          weekendTargetMinutes: roundToInt(draft.weekendHours * 60),
          preferredSessionMinutes: draft.focusDurationMinutes,
          defaultStudyPlace: isBlank(draft.studyPlace.trim()) ? null : draft.studyPlace.trim(),
        }));
        // Focus reads the same length (§6.4): keep its store in step.
        await this.settingsStore.setFocusDurationSec(draft.focusDurationMinutes * 60);
        if ((await this.rollingPlanRepository.replenish({ fromDate: localToday() })) == null) {
          throw new Error('The study plan could not be regenerated');
        }
      } catch (e) {
        console.warn(e);
        this._update((it) => it.copy({ saving: false, saveFailed: true }));
        return;
      }
      this._update((it) => it.copy({ saving: false, saveDone: true, hasUnsavedChanges: false, studyPlace: draft.studyPlace.trim() }));
    })();
  }

  dismissSaveResult() { this._update((it) => it.copy({ saveDone: false, saveFailed: false })); }

  /** Returns true when navigation can proceed immediately. */
  requestBack() {
    if (this._state.value.saving) return false;
    if (!this._state.value.hasUnsavedChanges) return true;
    this._update((it) => it.copy({ showDiscardDialog: true }));
    return false;
  }

  dismissDiscard() { this._update((it) => it.copy({ showDiscardDialog: false })); }
  confirmDiscard() { this._update((it) => it.copy({ showDiscardDialog: false, hasUnsavedChanges: false })); }

  requestClear() {
    if (!this._state.value.saving) this._update((it) => it.copy({ showClearDialog: true, clearFailed: false }));
  }
  dismissClear() {
    if (!this._state.value.clearing) this._update((it) => it.copy({ showClearDialog: false }));
  }
  dismissClearError() { this._update((it) => it.copy({ clearFailed: false })); }

  confirmClear(onCleared) {
    if (this._state.value.clearing) return;
    this._update((it) => it.copy({ clearing: true, clearFailed: false }));
    (async () => {
      try {
        const attempt = await this.attemptRepository.activeAttempt();
        if (attempt) {
          await this.examPackRepository.clearCustomChapters(attempt.examId);
          await this.attemptRepository.deleteAttemptAndAllData(attempt.id);
        }
        await this.planStore.clear();
        await this.syllabusStore.clear();
        await this.focusStore.clear();
        await this.studySessionStore.clear();
      } catch (e) {
        console.warn(e);
        this._update((it) => it.copy({ clearing: false, showClearDialog: false, clearFailed: true }));
        return;
      }
      this._update((it) => it.copy({ clearing: false, showClearDialog: false }));
      onCleared();
    })();
  }

  _updateDraft(transform) {
    this._update((current) => (current.loading || current.saving
      ? current
      : transform(current).copy({ hasUnsavedChanges: true, saveDone: false, saveFailed: false })));
  }

  onCleared() { this._unsubs.forEach((u) => u()); this._unsubs = []; }
}

/** internal fun steppedHours(value: Float): Float = (value * 2).roundToInt() / 2f */
export const steppedHours = (value) => roundToInt(value * 2) / 2;
