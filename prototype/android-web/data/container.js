// Port of android/app/src/main/kotlin/com/exam/assistant/AppContainer.kt — the whole dependency graph.
//
// Usage:  import { container } from '../data/container.js';   // the app-wide singleton
//         (or `new AppContainer()` for an isolated graph; repositories still share platform/store.js)
// Every field is a lazy getter (Kotlin `by lazy`): constructing the container costs nothing.
// Call `container.runMigrationInBackground()` once at startup (MainActivity does this on Android):
// it migrates, reconciles a Focus session left running before the reload, and tops up the plan.
//
// Web stubs (see each file's header): analytics = Analytics.NoOp, billing = local fake (ProRepository),
// notifications = StateFlows on focusNotifier / dailyReminder, alarms = setTimeout, widgets = none,
// restartProcess = location.reload(). Extra fields not in Kotlin: focusNotifier (the notifier the
// effects use, so the shell can render it), loadTodaySnapshot() (Kotlin's extension on AppContainer).
import { MutableStateFlow } from '../platform/flow.js';
import { today as localToday, nowMs } from '../domain/time.js';
import { FocusSession, FocusStatus, FocusBlockRef } from '../domain/FocusSession.js';
import { BlockTag } from '../domain/TodaySchedule.js';
import { DefaultAppDispatchers, formatAndroid } from './common.js';
import { SettingsStore } from './SettingsStore.js';
import { PlanStore } from './PlanStore.js';
import { SyllabusStore } from './SyllabusStore.js';
import { FocusStore } from './FocusStore.js';
import { StudySessionStore } from './StudySessionStore.js';
import { LocalRemoteConfig } from './RemoteConfig.js';
import { ProRepository } from './ProRepository.js';
import { FocusController } from './FocusController.js';
import { ExamPackRepository } from './ExamPackRepository.js';
import { StageLabelStore } from './StageLabelStore.js';
import { AttemptRepository } from './repo/AttemptRepository.js';
import { PlanRepository } from './repo/PlanRepository.js';
import { StudySessionRepository } from './repo/StudySessionRepository.js';
import { TopicProgressRepository } from './repo/TopicProgressRepository.js';
import { RevisionRepository } from './repo/RevisionRepository.js';
import { ChapterStageRepository } from './repo/ChapterStageRepository.js';
import { MockTestRepository } from './repo/MockTestRepository.js';
import { RollingPlanRepository } from './repo/RollingPlanRepository.js';
import { TargetSyllabusRepository } from './repo/TargetSyllabusRepository.js';
import { AvailabilityRepository } from './repo/AvailabilityRepository.js';
import { StudyPreferenceRepository } from './repo/StudyPreferenceRepository.js';
import { MigrationStore } from './repo/MigrationStore.js';
import { MigrationRepository } from './repo/MigrationRepository.js';
import { BackupRepository } from './backup/BackupRepository.js';
import { FirebaseAppAnalytics } from './app/FirebaseAppAnalytics.js';
import { AppFocusEffects } from './app/AppFocusEffects.js';
import { FocusNotifier } from './app/FocusNotifier.js';
import { FocusAlarm } from './app/FocusAlarm.js';
import { DailyReminder } from './app/DailyReminder.js';
import { AppWidgets } from './app/AppWidgets.js';
import { loadTodaySnapshot } from './app/TodaySnapshot.js';

const WIDGET_REFRESH_DEBOUNCE_MS = 1_000;
// app/src/main/res/values/strings.xml
const focus_notif_default_title = 'Focus session';
const focus_revision_title = 'Revision · %1$s';

/** `CoroutineScope(SupervisorJob())` stand-in: launch(block) runs it now; a failure never cancels siblings. */
class AppScope {
  constructor() { this.jobs = new Set(); }
  launch(block) {
    const p = Promise.resolve().then(block).catch((e) => console.error('[appScope]', e));
    this.jobs.add(p);
    p.finally(() => this.jobs.delete(p));
    return p;
  }
  /** Test helper (not in Kotlin): resolves when every launched job has finished. */
  async idle() { while (this.jobs.size) await Promise.allSettled([...this.jobs]); }
}

