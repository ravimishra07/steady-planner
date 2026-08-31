import { Injectable, computed, inject, signal } from '@angular/core';
import { COACHINGS, OnboardingStore, startOfToday } from '../onboarding/state';
import { Chapter } from '../onboarding/exam-pack';
import { availableChapters } from '../onboarding/sequence';
import { StudyStore, dateKey } from '../study/study-store';
import { Block, StudyBlock, freeWindows, layOutDay, subjectLabel } from './scheduler';
import { dayCandidates } from './day-plan';

/**
 * The day, in one place. Today draws it and Focus runs it, so it cannot live
 * inside either screen — the two would drift and disagree about what is next.
 */
@Injectable({ providedIn: 'root' })
export class DayPlanner {
  private readonly store = inject(OnboardingStore);
  private readonly study = inject(StudyStore);

  /** Minutes a block has been pushed, keyed by chapter+task. Session-local. */
  private readonly pushed = signal<ReadonlyMap<string, number>>(new Map());
  private readonly skipped = signal<ReadonlySet<string>>(new Set());

  blocksFor(date: Date): Block[] {
    const weekday = date.getDay();
    const commitments = this.store.commitments();

    const windows = freeWindows(
      commitments,
      weekday,
      this.store.wakeMinute(),
      this.store.sleepMinute(),
    );

    const askedHours =
      weekday === 0 || weekday === 6 ? this.store.weekendHours() : this.store.weekdayHours();
    const free = windows.reduce((n, w) => n + w.minutes, 0);
    // Never plan more than the day physically has, even if the slider says so.
    const target = Math.min(askedHours * 60, free);

    const candidates = dayCandidates({
      subjectIds: this.store.subjects().map((s) => s.id),
      doneUnits: this.planningDone(date),
      parked: this.store.parkedChapters(),
      available: (id) => this.availableIn(id),
      learnedSubtopics: this.study.learnedSubtopics(),
      stat: (id) => this.study.stat(id),
      date,
      slots: 8,
      allChapters: this.store.allChapters(),
    });

    const laid = layOutDay(
      windows,
      commitments,
      weekday,
      candidates,
      target,
      this.coachingName(),
      this.store.breakMinutes(),
    );

    return this.applyEdits(laid, date);
  }

  readonly today = computed(() => this.blocksFor(startOfToday()));

  /** The study blocks of today that are still owed. */
  readonly remainingToday = computed(
    () => this.today().filter((b): b is StudyBlock => b.kind === 'study' && !b.done),
  );

  /**
   * What to work on now: the block whose slot contains this minute, else the
   * next one still owed, else nothing. The minute is passed in so callers that
   * tick a clock get a fresh answer.
   */
  nextBlock(minuteOfDay: number): StudyBlock | null {
    const owed = this.remainingToday();
    const current = owed.find(
      (b) => minuteOfDay >= b.startMinute && minuteOfDay < b.startMinute + b.minutes,
    );
    if (current) return current;
    return owed.find((b) => b.startMinute >= minuteOfDay) ?? owed[0] ?? null;
  }

  /** The block after the given one, and the gap between them. */
  after(block: StudyBlock): { next: StudyBlock | null; breakMinutes: number } {
    const owed = this.remainingToday().filter((b) => blockKey(b) !== blockKey(block));
    const next = owed.find((b) => b.startMinute >= block.startMinute) ?? null;
    const gap = next ? Math.max(0, next.startMinute - (block.startMinute + block.minutes)) : 0;
    return { next, breakMinutes: gap };
  }

  /** The next fixed commitment starting after this minute — coaching, school. */
  nextFixed(minuteOfDay: number): Block | null {
    return (
      this.today().find((b) => b.kind === 'fixed' && b.startMinute > minuteOfDay) ?? null
    );
  }

  push(block: StudyBlock, minutes: number): void {
    const next = new Map(this.pushed());
    const key = blockKey(block);
    next.set(key, (next.get(key) ?? 0) + minutes);
    this.pushed.set(next);
  }

  skip(block: StudyBlock): void {
    this.skipped.set(new Set(this.skipped()).add(blockKey(block)));
  }

