import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { persisted } from '../core/persist';
import { startOfToday } from '../onboarding/state';
import { StudyStore, dateKey } from '../study/study-store';
import { Recall } from '../study/retention';
import { FocusStatus, FocusTarget } from '../domain/focus/models';
import { FocusRepository } from '../data/contracts/focus-repository';
export type { FocusStatus, FocusTarget } from '../domain/focus/models';

interface Persisted {
  status: FocusStatus;
  target: FocusTarget | null;
  durationSec: number;
  /** Wall-clock the timer ends at. Survives a reload; a paused timer has none. */
  endsAt: number | null;
  /** Seconds left when paused. */
  pausedAt: number | null;
  /** Seconds actually spent, accumulated across pauses. */
  elapsedSec: number;
  /** Wall clock at which the current running segment began. */
  segmentStartedAt: number | null;
}

const BLANK: Persisted = {
  status: 'idle',
  target: null,
  durationSec: 0,
  endsAt: null,
  pausedAt: null,
  elapsedSec: 0,
  segmentStartedAt: null,
};

/** A sitting shorter than this is not worth logging as study. */
const MIN_LOGGABLE_SEC = 60;

export function clock(seconds: number): string {
  const safe = Math.max(0, Math.round(seconds));
  return `${Math.floor(safe / 60)}:${String(safe % 60).padStart(2, '0')}`;
}

/**
 * The focus session. It owns the clock and, when it ends, writes the sitting —
 * so a finished timer is the app's input, not a thing to remember to log after.
 */
@Injectable({ providedIn: 'root' })
export class FocusStore implements FocusRepository {
  private readonly study = inject(StudyStore);

  private readonly state = persisted<Persisted>('focus', BLANK);
  /** Ticks while a session runs; nothing subscribes to it otherwise. */
  private readonly now = signal(Date.now());
  private timer: ReturnType<typeof setInterval> | null = null;

  readonly status = computed(() => this.state().status);
  readonly target = computed(() => this.state().target);
  readonly durationSec = computed(() => this.state().durationSec);

  readonly remainingSec = computed(() => {
    const s = this.state();
    if (s.status === 'paused') return s.pausedAt ?? 0;
    if (s.status !== 'running' || s.endsAt === null) return s.durationSec;
    return Math.max(0, Math.round((s.endsAt - this.now()) / 1000));
  });

  /** Fraction run, for the dial. */
  readonly progress = computed(() => {
    const total = this.durationSec();
    if (total <= 0) return 0;
    return Math.min(1, 1 - this.remainingSec() / total);
  });

  /** Minutes actually sat through, which is what gets logged. */
  readonly spentMinutes = computed(() =>
    Math.round((this.state().elapsedSec + this.currentSegmentSec()) / 60),
  );

  readonly sittingsToday = computed(
    () => this.study.sessionsOn(dateKey(startOfToday())).length,
  );

  constructor() {
    // One interval, alive only while a session is actually running.
    effect(() => {
      const running = this.state().status === 'running';
      if (running && this.timer === null) {
        this.timer = setInterval(() => this.now.set(Date.now()), 1000);
      } else if (!running && this.timer !== null) {
        clearInterval(this.timer);
        this.timer = null;
      }
    });

    // A timer that ran out — while the app was open or closed — is finished,
    // and a session run to completion is worth its whole duration.
    effect(() => {
      const s = this.state();
      if (s.status === 'running' && this.remainingSec() <= 0) {
        this.state.set({
          ...s,
          status: 'done',
          elapsedSec: s.elapsedSec + this.currentSegmentSec(),
          endsAt: null,
          segmentStartedAt: null,
        });
      }
    });
  }

  start(target: FocusTarget, minutes: number): void {
    const durationSec = Math.max(60, Math.round(minutes * 60));
    this.now.set(Date.now());
    this.state.set({
      status: 'running',
      target,
      durationSec,
      endsAt: Date.now() + durationSec * 1000,
      pausedAt: null,
      elapsedSec: 0,
      segmentStartedAt: Date.now(),
    });
  }

  pause(): void {
    const s = this.state();
    if (s.status !== 'running') return;
    this.state.set({
      ...s,
      status: 'paused',
      pausedAt: this.remainingSec(),
      elapsedSec: s.elapsedSec + this.currentSegmentSec(),
      endsAt: null,
      segmentStartedAt: null,
    });
  }

  resume(): void {
    const s = this.state();
    if (s.status !== 'paused') return;
    const left = s.pausedAt ?? s.durationSec;
    this.now.set(Date.now());
    this.state.set({
      ...s,
      status: 'running',
      endsAt: Date.now() + left * 1000,
      pausedAt: null,
      segmentStartedAt: Date.now(),
    });
  }

  /** Add time to a session already under way. */
  extend(minutes: number): void {
    const s = this.state();
    if (s.status === 'idle' || s.status === 'done') return;
    const add = minutes * 60;
    this.state.set({
      ...s,
      durationSec: s.durationSec + add,
      endsAt: s.endsAt === null ? null : s.endsAt + add * 1000,
      pausedAt: s.pausedAt === null ? null : s.pausedAt + add,
    });
  }

  /**
   * Stop early. The time actually sat through is still logged — bailing at
   * twelve minutes should be worth twelve minutes, not nothing.
   */
  stop(): void {
    const s = this.state();
    this.state.set({
      ...s,
      status: 'done',
      elapsedSec: s.elapsedSec + this.currentSegmentSec(),
      endsAt: null,
      pausedAt: null,
      segmentStartedAt: null,
    });
  }

  /** Write the sitting and clear the session. */
  finish(recall: Recall, attempted?: number, correct?: number): number {
    const s = this.state();
    const target = s.target;
    const seconds = s.elapsedSec + this.currentSegmentSec();
    if (!target || seconds < MIN_LOGGABLE_SEC) {
      this.reset();
      return 0;
    }

    const minutes = Math.max(1, Math.round(seconds / 60));
    this.study.log({
      dateKey: dateKey(startOfToday()),
      chapterId: target.chapterId,
      subtopicId: target.subtopicId,
      title: target.title,
      task: target.task,
      minutes,
      attempted: target.task === 'Practice' ? attempted : undefined,
      correct: target.task === 'Practice' ? correct : undefined,
      recall,
    });
    this.reset();
    return minutes;
  }

  /** Throw the session away without logging anything. */
  discard(): void {
    this.reset();
  }

  private reset(): void {
    this.state.set({ ...BLANK });
  }

  /** Seconds elapsed in the current run, zero unless running. */
  private currentSegmentSec(): number {
    const s = this.state();
    if (s.status !== 'running' || s.endsAt === null) return 0;
    if (s.segmentStartedAt !== null && s.segmentStartedAt !== undefined) {
      return Math.max(0, Math.round((this.now() - s.segmentStartedAt) / 1000));
    }
    // Compatibility for sessions persisted before segmentStartedAt existed.
    return Math.max(0, s.durationSec - this.remainingSec());
  }
}
