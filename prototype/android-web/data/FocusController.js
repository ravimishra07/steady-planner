// Port of core/data/FocusController.kt. Same state machine; every mutation serialised on one Mutex.
//
// Changed / stubbed:
//  - suspend funs -> async. Funs with a `nowMs` default take it positionally (or omit it).
//  - MutableSharedFlow<FocusTransition> `transitions` -> a tiny SharedFlow: `transitions.subscribe(fn)`
//    returns unsubscribe; no replay (like extraBufferCapacity without replay). `transitions.value` is
//    NOT provided — use load() for state.
//  - FocusEffects is the platform hook (alarm + notifications); data/app/AppFocusEffects.js implements
//    it with setTimeout (end alarm -> reconcile) and an in-page notification flow.
//  - Process death == page reload: FocusStore keeps ends_at, and AppContainer runs reconcile() on start.
import { nowMs as clockNowMs, epochMsToLocalDate, systemZoneId } from '../domain/time.js';
import { dataClass, coerceAtLeast } from '../domain/kt.js';
import { FocusStatus } from '../domain/FocusSession.js';
import { StudyActivityType } from '../domain/StudyPlan.js';
import { StudySession, StudySessionSegment, StudySessionStatus } from '../domain/StudyHistory.js';
import { Analytics, AnalyticsEvent, AnalyticsParam, Mutex, mapOf, randomUUID } from './common.js';

/**
 * Platform side effects of a Focus state change. interface FocusEffects {
 *   async onSessionChanged(session)   running arms the alarm + ongoing notification; anything else clears them
 *   async onSessionCompleted(session) posts the "Session complete" alert
 * }
 */
export const FocusEffects = Object.freeze({
  None: Object.freeze({ async onSessionChanged(_s) {}, async onSessionCompleted(_s) {} }),
});

/** One persisted Focus change. [completed] is the session closed by a natural end, if any. */
export const FocusTransition = dataClass({ session: undefined, didComplete: false, completed: null });

/** Minimal MutableSharedFlow (no replay). */
class SharedFlow {
  constructor() { this._subs = new Set(); }
  subscribe(fn) { this._subs.add(fn); return () => this._subs.delete(fn); }
  tryEmit(v) { for (const fn of [...this._subs]) { try { fn(v); } catch (e) { console.error(e); } } return true; }
}

const isLive = (s) => s.status === FocusStatus.RUNNING || s.status === FocusStatus.PAUSED;

/**
 * The single owner of Focus session mutations: FocusStore (the clock) plus the normalised session,
 * plus the alarm and notification side effects.
 */
export class FocusController {
  constructor({ focusStore, attemptRepository, planRepository, studySessionRepository, rollingPlanRepository, effects, analytics = Analytics.NoOp }) {
    this.focusStore = focusStore;
    this.attemptRepository = attemptRepository;
    this.planRepository = planRepository;
    this.studySessionRepository = studySessionRepository;
    this.rollingPlanRepository = rollingPlanRepository;
    this.effects = effects;
    this.analytics = analytics;
    this.mutex = new Mutex();
    this._transitions = new SharedFlow();
    /** Every persisted change, for a live Focus screen to follow changes made from a notification or alarm. */
    this.transitions = { subscribe: (fn) => this._transitions.subscribe(fn) };
    this.unseenCompletion = null;
  }

  async load() { return this.focusStore.load(); }

  /** The last natural completion no screen has shown yet, cleared on read. */
  consumeCompletion() {
    const value = this.unseenCompletion;
    this.unseenCompletion = null;
    return value;
  }

  /** Starts (or adopts) a running or paused session. */
  start(session) {
    return this.mutex.withLock(async () => {
      const previous = await this.focusStore.load();
      const adopting = isLive(previous) && (previous.block?.id ?? null) === (session.block?.id ?? null);
      if (!adopting) {
        this.analytics.log(AnalyticsEvent.BLOCK_STARTED, mapOf(
          [AnalyticsParam.PLANNED, session.block != null],
          [AnalyticsParam.IS_REVISION, session.block?.isRevision === true],
        ));
      }
      await this.focusStore.save(session);
      await this.ensureNormalizedSession(session, clockNowMs());
      return this.publish(FocusTransition({ session }));
    });
  }

  /** Saves a session that is not running — idle or a changed idle length. Clears alarm and notifications. */
  setIdle(session) {
    return this.mutex.withLock(async () => {
      await this.focusStore.save(session);
      return this.publish(FocusTransition({ session }));
    });
  }

  pause(nowMs = clockNowMs()) {
    return this.mutex.withLock(async () => {
      const stored = await this.focusStore.load();
      if (stored.isExpiredAt(nowMs)) return this.completeLocked(stored, nowMs);
      const current = stored.withClockAt(nowMs);
      if (current.status !== FocusStatus.RUNNING) return null;
      const session = current.copy({ status: FocusStatus.PAUSED, endsAtMs: null });
      await this.focusStore.save(session);
      await this.syncNormalizedSession(session, nowMs);
      return this.publish(FocusTransition({ session }));
    });
  }

