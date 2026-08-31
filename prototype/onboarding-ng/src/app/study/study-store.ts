import { Injectable, computed, inject } from '@angular/core';
import { persisted, persistedMap } from '../core/persist';
import { OnboardingStore, addDays, startOfToday } from '../onboarding/state';
import { ALL_CHAPTERS, chapterIsDone } from '../onboarding/exam-pack';

export type Task = 'Learn' | 'Practice' | 'Revise';

/** One sitting the user actually did. The only source of "done" minutes. */
export interface LoggedSession {
  id: string;
  /** yyyy-mm-dd, the key the timeline groups by. */
  dateKey: string;
  chapterId: string;
  subtopicId?: string;
  title: string;
  task: Task;
  minutes: number;
  attempted?: number;
  correct?: number;
}

/** Per-chapter state beyond done / not-done. */
export interface ChapterStat {
  /** Revision passes completed after the first learn. R1, R2, R3. */
  revisions: number;
  attempted: number;
  correct: number;
  /** dateKey of the last time the chapter was touched at all. */
  lastTouched: string | null;
}

/** A block the user added into a free slot themselves. */
export interface ExtraBlock {
  id: string;
  dateKey: string;
  startMinute: number;
  minutes: number;
  task: Task;
  chapterId: string;
  subtopicId?: string;
}