export class AppContainer {
  constructor() {
    this._lazy = new Map();
    /** The "application context": the container itself (receivers reach the graph through it). */
    this.context = this;

    /** One-shot: something asked for the paywall. The nav host drops it during onboarding and Focus. */
    this.paywallRequest = new MutableStateFlow(false);
    /** PendingSyllabusPick | null */
    this.pendingSyllabusPick = new MutableStateFlow(null);
    /** One-shot: a notification tap asked for the Focus screen. */
    this.openFocusRequest = new MutableStateFlow(false);
    /** One-shot: the daily reminder tap asked for Today. */
    this.openTodayRequest = new MutableStateFlow(false);
    /** One-shot: Today's "adjust plan" opens the setup flow on its Plan step. */
    this.onboardingStartAtPlan = new MutableStateFlow(false);
  }

  _get(name, factory) {
    if (!this._lazy.has(name)) this._lazy.set(name, factory());
    return this._lazy.get(name);
  }

  get dispatchers() { return this._get('dispatchers', () => new DefaultAppDispatchers()); }
  get settings() { return this._get('settings', () => new SettingsStore(this.context, this.dispatchers)); }
  get planStore() { return this._get('planStore', () => new PlanStore(this.context, this.dispatchers)); }
  get syllabusStore() { return this._get('syllabusStore', () => new SyllabusStore(this.context, this.dispatchers)); }
  get focusStore() { return this._get('focusStore', () => new FocusStore(this.context, this.dispatchers)); }
  get studySessionStore() { return this._get('studySessionStore', () => new StudySessionStore(this.context, this.dispatchers)); }
  get remoteConfig() { return this._get('remoteConfig', () => new LocalRemoteConfig()); }
  /** Firebase is never configured here: Analytics.NoOp. */
  get analytics() { return this._get('analytics', () => FirebaseAppAnalytics.createOrNoOp(this.context)); }

  /** Pro entitlement (fake local billing, cached). isDebugBuild = true so the debug "force Pro" switch works. */
  get proRepository() {
    return this._get('proRepository', () =>
      new ProRepository(this.context, this.dispatchers, this.remoteConfig, this.appScope, true, this.analytics));
  }

  /** Opens the paywall, unless the paywall is switched off remotely (then nothing is locked). */
  openPaywall() { if (this.proRepository.paywallEnabled) this.paywallRequest.value = true; }

  /** The notifier behind AppFocusEffects; `.state` / `.complete` are StateFlows the shell can render. */
  get focusNotifier() { return this._get('focusNotifier', () => new FocusNotifier(this.context)); }

  /** The one path for Focus session changes: screen, notification actions, end alarm, page load. */
  get focusController() {
    return this._get('focusController', () => new FocusController({
      focusStore: this.focusStore,
      attemptRepository: this.attemptRepository,
      planRepository: this.planRepository,
      studySessionRepository: this.studySessionRepository,
      rollingPlanRepository: this.rollingPlanRepository,
      effects: new AppFocusEffects(this.focusNotifier, new FocusAlarm(this.context), this.settings),
      analytics: this.analytics,
    }));
  }