  /**
   * Ticks made on the day being planned are held back, so finishing a block
   * does not rewrite the day underneath the user — it stays put and turns into
   * a logged one.
   */
  private planningDone(date: Date): ReadonlySet<string> {
    const logged = new Set(
      this.study
        .sessionsOn(dateKey(date))
        .map((s) => s.subtopicId)
        .filter((id): id is string => !!id),
    );
    if (logged.size === 0) return this.store.doneUnits();
    const out = new Set(this.store.doneUnits());
    for (const id of logged) out.delete(id);
    return out;
  }

  /** Pushes, skips, extras and logged state, applied over the generated day. */
  private applyEdits(blocks: Block[], date: Date): Block[] {
    const key = dateKey(date);
    const pushed = this.pushed();
    const skipped = this.skipped();

    const out = blocks
      .filter((b) => b.kind !== 'study' || !skipped.has(blockKey(b)))
      .map((b) => {
        if (b.kind !== 'study') return b;
        return {
          ...b,
          startMinute: b.startMinute + (pushed.get(blockKey(b)) ?? 0),
          done: this.study.isLogged(key, b.chapterId, b.task, b.subtopicId),
        };
      });

    for (const extra of this.study.extrasOn(key)) {
      const chapter = this.store.allChapters().find((c) => c.id === extra.chapterId);
      if (!chapter) continue;
      const subtopic = chapter.subtopics.find((t) => t.id === extra.subtopicId);
      out.push({
        kind: 'study',
        startMinute: extra.startMinute,
        minutes: extra.minutes,
        task: extra.task,
        title: subtopic?.name ?? chapter.name,
        context: subtopic ? `${subjectLabel(chapter)} · ${chapter.name}` : subjectLabel(chapter),
        chapterId: chapter.id,
        subtopicId: extra.subtopicId,
        questions: extra.task === 'Practice' ? Math.round(extra.minutes / 1.2) : undefined,
        done: this.study.isLogged(key, chapter.id, extra.task, extra.subtopicId),
      });
    }

    // An added block eats the free slot it was dropped into.
    const claimed = this.study.extrasOn(key);
    return out
      .filter(
        (b) =>
          b.kind !== 'gap' ||
          !claimed.some(
            (e) => e.startMinute >= b.startMinute && e.startMinute < b.startMinute + b.minutes,
          ),
      )
      .sort((a, b) => a.startMinute - b.startMinute);
  }

  /** A subject's chapters, in the chosen order and cut at the taught marker. */
  availableIn(subjectId: string): ReturnType<typeof availableChapters> {
    const subject = this.store.subjects().find((s) => s.id === subjectId);
    if (!subject) return [];
    return availableChapters(
      subject,
      this.store.orderMode(subjectId),
      this.store.customOrder().get(subjectId),
      this.store.taughtMarker(subjectId),
    );
  }

  private coachingName(): string {
    return COACHINGS.find((c) => c.id === this.store.coachingId())?.label ?? 'Coaching';
  }

  /** Minutes the real day layout gives study after fixed hours and breaks. */
  capacityOn(date: Date, revisionMinutes = 0): number {
    const weekday = date.getDay();
    const windows = freeWindows(
      this.store.commitments(), weekday, this.store.wakeMinute(), this.store.sleepMinute(),
    );
    const asked = (weekday === 0 || weekday === 6 ? this.store.weekendHours() : this.store.weekdayHours()) * 60;
    const free = windows.reduce((sum, window) => sum + window.minutes, 0);
    const candidates = Array.from({ length: 32 }, (_, index) => ({
      task: 'Learn' as const,
      chapter: { id: `projection.${index}`, name: '', cls: 11 as const, hours: 0, subtopics: [] } as Chapter,
      minutes: 90,
    }));
    const blocks = layOutDay(
      windows, this.store.commitments(), weekday, candidates, Math.min(asked, free), this.coachingName(), this.store.breakMinutes(),
    );
    const planned = blocks.filter((block): block is StudyBlock => block.kind === 'study')
      .reduce((sum, block) => sum + block.minutes, 0);
    return Math.max(0, planned - revisionMinutes);
  }
}

export function blockKey(block: StudyBlock): string {
  return `${block.chapterId}|${block.task}|${block.subtopicId ?? ''}`;
}
