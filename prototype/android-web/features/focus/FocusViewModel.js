// Port of feature/focus/FocusViewModel.kt
//
// Web stand-ins:
//  - viewModelScope.launch {} → fire-and-forget async fn (#launch); nothing runs after onCleared().
//  - transitions.collect {} is serial in Kotlin: transitions are applied one at a time on a promise chain.
//  - The ticker coroutine (delay(1000) loop) → an async loop over setTimeout, cancelled by stopTicker().
//  - System.currentTimeMillis() / LocalDate.now() → nowMs() / today() from domain/time.js (fake-clock aware).
//  - `state` (Kotlin name) is also exposed as `uiState` (CONTRACT.md name).
import { MutableStateFlow } from '../../platform/flow.js';
import { FocusController } from '../../data/FocusController.js';
import {
  FocusBlockRef, FocusSession, FocusStatus, FOCUS_LENGTH_MAX_MINUTES, FOCUS_LENGTH_MIN_MINUTES,
} from '../../domain/FocusSession.js';
import { StudyActivityType, PlanBlockStatus } from '../../domain/StudyPlan.js';
import { StudyPreferences } from '../../domain/StudyPreferences.js';
import { BlockTag } from '../../domain/TodaySchedule.js';
import { TopicProgressStatus } from '../../domain/TopicProgress.js';
import { chapterOf, findNode, leafIds, findSubjectOf } from '../../domain/ExamPack.js';
import { sortedBy } from '../../domain/kt.js';
import { nowMs, today } from '../../domain/time.js';
import { FocusQueueItem, FocusUiState, LearnPrompt } from './FocusUiState.js';

const CHECKPOINT_SECONDS = 60;