  // Canonical normalized architecture. Legacy stores remain migration inputs only.
  get examPackRepository() { return this._get('examPackRepository', () => new ExamPackRepository(this.context, this.dispatchers)); }
  get attemptRepository() { return this._get('attemptRepository', () => new AttemptRepository(this.context, this.dispatchers)); }
  get planRepository() { return this._get('planRepository', () => new PlanRepository(this.context, this.dispatchers, this.analytics)); }
  get studySessionRepository() { return this._get('studySessionRepository', () => new StudySessionRepository(this.context, this.dispatchers)); }
  get topicProgressRepository() { return this._get('topicProgressRepository', () => new TopicProgressRepository(this.context, this.dispatchers)); }
  get revisionRepository() { return this._get('revisionRepository', () => new RevisionRepository(this.context, this.dispatchers)); }
  get chapterStageRepository() { return this._get('chapterStageRepository', () => new ChapterStageRepository(this.context, this.dispatchers, this.analytics)); }
  get mockTestRepository() { return this._get('mockTestRepository', () => new MockTestRepository(this.context, this.dispatchers, this.analytics)); }
  get stageLabelStore() { return this._get('stageLabelStore', () => new StageLabelStore(this.context, this.dispatchers)); }
  get rollingPlanRepository() {
    return this._get('rollingPlanRepository', () =>
      new RollingPlanRepository(this.context, this.dispatchers, this.examPackRepository, this.analytics));
  }
  get targetSyllabusRepository() { return this._get('targetSyllabusRepository', () => new TargetSyllabusRepository(this.context, this.dispatchers)); }
  get availabilityRepository() { return this._get('availabilityRepository', () => new AvailabilityRepository(this.context, this.dispatchers)); }
  get studyPreferenceRepository() { return this._get('studyPreferenceRepository', () => new StudyPreferenceRepository(this.context, this.dispatchers)); }
  /** private in Kotlin */
  get migrationStore() { return this._get('migrationStore', () => new MigrationStore(this.context, this.dispatchers)); }
  get migrationRepository() {
    return this._get('migrationRepository', () => new MigrationRepository({
      context: this.context,
      dispatchers: this.dispatchers,
      planStore: this.planStore,
      studySessionStore: this.studySessionStore,
      syllabusStore: this.syllabusStore,
      migrationStore: this.migrationStore,
      attemptRepository: this.attemptRepository,
      examPackRepository: this.examPackRepository,
    }));
  }

  /** For work that must outlive a screen — a running focus session. */
  get appScope() { return this._get('appScope', () => new AppScope()); }

  /** Runs the one-time legacy-store migration off the critical path. Safe to call every launch. */
  runMigrationInBackground() {
    this.appScope.launch(async () => {
      await this.migrationRepository.migrateIfNeeded();
      await this.reconcileExpiredFocus();
      await this.rollingPlanRepository.replenish();
      this.refreshWidgetsOnChange();
    });
    this.appScope.launch(async () => {
      // Settings -> "Share anonymous usage data" drives analytics collection.
      this._unsubShare?.();
      this._unsubShare = this.settings.shareUsageData.subscribe((enabled) => this.analytics.setCollectionEnabled(enabled));
    });
    this.appScope.launch(async () => {
      // Restore on launch: picks up a purchase made elsewhere, or a refund.
      try { await this.proRepository.refreshPurchases(); } catch {}
    });
    this.appScope.launch(async () => {
      // The medium widget locks and unlocks with Pro. (drop(1): skip the initial value.)
      let first = true;
      this._unsubProWidget?.();
      this._unsubProWidget = this.proRepository.unlocked.subscribe(() => {
        if (first) { first = false; return; }
        AppWidgets.updateAll(this.context).catch(() => {});
      });
    });
  }

  /**
   * Widgets follow today's plan and coverage: any write to either redraws them, debounced.
   * (flatMapLatest over the active attempt, merge of plan-for-today and topic progress, debounce.)
   */
  refreshWidgetsOnChange() {
    this._unsubWidgets?.();
    let inner = null;
    let timer = null;
    const ping = () => {
      if (timer) clearTimeout(timer);
      timer = setTimeout(() => { timer = null; AppWidgets.updateAll(this.context).catch(() => {}); }, WIDGET_REFRESH_DEBOUNCE_MS);
    };
    const outer = this.attemptRepository.observeActiveAttempt().subscribe((attempt) => {
      inner?.forEach((u) => u());
      inner = attempt == null ? [] : [
        this.planRepository.observePlanForDate(attempt.id, localToday()).subscribe(ping),
        this.topicProgressRepository.observeAll(attempt.id).subscribe(ping),
      ];
      ping();
    });
    this._unsubWidgets = () => { outer(); inner?.forEach((u) => u()); if (timer) clearTimeout(timer); };
  }

