import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatRippleModule } from '@angular/material/core';
import { COACHINGS, OnboardingStore, addDays, startOfToday } from '../onboarding/state';
import { PACK, Chapter, ALL_CHAPTERS, chapterIsDone } from '../onboarding/exam-pack';

interface DayCell { date: Date; label: string; day: number; done: boolean; }

type Task = 'Learn' | 'Practice' | 'Revise';

interface Block {
  kind: 'study' | 'gap' | 'fixed';
  startMinute: number;
  minutes: number;
  title?: string;
  subject?: string;
  task?: Task;
  questions?: number;
  done?: boolean;
}

/** Coaching hours the plan must schedule around, not over. */
const CLASS_START = 16 * 60;
const CLASS_MINUTES = 180;

/** Minutes of the day rendered per pixel — duration has spatial meaning. */
const PX_PER_MINUTE = 0.9;
const MIN_BLOCK_HEIGHT = 64;

/**
 * Today: the calendar chrome, the day's budget, and the day itself as a
 * timeline. Mirrors HomeCalendarChrome.kt + HomeTimeline.kt.
 */
@Component({
  selector: 'app-today',
  imports: [MatIconModule, MatButtonModule, MatRippleModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="chrome">
      <button matRipple class="month" (click)="expanded.set(!expanded())">
        <span class="month-title">{{ selected() | date: 'MMMM y' }}</span>
        <mat-icon [class.up]="expanded()">keyboard_arrow_down</mat-icon>
      </button>

      <div class="weekdays">
        @for (d of weekdayLabels; track $index) { <span>{{ d }}</span> }
      </div>

      <div class="grid" [class.month-grid]="expanded()">
        @for (cell of (expanded() ? monthCells() : weekCells()); track $index) {
          @if (cell) {
            <button
              class="cell"
              [class.on]="isSelected(cell.date)"
              [class.today]="isToday(cell.date)"
              (click)="selected.set(cell.date)">
              <span class="num">{{ cell.day }}</span>
              @if (cell.done) { <span class="dot"></span> }
            </button>
          } @else {
            <span class="cell empty"></span>
          }
        }
      </div>
    </header>

    <section class="anchors">
      <span class="anchor">
        <mat-icon>quiz</mat-icon>
        Next test {{ nextTest() | date: 'EEE d MMM' }}
      </span>
      @if (backlog() > 0) {
        <span class="anchor warn">
          <mat-icon>error</mat-icon>
          {{ backlog() }} chapters behind pace
        </span>
      }
    </section>

    <section class="summary">
      <span class="left">
        <span class="planned">{{ format(plannedMinutes()) }}</span>
        <span class="caption">{{ format(completedMinutes()) }} done</span>
      </span>
      <span class="right">
        <span class="planned">{{ store.days() }} days to exam</span>
        <span class="caption">{{ syllabusPercent() }}% of syllabus</span>
      </span>
    </section>

    <section class="timeline">
      @for (block of blocks(); track block.startMinute) {
        <div class="row" [style.min-height.px]="height(block)">
          <span class="clock">{{ clock(block.startMinute) }}</span>

          <span class="rail">
            <span class="line" [class.dashed]="block.kind === 'gap'"></span>
            @if (block.kind === 'study') { <span class="node" [class.done]="block.done"></span> }
          </span>

          @if (block.kind === 'study') {
            <button matRipple class="block" [class.done]="block.done">
              <span class="block-head">
                <span class="tag" [class]="'tag-' + block.task!.toLowerCase()">{{ block.task }}</span>
                <span class="len">{{ format(block.minutes) }}</span>
              </span>
              <span class="title">{{ block.title }}</span>
              <span class="subject">
                {{ block.subject }}@if (block.questions) { · {{ block.questions }} questions }
              </span>
              @if (!block.done) {
                <span class="start"><mat-icon>play_arrow</mat-icon>Start</span>
              }
            </button>
          } @else if (block.kind === 'fixed') {
            <div class="block fixed">
              <span class="block-head">
                <span class="tag tag-class">Class</span>
                <span class="len">{{ format(block.minutes) }}</span>
              </span>
              <span class="title">{{ block.title }}</span>
              <span class="subject">{{ block.subject }}</span>
            </div>
          } @else {
            <button matRipple class="gap">
              <mat-icon>add</mat-icon>
              {{ format(block.minutes) }} free
            </button>
          }
        </div>
      }
    </section>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      height: 100%;
      overflow-y: auto;
      overflow-x: hidden;
    }

    /* Calendar chrome */
    .chrome { flex: none; padding-bottom: 8px; border-bottom: 1px solid var(--mat-sys-outline-variant); }

    .month {
      display: flex;
      align-items: center;
      gap: 4px;
      width: 100%;
      padding: 12px 16px 8px;
      border: none;
      background: transparent;
      color: var(--mat-sys-on-surface);
      cursor: pointer;
    }

    .month-title { flex: 1; text-align: left; font: var(--mat-sys-title-large); }
    .month mat-icon { color: var(--mat-sys-on-surface-variant); transition: transform 150ms; }
    .month mat-icon.up { transform: rotate(180deg); }

    .weekdays, .grid {
      display: grid;
      grid-template-columns: repeat(7, 1fr);
      gap: 2px;
      padding: 0 16px;
    }

    .weekdays span {
      text-align: center;
      font: var(--mat-sys-label-medium);
      color: var(--mat-sys-on-surface-variant);
      padding-bottom: 4px;
    }

    .cell {
      position: relative;
      height: 44px;
      display: grid;
      place-items: center;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface);
      font: var(--mat-sys-body-medium);
      cursor: pointer;
    }

    .cell.today .num { color: var(--mat-sys-primary); font-weight: 600; }

    .cell.on {
      background: var(--mat-sys-primary);
      color: var(--mat-sys-on-primary);
    }

    .cell.on .num { color: var(--mat-sys-on-primary); }
    .cell.empty { cursor: default; }

    /* A day that carries study shows a dot, as in DayCell's status. */
    .dot {
      position: absolute;
      bottom: 6px;
      width: 4px;
      height: 4px;
      border-radius: 50%;
      background: currentColor;
      opacity: .7;
    }

    /* Day summary */
    .summary {
      flex: none;
      display: flex;
      justify-content: space-between;
      padding: 12px 16px;
    }

    .left, .right { display: flex; flex-direction: column; gap: 2px; }
    .right { align-items: flex-end; }
    .planned { font: var(--mat-sys-title-medium); }
    .caption { font: var(--mat-sys-label-medium); color: var(--mat-sys-on-surface-variant); }

    /* Timeline */
    .timeline { padding: 0 16px 24px; }

    .row { display: flex; align-items: stretch; }

    .clock {
      width: 52px;
      flex: none;
      padding-top: 2px;
      font: var(--mat-sys-label-small);
      color: var(--mat-sys-on-surface-variant);
    }

    .rail {
      position: relative;
      width: 20px;
      flex: none;
      display: flex;
      justify-content: center;
    }

    .line {
      width: 2px;
      margin-top: 10px;
      background: var(--mat-sys-outline-variant);
    }

    .line.dashed {
      background: repeating-linear-gradient(
        var(--mat-sys-outline-variant) 0 5px,
        transparent 5px 10px
      );
    }

    .node {
      position: absolute;
      top: 2px;
      width: 10px;
      height: 10px;
      border-radius: 50%;
      background: var(--mat-sys-primary);
    }

    .node.done { background: var(--mat-sys-outline); }

    .block {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 4px;
      margin: 0 0 12px 8px;
      padding: 12px 16px;
      border: none;
      border-radius: var(--mat-sys-corner-large);
      background: var(--mat-sys-surface-container-high);
      color: var(--mat-sys-on-surface);
      text-align: left;
      cursor: pointer;
    }

    .block.done { color: var(--mat-sys-on-surface-variant); }

    .block-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }

    /* Task type carries the block's meaning; NEET is won on practice volume. */
    .tag {
      padding: 2px 10px;
      border-radius: var(--mat-sys-corner-full);
      font: var(--mat-sys-label-medium);
    }

    .tag-learn { background: var(--mat-sys-secondary-container); color: var(--mat-sys-on-secondary-container); }
    .tag-practice { background: var(--mat-sys-primary); color: var(--mat-sys-on-primary); }
    .tag-revise {
      background: transparent;
      color: var(--mat-sys-primary);
      box-shadow: inset 0 0 0 1px var(--mat-sys-outline);
    }
    .tag-class { background: var(--mat-sys-surface-container-highest); color: var(--mat-sys-on-surface-variant); }

    .block.fixed { cursor: default; opacity: .75; }

    .anchors {
      flex: none;
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      padding: 12px 16px 0;
    }

    .anchor {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 6px 12px;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-surface-container-high);
      font: var(--mat-sys-label-large);
      color: var(--mat-sys-on-surface-variant);
    }

    .anchor.warn { background: var(--mat-sys-error-container); color: var(--mat-sys-on-error-container); }
    .anchor mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .title { font: var(--mat-sys-title-medium); }
    .len { font: var(--mat-sys-label-large); color: var(--mat-sys-on-surface-variant); }
    .subject { font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }

    .start {
      display: flex;
      align-items: center;
      gap: 4px;
      margin-top: 4px;
      font: var(--mat-sys-label-large);
      color: var(--mat-sys-primary);
    }

    .start mat-icon { font-size: 18px; width: 18px; height: 18px; }

    /* Free time is an invitation, not a card. */
    .gap {
      flex: 1;
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0 0 12px 8px;
      padding: 12px 16px;
      border: 1px dashed var(--mat-sys-outline-variant);
      border-radius: var(--mat-sys-corner-large);
      background: transparent;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .gap mat-icon { font-size: 18px; width: 18px; height: 18px; }
  `,
})
export class TodayScreen {
  protected readonly store = inject(OnboardingStore);
  protected readonly weekdayLabels = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];

  protected readonly expanded = signal(false);
  protected readonly selected = signal(startOfToday());

  /** Sunday-anchored week around the selected date. */
  protected readonly weekCells = computed<DayCell[]>(() => {
    const start = addDays(this.selected(), -this.selected().getDay());
    return Array.from({ length: 7 }, (_, i) => this.cell(addDays(start, i)));
  });

  protected readonly monthCells = computed<(DayCell | null)[]>(() => {
    const first = new Date(this.selected().getFullYear(), this.selected().getMonth(), 1);
    const days = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate();
    const lead = Array.from({ length: first.getDay() }, () => null);
    const cells = Array.from({ length: days }, (_, i) =>
      this.cell(new Date(first.getFullYear(), first.getMonth(), i + 1)),
    );
    return [...lead, ...cells];
  });

  /** The next Sunday — coaching weeks are ruled by the weekly test. */
  protected readonly nextTest = computed(() => {
    const d = this.selected();
    return addDays(d, (7 - d.getDay()) % 7 || 7);
  });

  /** Chapters the plan needed by now but that are still unticked. */
  protected readonly backlog = computed(() => {
    const elapsed = Math.max(0, Math.round((startOfToday().getTime() - this.planStart) / 86_400_000));
    const expected = Math.floor(elapsed / 3);
    const done = ALL_CHAPTERS.filter((c) => chapterIsDone(c, this.store.doneUnits())).length;
    return Math.max(0, expected - done);
  });

  /**
   * The day, built from the plan: coaching hours are fixed, study fills the
   * gaps, and the mix follows the paper — half of it is Biology.
   */
  protected readonly blocks = computed<Block[]>(() => {
    const hours = this.dayHours();
    if (hours <= 0) return [];

    const picks = this.pickChapters(3);
    const attends = this.store.coachingId() !== 'self' && this.store.coachingId() !== '';
    const tasks: Task[] = ['Learn', 'Practice', 'Revise'];

    // Sessions run 60-90 min: longer than that and nobody holds attention.
    const sessions = Math.max(1, Math.min(4, Math.ceil((hours * 60) / 90)));
    const each = Math.round((hours * 60) / sessions / 15) * 15;

    const out: Block[] = [];
    let minute = 9 * 60;

    for (let i = 0; i < sessions; i++) {
      if (attends && minute + each > CLASS_START && !out.some((b) => b.kind === 'fixed')) {
        if (minute < CLASS_START) {
          out.push({ kind: 'gap', startMinute: minute, minutes: CLASS_START - minute });
        }
        out.push({
          kind: 'fixed',
          startMinute: CLASS_START,
          minutes: CLASS_MINUTES,
          title: 'Coaching class',
          subject: this.coachingName(),
        });
        minute = CLASS_START + CLASS_MINUTES;
      }

      const chapter = picks[i % Math.max(1, picks.length)];
      const task = tasks[i % tasks.length];
      out.push({
        kind: 'study',
        startMinute: minute,
        minutes: each,
        title: chapter?.name ?? 'Revision',
        subject: chapter ? subjectName(chapter.id) : 'Mixed',
        task,
        questions: task === 'Practice' ? Math.round(each / 1.5) : undefined,
        done: i === 0 && this.isToday(this.selected()),
      });
      minute += each;

      if (i < sessions - 1) {
        out.push({ kind: 'gap', startMinute: minute, minutes: 45 });
        minute += 45;
      }
    }
    return merge(out);
  });

  protected readonly plannedMinutes = computed(() =>
    this.blocks().filter((b) => b.kind === 'study').reduce((n, b) => n + b.minutes, 0),
  );

  protected readonly completedMinutes = computed(() =>
    this.blocks().filter((b) => b.kind === 'study' && b.done).reduce((n, b) => n + b.minutes, 0),
  );

  protected readonly syllabusPercent = computed(() => {
    const done = ALL_CHAPTERS.filter((c) => chapterIsDone(c, this.store.doneUnits())).length;
    return Math.round((done / ALL_CHAPTERS.length) * 100);
  });

  private readonly planStart = startOfToday().getTime();

  private coachingName(): string {
    return COACHINGS.find((c) => c.id === this.store.coachingId())?.label ?? 'Coaching';
  }

  /**
   * Weighted by the paper: Biology is 90 of 180 questions, so half the picks
   * come from Botany and Zoology.
   */
  private pickChapters(count: number): Chapter[] {
    const order = ['botany', 'physics', 'zoology', 'chemistry'];
    const done = this.store.doneUnits();
    const out: Chapter[] = [];
    for (const subjectId of order) {
      const subject = PACK.subjects.find((s) => s.id === subjectId)!;
      const next = subject.sections
        .flatMap((sec) => sec.chapters)
        .find((c) => !chapterIsDone(c, done));
      if (next) out.push(next);
      if (out.length === count) break;
    }
    return out;
  }

  protected isToday(date: Date): boolean {
    return date.toDateString() === startOfToday().toDateString();
  }

  protected isSelected(date: Date): boolean {
    return date.toDateString() === this.selected().toDateString();
  }

  protected height(block: Block): number {
    return Math.max(MIN_BLOCK_HEIGHT, Math.round(block.minutes * PX_PER_MINUTE));
  }

  protected clock(minuteOfDay: number): string {
    const h = Math.floor(minuteOfDay / 60);
    const m = minuteOfDay % 60;
    const suffix = h < 12 ? 'AM' : 'PM';
    const hour = h % 12 === 0 ? 12 : h % 12;
    return `${hour}:${String(m).padStart(2, '0')} ${suffix}`;
  }

  protected format(minutes: number): string {
    if (minutes <= 0) return '0 min';
    if (minutes < 60) return `${minutes} min`;
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return m === 0 ? `${h}h` : `${h}h ${m}m`;
  }

  private dayHours(): number {
    const day = this.selected().getDay();
    return day === 0 || day === 6 ? this.store.weekendHours() : this.store.weekdayHours();
  }

  private cell(date: Date): DayCell {
    return {
      date,
      label: this.weekdayLabels[date.getDay()],
      day: date.getDate(),
      done: date >= startOfToday() && date <= this.store.targetDate(),
    };
  }

}

/** Two gaps in a row is a rendering artefact, not two breaks. */
function merge(blocks: Block[]): Block[] {
  const out: Block[] = [];
  for (const block of blocks) {
    const last = out[out.length - 1];
    if (block.kind === 'gap' && last?.kind === 'gap') {
      last.minutes += block.minutes;
      continue;
    }
    out.push({ ...block });
  }
  return out.filter((b) => b.kind !== 'gap' || b.minutes >= 30);
}

function subjectName(chapterId: string): string {
  return PACK.subjects.find((s) => chapterId.startsWith(s.id + '.'))!.name;
}