export function dateKey(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

export function parseKey(key: string): Date {
  const [y, m, d] = key.split('-').map(Number);
  return new Date(y, m - 1, d);
}

const EMPTY_STAT: ChapterStat = { revisions: 0, attempted: 0, correct: 0, lastTouched: null };

@Injectable({ providedIn: 'root' })
export class StudyStore {
  private readonly onboarding = inject(OnboardingStore);

  readonly sessions = persisted<LoggedSession[]>('sessions', []);
  readonly stats = persistedMap<ChapterStat>('chapter-stats');
  readonly extras = persisted<ExtraBlock[]>('extras', []);

  addExtra(extra: Omit<ExtraBlock, 'id'>): void {
    this.extras.set([...this.extras(), { ...extra, id: crypto.randomUUID() }]);
  }

  removeExtra(id: string): void {
    this.extras.set(this.extras().filter((e) => e.id !== id));
  }

  extrasOn(key: string): ExtraBlock[] {
    return this.extras().filter((e) => e.dateKey === key);
  }

  /** Whether a planned block has already been logged on that day. */
  isLogged(key: string, chapterId: string, task: Task, subtopicId?: string): boolean {
    return this.sessions().some(
      (s) =>
        s.dateKey === key &&
        s.chapterId === chapterId &&
        s.task === task &&
        (subtopicId === undefined || s.subtopicId === subtopicId),
    );
  }

  stat(chapterId: string): ChapterStat {
    return this.stats().get(chapterId) ?? EMPTY_STAT;
  }

  private patch(chapterId: string, change: Partial<ChapterStat>): void {
    const next = new Map(this.stats());
    next.set(chapterId, { ...this.stat(chapterId), ...change });
    this.stats.set(next);
  }

  /** Logging a sitting is what moves everything else: minutes, rounds, ticks. */
  log(session: Omit<LoggedSession, 'id'>): void {
    this.sessions.set([...this.sessions(), { ...session, id: crypto.randomUUID() }]);

    const stat = this.stat(session.chapterId);
    this.patch(session.chapterId, {
      lastTouched: session.dateKey,
      attempted: stat.attempted + (session.attempted ?? 0),
      correct: stat.correct + (session.correct ?? 0),
      revisions: session.task === 'Revise' ? Math.min(3, stat.revisions + 1) : stat.revisions,
    });

    // A finished Learn block ticks the subtopic it covered, so the syllabus
    // percentage and the logged hours can never disagree again.
    if (session.task === 'Learn' && session.subtopicId) {
      this.onboarding.toggleUnitOn(session.subtopicId);
    }
  }

  minutesOn(key: string): number {
    return this.sessions().filter((s) => s.dateKey === key).reduce((n, s) => n + s.minutes, 0);
  }

  sessionsOn(key: string): LoggedSession[] {
    return this.sessions().filter((s) => s.dateKey === key);
  }

  /** Subtopics already covered by a logged Learn block, for de-duping picks. */
  readonly learnedSubtopics = computed(
    () => new Set(this.sessions().filter((s) => s.subtopicId).map((s) => s.subtopicId!)),
  );

  readonly accuracy = computed(() => {
    let attempted = 0;
    let correct = 0;
    for (const s of this.stats().values()) {
      attempted += s.attempted;
      correct += s.correct;
    }
    return attempted === 0 ? null : Math.round((correct / attempted) * 100);
  });

  /** Chapters at each revision depth — the shape a flat percentage hides. */
  readonly rounds = computed(() => {
    const done = this.onboarding.doneUnits();
    const learned = ALL_CHAPTERS.filter((c) => chapterIsDone(c, done));
    const at = (n: number) => learned.filter((c) => this.stat(c.id).revisions >= n).length;
    return { learned: learned.length, r1: at(1), r2: at(2), r3: at(3), total: ALL_CHAPTERS.length };
  });

  /* ---- Insight series ------------------------------------------------
   * Everything the Progress screen draws comes from here, so the numbers on
   * a chart and the numbers in a tile can never drift apart.
   */

  /** Minutes logged per day, the base series for streaks and the heatmap. */
  readonly minutesByDay = computed(() => {
    const out = new Map<string, number>();
    for (const s of this.sessions()) out.set(s.dateKey, (out.get(s.dateKey) ?? 0) + s.minutes);
    return out;
  });

  /** Consecutive days up to today with anything logged. Today may be empty. */
  readonly streak = computed(() => {
    const byDay = this.minutesByDay();
    const today = startOfToday();
    let current = 0;
    for (let i = byDay.get(dateKey(today)) ? 0 : 1; i < 400; i++) {
      if (!byDay.get(dateKey(addDays(today, -i)))) break;
      current++;
    }

    let longest = 0;
    let run = 0;
    const keys = [...byDay.keys()].sort();
    let previous: string | null = null;
    for (const key of keys) {
      run = previous && dateKey(addDays(parseKey(previous), 1)) === key ? run + 1 : 1;
      longest = Math.max(longest, run);
      previous = key;
    }
    return { current, longest };
  });

  /** One cell per day for the last `weeks` weeks, Sunday-aligned. */
  heatmap(weeks: number): { key: string; date: Date; minutes: number; future: boolean }[] {
    const byDay = this.minutesByDay();
    const today = startOfToday();
    const end = addDays(today, 6 - today.getDay());
    const start = addDays(end, -(weeks * 7 - 1));
    return Array.from({ length: weeks * 7 }, (_, i) => {
      const date = addDays(start, i);
      const key = dateKey(date);
      return { key, date, minutes: byDay.get(key) ?? 0, future: date > today };
    });
  }

  /** Daily minutes for the last `days` days, oldest first. */
  daily(days: number): { key: string; date: Date; minutes: number }[] {
    const byDay = this.minutesByDay();
    const today = startOfToday();
    return Array.from({ length: days }, (_, i) => {
      const date = addDays(today, -(days - 1 - i));
      const key = dateKey(date);
      return { key, date, minutes: byDay.get(key) ?? 0 };
    });
  }

  /**
   * Rolling seven-day windows ending today, most recent last. A calendar week
   * reads as a collapse every Monday morning, which is not what happened.
   */
  rollingWeeks(weeks: number): { start: Date; minutes: number }[] {
    const byDay = this.minutesByDay();
    const today = startOfToday();
    return Array.from({ length: weeks }, (_, i) => {
      const start = addDays(today, -((weeks - i) * 7 - 1));
      let minutes = 0;
      for (let d = 0; d < 7; d++) minutes += byDay.get(dateKey(addDays(start, d))) ?? 0;
      return { start, minutes };
    });
  }

  /** Average minutes a day over the window, counting the empty days too. */
  averageMinutes(days: number): number {
    const series = this.daily(days);
    return series.reduce((n, d) => n + d.minutes, 0) / days;
  }

  /** Chapters with enough questions behind them to judge, weakest first. */
  readonly weakChapters = computed(() =>
    ALL_CHAPTERS.map((chapter) => ({ chapter, stat: this.stat(chapter.id) }))
      .filter((row) => row.stat.attempted >= 20)
      .map((row) => ({ ...row, rate: row.stat.correct / row.stat.attempted }))
      .sort((a, b) => a.rate - b.rate),
  );

  /** Learnt, never revised, and not touched in a while — the quiet backlog. */
  readonly staleChapters = computed(() => {
    const done = this.onboarding.doneUnits();
    const cutoff = dateKey(addDays(startOfToday(), -10));
    return ALL_CHAPTERS.filter((c) => chapterIsDone(c, done))
      .map((chapter) => ({ chapter, stat: this.stat(chapter.id) }))
      .filter((row) => row.stat.revisions === 0 && (row.stat.lastTouched ?? '') < cutoff)
      .sort((a, b) => (a.stat.lastTouched ?? '').localeCompare(b.stat.lastTouched ?? ''));
  });
}

