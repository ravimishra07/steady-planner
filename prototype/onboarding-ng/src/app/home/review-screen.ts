import { ChangeDetectionStrategy, Component, computed, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatRippleModule } from '@angular/material/core';
import { OnboardingStore, addDays, startOfToday } from '../onboarding/state';
import { StudyStore, dateKey } from '../study/study-store';

/**
 * The Sunday reset. A week has a rhythm the daily screen cannot show: what
 * slipped, what the test said, and what the long day is for. It is also the
 * only place a test score is asked for, because a score that changes nothing
 * is a diary — this one pulls weak subjects forward in the queue.
 */
@Component({
  selector: 'app-review-screen',
  imports: [MatIconModule, MatRippleModule, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="bar">
      <button matRipple class="icon-btn" (click)="close.emit()" aria-label="Close">
        <mat-icon>close</mat-icon>
      </button>
      <h1 class="bar-title">Your week</h1>
    </header>

    <div class="scroll">
      <div class="metrics">
        <div class="metric">
          <span class="metric-value">{{ hours(weekMinutes()) }}</span>
          <span class="metric-label">studied</span>
        </div>
        <div class="metric">
          <span class="metric-value">{{ daysStudied() }}<span class="of">/7</span></span>
          <span class="metric-label">days</span>
        </div>
        <div class="metric">
          <span class="metric-value" [class.alert]="slipped() > 0">{{ slipped() }}</span>
          <span class="metric-label">slipped</span>
        </div>
      </div>

      <h2 class="group">How did the test go?</h2>
      <div class="sheet pad">
        @for (s of store.subjects(); track s.id) {
          <div class="score">
            <span class="score-name">{{ s.name }}</span>
            <input type="number" min="0" [ngModel]="correctFor(s.id)"
                   (ngModelChange)="setCorrect(s.id, +$event)" />
            <span class="of-total">of</span>
            <input type="number" min="0" [ngModel]="totalFor(s.id)"
                   (ngModelChange)="setTotal(s.id, +$event)" />
          </div>
        }
        <button matRipple class="filled-btn" (click)="saveTest()">Save and rebuild the queue</button>
        <p class="note">
          Anything under half comes back within two days, ahead of whatever the schedule had planned.
        </p>
      </div>

      <h2 class="group">What Sunday is for</h2>
      <div class="sheet">
        @for (row of focusList(); track row.chapter.id) {
          <div class="row">
            <span class="row-text">
              <span class="row-name">{{ row.chapter.name }}</span>
              <span class="row-meta">{{ row.reason }}</span>
            </span>
          </div>
        } @empty {
          <p class="note">Nothing outstanding. Use the long day to get ahead.</p>
        }
      </div>
    </div>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      height: 100%;
      background: var(--mat-sys-surface);
      color: var(--mat-sys-on-surface);
    }

    .bar { flex: none; display: flex; align-items: center; gap: 4px; height: 64px; padding: 0 16px 0 4px; }
    .bar-title { flex: 1; margin: 0; font: var(--mat-sys-title-large); }

    .icon-btn {
      display: grid;
      place-items: center;
      width: 48px;
      height: 48px;
      flex: none;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: transparent;
      color: var(--mat-sys-on-surface);
      cursor: pointer;
    }

    .scroll { flex: 1; min-height: 0; overflow-y: auto; padding: 0 16px 32px; }
    .metrics { display: flex; gap: 8px; padding: 8px 0 8px; }
    .metric { flex: 1; display: flex; flex-direction: column; gap: 2px; }
    .metric-value { font: var(--mat-sys-headline-small); }
    .metric-value.alert { color: var(--mat-sys-error); }
    .of { font: var(--mat-sys-body-medium); color: var(--mat-sys-on-surface-variant); }
    .metric-label { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }

    .group { margin: 24px 0 8px; font: var(--mat-sys-title-small); color: var(--mat-sys-on-surface-variant); }

    .sheet {
      display: flex;
      flex-direction: column;
      border-radius: var(--mat-sys-corner-large);
      background: var(--mat-sys-surface-container);
      overflow: hidden;
    }

    .sheet.pad { padding: 16px; gap: 12px; }
    .score { display: flex; align-items: center; gap: 8px; }
    .score-name { flex: 1; font: var(--mat-sys-body-medium); }
    .of-total { font: var(--mat-sys-label-medium); color: var(--mat-sys-on-surface-variant); }

    .score input {
      width: 64px;
      height: 40px;
      padding: 0 10px;
      border: 1px solid var(--mat-sys-outline);
      border-radius: var(--mat-sys-corner-small);
      background: transparent;
      color: var(--mat-sys-on-surface);
      font: var(--mat-sys-body-medium);
      text-align: center;
    }

    .filled-btn {
      height: 44px;
      margin-top: 4px;
      border: none;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-primary);
      color: var(--mat-sys-on-primary);
      font: var(--mat-sys-label-large);
      cursor: pointer;
    }

    .row { display: flex; align-items: center; min-height: 56px; padding: 8px 16px; }
    .row + .row { box-shadow: inset 0 1px 0 var(--mat-sys-outline-variant); }
    .row-text { display: flex; flex-direction: column; gap: 2px; }
    .row-name { font: var(--mat-sys-body-large); }
    .row-meta { font: var(--mat-sys-label-small); color: var(--mat-sys-on-surface-variant); }
    .note { margin: 0; padding: 12px 16px; font: var(--mat-sys-body-small); color: var(--mat-sys-on-surface-variant); }
  `,
})
export class ReviewScreen {
  readonly close = output<void>();

  protected readonly store = inject(OnboardingStore);
  private readonly study = inject(StudyStore);

  private readonly correct = signal<ReadonlyMap<string, number>>(new Map());
  private readonly total = signal<ReadonlyMap<string, number>>(new Map());

  protected correctFor(id: string): number { return this.correct().get(id) ?? 0; }
  protected totalFor(id: string): number { return this.total().get(id) ?? 0; }

  protected setCorrect(id: string, value: number): void {
    this.correct.set(new Map(this.correct()).set(id, value));
  }

  protected setTotal(id: string, value: number): void {
    this.total.set(new Map(this.total()).set(id, value));
  }

  protected readonly weekMinutes = computed(() =>
    this.study.rollingWeeks(1)[0]?.minutes ?? 0,
  );

  protected readonly daysStudied = computed(() => {
    const today = startOfToday();
    let n = 0;
    for (let i = 0; i < 7; i++) {
      if (this.study.minutesOn(dateKey(addDays(today, -i))) > 0) n++;
    }
    return n;
  });

  protected readonly slipped = computed(() => this.study.dueNow().length);

  /** What the long day should go on, and why — not a generic backlog list. */
  protected readonly focusList = computed(() =>
    this.study
      .dueNow()
      .slice(0, 5)
      .map((row) => ({
        chapter: row.chapter,
        reason:
          row.overdue > 0
            ? `Revision ${row.overdue} days late`
            : 'Revision due today',
      })),
  );

  protected saveTest(): void {
    const scores = this.store.subjects().map((s) => ({
      subjectId: s.id,
      correct: this.correctFor(s.id),
      total: this.totalFor(s.id),
    }));
    if (scores.every((s) => s.total === 0)) return;
    this.study.logTest(dateKey(startOfToday()), 'Weekly test', scores);
    this.close.emit();
  }

  protected hours(minutes: number): string {
    if (minutes <= 0) return '0h';
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return h === 0 ? `${m}m` : m === 0 ? `${h}h` : `${h}h ${m}m`;
  }
}