export class FocusViewModel {
  constructor(
    focusController,
    settingsStore,
    examPackRepository,
    attemptRepository,
    planRepository,
    studySessionRepository,
    rollingPlanRepository,
    studyPreferenceRepository,
    topicProgressRepository,
  ) {
    this.focusController = focusController;
    this.settingsStore = settingsStore;
    this.examPackRepository = examPackRepository;
    this.attemptRepository = attemptRepository;
    this.planRepository = planRepository;
    this.studySessionRepository = studySessionRepository;
    this.rollingPlanRepository = rollingPlanRepository;
    this.studyPreferenceRepository = studyPreferenceRepository;
    this.topicProgressRepository = topicProgressRepository;

    // StateFlow conflates equal values (data class equals).
    this._state = new MutableStateFlow(FocusUiState(), { equals: (a, b) => a.equals(b) });
    this.state = this._state.asStateFlow();
    this.uiState = this.state;

    this._remainingSeconds = new MutableStateFlow(0);
    this.remainingSeconds = this._remainingSeconds.asStateFlow();

    this.tickerJob = null;
    this.lastCheckpointFocusedSeconds = 0;
    this.selectedBlock = null;
    this.lastCompletedSessionId = null;
    /** A length picked on this screen; wins over a planned block's length until the session starts. */
    this.lengthOverrideMinutes = null;
    this._cleared = false;

    // Every change — from this screen, a notification action or the end alarm — arrives here.
    let chain = Promise.resolve();
    this._unsubTransitions = focusController.transitions.subscribe((t) => {
      chain = chain.then(() => (this._cleared ? null : this.applyTransition(t))).catch((e) => console.error(e));
    });
    this.#launch(async () => {
      const shown = await settingsStore.notificationPromptShown();
      this._state.update((it) => it.copy({ askNotificationPermission: !shown }));
    });
    this.refresh();
  }

  #launch(fn) {
    if (this._cleared) return;
    Promise.resolve().then(fn).catch((e) => console.error(e));
  }

  onCleared() {
    this._cleared = true;
    this._unsubTransitions?.();
    this.stopTicker();
  }

  /** Re-reads the clock from ends_at; called on creation and every time the screen resumes. */
  refresh() {
    this.#launch(async () => {
      const now = nowMs();
      const stored = await this.focusController.load();
      if (stored.isExpiredAt(now)) {
        await this.focusController.completeIfExpired(now);
        return;
      }
      let session = stored.withClockAt(now);
      if (session.status === FocusStatus.IDLE) {
        // The idle timer shows the one stored session length, even if Settings changed it since.
        const preferred = await this.preferredSessionSec();
        if (session.durationSec !== preferred) {
          session = session.copy({ durationSec: preferred, remainingSec: preferred });
          await this.focusController.setIdle(session);
          return;
        }
      }
      if (session.status === FocusStatus.DONE && this.lastCompletedSessionId == null) {
        // Completed by the alarm while this screen was gone: still offer the outcome and learn prompt.
        const completed = this.focusController.consumeCompletion();
        if (completed != null) {
          this.lastCompletedSessionId = completed.id;
          const prompt = await this.learnPromptFor(completed);
          this._state.update((it) => it.copy({ learnPrompt: prompt }));
        }
      }
      this.applySession(session);
      if (session.status === FocusStatus.RUNNING || session.status === FocusStatus.PAUSED) {
        await this.focusController.syncLive(now);
      }
      this.startTickerIfNeeded(session);
    });
  }

  /** Records that the notification permission prompt has been shown once. */
  markNotificationPromptShown() {
    this._state.update((it) => it.copy({ askNotificationPermission: false }));
    this.#launch(() => this.settingsStore.setNotificationPromptShown());
  }

  startSession() {
    this.#launch(async () => {
      const block = this.selectedBlock ?? (await this.nextBlock());
      let duration;
      if (this.lengthOverrideMinutes != null) duration = this.lengthOverrideMinutes * 60;
      else {
        const planned = block?.id != null ? (await this.planRepository.byId(block.id))?.plannedMinutes : null;
        duration = planned != null ? planned * 60 : await this.preferredSessionSec();
      }
      this.selectedBlock = null;
      this.lengthOverrideMinutes = null;
      const endsAtMs = nowMs() + duration * 1000;
      await this.focusController.start(FocusSession({
        status: FocusStatus.RUNNING,
        durationSec: duration,
        remainingSec: duration,
        endsAtMs,
        block,
        completedToday: (await this.focusController.load()).completedToday,
      }));
    });
  }

  pause() { this.#launch(() => this.focusController.pause()); }

  resume() { this.#launch(() => this.focusController.resume()); }

  requestStop() { this._state.update((it) => it.copy({ showStopDialog: true })); }

  /**
   * The length chips on the idle screen (§6.4): a preset or a custom 5–180 min. The choice is
   * used for the next start, even on a planned block, and saved as the default everywhere.
   */
  setDuration(minutes) {
    if (minutes < FOCUS_LENGTH_MIN_MINUTES || minutes > FOCUS_LENGTH_MAX_MINUTES) return;
    this.lengthOverrideMinutes = minutes;
    this._state.update((it) => it.copy({ durationMinutes: minutes }));
    this.#launch(async () => {
      await this.savePreferredSessionMinutes(minutes);
      const current = await this.focusController.load();
      if (current.status === FocusStatus.IDLE) {
        await this.focusController.setIdle(current.copy({ durationSec: minutes * 60, remainingSec: minutes * 60 }));
      }
    });
  }

  /**
   * Session length has one home: StudyPreferences.preferredSessionMinutes, which is also what
   * Settings edits and the planner reads. SettingsStore is only the fallback before any plan exists.
   */
  async preferredSessionSec() {
    const attempt = await this.attemptRepository.activeAttempt();
    const minutes = attempt ? (await this.studyPreferenceRepository.forAttempt(attempt.id))?.preferredSessionMinutes ?? null : null;
    return minutes != null ? minutes * 60 : await this.settingsStore.focusDurationSec();
  }

  async savePreferredSessionMinutes(minutes) {
    // Settings and Focus write the same stores (§6.4).
    await this.settingsStore.setFocusDurationSec(minutes * 60);
    const attempt = await this.attemptRepository.activeAttempt();
    if (!attempt) return;
    const current = (await this.studyPreferenceRepository.forAttempt(attempt.id)) ?? StudyPreferences({ attemptId: attempt.id });
    await this.studyPreferenceRepository.upsert(current.copy({ preferredSessionMinutes: minutes }));
  }

  selectQueueItem(id) {
    this.#launch(async () => {
      const chosen = await this.planRepository.byId(id);
      if (chosen == null) return;
      const chosenBlock = await this.resolveBlock(chosen);
      this.selectedBlock = chosenBlock;
      this._state.update((it) => it.copy({
        durationMinutes: this.lengthOverrideMinutes ?? chosen.plannedMinutes,
        blockTitle: chosenBlock.title,
        blockSubtitle: chosenBlock.subtitle,
        blockTag: chosenBlock.tag,
        hasBlock: true,
        queue: it.queue.filter((row) => row.id !== id),
      }));
    });
  }

  extendFiveMinutes() { this.#launch(() => this.focusController.extend(FocusController.EXTEND_SECONDS)); }

  dismissStopDialog() { this._state.update((it) => it.copy({ showStopDialog: false })); }

  recordOutcome(outcome) {
    const sessionId = this.lastCompletedSessionId;
    if (sessionId == null) return;
    this.#launch(async () => {
      await this.studySessionRepository.recordOutcome(sessionId, outcome, nowMs());
      this._state.update((it) => it.copy({ outcome }));
    });
  }

  /** "Yes" on the learned prompt: the same write as ticking Learned on the Syllabus screen. */
  markChapterLearned() {
    const prompt = this._state.value.learnPrompt;
    if (prompt == null) return;
    this._state.update((it) => it.copy({ learnPrompt: null }));
    this.#launch(async () => {
      const attempt = await this.attemptRepository.activeAttempt();
      if (!attempt) return;
      const chapter = chapterOf(await this.examPackRepository.examPackFor(attempt.examId, attempt.id), prompt.chapterId);
      if (!chapter) return;
      const now = nowMs();
      await this.topicProgressRepository.setCovered(attempt.id, leafIds(chapter), true, now);
      await this.rollingPlanRepository.replenish({ nowMs: now });
    });
  }

  dismissLearnPrompt() { this._state.update((it) => it.copy({ learnPrompt: null })); }

  /** Only a completed Learn session on a chapter that is not yet fully covered earns the prompt. */
  async learnPromptFor(session) {
    if (session == null || session.activityType !== StudyActivityType.LEARN) return null;
    // A titled session on a chapter is a coaching backlog lecture (§5.4), not Learn on the chapter.
    if (session.customTitle != null) return null;
    const nodeId = session.nodeId;
    if (nodeId == null) return null;
    const attempt = await this.attemptRepository.activeAttempt();
    if (!attempt) return null;
    const chapter = chapterOf(await this.examPackRepository.examPackFor(attempt.examId, attempt.id), nodeId);
    if (!chapter) return null;
    const covered = new Set((await this.topicProgressRepository.allOnce(attempt.id))
      .filter((it) => it.status === TopicProgressStatus.COVERED)
      .map((it) => it.nodeId));
    if (leafIds(chapter).every((it) => covered.has(it))) return null;
    return LearnPrompt({ chapterId: chapter.id, chapterTitle: chapter.title });
  }

  savePartialAndStop() {
    this.#launch(async () => {
      await this.focusController.stopSavingPartial();
      this.dismissStopDialog();
    });
  }

  discardAndStop() {
    this.#launch(async () => {
      await this.focusController.stopDiscarding();
      this.dismissStopDialog();
    });
  }

  startAnother() {
    this.#launch(async () => {
      const current = await this.focusController.load();
      await this.focusController.setIdle(current.copy({
        status: FocusStatus.IDLE,
        remainingSec: current.durationSec,
        endsAtMs: null,
        block: await this.nextBlock(),
      }));
      this.startSession();
    });
  }

  resetToIdle() {
    this.#launch(async () => {
      const current = await this.focusController.load();
      await this.focusController.setIdle(current.copy({
        status: FocusStatus.IDLE,
        remainingSec: current.durationSec,
        endsAtMs: null,
        block: await this.nextBlock(),
      }));
    });
  }

  async applyTransition(transition) {
    if (transition.didComplete) {
      this.focusController.consumeCompletion();
      this.lastCompletedSessionId = transition.completed?.id ?? null;
      const prompt = await this.learnPromptFor(transition.completed);
      this._state.update((it) => it.copy({ learnPrompt: prompt, outcome: null }));
      this.dismissStopDialog();
    }
    const session = transition.session.withClockAt(nowMs());
    this.applySession(session);
    this.startTickerIfNeeded(session);
  }

  async nextBlock() {
    return (await this.todayQueue())[0]?.[1] ?? null;
  }

  /** [StudyPlanBlock, FocusBlockRef][] */
  async todayQueue() {
    const attempt = await this.attemptRepository.activeAttempt();
    if (!attempt) return [];
    const planned = sortedBy(
      (await this.planRepository.planForDate(attempt.id, today())).filter((it) => it.status === PlanBlockStatus.PLANNED),
      (it) => it.startMinuteOfDay,
    );
    const out = [];
    for (const it of planned) out.push([it, await this.resolveBlock(it)]);
    return out;
  }

  async resolveBlock(block) {
    const attempt = await this.attemptRepository.activeAttempt();
    const pack = attempt ? await this.examPackRepository.examPackFor(attempt.examId, attempt.id) : null;
    const node = block.nodeId != null && pack ? findNode(pack, block.nodeId) : null;
    const subject = block.nodeId != null && pack ? findSubjectOf(pack, block.nodeId) : null;
    return toFocusBlock(block, block.customTitle ?? node?.title ?? 'Study session', subject?.name ?? '');
  }

  applySession(session) {
    const clocked = session;
    this._remainingSeconds.value = clocked.status === FocusStatus.DONE ? 0 : clocked.remainingSec;
    const block = clocked.block;
    this._state.update((it) => it.copy({
      loading: false,
      status: clocked.status,
      durationMinutes: Math.max(1, Math.trunc(clocked.durationSec / 60.0)),
      blockTitle: block?.title ?? '',
      blockSubtitle: block?.subtitle ?? '',
      blockTag: block?.tag ?? null,
      hasBlock: block != null,
      outcome: clocked.status === FocusStatus.DONE ? it.outcome : null,
      learnPrompt: clocked.status === FocusStatus.DONE ? it.learnPrompt : null,
    }));
    if (block == null && clocked.status === FocusStatus.IDLE) {
      this.#launch(async () => {
        const next = await this.nextBlock();
        const sessions = await this.todayQueue();
        this._state.update((it) => it.copy({
          blockTitle: next?.title ?? '',
          blockSubtitle: next?.subtitle ?? '',
          blockTag: next?.tag ?? null,
          hasBlock: next != null,
          durationMinutes: this.lengthOverrideMinutes
            ?? (next ? sessions.find((row) => row[0].id === next.id)?.[0]?.plannedMinutes : null)
            ?? it.durationMinutes,
          queue: sessions
            .filter((row) => row[0].id !== next?.id)
            .slice(0, 6)
            .map(([plan, b]) => toFocusQueueItem(plan, b)),
        }));
      });
    } else if (clocked.status === FocusStatus.IDLE) {
      this.#launch(async () => {
        const sessions = await this.todayQueue();
        this._state.update((current) => current.copy({
          queue: sessions
            .filter((row) => row[0].id !== block?.id)
            .slice(0, 6)
            .map(([plan, rowBlock]) => toFocusQueueItem(plan, rowBlock)),
        }));
      });
    }
  }

  startTickerIfNeeded(session) {
    this.stopTicker();
    if (session.status !== FocusStatus.RUNNING) return;
    this.lastCheckpointFocusedSeconds = session.focusedSecondsAt(nowMs());
    // The UI clock only. Completion is guaranteed by the end alarm and by refresh().
    const job = { active: true, timer: null };
    this.tickerJob = job;
    const delay = (ms) => new Promise((resolve) => { job.timer = setTimeout(resolve, ms); });
    (async () => {
      while (job.active && !this._cleared) {
        await delay(1000);
        if (!job.active || this._cleared) break;
        const now = nowMs();
        if (session.isExpiredAt(now)) {
          this._remainingSeconds.value = 0;
          await this.focusController.completeIfExpired(now);
          break;
        }
        const snapshot = session.withClockAt(now);
        this._remainingSeconds.value = snapshot.remainingSec;
        const focusedSeconds = snapshot.focusedSecondsAt(now);
        if (focusedSeconds - this.lastCheckpointFocusedSeconds >= CHECKPOINT_SECONDS) {
          await this.focusController.checkpoint(session.endsAtMs, now);
          this.lastCheckpointFocusedSeconds = focusedSeconds;
        }
      }
    })().catch((e) => console.error(e));
  }

  stopTicker() {
    if (this.tickerJob) {
      this.tickerJob.active = false;
      clearTimeout(this.tickerJob.timer);
    }
    this.tickerJob = null;
  }
}

/** FocusViewModel.Factory */
FocusViewModel.Factory = class {
  constructor(...deps) { this.deps = deps; }
  create() { return new FocusViewModel(...this.deps); }
};

function focusTag(block) {
  switch (block.activityType) {
    case StudyActivityType.REVISION: return BlockTag.REVISE;
    case StudyActivityType.PRACTICE:
    case StudyActivityType.MOCK_TEST: return BlockTag.PRACTICE;
    default: return BlockTag.READ;
  }
}

function toFocusBlock(block, title, subtitle) {
  return FocusBlockRef({
    id: block.id,
    title,
    subtitle,
    tag: focusTag(block),
    sessionId: '',
    nodeKey: block.nodeId ?? '',
    isRevision: block.activityType === StudyActivityType.REVISION,
  });
}

function toFocusQueueItem(plan, block) {
  return FocusQueueItem({
    id: plan.id,
    title: block.title,
    subtitle: block.subtitle,
    tag: focusTag(plan),
    durationMinutes: plan.plannedMinutes,
    completed: plan.status === PlanBlockStatus.COMPLETED,
  });
}