  /**
   * The medium widget's Start: begin today's planned [blockId] in Focus, the way Today's Start does,
   * then open Focus. A session already running or paused is never replaced.
   */
  startPlannedBlock(blockId) {
    return this.appScope.launch(async () => {
      try {
        const current = await this.focusController.load();
        if (current.status === FocusStatus.RUNNING || current.status === FocusStatus.PAUSED) return;
        const block = (await this.loadTodaySnapshot()).blocks.find((it) => it.id === blockId);
        if (!block) return;
        const seconds = block.plannedMinutes * 60;
        await this.focusController.start(FocusSession({
          status: FocusStatus.RUNNING,
          durationSec: seconds,
          remainingSec: seconds,
          endsAtMs: nowMs() + seconds * 1000,
          completedToday: current.completedToday,
          block: FocusBlockRef({
            id: block.id,
            title: block.title.trim() === '' ? focus_notif_default_title : block.title,
            subtitle: block.isRevision ? formatAndroid(focus_revision_title, block.subjectName) : block.subjectName,
            tag: block.isRevision ? BlockTag.REVISE : BlockTag.READ,
            sessionId: block.id,
            nodeKey: block.nodeId,
            isRevision: block.isRevision,
          }),
        }));
      } catch (e) { console.error(e); } finally {
        this.openFocusRequest.value = true;
      }
    });
  }

  /** AppContainer.loadTodaySnapshot(today) — Kotlin extension in today/TodaySnapshot.kt. */
  loadTodaySnapshot(today = localToday()) { return loadTodaySnapshot(this, today); }

  /** Cold-start recovery: an expired timer earns credit exactly once; a live one gets its alarm back. */
  async reconcileExpiredFocus() {
    try { await this.focusController.reconcile(); } catch (e) { console.error(e); }
  }

  /** BOOT_COMPLETED and MY_PACKAGE_REPLACED. Never delivered on the web; kept for API parity. */
  async onBootOrUpdate() {
    try { await this.focusController.reconcile(); } catch {}
    await this.rearmDailyReminder();
  }

  get dailyReminder() { return this._get('dailyReminder', () => new DailyReminder(this.context)); }

  get backupRepository() {
    return this._get('backupRepository', () => new BackupRepository(this.context, this.dispatchers, this.attemptRepository, this.analytics));
  }

  /**
   * After an import replaced every table and store: drop this device's live timer, re-arm the reminder
   * from the imported setting, then relaunch (page reload) so every lazy object starts on the new data.
   */
  async onBackupImported() {
    try {
      await this.focusStore.clear();
      await this.focusController.reconcile();
    } catch {}
    await this.rearmDailyReminder();
    this.restartProcess();
  }

  /** Settings -> Log out. Wipes everything on the device, drops the live timer and reminder, relaunches. */
  async logOut() {
    await this.backupRepository.wipeAll();
    try {
      await this.focusStore.clear();
      await this.studySessionStore.clear();
      await this.syllabusStore.clear();
      await this.focusController.reconcile();
    } catch {}
    await this.rearmDailyReminder();
    try { this.focusNotifier.cancelAll(); this.dailyReminder.cancel(); } catch {}
    this.restartProcess();
  }

  /** private: Runtime.exit + relaunch == page reload. Overridable (tests). */
  restartProcess() {
    if (typeof location !== 'undefined' && typeof location.reload === 'function') location.reload();
  }

  /** Arms or cancels the daily "Today's plan" reminder from the saved setting. */
  async rearmDailyReminder() {
    try { this.dailyReminder.apply(await this.settings.dailyReminderOnce()); } catch {}
  }
}

/** The app-wide singleton (SteadylineApp.container). */
export const container = new AppContainer();
