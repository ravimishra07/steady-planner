import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatSliderModule } from '@angular/material/slider';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatButtonModule } from '@angular/material/button';
import { FormsModule } from '@angular/forms';
import { Shell } from '../shell';
import { OnboardingStore, addDays, startOfToday } from '../state';

/** The plan in the terms the user gave: days, hours, and what each day is for. */
@Component({
  selector: 'ob-plan-step',
  imports: [Shell, MatIconModule, MatSliderModule, MatDatepickerModule, MatButtonModule, FormsModule, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ob-shell
      title="Your plan"
      ctaLabel="Start day 1"
      [progressIndex]="store.progressSegments - 1"
      [segments]="store.progressSegments"
      (continue)="restart()">

      <p class="verdict">
        @if (store.gapHours() > 0) {
          You're <strong>{{ abs(store.gapHours()) }} hours short</strong> of the whole
          syllabus by {{ store.examDate() | date: 'd MMM' }}.
        } @else {
          You finish the syllabus with <strong>{{ abs(store.gapHours()) }} hours spare</strong>
          before {{ store.examDate() | date: 'd MMM' }}.
        }
      </p>

      <div class="phases">
        <div class="bar">
          <span class="learn" [style.flex]="store.learnDays()"></span>
          <span class="revise" [style.flex]="store.reviseDays()"></span>
          <span class="buffer" [style.flex]="store.bufferDays()"></span>
        </div>

        <div class="legend">
          <span class="key"><i class="swatch learn"></i>Learn · {{ store.learnDays() }}d</span>
          <span class="key"><i class="swatch revise"></i>Revise · {{ store.reviseDays() }}d</span>
          <span class="key"><i class="swatch buffer"></i>Slack · {{ store.bufferDays() }}d</span>
        </div>
      </div>

      <div class="rows">
        <div class="item">
          <div class="entry">
            <mat-icon>today</mat-icon>
            <span class="k">Every weekday</span>
            <span class="v">{{ store.weekdayHours() }}h</span>
          </div>
          @if (editing()) {
            <div class="editor">
              <mat-slider min="1" max="14" step="0.5" discrete>
                <input matSliderThumb
                       [ngModel]="store.weekdayHours()"
                       (ngModelChange)="store.weekdayHours.set($event)" />
              </mat-slider>
            </div>
          }
        </div>

        <div class="item">
          <div class="entry">
            <mat-icon>weekend</mat-icon>
            <span class="k">Every weekend day</span>
            <span class="v">{{ store.weekendHours() }}h</span>
          </div>
          @if (editing()) {
            <div class="editor">
              <mat-slider min="1" max="16" step="0.5" discrete>
                <input matSliderThumb
                       [ngModel]="store.weekendHours()"
                       (ngModelChange)="store.weekendHours.set($event)" />
              </mat-slider>
            </div>
          }
        </div>

        <div class="item">
          <div class="entry">
            <mat-icon>flag</mat-icon>
            <span class="k">Starts {{ today | date: 'd MMM' }}, ends</span>
            <span class="v">{{ store.examDate() | date: 'd MMM' }}</span>
          </div>
          @if (editing()) {
            <div class="editor">
              <mat-calendar
                [startAt]="store.targetDate()"
                [selected]="store.targetDate()"
                [minDate]="min"
                [maxDate]="max"
                (selectedChange)="pick($event)" />
            </div>
          }
        </div>
      </div>

      <button matButton="outlined" class="edit" (click)="editing.set(!editing())">
        <mat-icon>{{ editing() ? 'check' : 'edit' }}</mat-icon>
        {{ editing() ? 'Done editing' : 'Edit plan' }}
      </button>

      <p class="note">
        <mat-icon>settings</mat-icon>
        You can edit your plan any time in Settings.
      </p>
    </ob-shell>
  `,
  styles: `
    .verdict {
      margin: 0;
      font: var(--mat-sys-title-medium);
      color: var(--mat-sys-on-surface-variant);
    }

    .verdict strong { color: var(--mat-sys-on-surface); font-weight: 500; }

    .phases { display: flex; flex-direction: column; gap: 12px; }

    .bar { display: flex; gap: 4px; height: 12px; }
    .bar > span { border-radius: var(--mat-sys-corner-full); }

    .learn { background: var(--mat-sys-primary); }

    /* One hue, three depths — stages of the same plan, not three topics. */
    .revise { background: color-mix(in srgb, var(--mat-sys-primary) 45%, var(--mat-sys-surface)); }
    .buffer { background: var(--mat-sys-surface-container-highest); }

    .legend {
      display: flex;
      flex-wrap: wrap;
      gap: 8px 16px;
      font: var(--mat-sys-label-large);
      color: var(--mat-sys-on-surface-variant);
    }

    .key { display: flex; align-items: center; gap: 6px; }

    .swatch {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      display: inline-block;
    }

    .rows { display: flex; flex-direction: column; gap: 8px; }

    .item {
      display: flex;
      flex-direction: column;
      border-radius: var(--mat-sys-corner-large);
      background: var(--mat-sys-surface-container-high);
      overflow: hidden;
    }

    .entry {
      display: flex;
      align-items: center;
      gap: 16px;
      min-height: 56px;
      padding: 8px 16px;
    }

    .entry mat-icon { color: var(--mat-sys-on-surface-variant); }
    .k { flex: 1; font: var(--mat-sys-body-large); color: var(--mat-sys-on-surface); }
    .v { font: var(--mat-sys-title-medium); color: var(--mat-sys-primary); }

    /* Controls drop in under the row they change; the row keeps reading it back. */
    .editor { padding: 0 16px 12px; }
    .editor mat-slider { width: 100%; margin-inline: 0; }

    ::ng-deep .editor .mat-calendar-body-label { visibility: hidden; }

    /* Secondary action: outlined, full width, under the summary it edits. */
    .edit { width: 100%; height: 56px; flex: none; }

    .note {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0;
      font: var(--mat-sys-body-medium);
      color: var(--mat-sys-on-surface-variant);
    }

    .note mat-icon { font-size: 18px; width: 18px; height: 18px; }
  `,
})
export class PlanStep {
  protected readonly store = inject(OnboardingStore);
  protected readonly today = startOfToday();

  protected abs(n: number): number { return Math.abs(n); }

  /** The edit screen replaces the summary while it is open. */
  protected readonly editing = signal(false);

  protected readonly min = addDays(startOfToday(), 7);
  protected readonly max = addDays(startOfToday(), 730);

  protected pick(date: Date | null): void {
    if (date) this.store.targetDate.set(date);
  }

  /** Onboarding ends here; the app shell takes over. */
  protected restart(): void {
    this.store.started.set(true);
  }
}