  resume(nowMs = clockNowMs()) {
    return this.mutex.withLock(async () => {
      const current = await this.focusStore.load();
      if (current.status !== FocusStatus.PAUSED) return null;
      const session = current.copy({ status: FocusStatus.RUNNING, endsAtMs: nowMs + current.remainingSec * 1000 });
      await this.focusStore.save(session);
      await this.syncNormalizedSession(session, nowMs);
      return this.publish(FocusTransition({ session }));
    });
  }

  extend(seconds, nowMs = clockNowMs()) {
    return this.mutex.withLock(async () => {
      const stored = await this.focusStore.load();
      if (stored.isExpiredAt(nowMs)) return this.completeLocked(stored, nowMs);
      const current = stored.withClockAt(nowMs);
      if (!isLive(current)) return null;
      const updated = current.copy({
        durationSec: current.durationSec + seconds,
        remainingSec: current.remainingSec + seconds,
        endsAtMs: current.endsAtMs != null ? current.endsAtMs + seconds * 1000 : null,
      });
      await this.focusStore.save(updated);
      await this.syncNormalizedSession(updated, nowMs);
      return this.publish(FocusTransition({ session: updated }));
    });
  }

  /** Stop and keep the focused time as a partial session. */
  stopSavingPartial(nowMs = clockNowMs()) {
    return this.mutex.withLock(async () => {
      const current = await this.focusStore.load();
      if (current.isExpiredAt(nowMs)) return this.completeLocked(current, nowMs);
      if (!isLive(current)) return null;
      const snapshot = current.withClockAt(nowMs);
      await this.persistPartialSession(snapshot, nowMs);
      return this.finishStoppedLocked(snapshot);
    });
  }

  /** Stop and throw the session away. */
  stopDiscarding(nowMs = clockNowMs()) {
    return this.mutex.withLock(async () => {
      const current = await this.focusStore.load();
      if (current.isExpiredAt(nowMs)) return this.completeLocked(current, nowMs);
      if (!isLive(current)) return null;
      await this.abandonNormalizedSession(nowMs);
      return this.finishStoppedLocked(current);
    });
  }

  /** Completes a running session whose end time has passed. Null when there was nothing to complete. */
  completeIfExpired(nowMs = clockNowMs()) {
    return this.mutex.withLock(async () => {
      const current = await this.focusStore.load();
      if (!current.isExpiredAt(nowMs)) return null;
      return this.completeLocked(current, nowMs);
    });
  }

  /** Writes focused seconds so far, if the stored session is still the one being ticked. */
  checkpoint(expectedEndsAtMs, nowMs = clockNowMs()) {
    return this.mutex.withLock(async () => {
      const stored = await this.focusStore.load();
      if (stored.status !== FocusStatus.RUNNING || stored.endsAtMs !== expectedEndsAtMs) return;
      if (stored.isExpiredAt(nowMs)) return;
      await this.syncNormalizedSession(stored.withClockAt(nowMs), nowMs);
    });
  }

  /** Opening the Focus screen: makes sure storage mirrors a live session. */
  syncLive(nowMs = clockNowMs()) {
    return this.mutex.withLock(async () => {
      const stored = await this.focusStore.load();
      if (!isLive(stored)) return;
      if (stored.isExpiredAt(nowMs)) return;
      await this.syncNormalizedSession(stored.withClockAt(nowMs), nowMs);
    });
  }

  /**
   * Cold start (page load), the end alarm, boot and app update: an expired session is completed
   * exactly once; a live one gets its alarm and notification back.
   */
  reconcile(nowMs = clockNowMs()) {
    return this.mutex.withLock(async () => {
      const stored = await this.focusStore.load();
      if (stored.isExpiredAt(nowMs)) return this.completeLocked(stored, nowMs);
      const session = stored.withClockAt(nowMs);
      try { await this.effects.onSessionChanged(session); } catch {}
      return FocusTransition({ session });
    });
  }

  async completeLocked(current, nowMs) {
    await this.ensureNormalizedSession(current, nowMs);
    const completed = await this.completeNormalizedSession(current.durationSec, nowMs);
    try { await this.rollingPlanRepository.replenish({ nowMs }); } catch (e) { console.error(e); }
    const session = current.copy({
      status: FocusStatus.DONE,
      remainingSec: 0,
      endsAtMs: null,
      completedToday: current.completedToday + 1,
    });
    await this.focusStore.save(session);
    this.unseenCompletion = completed;
    const isRevision = current.block?.isRevision === true;
    this.analytics.log(AnalyticsEvent.BLOCK_COMPLETED, mapOf(
      [AnalyticsParam.PLANNED, current.block != null],
      [AnalyticsParam.IS_REVISION, isRevision],
    ));
    if (isRevision) this.analytics.log(AnalyticsEvent.REVISION_COMPLETED, mapOf([AnalyticsParam.SOURCE, 'focus']));
    try { await this.effects.onSessionCompleted(session); } catch {}
    return this.publish(FocusTransition({ session, didComplete: true, completed }));
  }

  async finishStoppedLocked(current) {
    const session = current.copy({ status: FocusStatus.IDLE, remainingSec: current.durationSec, endsAtMs: null, block: null });
    await this.focusStore.save(session);
    return this.publish(FocusTransition({ session }));
  }

  async publish(transition) {
    try { await this.effects.onSessionChanged(transition.session); } catch {}
    this._transitions.tryEmit(transition);
    return transition;
  }

  async persistPartialSession(focus, nowMs) {
    const attempt = await this.attemptRepository.activeAttempt();
    if (!attempt) return;
    let active = await this.studySessionRepository.activeSession(attempt.id);
    if (active == null) {
      await this.ensureNormalizedSession(focus, nowMs);
      active = await this.studySessionRepository.activeSession(attempt.id);
      if (active == null) return;
    }
    const focusedSeconds = focus.focusedSecondsAt(nowMs);
    if (focusedSeconds > 0) {
      await this.studySessionRepository.completePartialSession(
        active.copy({ focusedSeconds: coerceAtLeast(focusedSeconds, active.focusedSeconds) }), nowMs);
    } else {
      await this.studySessionRepository.abandonSession(active, nowMs);
    }
    await this.rollingPlanRepository.replenish({ nowMs });
  }

  async ensureNormalizedSession(focus, nowMs) {
    if (!isLive(focus)) return;
    const attempt = await this.attemptRepository.activeAttempt();
    if (!attempt) return;
    const existing = await this.studySessionRepository.activeSession(attempt.id);
    if (existing != null) return;
    const block = focus.block;
    const planBlock = block != null ? await this.planRepository.byId(block.id) : null;
    const focusedSeconds = focus.focusedSecondsAt(nowMs);
    const startedAt = focus.endsAtMs != null ? focus.endsAtMs - focus.durationSec * 1000 : nowMs - focusedSeconds * 1000;
    const nodeKey = block?.nodeKey;
    const session = StudySession({
      id: `focus_${randomUUID()}`,
      attemptId: attempt.id,
      // block?.nodeKey?.ifBlank { planBlock?.nodeId }
      nodeId: nodeKey == null ? null : (nodeKey.trim() === '' ? (planBlock?.nodeId ?? null) : nodeKey),
      subjectId: planBlock?.subjectId ?? null,
      planBlockId: planBlock?.id ?? null,
      activityType: block?.isRevision === true ? StudyActivityType.REVISION : (planBlock?.activityType ?? StudyActivityType.LEARN),
      startedAtEpochMs: startedAt,
      endedAtEpochMs: null,
      studyDate: epochMsToLocalDate(startedAt),
      timeZoneId: systemZoneId(),
      focusedSeconds,
      pausedSeconds: 0,
      status: focus.status === FocusStatus.PAUSED ? StudySessionStatus.PAUSED : StudySessionStatus.RUNNING,
      focusLockUsed: false,
      interruptionCount: 0,
      customTitle: planBlock?.nodeId == null ? (block?.title ?? 'Focus session') : planBlock.customTitle,
      createdAtEpochMs: nowMs,
      updatedAtEpochMs: nowMs,
    });
    await this.studySessionRepository.startSession(session, StudySessionSegment({
      id: `${session.id}_0`,
      sessionId: session.id,
      nodeId: session.nodeId,
      subjectId: session.subjectId,
      startedAtEpochMs: startedAt,
      endedAtEpochMs: null,
      focusedSeconds: 0,
      order: 0,
    }));
  }

  async syncNormalizedSession(focus, nowMs) {
    const attempt = await this.attemptRepository.activeAttempt();
    if (!attempt) return;
    const active = await this.studySessionRepository.activeSession(attempt.id);
    if (active == null) { await this.ensureNormalizedSession(focus, nowMs); return; }
    await this.studySessionRepository.updateRunning(active.copy({
      focusedSeconds: coerceAtLeast(focus.focusedSecondsAt(nowMs), active.focusedSeconds),
      status: focus.status === FocusStatus.PAUSED ? StudySessionStatus.PAUSED : StudySessionStatus.RUNNING,
      updatedAtEpochMs: nowMs,
    }));
  }

  async completeNormalizedSession(focusedSeconds, nowMs) {
    const attempt = await this.attemptRepository.activeAttempt();
    if (!attempt) return null;
    const active = await this.studySessionRepository.activeSession(attempt.id);
    if (!active) return null;
    return this.studySessionRepository.completeSession(
      active.copy({ focusedSeconds: coerceAtLeast(focusedSeconds, active.focusedSeconds) }), active.studyDate, nowMs);
  }

  async abandonNormalizedSession(nowMs) {
    const attempt = await this.attemptRepository.activeAttempt();
    if (!attempt) return;
    const active = await this.studySessionRepository.activeSession(attempt.id);
    if (!active) return;
    await this.studySessionRepository.abandonSession(active, nowMs);
  }
}
FocusController.EXTEND_SECONDS = 5 * 60;
